/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public final class ModelFactory {

	/**
	 * 符合条件的 {@link ModelAttribute} 方法
	 */
	private final List<ModelMethod> modelMethods = new ArrayList<>();

	/**
	 * 创建 {@link WebDataBinder} 实例的工厂
	 */
	private final WebDataBinderFactory dataBinderFactory;

	/**
	 * 将属性保存在会话中工具类
	 */
	private final SessionAttributesHandler sessionAttributesHandler;


	/**
	 * 使用传入的 {@code @ModelAttribute} 方法创建实例
	 * @param handlerMethods the {@code @ModelAttribute} methods to invoke
	 * @param binderFactory for preparation of {@link BindingResult} attributes
	 * @param attributeHandler for access to session attributes
	 */
	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}


	/**
	 * 按照下面的顺序填充模型
	 * <ol>
	 * <li> 使用了 {@code @SessionAttributes} 注解的方法
	 * <li> 使用了 {@code @ModelAttribute} 注解的方法
	 * <li> 使用了 {@code @ModelAttribute} 注解的方法同时参数也在 {@code @SessionAttributes} 中
	 * </ol>
	 * @param request the current request
	 * @param container a container with the model to be initialized
	 * @param handlerMethod the method for which the model is initialized
	 * @throws Exception may arise from {@code @ModelAttribute} methods
	 */
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		// 读取会话中的保存的所有有关 @SessionAttributes 的属性
		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		// 1、属性合并
		container.mergeAttributes(sessionAttributes);
		// 2、执行标注了 @ModelAttribute 的方法
		invokeModelAttributeMethods(request, container);

		// 3、遍历标注了 @ModelAttribute 的参数同时也标注了 @SessionAttributes 的参数
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			if (!container.containsAttribute(name)) {
				// 读取会话中的保存的特定的 @SessionAttributes 属性
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value);
			}
		}
	}

	/**
	 * 执行标注了 {@link ModelAttribute} 的方法
	 */
	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		while (!this.modelMethods.isEmpty()) {
			// 获得需要执行的Model方法
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			// 获得方法上的 @ModelAttribute 属性值
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			// 一般情况下此时的Model都没有属性
			if (container.containsAttribute(ann.name())) {
				if (!ann.binding()) {
					container.setBindingDisabled(ann.name());
				}
				continue;
			}

			// 解析方法的参数，并执行目标方法
			Object returnValue = modelMethod.invokeForRequest(request, container);
			// 当返回值不是Void的情况
			if (!modelMethod.isVoid()){
				// 获得返回值的类型名称，比如说返回值是String类型，那么值就是string
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				// 将返回值保存到模型中
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	/**
	 * 返回下一个需要执行的Model方法
	 * <p>以入参上有 @ModelAttribute 优先</p>
	 * @param container
	 * @return
	 */
	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			// 选择有依赖关系的方法优先
			if (modelMethod.checkDependencies(container)) {
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		ModelMethod modelMethod = this.modelMethods.get(0);
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}

	/**
	 * 找到标注了 {@code @ModelAttribute} 的参数同时也标注了 {@code @SessionAttributes} 的参数
	 */
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		// 遍历方法的所有入参
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			// 判断是否有指定注解
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				// 拿到参数的名称
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				// 判断属性是否在 需要设置到会话的属性集合中
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}

	/**
	 * 将 model 中有关 {@code @SessionAttributes} 的属性保存在会话中
	 * Add {@link BindingResult} attributes where necessary.
	 * @param request the current request
	 * @param container contains the model to update
	 * @throws Exception if creating BindingResult attributes fails
	 */
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		// 注意：是直接使用默认模型，而不是通过getModel()获得的
		ModelMap defaultModel = container.getDefaultModel();
		// 判断会话还没有结束
		if (container.getSessionStatus().isComplete()){
			// 清除会话中的保存的所有有关 SessionAttributes 的属性
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			// 将 model 中有关 @SessionAttributes 的属性保存在会话中
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		// 如果请求还没有完全处理完毕，更新 BindingResult
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			// 添加 BindingResult 属性到Model中
			updateBindingResult(request, defaultModel);
		}
	}

	/**
	 * 添加 {@code BindingResult} 属性到Model中
	 */
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		// 遍历所有Model中的属性
		for (String name : keyNames) {
			Object value = model.get(name);
			// 是否是候选的 BindingResult 属性
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}

	/**
	 * 判断 {@link BindingResult} 属性是否应该填充在Model中
	 */
	private boolean isBindingCandidate(String attributeName, Object value) {
		// 是否是有关 BindingResult 的属性
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}
		// 判断属性是否满足 {@code SessionAttribute}
		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		// 判断类型是否满足条件
		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}


	/**
	 * 拿到参数的名称
	 * @param parameter a descriptor for the method parameter
	 * @return the derived name
	 * @see Conventions#getVariableNameForParameter(MethodParameter)
	 */
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		// 要么使用注解中的参数名称，要么用参数定义的名称
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}

	/**
	 * 为给定的返回值派生模型属性名，基于:
	 * <ol>
	 * <li> {@code ModelAttribute}
	 * <li> 声明的类型
	 * <li> 实际的返回值类型
	 * </ol>
	 * @param returnValue the value returned from a method invocation
	 * @param returnType a descriptor for the return type of the method
	 * @return the derived name (never {@code null} or empty String)
	 */
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		// 1、使用 @ModelAttribute 中的参数名作为名称
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		/**
		 * HandlerMethod的扩展，负责如何绑定方法入参中的参数值
		 */
		private final InvocableHandlerMethod handlerMethod;

		/**
		 * 方法中标注了 {@link ModelAttribute} 的入参
		 */
		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			// 将入参中有 @ModelAttribute 注解的保存起来
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		/**
		 * 选择有依赖关系的方法
		 * @param mavContainer
		 * @return
		 */
		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
