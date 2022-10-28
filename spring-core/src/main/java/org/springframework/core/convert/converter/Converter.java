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

import org.springframework.lang.Nullable;

/**
 * 转换器：负责将源类型转为目标类型
 * 该接口的实现是线程安全的，可以共享
 */
@FunctionalInterface
public interface Converter<S, T> {

	/**
	 * 将 S 转为 T 类型
	 * @param source
	 * @return
	 */
	@Nullable
	T convert(S source);

}
