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

package org.springframework.transaction.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;

/**
 * 该接口将回滚规范添加到 {@link TransactionDefinition}。由于自定义回滚只能在AOP中实现，所以它驻留在AOP相关的事务子包中
 */
public interface TransactionAttribute extends TransactionDefinition {

	/**
	 * 返回与此事务属性关联的限定符值
	 * <li>默认是在 {@link org.springframework.transaction.annotation.Transactional @Transactional} 中设置的事务管理器</li>
	 */
	@Nullable
	String getQualifier();

	/**
	 * 是否应该回滚给定的异常
	 * @param ex the exception to evaluate
	 * @return whether to perform a rollback or not
	 */
	boolean rollbackOn(Throwable ex);

}
