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

package org.springframework.transaction.annotation;

import java.lang.reflect.AnnotatedElement;

import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * 用于解析已知的事务注解类型的策略接口
 * <p>{@link AnnotationTransactionAttributeSource} delegates to such
 * parsers for supporting specific annotation types such as Spring's own
 * {@link Transactional}, JTA 1.2's {@link javax.transaction.Transactional}
 * or EJB3's {@link javax.ejb.TransactionAttribute}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see AnnotationTransactionAttributeSource
 * @see SpringTransactionAnnotationParser
 * @see Ejb3TransactionAnnotationParser
 * @see JtaTransactionAnnotationParser
 */
public interface TransactionAnnotationParser {

	/**
	 * 确定给定的类是否是带有事务注解格式的候选类
	 * <p>If this method returns {@code false}, the methods on the given class
	 * will not get traversed for {@code #parseTransactionAnnotation} introspection.
	 * Returning {@code false} is therefore an optimization for non-affected
	 * classes, whereas {@code true} simply means that the class needs to get
	 * fully introspected for each method on the given class individually.
	 * @param targetClass the class to introspect
	 * @return {@code false} if the class is known to have no transaction
	 * annotations at class or method level; {@code true} otherwise. The default
	 * implementation returns {@code true}, leading to regular introspection.
	 * @since 5.2
	 */
	default boolean isCandidateClass(Class<?> targetClass) {
		return true;
	}

	/**
	 * 返回指定元素上带有的事务注解的信息
	 * <p>This essentially parses a known transaction annotation into Spring's metadata
	 * attribute class. Returns {@code null} if the method/class is not transactional.
	 * @param element the annotated method or class
	 * @return the configured transaction attribute, or {@code null} if none found
	 * @see AnnotationTransactionAttributeSource#determineTransactionAttribute
	 */
	@Nullable
	TransactionAttribute parseTransactionAnnotation(AnnotatedElement element);

}
