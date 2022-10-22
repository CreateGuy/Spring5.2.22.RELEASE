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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

/**
 * 属性方法：提供了一种快速的方法来访问注解的属性方法
 */
final class AttributeMethods {

	static final AttributeMethods NONE = new AttributeMethods(null, new Method[0]);


	//缓存作用
	private static final Map<Class<? extends Annotation>, AttributeMethods> cache =
			new ConcurrentReferenceHashMap<>();

	//根据方法名称排序
	private static final Comparator<Method> methodComparator = (m1, m2) -> {
		if (m1 != null && m2 != null) {
			return m1.getName().compareTo(m2.getName());
		}
		return m1 != null ? -1 : 1;
	};


	//当前AttributeMethods表示的注解
	@Nullable
	private final Class<? extends Annotation> annotationType;

	//注解的所有方法
	private final Method[] attributeMethods;

	//方法是否进行类找不到检查，是一个数组，是所有的方法的一个映射
	//true的情况是Class等作为返回值
	private final boolean[] canThrowTypeNotPresentException;

	//方法是否有默认值：有任何一个属性方法有默认值就为true
	private final boolean hasDefaultValueMethod;

	//方法是否有嵌套注解: 任何属性方法的返回值是注解，或者返回一个注解数组都是true
	private final boolean hasNestedAnnotation;


	/**
	 * 获得
	 * @param annotationType
	 * @param attributeMethods
	 */
	private AttributeMethods(@Nullable Class<? extends Annotation> annotationType, Method[] attributeMethods) {
		this.annotationType = annotationType;
		this.attributeMethods = attributeMethods;
		this.canThrowTypeNotPresentException = new boolean[attributeMethods.length];
		boolean foundDefaultValueMethod = false;
		boolean foundNestedAnnotation = false;
		//遍历所有的属性方法
		for (int i = 0; i < attributeMethods.length; i++) {
			Method method = this.attributeMethods[i];
			//获得返回值
			Class<?> type = method.getReturnType();
			//设置标志位
			if (method.getDefaultValue() != null) {
				foundDefaultValueMethod = true;
			}
			//设置标志位
			if (type.isAnnotation() || (type.isArray() && type.getComponentType().isAnnotation())) {
				foundNestedAnnotation = true;
			}
			//使给定的方法可访问
			ReflectionUtils.makeAccessible(method);
			//属性方法没有默认值的时候是否抛出异常：只检查下面三种情况
			this.canThrowTypeNotPresentException[i] = (type == Class.class || type == Class[].class || type.isEnum());
		}
		this.hasDefaultValueMethod = foundDefaultValueMethod;
		this.hasNestedAnnotation = foundNestedAnnotation;
	}


	/**
	 * Determine if this instance only contains a single attribute named
	 * {@code value}.
	 * @return {@code true} if there is only a value attribute
	 */
	boolean hasOnlyValueAttribute() {
		return (this.attributeMethods.length == 1 &&
				MergedAnnotation.VALUE.equals(this.attributeMethods[0].getName()));
	}


