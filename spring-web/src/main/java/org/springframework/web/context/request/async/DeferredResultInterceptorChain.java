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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.web.context.request.NativeWebRequest;

/**
 * 延迟任务拦截器链
 */
class DeferredResultInterceptorChain {

	private static final Log logger = LogFactory.getLog(DeferredResultInterceptorChain.class);

	/**
	 * 注册的延迟任务拦截器
	 */
	private final List<DeferredResultProcessingInterceptor> interceptors;

	/**
	 * 拦截器执行的位置
	 */
	private int preProcessingIndex = -1;


	public DeferredResultInterceptorChain(List<DeferredResultProcessingInterceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public void applyBeforeConcurrentHandling(NativeWebRequest request, DeferredResult<?> deferredResult)
			throws Exception {

		for (DeferredResultProcessingInterceptor interceptor : this.interceptors) {
			interceptor.beforeConcurrentHandling(request, deferredResult);
		}
	}

	public void applyPreProcess(NativeWebRequest request, DeferredResult<?> deferredResult) throws Exception {
		for (DeferredResultProcessingInterceptor interceptor : this.interceptors) {
			interceptor.preProcess(request, deferredResult);
			this.preProcessingIndex++;
		}
	}

	/**
	 * 延迟任务执行完毕
	 * @param request
	 * @param deferredResult
	 * @param concurrentResult
	 * @return
	 */
	public Object applyPostProcess(NativeWebRequest request,  DeferredResult<?> deferredResult,
			Object concurrentResult) {

		try {
			for (int i = this.preProcessingIndex; i >= 0; i--) {
				this.interceptors.get(i).postProcess(request, deferredResult, concurrentResult);
			}
		}
		catch (Throwable ex) {
			return ex;
		}
		return concurrentResult;
	}

	/**
	 * 延迟任务的超时处理器
	 * @param request
	 * @param deferredResult
	 * @throws Exception
	 */
	public void triggerAfterTimeout(NativeWebRequest request, DeferredResult<?> deferredResult) throws Exception {
		for (DeferredResultProcessingInterceptor interceptor : this.interceptors) {
			if (deferredResult.isSetOrExpired()) {
				return;
			}
			if (!interceptor.handleTimeout(request, deferredResult)){
				break;
			}
		}
	}

	/**
	 * 延迟任务的错误处理器
	 * @param request
	 * @param deferredResult
	 * @param ex
	 * @return
	 * @throws Exception
	 */
	public boolean triggerAfterError(NativeWebRequest request, DeferredResult<?> deferredResult, Throwable ex)
			throws Exception {

		for (DeferredResultProcessingInterceptor interceptor : this.interceptors) {
			if (deferredResult.isSetOrExpired()) {
				return false;
			}
			if (!interceptor.handleError(request, deferredResult, ex)){
				return false;
			}
		}
		return true;
	}

	/**
	 * 延迟任务的完成处理器
	 * @param request
	 * @param deferredResult
	 */
	public void triggerAfterCompletion(NativeWebRequest request, DeferredResult<?> deferredResult) {
		for (int i = this.preProcessingIndex; i >= 0; i--) {
			try {
				this.interceptors.get(i).afterCompletion(request, deferredResult);
			}
			catch (Throwable ex) {
				logger.trace("Ignoring failure in afterCompletion method", ex);
			}
		}
	}

}
