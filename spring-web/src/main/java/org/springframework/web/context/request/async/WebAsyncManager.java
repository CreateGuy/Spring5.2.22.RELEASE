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

package org.springframework.web.context.request.async;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.async.DeferredResult.DeferredResultHandler;

/**
 * 用于管理异步请求处理的类，主要用作SPI，通常不被应用程序类直接使用。
 * <p>An async scenario starts with request processing as usual in a thread (T1).
 * Concurrent request handling can be initiated by calling
 * {@link #startCallableProcessing(Callable, Object...) startCallableProcessing} or
 * {@link #startDeferredResultProcessing(DeferredResult, Object...) startDeferredResultProcessing},
 * both of which produce a result in a separate thread (T2). The result is saved
 * and the request dispatched to the container, to resume processing with the saved
 * result in a third thread (T3). Within the dispatched thread (T3), the saved
 * result can be accessed via {@link #getConcurrentResult()} or its presence
 * detected via {@link #hasConcurrentResult()}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.2
 * @see org.springframework.web.context.request.AsyncWebRequestInterceptor
 * @see org.springframework.web.servlet.AsyncHandlerInterceptor
 * @see org.springframework.web.filter.OncePerRequestFilter#shouldNotFilterAsyncDispatch
 * @see org.springframework.web.filter.OncePerRequestFilter#isAsyncDispatch
 */
public final class WebAsyncManager {

	private static final Object RESULT_NONE = new Object();

	private static final AsyncTaskExecutor DEFAULT_TASK_EXECUTOR =
			new SimpleAsyncTaskExecutor(WebAsyncManager.class.getSimpleName());

	private static final Log logger = LogFactory.getLog(WebAsyncManager.class);

	/**
	 * {@link WebAsyncTask} 的超时拦截器
	 */
	private static final CallableProcessingInterceptor timeoutCallableInterceptor =
			new TimeoutCallableProcessingInterceptor();

	/**
	 * {@link DeferredResult} 的超时拦截器
	 */
	private static final DeferredResultProcessingInterceptor timeoutDeferredResultInterceptor =
			new TimeoutDeferredResultProcessingInterceptor();

	private static Boolean taskExecutorWarning = true;

	/**
	 * 包装 NativeWebRequest 的请求
	 */
	private AsyncWebRequest asyncWebRequest;

	/**
	 * 要使用的异步任务线程池
	 */
	private AsyncTaskExecutor taskExecutor = DEFAULT_TASK_EXECUTOR;

	/**
	 * 如果非默认值就代表异步任务执行完毕设置为返回值，或者是出现了异常而设置的异常
	 */
	private volatile Object concurrentResult = RESULT_NONE;

	/**
	 * 默认是准备开启异步任务的 {@link org.springframework.web.method.support.ModelAndViewContainer}
	 */
	private volatile Object[] concurrentResultContext;

	/*
	 * 任务执行过程中，是否出现了错误，注意：如果超时了，被处理了，依旧不算出错了
	 * Whether the concurrentResult is an error. If such errors remain unhandled, some
	 * Servlet containers will call AsyncListener#onError at the end, after the ASYNC
	 * and/or the ERROR dispatch (Boot's case), and we need to ignore those.
	 */
	private volatile boolean errorHandlingInProgress;

	/**
	 * 异步任务开始前后需要执行的回调方法，异步任务需要符合以下条件：
	 * <ol>
	 *     <li>支持 StreamingResponseBody 和 ResponseEntity<StreamingResponseBody> 类型的返回值，但是会被包装为 WebAsyncTask</li>
	 *     <li>支持 Callable 类型的返回值，但是会被包装为 WebAsyncTask</li>
	 *     <li>支持 WebAsyncTask 类型的返回值</li>
	 * </ol>
	 */
	private final Map<Object, CallableProcessingInterceptor> callableInterceptors = new LinkedHashMap<>();

