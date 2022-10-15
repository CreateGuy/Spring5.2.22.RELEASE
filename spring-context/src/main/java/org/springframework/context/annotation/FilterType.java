/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.context.annotation;

/**
 * @ComponentScan的filter的类型筛选器的枚举
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 2.5
 * @see ComponentScan
 * @see ComponentScan#includeFilters()
 * @see ComponentScan#excludeFilters()
 * @see org.springframework.core.type.filter.TypeFilter
 */
public enum FilterType {

	/**
	 * 按照注解过滤
	 */
	ANNOTATION,

	/**
	 * 按照类型过滤
	 */
	ASSIGNABLE_TYPE,

	/**
	 * 按照ASPECTJ表达式过滤
	 */
	ASPECTJ,

	/**
	 * 按照正则表达式过滤
	 */
	REGEX,

	/**
	 * 按照自定义的过滤规则过滤
	 */
	CUSTOM

}
