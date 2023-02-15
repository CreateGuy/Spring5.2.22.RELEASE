/*
 * Copyright 2002-2020 the original author or authors.
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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.util.function.SupplierUtils;

/**
 * 缓存方面的基类，主要实现是 {@link CacheInterceptor}
 *
 * <p>This enables the underlying Spring caching infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling relevant methods in the correct order.
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link CacheOperationSource} is
 * used for determining caching operations, a {@link KeyGenerator} will build the
 * cache keys, and a {@link CacheResolver} will resolve the actual cache(s) to use.
 *
 * <p>Note: A cache aspect is serializable but does not perform any actual caching
 * after deserialization.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheAspectSupport extends AbstractCacheInvoker
		implements BeanFactoryAware, InitializingBean, SmartInitializingSingleton {

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 缓存的值
	 */
	private final Map<CacheOperationCacheKey, CacheOperationMetadata> metadataCache = new ConcurrentHashMap<>(1024);

	private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator();

	/**
	 * 保存 {@link CacheOperation} 的地方
	 */
	@Nullable
	private CacheOperationSource cacheOperationSource;

	private SingletonSupplier<KeyGenerator> keyGenerator = SingletonSupplier.of(SimpleKeyGenerator::new);

	@Nullable
	private SingletonSupplier<CacheResolver> cacheResolver;

	@Nullable
	private BeanFactory beanFactory;

	/**
	 * 是否初始化完毕
	 */
	private boolean initialized = false;


	/**
	 * Configure this aspect with the given error handler, key generator and cache resolver/manager
	 * suppliers, applying the corresponding default if a supplier is not resolvable.
	 * @since 5.1
	 */
	public void configure(
			@Nullable Supplier<CacheErrorHandler> errorHandler, @Nullable Supplier<KeyGenerator> keyGenerator,
			@Nullable Supplier<CacheResolver> cacheResolver, @Nullable Supplier<CacheManager> cacheManager) {

		this.errorHandler = new SingletonSupplier<>(errorHandler, SimpleCacheErrorHandler::new);
		this.keyGenerator = new SingletonSupplier<>(keyGenerator, SimpleKeyGenerator::new);
		this.cacheResolver = new SingletonSupplier<>(cacheResolver,
				() -> SimpleCacheResolver.of(SupplierUtils.resolve(cacheManager)));
	}


	/**
	 * Set one or more cache operation sources which are used to find the cache
	 * attributes. If more than one source is provided, they will be aggregated
	 * using a {@link CompositeCacheOperationSource}.
	 * @see #setCacheOperationSource
	 */
	public void setCacheOperationSources(CacheOperationSource... cacheOperationSources) {
		Assert.notEmpty(cacheOperationSources, "At least 1 CacheOperationSource needs to be specified");
		this.cacheOperationSource = (cacheOperationSources.length > 1 ?
				new CompositeCacheOperationSource(cacheOperationSources) : cacheOperationSources[0]);
	}

	/**
	 * Set the CacheOperationSource for this cache aspect.
	 * @since 5.1
	 * @see #setCacheOperationSources
	 */
	public void setCacheOperationSource(@Nullable CacheOperationSource cacheOperationSource) {
		this.cacheOperationSource = cacheOperationSource;
	}

	/**
	 * Return the CacheOperationSource for this cache aspect.
	 */
	@Nullable
	public CacheOperationSource getCacheOperationSource() {
		return this.cacheOperationSource;
	}

	/**
	 * Set the default {@link KeyGenerator} that this cache aspect should delegate to
	 * if no specific key generator has been set for the operation.
	 * <p>The default is a {@link SimpleKeyGenerator}.
	 */
	public void setKeyGenerator(KeyGenerator keyGenerator) {
		this.keyGenerator = SingletonSupplier.of(keyGenerator);
	}

	/**
	 * Return the default {@link KeyGenerator} that this cache aspect delegates to.
	 */
	public KeyGenerator getKeyGenerator() {
		return this.keyGenerator.obtain();
	}

	/**
	 * Set the default {@link CacheResolver} that this cache aspect should delegate
	 * to if no specific cache resolver has been set for the operation.
	 * <p>The default resolver resolves the caches against their names and the
	 * default cache manager.
	 * @see #setCacheManager
	 * @see SimpleCacheResolver
	 */
	public void setCacheResolver(@Nullable CacheResolver cacheResolver) {
		this.cacheResolver = SingletonSupplier.ofNullable(cacheResolver);
	}

	/**
	 * Return the default {@link CacheResolver} that this cache aspect delegates to.
	 */
	@Nullable
	public CacheResolver getCacheResolver() {
		return SupplierUtils.resolve(this.cacheResolver);
	}

	/**
	 * Set the {@link CacheManager} to use to create a default {@link CacheResolver}.
	 * Replace the current {@link CacheResolver}, if any.
	 * @see #setCacheResolver
	 * @see SimpleCacheResolver
	 */
	public void setCacheManager(CacheManager cacheManager) {
		this.cacheResolver = SingletonSupplier.of(new SimpleCacheResolver(cacheManager));
	}

	/**
	 * Set the containing {@link BeanFactory} for {@link CacheManager} and other
	 * service lookups.
	 * @since 4.3
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		Assert.state(getCacheOperationSource() != null, "The 'cacheOperationSources' property is required: " +
				"If there are no cacheable methods, then don't use a cache aspect.");
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (getCacheResolver() == null) {
			// Lazily initialize cache resolver via default cache manager...
			Assert.state(this.beanFactory != null, "CacheResolver or BeanFactory must be set on cache aspect");
			try {
				setCacheManager(this.beanFactory.getBean(CacheManager.class));
			}
			catch (NoUniqueBeanDefinitionException ex) {
				throw new IllegalStateException("No CacheResolver specified, and no unique bean of type " +
						"CacheManager found. Mark one as primary or declare a specific CacheManager to use.");
			}
			catch (NoSuchBeanDefinitionException ex) {
				throw new IllegalStateException("No CacheResolver specified, and no bean of type CacheManager found. " +
						"Register a CacheManager bean or remove the @EnableCaching annotation from your configuration.");
			}
		}
		this.initialized = true;
	}


	/**
	 * Convenience method to return a String representation of this Method
	 * for use in logging. Can be overridden in subclasses to provide a
	 * different identifier for the given method.
	 * @param method the method we're interested in
	 * @param targetClass class the method is on
	 * @return log message identifying this method
	 * @see org.springframework.util.ClassUtils#getQualifiedMethodName
	 */
	protected String methodIdentification(Method method, Class<?> targetClass) {
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		return ClassUtils.getQualifiedMethodName(specificMethod);
	}

	protected Collection<? extends Cache> getCaches(
			CacheOperationInvocationContext<CacheOperation> context, CacheResolver cacheResolver) {

		Collection<? extends Cache> caches = cacheResolver.resolveCaches(context);
		if (caches.isEmpty()) {
			throw new IllegalStateException("No cache could be resolved for '" +
					context.getOperation() + "' using resolver '" + cacheResolver +
					"'. At least one cache should be provided per cache operation.");
		}
		return caches;
	}

	/**
	 *
	 * @param operation
	 * @param method
	 * @param args
	 * @param target 方法对应的对象
	 * @param targetClass 方法对应的类的Class对象
	 * @return
	 */
	protected CacheOperationContext getOperationContext(
			CacheOperation operation, Method method, Object[] args, Object target, Class<?> targetClass) {

		CacheOperationMetadata metadata = getCacheOperationMetadata(operation, method, targetClass);
		return new CacheOperationContext(metadata, args, target);
	}

	/**
	 * Return the {@link CacheOperationMetadata} for the specified operation.
	 * <p>Resolve the {@link CacheResolver} and the {@link KeyGenerator} to be
	 * used for the operation.
	 * @param operation the operation
	 * @param method the method on which the operation is invoked
	 * @param targetClass the target type
	 * @return the resolved metadata for the operation
	 */
	protected CacheOperationMetadata getCacheOperationMetadata(
			CacheOperation operation, Method method, Class<?> targetClass) {

		CacheOperationCacheKey cacheKey = new CacheOperationCacheKey(operation, method, targetClass);
		CacheOperationMetadata metadata = this.metadataCache.get(cacheKey);

		// 当缓存操作的元数据不存在，配置对象的属性
		if (metadata == null) {
			// 设置键生成器
			KeyGenerator operationKeyGenerator;
			if (StringUtils.hasText(operation.getKeyGenerator())) {
				operationKeyGenerator = getBean(operation.getKeyGenerator(), KeyGenerator.class);
			}
			else {
				operationKeyGenerator = getKeyGenerator();
			}

			// 感觉是
			CacheResolver operationCacheResolver;
			if (StringUtils.hasText(operation.getCacheResolver())) {
				operationCacheResolver = getBean(operation.getCacheResolver(), CacheResolver.class);
			}
			else if (StringUtils.hasText(operation.getCacheManager())) {
				CacheManager cacheManager = getBean(operation.getCacheManager(), CacheManager.class);
				operationCacheResolver = new SimpleCacheResolver(cacheManager);
			}
			else {
				operationCacheResolver = getCacheResolver();
				Assert.state(operationCacheResolver != null, "No CacheResolver/CacheManager set");
			}

			// 封装参数
			metadata = new CacheOperationMetadata(operation, method, targetClass,
					operationKeyGenerator, operationCacheResolver);
			this.metadataCache.put(cacheKey, metadata);
		}
		return metadata;
	}

	/**
	 * Return a bean with the specified name and type. Used to resolve services that
	 * are referenced by name in a {@link CacheOperation}.
	 * @param beanName the name of the bean, as defined by the operation
	 * @param expectedType type for the bean
	 * @return the bean matching that name
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if such bean does not exist
	 * @see CacheOperation#getKeyGenerator()
	 * @see CacheOperation#getCacheManager()
	 * @see CacheOperation#getCacheResolver()
	 */
	protected <T> T getBean(String beanName, Class<T> expectedType) {
		if (this.beanFactory == null) {
			throw new IllegalStateException(
					"BeanFactory must be set on cache aspect for " + expectedType.getSimpleName() + " retrieval");
		}
		return BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, expectedType, beanName);
	}

	/**
	 * Clear the cached metadata.
	 */
	protected void clearMetadataCache() {
		this.metadataCache.clear();
		this.evaluator.clear();
	}

	/**
	 * 执行拦截器
	 * @param invoker 具体执行的方法
	 * @param target 目标对象，比如说Controller
	 * @param method 目标方法
	 * @param args 目标方法中的参数
	 * @return
	 */
	@Nullable
	protected Object execute(CacheOperationInvoker invoker, Object target, Method method, Object[] args) {
		// 是否已经初始化完毕
		if (this.initialized) {
			Class<?> targetClass = getTargetClass(target);
			CacheOperationSource cacheOperationSource = getCacheOperationSource();
			if (cacheOperationSource != null) {
				// 获取 CacheOperation，其实就代表了那三个注解及内部的属性
				Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
				// 要设置过那三个注解才行
				if (!CollectionUtils.isEmpty(operations)) {
					return execute(invoker, method,
							new CacheOperationContexts(operations, method, args, target, targetClass));
				}
			}
		}

		return invoker.invoke();
	}

	/**
	 * 直接调用目标方法
	 * <p>If an exception occurs it will be wrapped in a
	 * {@link CacheOperationInvoker.ThrowableWrapper}: the exception can be handled
	 * or modified but it <em>must</em> be wrapped in a
	 * {@link CacheOperationInvoker.ThrowableWrapper} as well.
	 * @param invoker the invoker handling the operation being cached
	 * @return the result of the invocation
	 * @see CacheOperationInvoker#invoke()
	 */
	protected Object invokeOperation(CacheOperationInvoker invoker) {
		return invoker.invoke();
	}

	private Class<?> getTargetClass(Object target) {
		return AopProxyUtils.ultimateTargetClass(target);
	}

	@Nullable
	private Object execute(final CacheOperationInvoker invoker, Method method, CacheOperationContexts contexts) {
		// 开启了加锁的处理
		if (contexts.isSynchronized()) {
			// 先获取 CacheableOperation
			CacheOperationContext context = contexts.get(CacheableOperation.class).iterator().next();
			// 判断是否通过 Condition 的过滤
			if (isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
				// 计算缓存操作的key
				Object key = generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
				// 拿到第一个缓存文件夹
				Cache cache = context.getCaches().iterator().next();
				try {
					// 开始加锁获取返回值，然后包装
					return wrapCacheValue(method, handleSynchronizedGet(invoker, key, cache));
				}
				catch (Cache.ValueRetrievalException ex) {
					// Directly propagate ThrowableWrapper from the invoker,
					// or potentially also an IllegalArgumentException etc.
					ReflectionUtils.rethrowRuntimeException(ex.getCause());
				}
			}
			else {
				// 不需要缓存，直接调用目标方法
				return invokeOperation(invoker);
			}
		}


		// 是否需要清除缓存，对应 @CacheEvict
		// 注意第二个参数是 true，表明是在执行目标方法之前执行的
		processCacheEvicts(contexts.get(CacheEvictOperation.class), true,
				CacheOperationExpressionEvaluator.NO_RESULT);

		// 检查是否有符合条件的缓存值，对应 @Cacheable
		Cache.ValueWrapper cacheHit = findCachedItem(contexts.get(CacheableOperation.class));

		// @Cacheable没有命中，就代表是新的key
		List<CachePutRequest> cachePutRequests = new LinkedList<>();
		if (cacheHit == null) {
			//新增
			collectPutRequests(contexts.get(CacheableOperation.class),
					CacheOperationExpressionEvaluator.NO_RESULT, cachePutRequests);
		}

		Object cacheValue;
		Object returnValue;

		// 如果可以从@Cacheable中获取缓存值，并且无法命中 @CachePut
		if (cacheHit != null && !hasCachePut(contexts)) {
			// 直接包装缓存中的值，作为返回值
			cacheValue = cacheHit.get();
			returnValue = wrapCacheValue(method, cacheValue);
		}
		else {
			// 如果没有缓存命中，则调用目标方法，再进行包装
			returnValue = invokeOperation(invoker);
			cacheValue = unwrapReturnValue(returnValue);
		}

		// 新增或者更新缓存中的值
		// @CachePut更主要的是更新
		collectPutRequests(contexts.get(CachePutOperation.class), cacheValue, cachePutRequests);

		// 根据返回值判断是否需要加入缓存中，无论是来自 @CachePut 还是 @Cacheable 未命中的情况
		// @Cacheable 未命中的情况是因为是新的键
		for (CachePutRequest cachePutRequest : cachePutRequests) {
			cachePutRequest.apply(cacheValue);
		}

		// 是否需要清除缓存
		// 注意第二个参数是 false，表明是在执行目标方法之后执行的
		processCacheEvicts(contexts.get(CacheEvictOperation.class), false, cacheValue);

		return returnValue;
	}

	/**
	 * 开始加锁获取返回值，然后包装
	 * @param invoker
	 * @param key
	 * @param cache
	 * @return
	 */
	@Nullable
	private Object handleSynchronizedGet(CacheOperationInvoker invoker, Object key, Cache cache) {
		InvocationAwareResult invocationResult = new InvocationAwareResult();
		// 重点就是get方式
		// 像RedisCache的get方法会调用getSynchronized方法然后执行，而这个方法就标注了 synchronized 关键字
		Object result = cache.get(key, () -> {
			//标记为已调用
			invocationResult.invoked = true;
			if (logger.isTraceEnabled()) {
				logger.trace("No cache entry for key '" + key + "' in cache " + cache.getName());
			}
			//执行目标方法，然后包装
			return unwrapReturnValue(invokeOperation(invoker));
		});
		if (!invocationResult.invoked && logger.isTraceEnabled()) {
			logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
		}
		return result;
	}

	/**
	 * 包装缓存值
	 * @param method
	 * @param cacheValue
	 * @return
	 */
	@Nullable
	private Object wrapCacheValue(Method method, @Nullable Object cacheValue) {
		// 返回值是 Optional
		if (method.getReturnType() == Optional.class &&
				(cacheValue == null || cacheValue.getClass() != Optional.class)) {
			return Optional.ofNullable(cacheValue);
		}
		return cacheValue;
	}

	@Nullable
	private Object unwrapReturnValue(Object returnValue) {
		return ObjectUtils.unwrapOptional(returnValue);
	}

	private boolean hasCachePut(CacheOperationContexts contexts) {
		// Evaluate the conditions *without* the result object because we don't have it yet...
		Collection<CacheOperationContext> cachePutContexts = contexts.get(CachePutOperation.class);
		Collection<CacheOperationContext> excluded = new ArrayList<>();
		for (CacheOperationContext context : cachePutContexts) {
			try {
				//如果需要加载到缓存中
				if (!context.isConditionPassing(CacheOperationExpressionEvaluator.RESULT_UNAVAILABLE)) {
					excluded.add(context);
				}
			}
			catch (VariableNotAvailableException ex) {
				// Ignoring failure due to missing result, consider the cache put has to proceed
			}
		}
		// 如果不相同，就说明有CachePut表示不需要保存到缓存中
		return (cachePutContexts.size() != excluded.size());
	}

	/**
	 * 是否清除缓存
	 * @param contexts 是 CacheEvictOperation
	 * @param beforeInvocation 是否是在执行目标方法之前执行的本方法
	 * @param result
	 */
	private void processCacheEvicts(
			Collection<CacheOperationContext> contexts, boolean beforeInvocation, @Nullable Object result) {

		for (CacheOperationContext context : contexts) {
			CacheEvictOperation operation = (CacheEvictOperation) context.metadata.operation;

			// 如果是在目标方法执行之前，又设置需要在执行目标方法之前删除缓存
			// 是否是候选的键
			if (beforeInvocation == operation.isBeforeInvocation() && isConditionPassing(context, result)) {
				// 准备清除缓存
				performCacheEvict(context, operation, result);
			}
		}
	}

	/**
	 * 准备清除缓存
	 * @param context
	 * @param operation
	 * @param result
	 */
	private void performCacheEvict(
			CacheOperationContext context, CacheEvictOperation operation, @Nullable Object result) {

		Object key = null;
		for (Cache cache : context.getCaches()) {
			//是否清除所有缓存
			if (operation.isCacheWide()) {
				logInvalidating(context, operation, null);
				//清除所有键的缓存
				doClear(cache, operation.isBeforeInvocation());
			}
			else {
				if (key == null) {
					key = generateKey(context, result);
				}
				logInvalidating(context, operation, key);
				//清除单个键的缓存
				doEvict(cache, key, operation.isBeforeInvocation());
			}
		}
	}

	private void logInvalidating(CacheOperationContext context, CacheEvictOperation operation, @Nullable Object key) {
		if (logger.isTraceEnabled()) {
			logger.trace("Invalidating " + (key != null ? "cache key [" + key + "]" : "entire cache") +
					" for operation " + operation + " on method " + context.metadata.method);
		}
	}

	/**
	 * 查找指定建的缓存值
	 * @param contexts 用于读取和保存的Context
	 * @return
	 */
	@Nullable
	private Cache.ValueWrapper findCachedItem(Collection<CacheOperationContext> contexts) {
		Object result = CacheOperationExpressionEvaluator.NO_RESULT;
		for (CacheOperationContext context : contexts) {
			// 判断是否通过 Condition 的过滤
			if (isConditionPassing(context, result)) {
				// 计算缓存操作的key
				Object key = generateKey(context, result);
				// 获得缓存值
				Cache.ValueWrapper cached = findInCaches(context, key);
				if (cached != null) {
					return cached;
				}
				else {
					if (logger.isTraceEnabled()) {
						logger.trace("No cache entry for key '" + key + "' in cache(s) " + context.getCacheNames());
					}
				}
			}
		}
		return null;
	}

	/**
	 * 新增一个缓存，但是还没有加入缓存中去
	 * @param contexts the contexts to handle
	 * @param result the result item (never {@code null})
	 * @param putRequests the collection to update
	 */
	private void collectPutRequests(Collection<CacheOperationContext> contexts,
			@Nullable Object result, Collection<CachePutRequest> putRequests) {

		for (CacheOperationContext context : contexts) {
			// 判断是否是候选的键
			if (isConditionPassing(context, result)) {
				Object key = generateKey(context, result);
				putRequests.add(new CachePutRequest(context, key));
			}
		}
	}

	/**
	 * 获得缓存值
	 * @param context
	 * @param key
	 * @return
	 */
	@Nullable
	private Cache.ValueWrapper findInCaches(CacheOperationContext context, Object key) {
		//遍历所有缓存地址，获取缓存值
		for (Cache cache : context.getCaches()) {
			//从指定的 Cache 中获取缓存中的值
			Cache.ValueWrapper wrapper = doGet(cache, key);
			if (wrapper != null) {
				if (logger.isTraceEnabled()) {
					logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
				}
				return wrapper;
			}
		}
		return null;
	}

	/**
	 * 判断是否是候选的键
	 * @param context
	 * @param result
	 * @return
	 */
	private boolean isConditionPassing(CacheOperationContext context, @Nullable Object result) {
		//判断是否通过 Condition 的过滤
		boolean passing = context.isConditionPassing(result);
		if (!passing && logger.isTraceEnabled()) {
			logger.trace("Cache condition failed on method " + context.metadata.method +
					" for operation " + context.metadata.operation);
		}
		return passing;
	}

	/**
	 * 计算缓存操作的键
	 * @param context
	 * @param result
	 * @return
	 */
	private Object generateKey(CacheOperationContext context, @Nullable Object result) {
		// 计算缓存操作的key
		Object key = context.generateKey(result);
		if (key == null) {
			throw new IllegalArgumentException("Null key returned for cache operation (maybe you are " +
					"using named params on classes without debug info?) " + context.metadata.operation);
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Computed cache key '" + key + "' for operation " + context.metadata.operation);
		}
		return key;
	}


	private class CacheOperationContexts {

		private final MultiValueMap<Class<? extends CacheOperation>, CacheOperationContext> contexts;

		/**
		 * 是否开启了同步
		 */
		private final boolean sync;

		public CacheOperationContexts(Collection<? extends CacheOperation> operations, Method method,
				Object[] args, Object target, Class<?> targetClass) {

			this.contexts = new LinkedMultiValueMap<>(operations.size());
			for (CacheOperation op : operations) {
				this.contexts.add(op.getClass(), getOperationContext(op, method, args, target, targetClass));
			}
			this.sync = determineSyncFlag(method);
		}

		public Collection<CacheOperationContext> get(Class<? extends CacheOperation> operationClass) {
			Collection<CacheOperationContext> result = this.contexts.get(operationClass);
			return (result != null ? result : Collections.emptyList());
		}

		public boolean isSynchronized() {
			return this.sync;
		}

		private boolean determineSyncFlag(Method method) {
			// 是获得CacheableOperation，也就是@Cacheable的信息
			List<CacheOperationContext> cacheOperationContexts = this.contexts.get(CacheableOperation.class);
			if (cacheOperationContexts == null) {  // no @Cacheable operation at all
				return false;
			}
			boolean syncEnabled = false;

			// 其实就是看CacheableOperation对应的那一个@Cacheable是否设置了同步标记
			for (CacheOperationContext cacheOperationContext : cacheOperationContexts) {
				if (((CacheableOperation) cacheOperationContext.getOperation()).isSync()) {
					syncEnabled = true;
					break;
				}
			}
			if (syncEnabled) {
				// 开启了同步，不能设置多个缓存注解
				if (this.contexts.size() > 1) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) cannot be combined with other cache operations on '" + method + "'");
				}
				// 开启了同步，不能设置多个@Cacheabel
				if (cacheOperationContexts.size() > 1) {
					throw new IllegalStateException(
							"Only one @Cacheable(sync=true) entry is allowed on '" + method + "'");
				}
				CacheOperationContext cacheOperationContext = cacheOperationContexts.iterator().next();
				CacheableOperation operation = (CacheableOperation) cacheOperationContext.getOperation();
				if (cacheOperationContext.getCaches().size() > 1) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) only allows a single cache on '" + operation + "'");
				}
				if (StringUtils.hasText(operation.getUnless())) {
					throw new IllegalStateException(
							"@Cacheable(sync=true) does not support unless attribute on '" + operation + "'");
				}
				return true;
			}
			return false;
		}
	}


	/**
	 * 缓存操作的元数据，它不依赖于特定的调用，这使它成为缓存的一个很好的候选者
	 */
	protected static class CacheOperationMetadata {

		private final CacheOperation operation;

		private final Method method;

		private final Class<?> targetClass;

		private final Method targetMethod;

		private final AnnotatedElementKey methodKey;

		/**
		 * 键生成器
		 */
		private final KeyGenerator keyGenerator;

		/**
		 * 不懂
		 */
		private final CacheResolver cacheResolver;

		public CacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass,
				KeyGenerator keyGenerator, CacheResolver cacheResolver) {

			this.operation = operation;
			this.method = BridgeMethodResolver.findBridgedMethod(method);
			this.targetClass = targetClass;
			this.targetMethod = (!Proxy.isProxyClass(targetClass) ?
					AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
			this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
			this.keyGenerator = keyGenerator;
			this.cacheResolver = cacheResolver;
		}
	}


	/**
	 * A {@link CacheOperationInvocationContext} context for a {@link CacheOperation}.
	 */
	protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {

		private final CacheOperationMetadata metadata;

		/**
		 * 方法的入参
		 */
		private final Object[] args;

		/**
		 * 方法对应的对象
		 */
		private final Object target;

		/**
		 * 缓存文件夹，可以设置多个
		 * <li>可以理解为可以在磁盘的多个文件夹下都存放数据</li>
		 */
		private final Collection<? extends Cache> caches;

		/**
		 * 缓存的名称，是注解中的 key
		 */
		private final Collection<String> cacheNames;

		/**
		 * 是否通过了 Condition 的条件过滤
		 */
		@Nullable
		private Boolean conditionPassing;

		public CacheOperationContext(CacheOperationMetadata metadata, Object[] args, Object target) {
			this.metadata = metadata;
			this.args = extractArgs(metadata.method, args);
			this.target = target;
			// 调用缓存解析器获取缓存文件夹的所有记录(文件)
			this.caches = CacheAspectSupport.this.getCaches(this, metadata.cacheResolver);
			// 设置缓存名称
			this.cacheNames = createCacheNames(this.caches);
		}

		@Override
		public CacheOperation getOperation() {
			return this.metadata.operation;
		}

		@Override
		public Object getTarget() {
			return this.target;
		}

		@Override
		public Method getMethod() {
			return this.metadata.method;
		}

		@Override
		public Object[] getArgs() {
			return this.args;
		}

		private Object[] extractArgs(Method method, Object[] args) {
			if (!method.isVarArgs()) {
				return args;
			}
			Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
			Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
			System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
			System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
			return combinedArgs;
		}

		/**
		 * 判断是否通过 Condition 的过滤
		 * @param result
		 * @return
		 */
		protected boolean isConditionPassing(@Nullable Object result) {
			// 先从缓存中判断
			if (this.conditionPassing == null) {
				// 判断用户是否在 @Cacheable 中填写了 condition
				if (StringUtils.hasText(this.metadata.operation.getCondition())) {
					EvaluationContext evaluationContext = createEvaluationContext(result);
					//开始判断条件
					this.conditionPassing = evaluator.condition(this.metadata.operation.getCondition(),
							this.metadata.methodKey, evaluationContext);
				}
				else {
					//如果没有设置 condition 就是通过
					this.conditionPassing = true;
				}
			}
			return this.conditionPassing;
		}

		/**
		 * 判断是否通过 unless 的过滤
		 * @param value
		 * @return
		 */
		protected boolean canPutToCache(@Nullable Object value) {
			String unless = "";
			// 获得 @Cacheable 中 unless
			if (this.metadata.operation instanceof CacheableOperation) {
				unless = ((CacheableOperation) this.metadata.operation).getUnless();
			}
			// 获得 @CachePut 中 unless
			else if (this.metadata.operation instanceof CachePutOperation) {
				unless = ((CachePutOperation) this.metadata.operation).getUnless();
			}

			// 判断是否设置过 unless 条件
			if (StringUtils.hasText(unless)) {
				EvaluationContext evaluationContext = createEvaluationContext(value);
				//校验是否满足条件
				return !evaluator.unless(unless, this.metadata.methodKey, evaluationContext);
			}
			return true;
		}

		/**
		 * 计算缓存操作的key
		 */
		@Nullable
		protected Object generateKey(@Nullable Object result) {
			//判断是否设置过key
			if (StringUtils.hasText(this.metadata.operation.getKey())) {
				EvaluationContext evaluationContext = createEvaluationContext(result);
				//计算键
				//比如说是键是 query.id，那么键就是query.id的值
				return evaluator.key(this.metadata.operation.getKey(), this.metadata.methodKey, evaluationContext);
			}
			//没有设置过就利用键生成器，生成键
			return this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
		}

		/**
		 * 不懂
		 * @param result
		 * @return
		 */
		private EvaluationContext createEvaluationContext(@Nullable Object result) {
			return evaluator.createEvaluationContext(this.caches, this.metadata.method, this.args,
					this.target, this.metadata.targetClass, this.metadata.targetMethod, result, beanFactory);
		}

		protected Collection<? extends Cache> getCaches() {
			return this.caches;
		}

		protected Collection<String> getCacheNames() {
			return this.cacheNames;
		}

		/**
		 * 设置缓存名称
		 * @param caches
		 * @return
		 */
		private Collection<String> createCacheNames(Collection<? extends Cache> caches) {
			Collection<String> names = new ArrayList<>();
			for (Cache cache : caches) {
				names.add(cache.getName());
			}
			return names;
		}
	}


	private class CachePutRequest {

		private final CacheOperationContext context;

		private final Object key;

		public CachePutRequest(CacheOperationContext context, Object key) {
			this.context = context;
			this.key = key;
		}

		public void apply(@Nullable Object result) {
			//判断是否通过 unless 的过滤
			if (this.context.canPutToCache(result)) {
				//将值加入缓存中
				for (Cache cache : this.context.getCaches()) {
					doPut(cache, this.key, result);
				}
			}
		}
	}


	private static final class CacheOperationCacheKey implements Comparable<CacheOperationCacheKey> {

		private final CacheOperation cacheOperation;

		private final AnnotatedElementKey methodCacheKey;

		private CacheOperationCacheKey(CacheOperation cacheOperation, Method method, Class<?> targetClass) {
			this.cacheOperation = cacheOperation;
			this.methodCacheKey = new AnnotatedElementKey(method, targetClass);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof CacheOperationCacheKey)) {
				return false;
			}
			CacheOperationCacheKey otherKey = (CacheOperationCacheKey) other;
			return (this.cacheOperation.equals(otherKey.cacheOperation) &&
					this.methodCacheKey.equals(otherKey.methodCacheKey));
		}

		@Override
		public int hashCode() {
			return (this.cacheOperation.hashCode() * 31 + this.methodCacheKey.hashCode());
		}

		@Override
		public String toString() {
			return this.cacheOperation + " on " + this.methodCacheKey;
		}

		@Override
		public int compareTo(CacheOperationCacheKey other) {
			int result = this.cacheOperation.getName().compareTo(other.cacheOperation.getName());
			if (result == 0) {
				result = this.methodCacheKey.compareTo(other.methodCacheKey);
			}
			return result;
		}
	}

	/**
	 * 用于记录缓存方法被调用的内部holder类
	 */
	private static class InvocationAwareResult {

		boolean invoked;

	}

}
