/*
 * Copyright 2002-2018 the original author or authors.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 发现给定类中标注了 {@link ExceptionHandler} 的方法，包括其所有超类
 */
public class ExceptionHandlerMethodResolver {

	/**
	 * 查找标志了 {@code @ExceptionHandler} 注解的方法的表达式
	 */
	public static final MethodFilter EXCEPTION_HANDLER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class);


	/**
	 * 异常类型 和 Controller内部的标注了 {@code @ExceptionHandler} 注解的方法的映射关系
	 */
	private final Map<Class<? extends Throwable>, Method> mappedMethods = new HashMap<>(16);

	/**
	 * 和上面的类型，不过这个是最佳的匹配
	 * <li>比如说有 A,B 两个异常，A是B的父类，如果说抛出了B异常，肯定是直接B异常对应的方法处理，A虽然也行，但不是最佳的</li>
	 */
	private final Map<Class<? extends Throwable>, Method> exceptionLookupCache = new ConcurrentReferenceHashMap<>(16);


	/**
	 * A constructor that finds {@link ExceptionHandler} methods in the given type.
	 * @param handlerType the type to introspect
	 */
	public ExceptionHandlerMethodResolver(Class<?> handlerType) {
		// 查找标志了 @ExceptionHandler 注解的方法
		for (Method method : MethodIntrospector.selectMethods(handlerType, EXCEPTION_HANDLER_METHODS)) {
			// 选择符合规则的做映射
			for (Class<? extends Throwable> exceptionType : detectExceptionMappings(method)) {
				addExceptionMapping(exceptionType, method);
			}
		}
	}


	/**
	 * 首先从 {@code ExceptionHandler} 注解中提取异常映射，然后再选择方法签名本身的异常
	 */
	@SuppressWarnings("unchecked")
	private List<Class<? extends Throwable>> detectExceptionMappings(Method method) {
		List<Class<? extends Throwable>> result = new ArrayList<>();
		// 查找 @ExceptionHandler 设置的异常
		detectAnnotationExceptionMappings(method, result);
		if (result.isEmpty()) {
			// 如果处理程序的入参列表有错误类型的入参，也视为支持此异常解析器
			for (Class<?> paramType : method.getParameterTypes()) {
				if (Throwable.class.isAssignableFrom(paramType)) {
					result.add((Class<? extends Throwable>) paramType);
				}
			}
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("No exception types mapped to " + method);
		}
		return result;
	}

	/**
	 * 查找 {@code @ExceptionHandler} 设置的异常
	 * @param method
	 * @param result
	 */
	private void detectAnnotationExceptionMappings(Method method, List<Class<? extends Throwable>> result) {
		ExceptionHandler ann = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
		Assert.state(ann != null, "No ExceptionHandler annotation");
		result.addAll(Arrays.asList(ann.value()));
	}

	/**
	 * 添加异常和异常处理方法的映射关系
	 * @param exceptionType
	 * @param method
	 */
	private void addExceptionMapping(Class<? extends Throwable> exceptionType, Method method) {
		Method oldMethod = this.mappedMethods.put(exceptionType, method);
		// 如果新的和旧的异常处理方法不一样，抛出异常
		// 我猜测是配置了同一个异常的不同的异常处理方法导致的
		if (oldMethod != null && !oldMethod.equals(method)) {
			throw new IllegalStateException("Ambiguous @ExceptionHandler method mapped for [" +
					exceptionType + "]: {" + oldMethod + ", " + method + "}");
		}
	}

	/**
	 * Whether the contained type has any exception mappings.
	 */
	public boolean hasExceptionMappings() {
		return !this.mappedMethods.isEmpty();
	}

	/**
	 * 找到一个方法来处理传入的异常
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethod(Exception exception) {
		// 找到一个方法来处理传入的异常
		return resolveMethodByThrowable(exception);
	}

	/**
	 * 找到一个方法来处理传入的异常
	 * Use {@link ExceptionDepthComparator} if more than one match is found.
	 * @param exception the exception
	 * @return a Method to handle the exception, or {@code null} if none found
	 * @since 5.0
	 */
	@Nullable
	public Method resolveMethodByThrowable(Throwable exception) {
		// 找到一个方法来处理传入的异常
		Method method = resolveMethodByExceptionType(exception.getClass());
		if (method == null) {
			// 应该是获得异常内部的上一级异常，然后再次尝试找到异常处理方法
			Throwable cause = exception.getCause();
			if (cause != null) {
				method = resolveMethodByExceptionType(cause.getClass());
			}
		}
		return method;
	}

	/**
	 * 找到一个方法来处理传入的异常
	 * useful if an {@link Exception} instance is not available (e.g. for tools).
	 * @param exceptionType the exception type
	 * @return a Method to handle the exception, or {@code null} if none found
	 */
	@Nullable
	public Method resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
		Method method = this.exceptionLookupCache.get(exceptionType);
		if (method == null) {
			// 返回一个可以处理传入的异常的方法
			method = getMappedMethod(exceptionType);
			// 添加到对应的集合中
			this.exceptionLookupCache.put(exceptionType, method);
		}
		return method;
	}

	/**
	 * 返回一个可以处理传入的异常的方法
	 */
	@Nullable
	private Method getMappedMethod(Class<? extends Throwable> exceptionType) {
		List<Class<? extends Throwable>> matches = new ArrayList<>();
		for (Class<? extends Throwable> mappedException : this.mappedMethods.keySet()) {
			// 判断异常处理方法可以处理的异常 是否是 抛出的异常的父类，就视为支持处理
			// 相同类型估计也行
			if (mappedException.isAssignableFrom(exceptionType)) {
				matches.add(mappedException);
			}
		}
		if (!matches.isEmpty()) {
			// 排序
			matches.sort(new ExceptionDepthComparator(exceptionType));
			// 取优先级最高的
			return this.mappedMethods.get(matches.get(0));
		}
		else {
			return null;
		}
	}

}