	/**
	 * Determine if values from the given annotation can be safely accessed without
	 * causing any {@link TypeNotPresentException TypeNotPresentExceptions}.
	 * @param annotation the annotation to check
	 * @return {@code true} if all values are present
	 * @see #validate(Annotation)
	 */
	boolean isValid(Annotation annotation) {
		assertAnnotation(annotation);
		for (int i = 0; i < size(); i++) {
			if (canThrowTypeNotPresentException(i)) {
				try {
					get(i).invoke(annotation);
				}
				catch (Throwable ex) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 检查给定注解中的值是否可以安全找到，而不会导致任何TypeNotPresentExceptions(类找不到异常)
	 * @param annotation
	 */
	void validate(Annotation annotation) {
		assertAnnotation(annotation);
		//遍历检查所有的方法
		for (int i = 0; i < size(); i++) {
			//判断当前方法的返回值是否是Class对象或者Class数组：如果是的话就要进行类型检查
			if (canThrowTypeNotPresentException(i)) {
				try {
					//如果能通过执行方法获取返回值，就没事
					get(i).invoke(annotation);
				}
				catch (Throwable ex) {
					throw new IllegalStateException("Could not obtain annotation attribute value for " +
							get(i).getName() + " declared on " + annotation.annotationType(), ex);
				}
			}
		}
	}

	/**
	 * 断言检查
	 * @param annotation
	 */
	private void assertAnnotation(Annotation annotation) {
		Assert.notNull(annotation, "Annotation must not be null");
		if (this.annotationType != null) {
			//annotation必须是annotationType指定的类，注解这样判断没太懂(不懂)
			Assert.isInstanceOf(this.annotationType, annotation);
		}
	}

	/**
	 * Get the attribute with the specified name or {@code null} if no
	 * matching attribute exists.
	 * @param name the attribute name to find
	 * @return the attribute method or {@code null}
	 */
	@Nullable
	Method get(String name) {
		int index = indexOf(name);
		return index != -1 ? this.attributeMethods[index] : null;
	}

	/**
	 * Get the attribute at the specified index.
	 * @param index the index of the attribute to return
	 * @return the attribute method
	 * @throws IndexOutOfBoundsException if the index is out of range
	 * (<tt>index &lt; 0 || index &gt;= size()</tt>)
	 */
	Method get(int index) {
		return this.attributeMethods[index];
	}

	/**
	 * Determine if the attribute at the specified index could throw a
	 * {@link TypeNotPresentException} when accessed.
	 * @param index the index of the attribute to check
	 * @return {@code true} if the attribute can throw a
	 * {@link TypeNotPresentException}
	 */
	boolean canThrowTypeNotPresentException(int index) {
		return this.canThrowTypeNotPresentException[index];
	}

	/**
	 * Get the index of the attribute with the specified name, or {@code -1}
	 * if there is no attribute with the name.
	 * @param name the name to find
	 * @return the index of the attribute, or {@code -1}
	 */
	int indexOf(String name) {
		for (int i = 0; i < this.attributeMethods.length; i++) {
			if (this.attributeMethods[i].getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the index of the specified attribute, or {@code -1} if the
	 * attribute is not in this collection.
	 * @param attribute the attribute to find
	 * @return the index of the attribute, or {@code -1}
	 */
	int indexOf(Method attribute) {
		for (int i = 0; i < this.attributeMethods.length; i++) {
			if (this.attributeMethods[i].equals(attribute)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the number of attributes in this collection.
	 * @return the number of attributes
	 */
	int size() {
		return this.attributeMethods.length;
	}

	/**
	 * Determine if at least one of the attribute methods has a default value.
	 * @return {@code true} if there is at least one attribute method with a default value
	 */
	boolean hasDefaultValueMethod() {
		return this.hasDefaultValueMethod;
	}

	/**
	 * Determine if at least one of the attribute methods is a nested annotation.
	 * @return {@code true} if there is at least one attribute method with a nested
	 * annotation type
	 */
	boolean hasNestedAnnotation() {
		return this.hasNestedAnnotation;
	}


	/**
	 * 获得属性方法
	 * @param annotationType
	 * @return
	 */
	static AttributeMethods forAnnotationType(@Nullable Class<? extends Annotation> annotationType) {
		if (annotationType == null) {
			return NONE;
		}
		//先从缓存中获取，如果没有就先创建一个再返回
		return cache.computeIfAbsent(annotationType, AttributeMethods::compute);
	}

	/**
	 * 创建一个属性方法
	 * @param annotationType 注解
	 * @return
	 */
	private static AttributeMethods compute(Class<? extends Annotation> annotationType) {
		//获得方法
		Method[] methods = annotationType.getDeclaredMethods();
		int size = methods.length;
		for (int i = 0; i < methods.length; i++) {
			//如果是空返回值没有意义
			if (!isAttributeMethod(methods[i])) {
				methods[i] = null;
				size--;
			}
		}
		//如果没有方法，或者方法都是void返回值
		if (size == 0) {
			return NONE;
		}
		//根据方法名称排序
		Arrays.sort(methods, methodComparator);
		Method[] attributeMethods = Arrays.copyOf(methods, size);
		return new AttributeMethods(annotationType, attributeMethods);
	}

	/**
	 * 校验是否是是void作为返回值，或者没有设置值
	 * @param method
	 * @return
	 */
	private static boolean isAttributeMethod(Method method) {
		return (method.getParameterCount() == 0 && method.getReturnType() != void.class);
	}

	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param attribute the attribute to describe
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Method attribute) {
		if (attribute == null) {
			return "(none)";
		}
		return describe(attribute.getDeclaringClass(), attribute.getName());
	}

	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param annotationType the annotation type
	 * @param attributeName the attribute name
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Class<?> annotationType, @Nullable String attributeName) {
		if (attributeName == null) {
			return "(none)";
		}
		String in = (annotationType != null ? " in annotation [" + annotationType.getName() + "]" : "");
		return "attribute '" + attributeName + "'" + in;
	}

}
