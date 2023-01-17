/*
 * Copyright 2002-2021 the original author or authors.
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

import java.beans.ConstructorProperties;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolve {@code @ModelAttribute} annotated method arguments and handle
 * return values from {@code @ModelAttribute} annotated methods.
 *
 * <p>Model attributes are obtained from the model or created with a default
 * constructor (and then added to the model). Once created the attribute is
 * populated via data binding to Servlet request parameters. Validation may be
 * applied if the argument is annotated with {@code @javax.validation.Valid}.
 * or Spring's own {@code @org.springframework.validation.annotation.Validated}.
 *
 * <p>When this handler is created with {@code annotationNotRequired=true}
 * any non-simple type argument and return value is regarded as a model
 * attribute with or without the presence of an {@code @ModelAttribute}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Vladislav Kisel
 * @since 3.1
 */
public class ModelAttributeMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {

	/**
	 * 参数名发现器
	 */
	private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 注解不存在时候，是否由此参数解析器解析
	 */
	private final boolean annotationNotRequired;


	/**
	 * Class constructor.
	 * @param annotationNotRequired if "true", non-simple method arguments and
	 * return values are considered model attributes with or without a
	 * {@code @ModelAttribute} annotation
	 */
	public ModelAttributeMethodProcessor(boolean annotationNotRequired) {
		this.annotationNotRequired = annotationNotRequired;
	}


	/**
	 * 此参数解析器支持：
	 * <ul>
	 *     <li>支持 @{@code ModelAttribute}</li>
	 *     <li>支持 非简单类型</li>
	 * </ul>
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (parameter.hasParameterAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(parameter.getParameterType())));
	}

	/**
	 * Resolve the argument from the model or if not found instantiate it with
	 * its default if it is available. The model attribute is then populated
	 * with request values via data binding and optionally validated
	 * if {@code @java.validation.Valid} is present on the argument.
	 * @throws BindException if data binding and validation result in an error
	 * and the next method parameter is not of type {@link Errors}
	 * @throws Exception if WebDataBinder initialization fails
	 */
	@Override
	@Nullable
	public final Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		Assert.state(mavContainer != null, "ModelAttributeMethodProcessor requires ModelAndViewContainer");
		Assert.state(binderFactory != null, "ModelAttributeMethodProcessor requires WebDataBinderFactory");

		// 拿到参数名称
		String name = ModelFactory.getNameForParameter(parameter);
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		// 是否允许此参数进行绑定
		if (ann != null) {
			mavContainer.setBinding(name, ann.binding());
		}

		Object attribute = null;
		BindingResult bindingResult = null;

		// 能从Model中获取
		if (mavContainer.containsAttribute(name)) {
			attribute = mavContainer.getModel().get(name);
		}
		else {
			try {
				// 获得指定参数的值，然后转换为指定类型
				attribute = createAttribute(name, parameter, binderFactory, webRequest);
			}
			catch (BindException ex) {
				// 是否引发致命绑定异常
				if (isBindExceptionRequired(parameter)) {
					// No BindingResult parameter -> fail with BindException
					throw ex;
				}
				// 其他情况，比如说是空值对象
				if (parameter.getParameterType() == Optional.class) {
					attribute = Optional.empty();
				}
				bindingResult = ex.getBindingResult();
			}
		}

