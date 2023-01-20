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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * 是针对于 http streaming 的，让请求处于一个长链接的过程，这样就服务端就可以一直想客户端发送消息了
 * A controller method return value type for asynchronous request processing
 * where one or more objects are written to the response.
 *
 * <p>While {@link org.springframework.web.context.request.async.DeferredResult}
 * is used to produce a single result, a {@code ResponseBodyEmitter} can be used
 * to send multiple objects where each object is written with a compatible
 * {@link org.springframework.http.converter.HttpMessageConverter}.
 *
 * <p>Supported as a return type on its own as well as within a
 * {@link org.springframework.http.ResponseEntity}.
 *
 * <pre>
 * &#064;RequestMapping(value="/stream", method=RequestMethod.GET)
 * public ResponseBodyEmitter handle() {
 * 	   ResponseBodyEmitter emitter = new ResponseBodyEmitter();
 * 	   // Pass the emitter to another component...
 * 	   return emitter;
 * }
 *
 * // in another thread
 * emitter.send(foo1);
 *
 * // and again
 * emitter.send(foo2);
 *
 * // and done
 * emitter.complete();
 * </pre>
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 4.2
 */
public class ResponseBodyEmitter {

	/**
	 * 超时时间
	 */
	@Nullable
	private final Long timeout;

	/**
	 * 处理响应的发送等的处理器
	 */
	@Nullable
	private Handler handler;

	/** 在初始化的时候需要发送的早期消息 */
	private final Set<DataWithMediaType> earlySendAttempts = new LinkedHashSet<>(8);

	/** 任务是否完成 */
	private boolean complete;

	/** 在初始化处理程序之前就发生的错误。 */
	@Nullable
	private Throwable failure;

	/**
	 * 消息在发送过程是否出现了异常/错误
	 */
	private boolean sendFailed;

	/**
	 * 任务超时回调
	 */
	private final DefaultCallback timeoutCallback = new DefaultCallback();

	/**
	 * 发送错误回调
	 */
	private final ErrorCallback errorCallback = new ErrorCallback();

	/**
	 * 任务完成回调
	 */
	private final DefaultCallback completionCallback = new DefaultCallback();


	/**
	 * Create a new ResponseBodyEmitter instance.
	 */
	public ResponseBodyEmitter() {
		this.timeout = null;
	}

	/**
	 * Create a ResponseBodyEmitter with a custom timeout value.
	 * <p>By default not set in which case the default configured in the MVC
	 * Java Config or the MVC namespace is used, or if that's not set, then the
	 * timeout depends on the default of the underlying server.
	 * @param timeout the timeout value in milliseconds
	 */
	public ResponseBodyEmitter(Long timeout) {
		this.timeout = timeout;
	}


	/**
	 * Return the configured timeout value, if any.
	 */
	@Nullable
	public Long getTimeout() {
		return this.timeout;
	}


	/**
	 * 初始化：主要是保存了响应和发送早期消息
	 * @param handler
	 * @throws IOException
	 */
	synchronized void initialize(Handler handler) throws IOException {
		// 重点：这个处理器中会包含响应
		this.handler = handler;

		try {
			// 发送早期消息
			for (DataWithMediaType sendAttempt : this.earlySendAttempts) {
				sendInternal(sendAttempt.getData(), sendAttempt.getMediaType());
			}
		}
		finally {
			this.earlySendAttempts.clear();
		}

		// 任务完成
		if (this.complete) {
			// 中途出现了错误？
			if (this.failure != null) {
				this.handler.completeWithError(this.failure);
			}
			else {
				this.handler.complete();
			}
		}
		else {
			// 初始化各种回调
			this.handler.onTimeout(this.timeoutCallback);
			this.handler.onError(this.errorCallback);
			this.handler.onCompletion(this.completionCallback);
		}
	}

	/**
	 * 初始化之前出现错误的方法
	 * @param ex
	 */
	synchronized void initializeWithError(Throwable ex) {
		this.complete = true;
		this.failure = ex;
		this.earlySendAttempts.clear();
		this.errorCallback.accept(ex);
	}

	/**
	 * 如果ResponseBodyEmitter被包装在ResponseEntity中，
	 * 则在使用状态代码和报头更新响应之后调用，但在提交响应之前，即在向响应体写入之前。
	 */
	protected void extendResponse(ServerHttpResponse outputMessage) {
	}

	/**
	 * Write the given object to the response.
	 * <p>If any exception occurs a dispatch is made back to the app server where
	 * Spring MVC will pass the exception through its exception handling mechanism.
	 * <p><strong>Note:</strong> if the send fails with an IOException, you do
	 * not need to call {@link #completeWithError(Throwable)} in order to clean
	 * up. Instead the Servlet container creates a notification that results in a
	 * dispatch where Spring MVC invokes exception resolvers and completes
	 * processing.
	 * @param object the object to write
	 * @throws IOException raised when an I/O error occurs
	 * @throws java.lang.IllegalStateException wraps any other errors
	 */
	public void send(Object object) throws IOException {
		send(object, null);
	}