	/**
	 * 异步任务开始前后需要执行的回调方法，异步任务需要符合以下条件：
	 * <ol>
	 *     <li>支持DeferredResult ListenableFuture CompletionStage 类型作为返回值</li>
	 *     <li>不懂</li>
	 * </ol>
	 */
	private final Map<Object, DeferredResultProcessingInterceptor> deferredResultInterceptors = new LinkedHashMap<>();


	/**
	 * Package-private constructor.
	 * @see WebAsyncUtils#getAsyncManager(javax.servlet.ServletRequest)
	 * @see WebAsyncUtils#getAsyncManager(org.springframework.web.context.request.WebRequest)
	 */
	WebAsyncManager() {
	}


	/**
	 * Configure the {@link AsyncWebRequest} to use. This property may be set
	 * more than once during a single request to accurately reflect the current
	 * state of the request (e.g. following a forward, request/response
	 * wrapping, etc). However, it should not be set while concurrent handling
	 * is in progress, i.e. while {@link #isConcurrentHandlingStarted()} is
	 * {@code true}.
	 * @param asyncWebRequest the web request to use
	 */
	public void setAsyncWebRequest(AsyncWebRequest asyncWebRequest) {
		Assert.notNull(asyncWebRequest, "AsyncWebRequest must not be null");
		this.asyncWebRequest = asyncWebRequest;
		this.asyncWebRequest.addCompletionHandler(() -> asyncWebRequest.removeAttribute(
				WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
	}

	/**
	 * Configure an AsyncTaskExecutor for use with concurrent processing via
	 * {@link #startCallableProcessing(Callable, Object...)}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * 判断异步任务是否已经启动
	 * <li>比如说返回值是 WebAsyncTask，那么对应的返回值处理器就已经开始处理</li>
	 * @return
	 */
	public boolean isConcurrentHandlingStarted() {
		return (this.asyncWebRequest != null && this.asyncWebRequest.isAsyncStarted());
	}

	/**
	 * 否存在一个结果值作为异步处理的结果
	 */
	public boolean hasConcurrentResult() {
		return (this.concurrentResult != RESULT_NONE);
	}

	/**
	 * Provides access to the result from concurrent handling.
	 * @return an Object, possibly an {@code Exception} or {@code Throwable} if
	 * concurrent handling raised one.
	 * @see #clearConcurrentResult()
	 */
	public Object getConcurrentResult() {
		return this.concurrentResult;
	}

	/**
	 * Provides access to additional processing context saved at the start of
	 * concurrent handling.
	 * @see #clearConcurrentResult()
	 */
	public Object[] getConcurrentResultContext() {
		return this.concurrentResultContext;
	}

	/**
	 * Get the {@link CallableProcessingInterceptor} registered under the given key.
	 * @param key the key
	 * @return the interceptor registered under that key, or {@code null} if none
	 */
	@Nullable
	public CallableProcessingInterceptor getCallableInterceptor(Object key) {
		return this.callableInterceptors.get(key);
	}

	/**
	 * Get the {@link DeferredResultProcessingInterceptor} registered under the given key.
	 * @param key the key
	 * @return the interceptor registered under that key, or {@code null} if none
	 */
	@Nullable
	public DeferredResultProcessingInterceptor getDeferredResultInterceptor(Object key) {
		return this.deferredResultInterceptors.get(key);
	}

	/**
	 * Register a {@link CallableProcessingInterceptor} under the given key.
	 * @param key the key
	 * @param interceptor the interceptor to register
	 */
	public void registerCallableInterceptor(Object key, CallableProcessingInterceptor interceptor) {
		Assert.notNull(key, "Key is required");
		Assert.notNull(interceptor, "CallableProcessingInterceptor  is required");
		this.callableInterceptors.put(key, interceptor);
	}

	/**
	 * Register a {@link CallableProcessingInterceptor} without a key.
	 * The key is derived from the class name and hashcode.
	 * @param interceptors one or more interceptors to register
	 */
	public void registerCallableInterceptors(CallableProcessingInterceptor... interceptors) {
		Assert.notNull(interceptors, "A CallableProcessingInterceptor is required");
		for (CallableProcessingInterceptor interceptor : interceptors) {
			String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
			this.callableInterceptors.put(key, interceptor);
		}
	}

	/**
	 * Register a {@link DeferredResultProcessingInterceptor} under the given key.
	 * @param key the key
	 * @param interceptor the interceptor to register
	 */
	public void registerDeferredResultInterceptor(Object key, DeferredResultProcessingInterceptor interceptor) {
		Assert.notNull(key, "Key is required");
		Assert.notNull(interceptor, "DeferredResultProcessingInterceptor is required");
		this.deferredResultInterceptors.put(key, interceptor);
	}

	/**
	 * Register one or more {@link DeferredResultProcessingInterceptor DeferredResultProcessingInterceptors} without a specified key.
	 * The default key is derived from the interceptor class name and hash code.
	 * @param interceptors one or more interceptors to register
	 */
	public void registerDeferredResultInterceptors(DeferredResultProcessingInterceptor... interceptors) {
		Assert.notNull(interceptors, "A DeferredResultProcessingInterceptor is required");
		for (DeferredResultProcessingInterceptor interceptor : interceptors) {
			String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
			this.deferredResultInterceptors.put(key, interceptor);
		}
	}

	/**
	 * Clear {@linkplain #getConcurrentResult() concurrentResult} and
	 * {@linkplain #getConcurrentResultContext() concurrentResultContext}.
	 */
	public void clearConcurrentResult() {
		synchronized (WebAsyncManager.this) {
			this.concurrentResult = RESULT_NONE;
			this.concurrentResultContext = null;
		}
	}

	/**
	 * Start concurrent request processing and execute the given task with an
	 * {@link #setTaskExecutor(AsyncTaskExecutor) AsyncTaskExecutor}. The result
	 * from the task execution is saved and the request dispatched in order to
	 * resume processing of that result. If the task raises an Exception then
	 * the saved result will be the raised Exception.
	 * @param callable a unit of work to be executed asynchronously
	 * @param processingContext additional context to save that can be accessed
	 * via {@link #getConcurrentResultContext()}
	 * @throws Exception if concurrent processing failed to start
	 * @see #getConcurrentResult()
	 * @see #getConcurrentResultContext()
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void startCallableProcessing(Callable<?> callable, Object... processingContext) throws Exception {
		Assert.notNull(callable, "Callable must not be null");
		startCallableProcessing(new WebAsyncTask(callable), processingContext);
	}

	/**
	 * 开始处理 Callable 的异步任务
	 * @param webAsyncTask
	 * @param processingContext
	 * @throws Exception
	 */
	public void startCallableProcessing(final WebAsyncTask<?> webAsyncTask, Object... processingContext)
			throws Exception {

		Assert.notNull(webAsyncTask, "WebAsyncTask must not be null");
		Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");

		// 设置超时时间
		Long timeout = webAsyncTask.getTimeout();
		if (timeout != null) {
			this.asyncWebRequest.setTimeout(timeout);
		}

		// 设置异步任务线程池
		AsyncTaskExecutor executor = webAsyncTask.getExecutor();
		if (executor != null) {
			this.taskExecutor = executor;
		}
		else {
			logExecutorWarning();
		}

		// 设置执行异步任务前后的回调接口
		List<CallableProcessingInterceptor> interceptors = new ArrayList<>();
		// 将WebAsyncTask中的超时回调，错误回调，完成回调注册成为一个拦截器
		interceptors.add(webAsyncTask.getInterceptor());
		interceptors.addAll(this.callableInterceptors.values());
		interceptors.add(timeoutCallableInterceptor);

		// 返回具体的任务
		final Callable<?> callable = webAsyncTask.getCallable();
		// 包装为拦截器链
		final CallableInterceptorChain interceptorChain = new CallableInterceptorChain(interceptors);

		// 注册超时处理器
		this.asyncWebRequest.addTimeoutHandler(() -> {
			if (logger.isDebugEnabled()) {
				logger.debug("Async request timeout for " + formatRequestUri());
			}
			// 超时后触发，为了从拦截器中获得返回值
			Object result = interceptorChain.triggerAfterTimeout(this.asyncWebRequest, callable);
			// 是否成功处理了超时
			if (result != CallableProcessingInterceptor.RESULT_NONE) {
				// 设置并发结果和派发
				setConcurrentResultAndDispatch(result);
			}
		});

		// 注册错误处理器
		this.asyncWebRequest.addErrorHandler(ex -> {
			if (!this.errorHandlingInProgress) {
				if (logger.isDebugEnabled()) {
					logger.debug("Async request error for " + formatRequestUri() + ": " + ex);
				}
				// 抛出异常后触发，为了从拦截器中获得返回值
				Object result = interceptorChain.triggerAfterError(this.asyncWebRequest, callable, ex);
				result = (result != CallableProcessingInterceptor.RESULT_NONE ? result : ex);
				// 设置并发结果和派发
				setConcurrentResultAndDispatch(result);
			}
		});

		// 注册任务完成处理器
		this.asyncWebRequest.addCompletionHandler(() ->
				//  任务完成后触发，执行完成任务回调
				interceptorChain.triggerAfterCompletion(this.asyncWebRequest, callable));

		// 在原始线程中，在执行 异步任务(Callable) 之前调用
		interceptorChain.applyBeforeConcurrentHandling(this.asyncWebRequest, callable);
		// 标记未开始异步任务处理
		startAsyncProcessing(processingContext);
		try {
			Future<?> future = this.taskExecutor.submit(() -> {
				Object result = null;
				try {
					// 在异步线程中，在执行 异步任务(Callable) 之前调用
					interceptorChain.applyPreProcess(this.asyncWebRequest, callable);
					// 执行任务
					result = callable.call();
				}
				catch (Throwable ex) {
					result = ex;
				}
				finally {
					// 在异步线程中，在执行 异步任务(Callable) 之后调用
					result = interceptorChain.applyPostProcess(this.asyncWebRequest, callable, result);
				}
				// 设置并发结果和派发
				setConcurrentResultAndDispatch(result);
			});
			// 设置执行结果
			interceptorChain.setTaskFuture(future);
		}
		// 当不能接受任务执行时抛出的异常
		catch (RejectedExecutionException ex) {
			Object result = interceptorChain.applyPostProcess(this.asyncWebRequest, callable, ex);
			// 设置并发结果和派发
			setConcurrentResultAndDispatch(result);
			throw ex;
		}
	}

	private void logExecutorWarning() {
		if (taskExecutorWarning && logger.isWarnEnabled()) {
			synchronized (DEFAULT_TASK_EXECUTOR) {
				AsyncTaskExecutor executor = this.taskExecutor;
				if (taskExecutorWarning &&
						(executor instanceof SimpleAsyncTaskExecutor || executor instanceof SyncTaskExecutor)) {
					String executorTypeName = executor.getClass().getSimpleName();
					logger.warn("\n!!!\n" +
							"An Executor is required to handle java.util.concurrent.Callable return values.\n" +
							"Please, configure a TaskExecutor in the MVC config under \"async support\".\n" +
							"The " + executorTypeName + " currently in use is not suitable under load.\n" +
							"-------------------------------\n" +
							"Request URI: '" + formatRequestUri() + "'\n" +
							"!!!");
					taskExecutorWarning = false;
				}
			}
		}
	}

	private String formatRequestUri() {
		HttpServletRequest request = this.asyncWebRequest.getNativeRequest(HttpServletRequest.class);
		return request != null ? request.getRequestURI() : "servlet container";
	}

	/**
	 * 设置并发结果和派发，注意：java是地址传递，也就说等异步任务结束，这里的值也会更新
	 * @param result
	 */
	private void setConcurrentResultAndDispatch(Object result) {
		// 设置处理结果
		synchronized (WebAsyncManager.this) {
			if (this.concurrentResult != RESULT_NONE) {
				return;
			}
			this.concurrentResult = result;
			this.errorHandlingInProgress = (result instanceof Throwable);
		}

		// 异步情况是否已经处理完成
		if (this.asyncWebRequest.isAsyncComplete()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Async result set but request already complete: " + formatRequestUri());
			}
			return;
		}

		if (logger.isDebugEnabled()) {
			boolean isError = result instanceof Throwable;
			logger.debug("Async " + (isError ? "error" : "result set") + ", dispatch to " + formatRequestUri());
		}
		this.asyncWebRequest.dispatch();
	}

	/**
	 * 启动并发请求处理并初始化给定的 {@code DeferredResult}
	 * {@link DeferredResult} with a {@link DeferredResultHandler} that saves
	 * the result and dispatches the request to resume processing of that
	 * result. The {@code AsyncWebRequest} is also updated with a completion
	 * handler that expires the {@code DeferredResult} and a timeout handler
	 * assuming the {@code DeferredResult} has a default timeout result.
	 * @param deferredResult the DeferredResult instance to initialize
	 * @param processingContext additional context to save that can be accessed
	 * via {@link #getConcurrentResultContext()}
	 * @throws Exception if concurrent processing failed to start
	 * @see #getConcurrentResult()
	 * @see #getConcurrentResultContext()
	 */
	public void startDeferredResultProcessing(
			final DeferredResult<?> deferredResult, Object... processingContext) throws Exception {

		Assert.notNull(deferredResult, "DeferredResult must not be null");
		Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");

		// 设置超时时间
		Long timeout = deferredResult.getTimeoutValue();
		if (timeout != null) {
			this.asyncWebRequest.setTimeout(timeout);
		}

		// 配置延时任务的拦截器
		List<DeferredResultProcessingInterceptor> interceptors = new ArrayList<>();
		interceptors.add(deferredResult.getInterceptor());
		interceptors.addAll(this.deferredResultInterceptors.values());
		interceptors.add(timeoutDeferredResultInterceptor);

		final DeferredResultInterceptorChain interceptorChain = new DeferredResultInterceptorChain(interceptors);

		// 注册延迟任务的超时拦截器
		this.asyncWebRequest.addTimeoutHandler(() -> {
			try {
				interceptorChain.triggerAfterTimeout(this.asyncWebRequest, deferredResult);
			}
			catch (Throwable ex) {
				setConcurrentResultAndDispatch(ex);
			}
		});

		// 注册延迟任务的错误拦截器
		this.asyncWebRequest.addErrorHandler(ex -> {
			if (!this.errorHandlingInProgress) {
				try {
					if (!interceptorChain.triggerAfterError(this.asyncWebRequest, deferredResult, ex)) {
						return;
					}
					deferredResult.setErrorResult(ex);
				}
				catch (Throwable interceptorEx) {
					setConcurrentResultAndDispatch(interceptorEx);
				}
			}
		});

		// 注册延迟任务的完成拦截器
		this.asyncWebRequest.addCompletionHandler(()
				-> interceptorChain.triggerAfterCompletion(this.asyncWebRequest, deferredResult));

		interceptorChain.applyBeforeConcurrentHandling(this.asyncWebRequest, deferredResult);
		startAsyncProcessing(processingContext);

		try {
			// 启动并发请求处理并初始化给定的 deferredResult
			interceptorChain.applyPreProcess(this.asyncWebRequest, deferredResult);
			deferredResult.setResultHandler(result -> {
				// 到这就说明延迟任务执行完毕
				result = interceptorChain.applyPostProcess(this.asyncWebRequest, deferredResult, result);
				// 设置并发结果和派发
				setConcurrentResultAndDispatch(result);
			});
		}
		catch (Throwable ex) {
			setConcurrentResultAndDispatch(ex);
		}
	}

	/**
	 * 开始异步任务处理
	 * @param processingContext
	 */
	private void startAsyncProcessing(Object[] processingContext) {
		synchronized (WebAsyncManager.this) {
			this.concurrentResult = RESULT_NONE;
			this.concurrentResultContext = processingContext;
			this.errorHandlingInProgress = false;
		}
		// 重点
		this.asyncWebRequest.startAsync();

		if (logger.isDebugEnabled()) {
			logger.debug("Started async request");
		}
	}

}
