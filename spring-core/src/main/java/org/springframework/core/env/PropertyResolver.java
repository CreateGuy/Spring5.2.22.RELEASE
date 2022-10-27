/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.core.env;

import org.springframework.lang.Nullable;

/**
 * 属性解析器
 * @see PropertySourcesPropertyResolver
 */
public interface PropertyResolver {

	/**
	 * *返回给定的属性键是否可用于解析
	 * 即通过key是否可以找到value
	 */
	boolean containsProperty(String key);

	/**
	 * 返回与给定键相关联的属性值，如果键不能解析则返回null
	 */
	@Nullable
	String getProperty(String key);

	/**
	 * 返回与给定键相关联的属性值，如果键不能解析则返回defaultValue。
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	String getProperty(String key, String defaultValue);

	/**
	 * 将key对应是属性值转为目标类型
	 * @param key
	 * @param targetType
	 * @param <T>
	 * @return
	 */
	@Nullable
	<T> T getProperty(String key, Class<T> targetType);

	/**
	 * 将key对应是属性值转为目标类型,如果不行就返回defaultValue
	 * @param key
	 * @param targetType
	 * @param defaultValue
	 * @param <T>
	 * @return
	 */
	<T> T getProperty(String key, Class<T> targetType, T defaultValue);

	/**
	 * 返回与给定键相关联的属性值(绝不为空)。
	 */
	String getRequiredProperty(String key) throws IllegalStateException;

	/**
	 * 返回与给定键相关联的属性值，转换为给定的targetType(绝不为空)。
	 */
	<T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException;

	/**
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding
	 * property values as resolved by {@link #getProperty}. Unresolvable placeholders with
	 * no default value are ignored and passed through unchanged.
	 * @param text the String to resolve
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * @see #resolveRequiredPlaceholders
	 */
	String resolvePlaceholders(String text);

	/**
	 * 解析传入的文本。将其替换为对应的属性值，无法解析将抛出异常
	 * @param text 一个路径(classpath:beans.xml)或者其他class的全路径
	 * @return text对应的属性值
	 * @throws IllegalArgumentException
	 */
	String resolveRequiredPlaceholders(String text) throws IllegalArgumentException;

}
