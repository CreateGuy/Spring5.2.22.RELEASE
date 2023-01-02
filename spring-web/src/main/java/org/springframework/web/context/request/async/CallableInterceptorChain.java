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

package org.springframework.web.context.request.async;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.web.context.request.NativeWebRequest;

/**
 * Assists with the invocation of {@link CallableProcessingInterceptor}'s.
 *
 * @author Rossen Stoyanchev
 * @author Rob Winch
 * @since 3.2
 */
class CallableInterceptorChain {

	private static final Log logger = LogFactory.getLog(CallableInterceptorChain.class);

	/**
	 * 拦截器集合
	 */
	private final List<CallableProcessingInterceptor> interceptors;

	/**
	 * 当前执行的拦截器位置
	 */
	private int preProcessIndex = -1;

	/**
	 * 执行结果
	 */
	private volatile Future<?> taskFuture;


	public CallableInterceptorChain(List<CallableProcessingInterceptor> interceptors) {
		this.interceptors = interceptors;
	}


	public void setTaskFuture(Future<?> taskFuture) {
		this.taskFuture = taskFuture;
	}


	/**
	 * 在原始线程中，在执行 异步任务(Callable) 之前调用
	 * @param request
	 * @param task
	 * @throws Exception
	 */
	public void applyBeforeConcurrentHandling(NativeWebRequest request, Callable<?> task) throws Exception {
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			interceptor.beforeConcurrentHandling(request, task);
		}
	}

	/**
	 * 在异步线程中，在执行 异步任务(Callable) 之前调用
	 * @param request
	 * @param task
	 * @throws Exception
	 */
	public void applyPreProcess(NativeWebRequest request, Callable<?> task) throws Exception {
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			interceptor.preProcess(request, task);
			this.preProcessIndex++;
		}
	}

	/**
	 * 在异步线程中，在执行 异步任务(Callable) 之后调用
	 * @param request
	 * @param task
	 * @param concurrentResult
	 * @return
	 */
	public Object applyPostProcess(NativeWebRequest request, Callable<?> task, Object concurrentResult) {
		Throwable exceptionResult = null;
		for (int i = this.preProcessIndex; i >= 0; i--) {
			try {
				this.interceptors.get(i).postProcess(request, task, concurrentResult);
			}
			catch (Throwable ex) {
				// Save the first exception but invoke all interceptors
				if (exceptionResult != null) {
					if (logger.isTraceEnabled()) {
						logger.trace("Ignoring failure in postProcess method", ex);
					}
				}
				else {
					exceptionResult = ex;
				}
			}
		}
		return (exceptionResult != null) ? exceptionResult : concurrentResult;
	}

	/**
	 * 超时后触发，为了从拦截器中获得返回值
	 * @param request
	 * @param task
	 * @return
	 */
	public Object triggerAfterTimeout(NativeWebRequest request, Callable<?> task) {
		// 取消任务
		cancelTask();
		// 遍历所有拦截器
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			try {
				Object result = interceptor.handleTimeout(request, task);
				// 已经处理了超时，但是没有返回值
				if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
					break;
				}
				// 已经处理了超时，有返回值
				else if (result != CallableProcessingInterceptor.RESULT_NONE) {
					return result;
				}
			}
			catch (Throwable ex) {
				return ex;
			}
		}
		return CallableProcessingInterceptor.RESULT_NONE;
	}

	/**
	 * 取消任务
	 */
	private void cancelTask() {
		Future<?> future = this.taskFuture;
		if (future != null) {
			try {
				// 中断处理
				future.cancel(true);
			}
			catch (Throwable ex) {
				// Ignore
			}
		}
	}

	/**
	 * 抛出异常后触发，为了从拦截器中获得返回值
	 * @param request
	 * @param task
	 * @param throwable
	 * @return
	 */
	public Object triggerAfterError(NativeWebRequest request, Callable<?> task, Throwable throwable) {
		// 取消任务
		cancelTask();
		// 遍历所有拦截器
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			try {
				Object result = interceptor.handleError(request, task, throwable);
				// 已经处理了异常，但是没有返回值
				if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
					break;
				}
				// 已经处理了异常，有返回值
				else if (result != CallableProcessingInterceptor.RESULT_NONE) {
					return result;
				}
			}
			catch (Throwable ex) {
				return ex;
			}
		}
		return CallableProcessingInterceptor.RESULT_NONE;
	}

	/**
	 * 任务完成后触发，执行完成任务回调
	 * @param request
	 * @param task
	 */
	public void triggerAfterCompletion(NativeWebRequest request, Callable<?> task) {
		// 遍历所有拦截器
		for (int i = this.interceptors.size()-1; i >= 0; i--) {
			try {
				// 执行完成任务回调
				this.interceptors.get(i).afterCompletion(request, task);
			}
			catch (Throwable ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Ignoring failure in afterCompletion method", ex);
				}
			}
		}
	}

}
