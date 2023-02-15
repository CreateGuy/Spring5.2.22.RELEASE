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
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.aop.ClassFilter;
import org.springframework.cache.interceptor.CacheEvictOperation;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Strategy implementation for parsing Spring's {@link Caching}, {@link Cacheable},
 * {@link CacheEvict}, and {@link CachePut} annotations.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @since 3.1
 */
@SuppressWarnings("serial")
public class SpringCacheAnnotationParser implements CacheAnnotationParser, Serializable {

	/**
	 * 候选缓存类需要携带的注解
	 */
	private static final Set<Class<? extends Annotation>> CACHE_OPERATION_ANNOTATIONS = new LinkedHashSet<>(8);

	static {
		CACHE_OPERATION_ANNOTATIONS.add(Cacheable.class);
		CACHE_OPERATION_ANNOTATIONS.add(CacheEvict.class);
		CACHE_OPERATION_ANNOTATIONS.add(CachePut.class);
		CACHE_OPERATION_ANNOTATIONS.add(Caching.class);
	}

	/**
	 * 判断给定的类是否是标注了指定注释的的候选类(在类型、方法或字段上去判断)
	 * <li>通常在 {@link org.springframework.cache.interceptor.CacheOperationSourcePointcut.CacheOperationSourceClassFilter CacheOperationSourceClassFilter} 中调用</li>
	 * @param targetClass the class to introspect
	 * @return
	 */
	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		return AnnotationUtils.isCandidateClass(targetClass, CACHE_OPERATION_ANNOTATIONS);
	}

	/**
	 * 解析方法上的特定注解然后转换为 {@link CacheOperation} 并返回
	 * @param type the annotated class
	 * @return
	 */
	@Override
	@Nullable
	public Collection<CacheOperation> parseCacheAnnotations(Class<?> type) {
		DefaultCacheConfig defaultConfig = new DefaultCacheConfig(type);
		return parseCacheAnnotations(defaultConfig, type);
	}

	@Override
	@Nullable
	public Collection<CacheOperation> parseCacheAnnotations(Method method) {
		DefaultCacheConfig defaultConfig = new DefaultCacheConfig(method.getDeclaringClass());
		return parseCacheAnnotations(defaultConfig, method);
	}

	/**
	 * 获得指定元素对应的 CacheOperation
	 * @param cachingConfig
	 * @param ae
	 * @return
	 */
	@Nullable
	private Collection<CacheOperation> parseCacheAnnotations(DefaultCacheConfig cachingConfig, AnnotatedElement ae) {
		// 获得指定元素对应的 CacheOperation
		Collection<CacheOperation> ops = parseCacheAnnotations(cachingConfig, ae, false);
		if (ops != null && ops.size() > 1) {
			// 发现了多个,然后将实例中的声明覆盖接口的声明
			Collection<CacheOperation> localOps = parseCacheAnnotations(cachingConfig, ae, true);
			if (localOps != null) {
				return localOps;
			}
		}
		return ops;
	}

	/**
	 * 根据指定元素上的缓存注解生成对应的 {@link CacheOperation}，然后返回
	 * @param cachingConfig
	 * @param ae
	 * @param localOnly 相同注解的排序规则
	 * @return
	 */
	@Nullable
	private Collection<CacheOperation> parseCacheAnnotations(
			DefaultCacheConfig cachingConfig, AnnotatedElement ae, boolean localOnly) {

		// 获得元素上指定的注解信息
		Collection<? extends Annotation> anns = (localOnly ?
				AnnotatedElementUtils.getAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS) :
				AnnotatedElementUtils.findAllMergedAnnotations(ae, CACHE_OPERATION_ANNOTATIONS));
		if (anns.isEmpty()) {
			return null;
		}

		final Collection<CacheOperation> ops = new ArrayList<>(1);
		anns.stream().filter(ann -> ann instanceof Cacheable).forEach(
				ann -> ops.add(parseCacheableAnnotation(ae, cachingConfig, (Cacheable) ann)));
		anns.stream().filter(ann -> ann instanceof CacheEvict).forEach(
				ann -> ops.add(parseEvictAnnotation(ae, cachingConfig, (CacheEvict) ann)));
		anns.stream().filter(ann -> ann instanceof CachePut).forEach(
				ann -> ops.add(parsePutAnnotation(ae, cachingConfig, (CachePut) ann)));
		anns.stream().filter(ann -> ann instanceof Caching).forEach(
				ann -> parseCachingAnnotation(ae, cachingConfig, (Caching) ann, ops));
		return ops;
	}

	/**
	 * 根据 {@link Cacheable @Cacheable} 创建对应的 {@link CacheableOperation}
	 * @param ae
	 * @param defaultConfig
	 * @param cacheable
	 * @return
	 */
	private CacheableOperation parseCacheableAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, Cacheable cacheable) {

		CacheableOperation.Builder builder = new CacheableOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cacheable.cacheNames());
		builder.setCondition(cacheable.condition());
		builder.setUnless(cacheable.unless());
		builder.setKey(cacheable.key());
		builder.setKeyGenerator(cacheable.keyGenerator());
		builder.setCacheManager(cacheable.cacheManager());
		builder.setCacheResolver(cacheable.cacheResolver());
		builder.setSync(cacheable.sync());

		// 设置默认值
		defaultConfig.applyDefault(builder);

		CacheableOperation op = builder.build();

		// 验证指定的 CacheOperation.
		validateCacheOperation(ae, op);

		return op;
	}

	/**
	 * 根据 {@link CacheEvict @CacheEvict} 创建对应的 {@link CacheEvictOperation}
	 * @param ae
	 * @param defaultConfig
	 * @param cacheEvict
	 * @return
	 */
	private CacheEvictOperation parseEvictAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, CacheEvict cacheEvict) {

		CacheEvictOperation.Builder builder = new CacheEvictOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cacheEvict.cacheNames());
		builder.setCondition(cacheEvict.condition());
		builder.setKey(cacheEvict.key());
		builder.setKeyGenerator(cacheEvict.keyGenerator());
		builder.setCacheManager(cacheEvict.cacheManager());
		builder.setCacheResolver(cacheEvict.cacheResolver());
		builder.setCacheWide(cacheEvict.allEntries());
		builder.setBeforeInvocation(cacheEvict.beforeInvocation());

		// 设置默认值
		defaultConfig.applyDefault(builder);

		CacheEvictOperation op = builder.build();

		// 验证指定的 CacheOperation.
		validateCacheOperation(ae, op);

		return op;
	}

	/**
	 * 根据 {@link CachePut @CachePut} 创建对应的 {@link CachePutOperation}
	 * @param ae
	 * @param defaultConfig
	 * @param cachePut
	 * @return
	 */
	private CacheOperation parsePutAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, CachePut cachePut) {

		CachePutOperation.Builder builder = new CachePutOperation.Builder();

		builder.setName(ae.toString());
		builder.setCacheNames(cachePut.cacheNames());
		builder.setCondition(cachePut.condition());
		builder.setUnless(cachePut.unless());
		builder.setKey(cachePut.key());
		builder.setKeyGenerator(cachePut.keyGenerator());
		builder.setCacheManager(cachePut.cacheManager());
		builder.setCacheResolver(cachePut.cacheResolver());

		// 设置默认值
		defaultConfig.applyDefault(builder);

		CachePutOperation op = builder.build();

		// 验证指定的 CacheOperation.
		validateCacheOperation(ae, op);

		return op;
	}

	/**
	 * 根据组合注解 {@link Caching @Caching}，添加对应的 {@link CacheOperation}
	 * @param ae
	 * @param defaultConfig
	 * @param caching
	 * @param ops
	 */
	private void parseCachingAnnotation(
			AnnotatedElement ae, DefaultCacheConfig defaultConfig, Caching caching, Collection<CacheOperation> ops) {

		Cacheable[] cacheables = caching.cacheable();
		for (Cacheable cacheable : cacheables) {
			ops.add(parseCacheableAnnotation(ae, defaultConfig, cacheable));
		}
		CacheEvict[] cacheEvicts = caching.evict();
		for (CacheEvict cacheEvict : cacheEvicts) {
			ops.add(parseEvictAnnotation(ae, defaultConfig, cacheEvict));
		}
		CachePut[] cachePuts = caching.put();
		for (CachePut cachePut : cachePuts) {
			ops.add(parsePutAnnotation(ae, defaultConfig, cachePut));
		}
	}


	/**
	 * 验证指定的 {@link CacheOperation}.
	 * <p>Throws an {@link IllegalStateException} if the state of the operation is
	 * invalid. As there might be multiple sources for default values, this ensure
	 * that the operation is in a proper state before being returned.
	 * @param ae the annotated element of the cache operation
	 * @param operation the {@link CacheOperation} to validate
	 */
	private void validateCacheOperation(AnnotatedElement ae, CacheOperation operation) {
		// 必须设置键
		if (StringUtils.hasText(operation.getKey()) && StringUtils.hasText(operation.getKeyGenerator())) {
			throw new IllegalStateException("Invalid cache annotation configuration on '" +
					ae.toString() + "'. Both 'key' and 'keyGenerator' attributes have been set. " +
					"These attributes are mutually exclusive: either set the SpEL expression used to" +
					"compute the key at runtime or set the name of the KeyGenerator bean to use.");
		}

		// 必须设置缓存管理器
		if (StringUtils.hasText(operation.getCacheManager()) && StringUtils.hasText(operation.getCacheResolver())) {
			throw new IllegalStateException("Invalid cache annotation configuration on '" +
					ae.toString() + "'. Both 'cacheManager' and 'cacheResolver' attributes have been set. " +
					"These attributes are mutually exclusive: the cache manager is used to configure a" +
					"default cache resolver if none is set. If a cache resolver is set, the cache manager" +
					"won't be used.");
		}
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || other instanceof SpringCacheAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringCacheAnnotationParser.class.hashCode();
	}


	/**
	 * 为给定的一组缓存操作提供默认设置
	 */
	private static class DefaultCacheConfig {

		/**
		 * 缓存类
		 */
		private final Class<?> target;

		/**
		 * 缓存文件夹名称
		 */
		@Nullable
		private String[] cacheNames;

		/**
		 * 键生成器
		 */
		@Nullable
		private String keyGenerator;

		/**
		 * 缓存管理器
		 */
		@Nullable
		private String cacheManager;

		@Nullable
		private String cacheResolver;

		/**
		 * 是否已经初始化完毕
		 */
		private boolean initialized = false;

		public DefaultCacheConfig(Class<?> target) {
			this.target = target;
		}

		/**
		 * 将默认值设置到 {@link CacheOperation.Builder} 中
		 * @param builder the operation builder to update
		 */
		public void applyDefault(CacheOperation.Builder builder) {
			if (!this.initialized) {
				// 从@CacheConfig中取缓存属性
				CacheConfig annotation = AnnotatedElementUtils.findMergedAnnotation(this.target, CacheConfig.class);
				if (annotation != null) {
					this.cacheNames = annotation.cacheNames();
					this.keyGenerator = annotation.keyGenerator();
					this.cacheManager = annotation.cacheManager();
					this.cacheResolver = annotation.cacheResolver();
				}
				this.initialized = true;
			}

			if (builder.getCacheNames().isEmpty() && this.cacheNames != null) {
				builder.setCacheNames(this.cacheNames);
			}
			if (!StringUtils.hasText(builder.getKey()) && !StringUtils.hasText(builder.getKeyGenerator()) &&
					StringUtils.hasText(this.keyGenerator)) {
				builder.setKeyGenerator(this.keyGenerator);
			}

			if (StringUtils.hasText(builder.getCacheManager()) || StringUtils.hasText(builder.getCacheResolver())) {
				// 其中任何一个已经设置好了，所以我们不需要设置
			}
			else if (StringUtils.hasText(this.cacheResolver)) {
				builder.setCacheResolver(this.cacheResolver);
			}
			else if (StringUtils.hasText(this.cacheManager)) {
				builder.setCacheManager(this.cacheManager);
			}
		}
	}

}
