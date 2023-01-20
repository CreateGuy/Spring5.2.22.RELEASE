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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Handler for return values of type {@link ResponseBodyEmitter} and sub-classes
 * such as {@link SseEmitter} including the same types wrapped with
 * {@link ResponseEntity}.
 *
 * <p>As of 5.0 also supports reactive return value types for any reactive
 * library with registered adapters in {@link ReactiveAdapterRegistry}.
 *
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public class ResponseBodyEmitterReturnValueHandler implements HandlerMethodReturnValueHandler {

	private final List<HttpMessageConverter<?>> messageConverters;

	/**
	 * 后面为了将消息写入响应的
	 */
	private final List<HttpMessageConverter<?>> sseMessageConverters;

	private final ReactiveTypeHandler reactiveHandler;


	/**
	 * Simple constructor with reactive type support based on a default instance of
	 * {@link ReactiveAdapterRegistry},
	 * {@link org.springframework.core.task.SyncTaskExecutor}, and
	 * {@link ContentNegotiationManager} with an Accept header strategy.
	 */
	public ResponseBodyEmitterReturnValueHandler(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "HttpMessageConverter List must not be empty");
		this.messageConverters = messageConverters;
		this.sseMessageConverters = initSseConverters(messageConverters);
		this.reactiveHandler = new ReactiveTypeHandler();
	}

	/**
	 * Complete constructor with pluggable "reactive" type support.
	 * @param messageConverters converters to write emitted objects with
	 * @param registry for reactive return value type support
	 * @param executor for blocking I/O writes of items emitted from reactive types
	 * @param manager for detecting streaming media types
	 * @since 5.0
	 */
	public ResponseBodyEmitterReturnValueHandler(List<HttpMessageConverter<?>> messageConverters,
			ReactiveAdapterRegistry registry, TaskExecutor executor, ContentNegotiationManager manager) {

		Assert.notEmpty(messageConverters, "HttpMessageConverter List must not be empty");
		this.messageConverters = messageConverters;
		this.sseMessageConverters = initSseConverters(messageConverters);
		this.reactiveHandler = new ReactiveTypeHandler(registry, executor, manager);
	}

	/**
	 * 初始化 {@code HttpMessageConverter}
	 * @param converters
	 * @return
	 */
	private static List<HttpMessageConverter<?>> initSseConverters(List<HttpMessageConverter<?>> converters) {
		for (HttpMessageConverter<?> converter : converters) {
			// 意思是遇到纯文本的就直接返回?
			if (converter.canWrite(String.class, MediaType.TEXT_PLAIN)) {
				return converters;
			}
		}
		List<HttpMessageConverter<?>> result = new ArrayList<>(converters.size() + 1);
		result.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
		result.addAll(converters);
		return result;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		Class<?> bodyType =
				// 如果是 ResponseEntity 就直接去响应的类型
				ResponseEntity.class.isAssignableFrom(returnType.getParameterType()) ?
				ResolvableType.forMethodParameter(returnType).getGeneric().resolve() :
				returnType.getParameterType();

		return (bodyType != null && (ResponseBodyEmitter.class.isAssignableFrom(bodyType) ||
				this.reactiveHandler.isReactiveType(bodyType)));
	}

	@Override
	@SuppressWarnings("resource")
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue == null) {
			// 标记请求已经完成
			mavContainer.setRequestHandled(true);
			return;
		}

		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		Assert.state(response != null, "No HttpServletResponse");
		ServerHttpResponse outputMessage = new ServletServerHttpResponse(response);

		// 将ResponseEntity中的信息填充进响应中
		if (returnValue instanceof ResponseEntity) {
			ResponseEntity<?> responseEntity = (ResponseEntity<?>) returnValue;
			response.setStatus(responseEntity.getStatusCodeValue());
			outputMessage.getHeaders().putAll(responseEntity.getHeaders());
			returnValue = responseEntity.getBody();
			returnType = returnType.nested();
			if (returnValue == null) {
				mavContainer.setRequestHandled(true);
				outputMessage.flush();
				return;
			}
		}

		ServletRequest request = webRequest.getNativeRequest(ServletRequest.class);
		Assert.state(request != null, "No ServletRequest");

		ResponseBodyEmitter emitter;
		if (returnValue instanceof ResponseBodyEmitter) {
			emitter = (ResponseBodyEmitter) returnValue;
		}
		else {
			emitter = this.reactiveHandler.handleValue(returnValue, returnType, mavContainer, webRequest);
			if (emitter == null) {
				// Not streaming: write headers without committing response..
				outputMessage.getHeaders().forEach((headerName, headerValues) -> {
					for (String headerValue : headerValues) {
						response.addHeader(headerName, headerValue);
					}
				});
				return;
			}
		}
		emitter.extendResponse(outputMessage);

		// 禁止缓存
		ShallowEtagHeaderFilter.disableContentCaching(request);

		// 封装响应以忽略进一步的报头更改
		// 报头将在第一次写入时刷新
		outputMessage = new StreamingServletServerHttpResponse(outputMessage);

		HttpMessageConvertingHandler handler;
		try {
			DeferredResult<?> deferredResult = new DeferredResult<>(emitter.getTimeout());
			// 启动并发请求处理并初始化给定的 deferredResult
			WebAsyncUtils.getAsyncManager(webRequest).startDeferredResultProcessing(deferredResult, mavContainer);
			handler = new HttpMessageConvertingHandler(outputMessage, deferredResult);
		}
		catch (Throwable ex) {
			emitter.initializeWithError(ex);
			throw ex;
		}

		// 初始化：主要是保存了响应和发送早期消息
		emitter.initialize(handler);
	}


	/**
	 * 使用 {@link HttpMessageConverter} 写入消息
	 */
	private class HttpMessageConvertingHandler implements ResponseBodyEmitter.Handler {

		/**
		 * 当时的响应
		 */
		private final ServerHttpResponse outputMessage;

		/**
		 * 延迟任务执行的结果，比如说是否完成，是否抛出了异常
		 */
		private final DeferredResult<?> deferredResult;

		public HttpMessageConvertingHandler(ServerHttpResponse outputMessage, DeferredResult<?> deferredResult) {
			this.outputMessage = outputMessage;
			this.deferredResult = deferredResult;
		}

		/**
		 * 发送消息
		 * @param data
		 * @param mediaType
		 * @throws IOException
		 */
		@Override
		public void send(Object data, @Nullable MediaType mediaType) throws IOException {
			sendInternal(data, mediaType);
		}

		/**
		 * 拿到以前的响应，然后通过 {@code HttpMessageConverter将 消息写入响应中
		 * @param data
		 * @param mediaType
		 * @param <T>
		 * @throws IOException
		 */
		@SuppressWarnings("unchecked")
		private <T> void sendInternal(T data, @Nullable MediaType mediaType) throws IOException {
			for (HttpMessageConverter<?> converter : ResponseBodyEmitterReturnValueHandler.this.sseMessageConverters) {
				if (converter.canWrite(data.getClass(), mediaType)) {
					((HttpMessageConverter<T>) converter).write(data, mediaType, this.outputMessage);
					this.outputMessage.flush();
					return;
				}
			}
			throw new IllegalArgumentException("No suitable converter for " + data.getClass());
		}

		/**
		 * 任务完成
		 */
		@Override
		public void complete() {
			try {
				this.outputMessage.flush();
				// 设置结果为空，以为着没有出现错误
				this.deferredResult.setResult(null);
			}
			catch (IOException ex) {
				this.deferredResult.setErrorResult(ex);
			}
		}

		@Override
		public void completeWithError(Throwable failure) {
			this.deferredResult.setErrorResult(failure);
		}

		@Override
		public void onTimeout(Runnable callback) {
			this.deferredResult.onTimeout(callback);
		}

		@Override
		public void onError(Consumer<Throwable> callback) {
			this.deferredResult.onError(callback);
		}

		@Override
		public void onCompletion(Runnable callback) {
			this.deferredResult.onCompletion(callback);
		}
	}


	/**
	 * Wrap to silently ignore header changes HttpMessageConverter's that would
	 * otherwise cause HttpHeaders to raise exceptions.
	 */
	private static class StreamingServletServerHttpResponse implements ServerHttpResponse {

		/**
		 * 原始的响应
		 */
		private final ServerHttpResponse delegate;

		/**
		 * 原始的请求头
		 */
		private final HttpHeaders mutableHeaders = new HttpHeaders();

		public StreamingServletServerHttpResponse(ServerHttpResponse delegate) {
			this.delegate = delegate;
			this.mutableHeaders.putAll(delegate.getHeaders());
		}

		@Override
		public void setStatusCode(HttpStatus status) {
			this.delegate.setStatusCode(status);
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.mutableHeaders;
		}

		@Override
		public OutputStream getBody() throws IOException {
			return this.delegate.getBody();
		}

		@Override
		public void flush() throws IOException {
			this.delegate.flush();
		}

		@Override
		public void close() {
			this.delegate.close();
		}
	}

}
