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

package org.springframework.cache.interceptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Abstract implementation of {@link CacheOperation} that caches attributes
 * for methods and implements a fallback policy: 1. specific target method;
 * 2. target class; 3. declaring method; 4. declaring class/interface.
 *
 * <p>Defaults to using the target class's caching attribute if none is
 * associated with the target method. Any caching attribute associated with
 * the target method completely overrides a class caching attribute.
 * If none found on the target class, the interface that the invoked method
 * has been called through (in case of a JDK proxy) will be checked.
 *
 * <p>This implementation caches attributes by method after they are first
 * used. If it is ever desirable to allow dynamic changing of cacheable
 * attributes (which is very unlikely), caching could be made configurable.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractFallbackCacheOperationSource implements CacheOperationSource {

	/**
	 * Canonical value held in cache to indicate no caching attribute was
	 * found for this method and we don't need to look again.
	 */
	private static final Collection<CacheOperation> NULL_CACHING_ATTRIBUTE = Collections.emptyList();


	/**
	 * Logger available to subclasses.
	 * <p>As this base class is not marked Serializable, the logger will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 指定对象和对应的 {@link CacheOperation} 的映射关系
	 * <li>比如说 {@link MethodClassKey} 和对应的 {@link CacheOperation}</li>
	 */
	private final Map<Object, Collection<CacheOperation>> attributeCache = new ConcurrentHashMap<>(1024);


	/**
	 * 返回此方法对的 {@link CacheOperation}
	 * <p>Defaults to the class's caching attribute if no method attribute is found.
	 * @param method the method for the current invocation (never {@code null})
	 * @param targetClass the target class for this invocation (may be {@code null})
	 * @return {@link CacheOperation} for this method, or {@code null} if the method
	 * is not cacheable
	 */
	@Override
	@Nullable
	public Collection<CacheOperation> getCacheOperations(Method method, @Nullable Class<?> targetClass) {
		if (method.getDeclaringClass() == Object.class) {
			return null;
		}

		Object cacheKey = getCacheKey(method, targetClass);
		Collection<CacheOperation> cached = this.attributeCache.get(cacheKey);

		// 先从缓存中获取
		if (cached != null) {
			return (cached != NULL_CACHING_ATTRIBUTE ? cached : null);
		}
		else {
			// 从方法和类上获取CacheOperation
			Collection<CacheOperation> cacheOps = computeCacheOperations(method, targetClass);
			if (cacheOps != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Adding cacheable method '" + method.getName() + "' with attribute: " + cacheOps);
				}
				this.attributeCache.put(cacheKey, cacheOps);
			}
			else {
				this.attributeCache.put(cacheKey, NULL_CACHING_ATTRIBUTE);
			}
			return cacheOps;
		}
	}

	/**
	 * Determine a cache key for the given method and target class.
	 * <p>Must not produce same key for overloaded methods.
	 * Must produce same key for different instances of the same method.
	 * @param method the method (never {@code null})
	 * @param targetClass the target class (may be {@code null})
	 * @return the cache key (never {@code null})
	 */
	protected Object getCacheKey(Method method, @Nullable Class<?> targetClass) {
		return new MethodClassKey(method, targetClass);
	}

	/**
	 * 从方法和类上获取 {@link CacheOperation}
	 * @param method
	 * @param targetClass
	 * @return
	 */
	@Nullable
	private Collection<CacheOperation> computeCacheOperations(Method method, @Nullable Class<?> targetClass) {
		// 是否不缓存非Public的方法
		if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		// 如果传入的方法可能是来自接口，那么就找到具体的实现方法
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

		// 首先：获取给定方法对应的CacheOperation
		Collection<CacheOperation> opDef = findCacheOperations(specificMethod);
		if (opDef != null) {
			return opDef;
		}

		// 第二次：获取给定类对应的CacheOperation
		opDef = findCacheOperations(specificMethod.getDeclaringClass());
		if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
			return opDef;
		}

		// 如果是在子类上没有找到，那就去更高一级的方法和类去查找
		if (specificMethod != method) {
			// Fallback is to look at the original method.
			opDef = findCacheOperations(method);
			if (opDef != null) {
				return opDef;
			}
			// Last fallback is the class of the original method.
			opDef = findCacheOperations(method.getDeclaringClass());
			if (opDef != null && ClassUtils.isUserLevelMethod(method)) {
				return opDef;
			}
		}

		return null;
	}


	/**
	 * 子类需要实现此方法，以返回给定类对应的 {@link CacheOperation}
	 * @param clazz the class to retrieve the attribute for
	 * @return all caching attribute associated with this class, or {@code null} if none
	 */
	@Nullable
	protected abstract Collection<CacheOperation> findCacheOperations(Class<?> clazz);

	/**
	 * 子类需要实现此方法，以返回给定方法对应的 {@link CacheOperation}
	 * @param method the method to retrieve the attribute for
	 * @return all caching attribute associated with this method, or {@code null} if none
	 */
	@Nullable
	protected abstract Collection<CacheOperation> findCacheOperations(Method method);

	/**
	 * Should only public methods be allowed to have caching semantics?
	 * <p>The default implementation returns {@code false}.
	 */
	protected boolean allowPublicMethodsOnly() {
		return false;
	}

}
