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

package org.springframework.transaction.annotation;

import org.springframework.transaction.TransactionDefinition;

/**
 * 事务隔离级别：与 {@link Transactional @Transactional} 一起使用的，对应于 {@link TransactionDefinition} 接口
 */
public enum Isolation {

	/**
	 * 使用底层数据存储的默认隔离级别。所有其他级别都对应于JDBC隔离级别
	 * @see java.sql.Connection
	 */
	DEFAULT(TransactionDefinition.ISOLATION_DEFAULT),

	/**
	 * 读未提交：会出现脏读，不可重复读，幻读
	 */
	READ_UNCOMMITTED(TransactionDefinition.ISOLATION_READ_UNCOMMITTED),

	/**
	 * 读已提交：会出现不可重复读，幻读
	 */
	READ_COMMITTED(TransactionDefinition.ISOLATION_READ_COMMITTED),

	/**
	 * 可重复读：没有完全解决幻读
	 */
	REPEATABLE_READ(TransactionDefinition.ISOLATION_REPEATABLE_READ),

	/**
	 * 串行：解决上述的三个问题
	 */
	SERIALIZABLE(TransactionDefinition.ISOLATION_SERIALIZABLE);


	private final int value;


	Isolation(int value) {
		this.value = value;
	}

	public int value() {
		return this.value;
	}

}
