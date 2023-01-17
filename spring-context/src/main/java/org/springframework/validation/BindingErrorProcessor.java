/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.validation;

import org.springframework.beans.PropertyAccessException;

/**
 * 绑定错误处理器
 * <li>处理DataBinder缺失字段错误的策略</li>
 * <li>以及将PropertyAccessException转换为FieldError的策略</li>
 */
public interface BindingErrorProcessor {

	/**
	 * 参数绑定过程中，没有找到指定字段值的时候执行，不是抛出异常
	 */
	void processMissingFieldError(String missingField, BindingResult bindingResult);

	/**
	 * 参数绑定过程中抛出异常会执行，异常类型为：
	 * <ul>
	 *     <li>不可写属性的值时引发的异常(通常是因为没有setter方法)</li>
	 *     <li>字段名不存在的</li>
	 *     <li>属性访问异常：{@link PropertyAccessException}</li>
	 * </ul>
	 * @param ex
	 * @param bindingResult
	 */
	void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult);

}
