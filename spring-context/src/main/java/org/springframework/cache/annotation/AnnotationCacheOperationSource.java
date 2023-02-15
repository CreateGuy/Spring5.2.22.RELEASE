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

package org.springframework.cache.annotation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cache.interceptor.AbstractFallbackCacheOperationSource;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 读取 {@link Cacheable}, {@link CachePut} and {@link CacheEvict} 的信息，然后转换为 {@link org.springframework.cache.interceptor.CacheOperation CacheOperation}
 * @since 3.1
 */
@SuppressWarnings("serial")
public class AnnotationCacheOperationSource extends AbstractFallbackCacheOperationSource implements Serializable {

	/**
	 * 是否只允许缓存公共的方法
	 */
	private final boolean publicMethodsOnly;

	/**
	 * 缓存相关注解的解析器：默认情况下只有 {@link SpringCacheAnnotationParser}
	 */
	private final Set<CacheAnnotationParser> annotationParsers;


	/**
	 * Create a default AnnotationCacheOperationSource, supporting public methods
	 * that carry the {@code Cacheable} and {@code CacheEvict} annotations.
	 */
	public AnnotationCacheOperationSource() {
		this(true);
	}

	/**
	 * Create a default {@code AnnotationCacheOperationSource}, supporting public methods
	 * that carry the {@code Cacheable} and {@code CacheEvict} annotations.
	 * @param publicMethodsOnly whether to support only annotated public methods
	 * typically for use with proxy-based AOP), or protected/private methods as well
	 * (typically used with AspectJ class weaving)
	 */
	public AnnotationCacheOperationSource(boolean publicMethodsOnly) {
		this.publicMethodsOnly = publicMethodsOnly;
		this.annotationParsers = Collections.singleton(new SpringCacheAnnotationParser());
	}

	/**
	 * Create a custom AnnotationCacheOperationSource.
	 * @param annotationParser the CacheAnnotationParser to use
	 */
	public AnnotationCacheOperationSource(CacheAnnotationParser annotationParser) {
		this.publicMethodsOnly = true;
		Assert.notNull(annotationParser, "CacheAnnotationParser must not be null");
		this.annotationParsers = Collections.singleton(annotationParser);
	}

	/**
	 * Create a custom AnnotationCacheOperationSource.
	 * @param annotationParsers the CacheAnnotationParser to use
	 */
	public AnnotationCacheOperationSource(CacheAnnotationParser... annotationParsers) {
		this.publicMethodsOnly = true;
		Assert.notEmpty(annotationParsers, "At least one CacheAnnotationParser needs to be specified");
		this.annotationParsers = new LinkedHashSet<>(Arrays.asList(annotationParsers));
	}

	/**
	 * Create a custom AnnotationCacheOperationSource.
	 * @param annotationParsers the CacheAnnotationParser to use
	 */
	public AnnotationCacheOperationSource(Set<CacheAnnotationParser> annotationParsers) {
		this.publicMethodsOnly = true;
		Assert.notEmpty(annotationParsers, "At least one CacheAnnotationParser needs to be specified");
		this.annotationParsers = annotationParsers;
	}

	/**
	 * 是否是一个需要利用缓存机制的类
	 * @param targetClass the class to introspect
	 * @return
	 */
	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		for (CacheAnnotationParser parser : this.annotationParsers) {
			if (parser.isCandidateClass(targetClass)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 返回给定类对应的 {@link CacheOperation}
	 * @param clazz the class to retrieve the attribute for
	 * @return
	 */
	@Override
	@Nullable
	protected Collection<CacheOperation> findCacheOperations(Class<?> clazz) {
		return determineCacheOperations(parser -> parser.parseCacheAnnotations(clazz));
	}

	/**
	 * 返回给定方法对应的 {@link CacheOperation}
	 * @param method the method to retrieve the attribute for
	 * @return
	 */
	@Override
	@Nullable
	protected Collection<CacheOperation> findCacheOperations(Method method) {
		return determineCacheOperations(parser -> parser.parseCacheAnnotations(method));
	}

	/**
	 * 通过 {@link CacheAnnotationParser CacheAnnotationParsers}，返回 {@link CacheOperation}
	 * <p>Can be overridden to support custom annotations that carry caching metadata.
	 * @param provider the cache operation provider to use
	 * @return the configured caching operations, or {@code null} if none found
	 */
	@Nullable
	protected Collection<CacheOperation> determineCacheOperations(CacheOperationProvider provider) {
		Collection<CacheOperation> ops = null;
		for (CacheAnnotationParser parser : this.annotationParsers) {
			Collection<CacheOperation> annOps = provider.getCacheOperations(parser);
			if (annOps != null) {
				if (ops == null) {
					ops = annOps;
				}
				else {
					Collection<CacheOperation> combined = new ArrayList<>(ops.size() + annOps.size());
					combined.addAll(ops);
					combined.addAll(annOps);
					ops = combined;
				}
			}
		}
		return ops;
	}

	/**
	 * By default, only public methods can be made cacheable.
	 */
	@Override
	protected boolean allowPublicMethodsOnly() {
		return this.publicMethodsOnly;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AnnotationCacheOperationSource)) {
			return false;
		}
		AnnotationCacheOperationSource otherCos = (AnnotationCacheOperationSource) other;
		return (this.annotationParsers.equals(otherCos.annotationParsers) &&
				this.publicMethodsOnly == otherCos.publicMethodsOnly);
	}

	@Override
	public int hashCode() {
		return this.annotationParsers.hashCode();
	}


	/**
	 * Callback interface providing {@link CacheOperation} instance(s) based on
	 * a given {@link CacheAnnotationParser}.
	 */
	@FunctionalInterface
	protected interface CacheOperationProvider {

		/**
		 * Return the {@link CacheOperation} instance(s) provided by the specified parser.
		 * @param parser the parser to use
		 * @return the cache operations, or {@code null} if none found
		 */
		@Nullable
		Collection<CacheOperation> getCacheOperations(CacheAnnotationParser parser);
	}

}
