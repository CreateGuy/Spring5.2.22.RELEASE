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

package org.springframework.cache.annotation;

import java.util.Collection;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * 缓存配置类的基类
 * @since 3.1
 * @see EnableCaching
 */
@Configuration
public abstract class AbstractCachingConfiguration implements ImportAware {

	/**
	 * 导入类上有关 {@link EnableCaching @EnableCaching} 的属性
	 */
	@Nullable
	protected AnnotationAttributes enableCaching;

	/**
	 * 缓存管理器
	 */
	@Nullable
	protected Supplier<CacheManager> cacheManager;

	/**
	 * 缓存解析器：感觉只会在JCache中使用
	 */
	@Nullable
	protected Supplier<CacheResolver> cacheResolver;

	/**
	 * 缓存键生成器
	 */
	@Nullable
	protected Supplier<KeyGenerator> keyGenerator;

	/**
	 * 缓存操作错误处理器
	 */
	@Nullable
	protected Supplier<CacheErrorHandler> errorHandler;


	/**
	 * 将导入类上有关 {@link EnableCaching @EnableCaching} 的属性保存起来
	 * @param importMetadata
	 */
	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		this.enableCaching = AnnotationAttributes.fromMap(
				importMetadata.getAnnotationAttributes(EnableCaching.class.getName(), false));
		if (this.enableCaching == null) {
			throw new IllegalArgumentException(
					"@EnableCaching is not present on importing class " + importMetadata.getClassName());
		}
	}

	/**
	 * 通过 {@link CachingConfigurer} 来配置缓存的相关属性
	 * @param configurers
	 */
	@Autowired(required = false)
	void setConfigurers(Collection<CachingConfigurer> configurers) {
		if (CollectionUtils.isEmpty(configurers)) {
			return;
		}
		if (configurers.size() > 1) {
			throw new IllegalStateException(configurers.size() + " implementations of " +
					"CachingConfigurer were found when only 1 was expected. " +
					"Refactor the configuration such that CachingConfigurer is " +
					"implemented only once or not at all.");
		}
		CachingConfigurer configurer = configurers.iterator().next();
		// 使用容器中的CachingConfigurer来配置缓存的属性
		useCachingConfigurer(configurer);
	}

	/**
	 * 从 {@link CachingConfigurer} 中提取缓存属性
	 */
	protected void useCachingConfigurer(CachingConfigurer config) {
		this.cacheManager = config::cacheManager;
		this.cacheResolver = config::cacheResolver;
		this.keyGenerator = config::keyGenerator;
		this.errorHandler = config::errorHandler;
	}

}