		if (bindingResult == null) {
			// 参数绑定和参数校验
			WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name);
			if (binder.getTarget() != null) {
				// 如果没有禁止此参数的绑定
				if (!mavContainer.isBindingDisabled(name)) {
					bindRequestParameters(binder, webRequest);
				}
				validateIfApplicable(binder, parameter);
				// 绑定过程中如果出现了异常并且不允许致命异常，那么抛出异常
				if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
					throw new BindException(binder.getBindingResult());
				}
			}
			// 当参数值和指定参数类型不匹配的时候，进行转换
			if (!parameter.getParameterType().isInstance(attribute)) {
				attribute = binder.convertIfNecessary(binder.getTarget(), parameter.getParameterType(), parameter);
			}
			bindingResult = binder.getBindingResult();
		}

		// 在模型的最后面添加有关绑定的相关参数
		Map<String, Object> bindingResultModel = bindingResult.getModel();
		mavContainer.removeAttributes(bindingResultModel);
		mavContainer.addAllAttributes(bindingResultModel);

		return attribute;
	}

	/**
	 * 创建指定的类型的实例
	 * Extension point to create the model attribute if not found in the model,
	 * with subsequent parameter binding through bean properties (unless suppressed).
	 * <p>The default implementation typically uses the unique public no-arg constructor
	 * if available but also handles a "primary constructor" approach for data classes:
	 * It understands the JavaBeans {@link ConstructorProperties} annotation as well as
	 * runtime-retained parameter names in the bytecode, associating request parameters
	 * with constructor arguments by name. If no such constructor is found, the default
	 * constructor will be used (even if not public), assuming subsequent bean property
	 * bindings through setter methods.
	 * @param attributeName the name of the attribute (never {@code null})
	 * @param parameter the method parameter declaration
	 * @param binderFactory for creating WebDataBinder instance
	 * @param webRequest the current request
	 * @return the created model attribute (never {@code null})
	 * @throws BindException in case of constructor argument binding failure
	 * @throws Exception in case of constructor invocation failure
	 * @see #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)
	 * @see BeanUtils#findPrimaryConstructor(Class)
	 */
	protected Object createAttribute(String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		MethodParameter nestedParameter = parameter.nestedIfOptional();
		Class<?> clazz = nestedParameter.getNestedParameterType();

		// 返回所提供类的主构造函数(Kotlin的情况)
		Constructor<?> ctor = BeanUtils.findPrimaryConstructor(clazz);
		if (ctor == null) {
			Constructor<?>[] ctors = clazz.getConstructors();
			// 如果只有一个构造方法就直接使用，否则就使用无参构造方法
			if (ctors.length == 1) {
				ctor = ctors[0];
			}
			else {
				try {
					ctor = clazz.getDeclaredConstructor();
				}
				catch (NoSuchMethodException ex) {
					throw new IllegalStateException("No primary or default constructor found for " + clazz, ex);
				}
			}
		}

		// 使用给定的构造函数构造一个新的属性实例
		Object attribute = constructAttribute(ctor, attributeName, parameter, binderFactory, webRequest);
		if (parameter != nestedParameter) {
			attribute = Optional.of(attribute);
		}
		return attribute;
	}

	/**
	 * 使用给定的构造函数构造一个新的实例
	 * @param ctor
	 * @param attributeName 方法入参上的属性名
	 * @param parameter 方法入参上对应的 MethodParameter
	 * @param binderFactory
	 * @param webRequest
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	protected Object constructAttribute(Constructor<?> ctor, String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		// 使用给定的构造函数构造一个新的属性实例，一般情况下都无实现
		Object constructed = constructAttribute(ctor, attributeName, binderFactory, webRequest);
		if (constructed != null) {
			return constructed;
		}

		// 如果是无参构造，直接实例化就好了
		if (ctor.getParameterCount() == 0) {
			// A single default constructor -> clearly a standard JavaBeans arrangement.
			return BeanUtils.instantiateClass(ctor);
		}

		// 到这就说明是有参构造
		// 看构造方法上是否有 @ConstructorProperties
		ConstructorProperties cp = ctor.getAnnotation(ConstructorProperties.class);
		// 从注解上还是从方法上获取参数名称
		String[] paramNames = (cp != null ? cp.value() : parameterNameDiscoverer.getParameterNames(ctor));
		Assert.state(paramNames != null, () -> "Cannot resolve parameter names for constructor " + ctor);
		Class<?>[] paramTypes = ctor.getParameterTypes();
		Assert.state(paramNames.length == paramTypes.length,
				() -> "Invalid number of parameter names: " + paramNames.length + " for constructor " + ctor);

		Object[] args = new Object[paramTypes.length];
		// 创建此参数对应的 WebDataBinder
		WebDataBinder binder = binderFactory.createBinder(webRequest, null, attributeName);
		String fieldDefaultPrefix = binder.getFieldDefaultPrefix();
		String fieldMarkerPrefix = binder.getFieldMarkerPrefix();
		boolean bindingFailure = false;
		// 出错的参数
		Set<String> failedParams = new HashSet<>(4);

		// 开始构建构造方法入参
		for (int i = 0; i < paramNames.length; i++) {
			String paramName = paramNames[i];
			Class<?> paramType = paramTypes[i];
			// 获得参数值
			Object value = webRequest.getParameterValues(paramName);

			// Since WebRequest#getParameter exposes a single-value parameter as an array
			// with a single element, we unwrap the single value in such cases, analogous
			// to WebExchangeDataBinder.addBindValue(Map<String, Object>, String, List<?>).
			// 如果参数值是数组而且只有一个数据，那还是当成单值处理
			if (ObjectUtils.isArray(value) && Array.getLength(value) == 1) {
				value = Array.get(value, 0);
			}

			// 如果找不到指定的参数
			if (value == null) {
				// 对属性名进行修正，然后再去找
				if (fieldDefaultPrefix != null) {
					value = webRequest.getParameter(fieldDefaultPrefix + paramName);
				}
				// 对属性名进行修正，然后再去找
				if (value == null && fieldMarkerPrefix != null) {
					if (webRequest.getParameter(fieldMarkerPrefix + paramName) != null) {
						value = binder.getEmptyValue(paramType);
					}
				}
			}

			try {
				MethodParameter methodParam = new FieldAwareConstructorParameter(ctor, i, paramName);
				// 如果参数是空值对象
				if (value == null && methodParam.isOptional()) {
					args[i] = (methodParam.getParameterType() == Optional.class ? Optional.empty() : null);
				}
				else {
					// 转换为指定类型
					args[i] = binder.convertIfNecessary(value, paramType, methodParam);
				}
			}
			catch (TypeMismatchException ex) {
				// 抛出了类型不匹配异常
				ex.initPropertyName(paramName);
				args[i] = value;
				failedParams.add(paramName);

				// 记录错误字段和执行错误回调
				binder.getBindingResult().recordFieldValue(paramName, paramType, value);
				binder.getBindingErrorProcessor().processPropertyAccessException(ex, binder.getBindingResult());
				bindingFailure = true;
			}
		}

		// 如果绑定过程中抛出了异常
		if (bindingFailure) {
			BindingResult result = binder.getBindingResult();
			for (int i = 0; i < paramNames.length; i++) {
				String paramName = paramNames[i];
				// 找到错误的参数
				if (!failedParams.contains(paramName)) {
					Object value = args[i];
					// 搞不懂明明在上面的catch中已经记录了错误字段信息，这里又执行一次
					result.recordFieldValue(paramName, paramTypes[i], value);
					// 进行参数验证
					validateValueIfApplicable(binder, parameter, ctor.getDeclaringClass(), paramName, value);
				}
			}
			throw new BindException(result);
		}

		// 利用有参构造进行实例化
		return BeanUtils.instantiateClass(ctor, args);
	}

	/**
	 * 使用给定的构造函数构造一个新的属性实例。
	 * @since 5.0
	 * @deprecated as of 5.1, in favor of
	 * {@link #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}
	 */
	@Deprecated
	@Nullable
	protected Object constructAttribute(Constructor<?> ctor, String attributeName,
			WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

		return null;
	}

	/**
	 * 参数绑定
	 * Extension point to bind the request to the target object.
	 * @param binder the data binder instance to use for the binding
	 * @param request the current request
	 */
	protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
		((WebRequestDataBinder) binder).bind(request);
	}

	/**
	 * Validate the model attribute if applicable.
	 * <p>The default implementation checks for {@code @javax.validation.Valid},
	 * Spring's {@link org.springframework.validation.annotation.Validated},
	 * and custom annotations whose name starts with "Valid".
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @see WebDataBinder#validate(Object...)
	 * @see SmartValidator#validate(Object, Errors, Object...)
	 */
	protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
		for (Annotation ann : parameter.getParameterAnnotations()) {
			Object[] validationHints = determineValidationHints(ann);
			if (validationHints != null) {
				binder.validate(validationHints);
				break;
			}
		}
	}

	/**
	 * 进行参数验证，可用于校验的如下：
	 * <ul>
	 *     <li>使用 {@code Valid}</li>
	 *     <li>Spring的 {@code Validated}</li>
	 *     <li>为 "Valid" 开头的类</li>
	 * </ul>
	 * @param binder the DataBinder to be used
	 * @param parameter the method parameter declaration
	 * @param targetType the target type
	 * @param fieldName the name of the field
	 * @param value the candidate value
	 * @since 5.1
	 * @see #validateIfApplicable(WebDataBinder, MethodParameter)
	 * @see SmartValidator#validateValue(Class, String, Object, Errors, Object...)
	 */
	protected void validateValueIfApplicable(WebDataBinder binder, MethodParameter parameter,
			Class<?> targetType, String fieldName, @Nullable Object value) {

		for (Annotation ann : parameter.getParameterAnnotations()) {
			// 如果是校验注解，那就返回校验注解的一个或多个校验组
			Object[] validationHints = determineValidationHints(ann);
			if (validationHints != null) {
				// 开始校验
				for (Validator validator : binder.getValidators()) {
					if (validator instanceof SmartValidator) {
						try {
							((SmartValidator) validator).validateValue(targetType, fieldName, value,
									binder.getBindingResult(), validationHints);
						}
						catch (IllegalArgumentException ex) {
							// No corresponding field on the target class...
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * 如果是校验注解，那就返回校验注解的一个或多个校验组
	 * @param ann
	 * @return
	 */
	@Nullable
	private Object[] determineValidationHints(Annotation ann) {
		Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
		// 要么是@Validated要么是以为Valid开头
		if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
			Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
			if (hints == null) {
				return new Object[0];
			}
			return (hints instanceof Object[] ? (Object[]) hints : new Object[] {hints});
		}
		return null;
	}

	/**
	 * 是否允许致命异常
	 * <p>The default implementation delegates to {@link #isBindExceptionRequired(MethodParameter)}.
	 * @param binder the data binder used to perform data binding
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @see #isBindExceptionRequired(MethodParameter)
	 */
	protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
		return isBindExceptionRequired(parameter);
	}

	/**
	 * 当指定指定参数的后面是一个 {@code Errors} 时，返回true
	 * <p>比如说倒数第二个参数抛出了异常，那么倒数第一个是异常类型，那么就可以丢给处理程序处理</p>
	 * @param parameter the method parameter declaration
	 * @return {@code true} if the next method parameter is not of type {@link Errors}
	 * @since 5.0
	 */
	protected boolean isBindExceptionRequired(MethodParameter parameter) {
		int i = parameter.getParameterIndex();
		Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
		boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
		return !hasBindingResult;
	}

	/**
	 * Return {@code true} if there is a method-level {@code @ModelAttribute}
	 * or, in default resolution mode, for any return value type that is not
	 * a simple type.
	 */
	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (returnType.hasMethodAnnotation(ModelAttribute.class) ||
				(this.annotationNotRequired && !BeanUtils.isSimpleProperty(returnType.getParameterType())));
	}

	/**
	 * Add non-null return values to the {@link ModelAndViewContainer}.
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue != null) {
			String name = ModelFactory.getNameForReturnValue(returnValue, returnType);
			mavContainer.addAttribute(name, returnValue);
		}
	}


	/**
	 * {@link MethodParameter} subclass which detects field annotations as well.
	 * @since 5.1
	 */
	private static class FieldAwareConstructorParameter extends MethodParameter {

		private final String parameterName;

		@Nullable
		private volatile Annotation[] combinedAnnotations;

		public FieldAwareConstructorParameter(Constructor<?> constructor, int parameterIndex, String parameterName) {
			super(constructor, parameterIndex);
			this.parameterName = parameterName;
		}

		@Override
		public Annotation[] getParameterAnnotations() {
			Annotation[] anns = this.combinedAnnotations;
			if (anns == null) {
				anns = super.getParameterAnnotations();
				try {
					Field field = getDeclaringClass().getDeclaredField(this.parameterName);
					Annotation[] fieldAnns = field.getAnnotations();
					if (fieldAnns.length > 0) {
						List<Annotation> merged = new ArrayList<>(anns.length + fieldAnns.length);
						merged.addAll(Arrays.asList(anns));
						for (Annotation fieldAnn : fieldAnns) {
							boolean existingType = false;
							for (Annotation ann : anns) {
								if (ann.annotationType() == fieldAnn.annotationType()) {
									existingType = true;
									break;
								}
							}
							if (!existingType) {
								merged.add(fieldAnn);
							}
						}
						anns = merged.toArray(new Annotation[0]);
					}
				}
				catch (NoSuchFieldException | SecurityException ex) {
					// ignore
				}
				this.combinedAnnotations = anns;
			}
			return anns;
		}

		@Override
		public String getParameterName() {
			return this.parameterName;
		}
	}

}
