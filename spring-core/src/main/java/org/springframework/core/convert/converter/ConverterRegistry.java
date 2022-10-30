/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.core.convert.converter;

/**
 * For registering converters with a type conversion system.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 */
public interface ConverterRegistry {

	/**
	 * 注册一个1:1的转换器----会尝试去参数化类型中提取sourceType和targetType
	 */
	void addConverter(Converter<?, ?> converter);

	/**
	 * 注册一个1:1的转换器----明确sourceType和targetType
	 */
	<S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter);

	/**
	 * 注册一个n:n的转换器
	 */
	void addConverter(GenericConverter converter);

	/**
	 * 注册一个1:n的转化器
	 */
	void addConverterFactory(ConverterFactory<?, ?> factory);

	/**
	 * 移除某个转换器
	 */
	void removeConvertible(Class<?> sourceType, Class<?> targetType);

}
