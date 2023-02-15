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

package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.util.Collection;

import org.springframework.lang.Nullable;

/**
 * 保存 {@link CacheOperation} 的地方
 * @since 3.1
 */
public interface CacheOperationSource {

	/**
	 * 是否是一个需要利用缓存机制的类
	 * <p>If this method returns {@code false}, the methods on the given class
	 * will not get traversed for {@link #getCacheOperations} introspection.
	 * Returning {@code false} is therefore an optimization for non-affected
	 * classes, whereas {@code true} simply means that the class needs to get
	 * fully introspected for each method on the given class individually.
	 * @param targetClass the class to introspect
	 * @return {@code false} if the class is known to have no cache operation
	 * metadata at class or method level; {@code true} otherwise. The default
	 * implementation returns {@code true}, leading to regular introspection.
	 * @since 5.2
	 */
	default boolean isCandidateClass(Class<?> targetClass) {
		return true;
	}

	/**
	 * 返回方法对应的 {@link CacheOperation}
	 * @param method the method to introspect
	 * @param targetClass the target class (may be {@code null}, in which case
	 * the declaring class of the method must be used)
	 * @return all cache operations for this method, or {@code null} if none found
	 */
	@Nullable
	Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass);

}