	/**
	 * Overloaded variant of {@link #send(Object)} that also accepts a MediaType
	 * hint for how to serialize the given Object.
	 * @param object the object to write
	 * @param mediaType a MediaType hint for selecting an HttpMessageConverter
	 * @throws IOException raised when an I/O error occurs
	 * @throws java.lang.IllegalStateException wraps any other errors
	 */
	public synchronized void send(Object object, @Nullable MediaType mediaType) throws IOException {
		Assert.state(!this.complete,
				"ResponseBodyEmitter has already completed" +
						(this.failure != null ? " with error: " + this.failure : ""));
		sendInternal(object, mediaType);
	}

	/**
	 * 发送消息给客户端
	 * @param object
	 * @param mediaType
	 * @throws IOException
	 */
	private void sendInternal(Object object, @Nullable MediaType mediaType) throws IOException {
		if (this.handler != null) {
			try {
				this.handler.send(object, mediaType);
			}
			catch (IOException ex) {
				this.sendFailed = true;
				throw ex;
			}
			catch (Throwable ex) {
				this.sendFailed = true;
				throw new IllegalStateException("Failed to send " + object, ex);
			}
		}
		else {
			// handler是在 ResponseBodyEmitterReturnValueHandler 中会复制的
			// 如果是没有那就说明在处理返回值之前发送了消息，那么就填入早期消息，等初始化处理器的时候会发送
			this.earlySendAttempts.add(new DataWithMediaType(object, mediaType));
		}
	}

	/**
	 * Complete request processing by performing a dispatch into the servlet
	 * container, where Spring MVC is invoked once more, and completes the
	 * request processing lifecycle.
	 * <p><strong>Note:</strong> this method should be called by the application
	 * to complete request processing. It should not be used after container
	 * related events such as an error while {@link #send(Object) sending}.
	 */
	public synchronized void complete() {
		// 忽略发送中途的错误
		if (this.sendFailed) {
			return;
		}
		this.complete = true;
		if (this.handler != null) {
			this.handler.complete();
		}
	}

	/**
	 * Complete request processing with an error.
	 * <p>A dispatch is made into the app server where Spring MVC will pass the
	 * exception through its exception handling mechanism. Note however that
	 * at this stage of request processing, the response is committed and the
	 * response status can no longer be changed.
	 * <p><strong>Note:</strong> this method should be called by the application
	 * to complete request processing with an error. It should not be used after
	 * container related events such as an error while
	 * {@link #send(Object) sending}.
	 */
	public synchronized void completeWithError(Throwable ex) {
		// Ignore, after send failure
		if (this.sendFailed) {
			return;
		}
		this.complete = true;
		this.failure = ex;
		if (this.handler != null) {
			this.handler.completeWithError(ex);
		}
	}

	/**
	 * Register code to invoke when the async request times out. This method is
	 * called from a container thread when an async request times out.
	 */
	public synchronized void onTimeout(Runnable callback) {
		this.timeoutCallback.setDelegate(callback);
	}

	/**
	 * Register code to invoke for an error during async request processing.
	 * This method is called from a container thread when an error occurred
	 * while processing an async request.
	 * @since 5.0
	 */
	public synchronized void onError(Consumer<Throwable> callback) {
		this.errorCallback.setDelegate(callback);
	}

	/**
	 * Register code to invoke when the async request completes. This method is
	 * called from a container thread when an async request completed for any
	 * reason including timeout and network error. This method is useful for
	 * detecting that a {@code ResponseBodyEmitter} instance is no longer usable.
	 */
	public synchronized void onCompletion(Runnable callback) {
		this.completionCallback.setDelegate(callback);
	}


	@Override
	public String toString() {
		return "ResponseBodyEmitter@" + ObjectUtils.getIdentityHexString(this);
	}


	/**
	 * 响应的发送、发送完成、发送完成但是出现了异常、超时、错误和由于任何原因(包括从容器端)完成时调用的回调的注册
	 */
	interface Handler {

		void send(Object data, @Nullable MediaType mediaType) throws IOException;

		void complete();

		void completeWithError(Throwable failure);

		void onTimeout(Runnable callback);

		void onError(Consumer<Throwable> callback);

		void onCompletion(Runnable callback);
	}


	/**
	 * A simple holder of data to be written along with a MediaType hint for
	 * selecting a message converter to write with.
	 */
	public static class DataWithMediaType {

		private final Object data;

		@Nullable
		private final MediaType mediaType;

		public DataWithMediaType(Object data, @Nullable MediaType mediaType) {
			this.data = data;
			this.mediaType = mediaType;
		}

		public Object getData() {
			return this.data;
		}

		@Nullable
		public MediaType getMediaType() {
			return this.mediaType;
		}
	}


	private class DefaultCallback implements Runnable {

		@Nullable
		private Runnable delegate;

		public void setDelegate(Runnable delegate) {
			this.delegate = delegate;
		}

		@Override
		public void run() {
			ResponseBodyEmitter.this.complete = true;
			if (this.delegate != null) {
				this.delegate.run();
			}
		}
	}


	private class ErrorCallback implements Consumer<Throwable> {

		@Nullable
		private Consumer<Throwable> delegate;

		public void setDelegate(Consumer<Throwable> callback) {
			this.delegate = callback;
		}

		@Override
		public void accept(Throwable t) {
			ResponseBodyEmitter.this.complete = true;
			if (this.delegate != null) {
				this.delegate.accept(t);
			}
		}
	}

}
