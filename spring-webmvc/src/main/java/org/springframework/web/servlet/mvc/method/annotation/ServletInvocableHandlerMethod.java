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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.View;
import org.springframework.web.util.NestedServletException;

/**
 * 扩展了 {@link InvocableHandlerMethod}，使其能够通过注册的 {@link HandlerMethodReturnValueHandler} 处理返回值，还支持基于方法级别的 {@link ResponseStatus} 注解设置响应状态。
 *
 * <p>A {@code null} return value (including void) may be interpreted as the
 * end of request processing in combination with a {@code @ResponseStatus}
 * annotation, a not-modified check condition
 * (see {@link ServletWebRequest#checkNotModified(long)}), or
 * a method argument that provides access to the response stream.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletInvocableHandlerMethod extends InvocableHandlerMethod {

	/**
	 * 异步任务的指定方法
	 */
	private static final Method CALLABLE_METHOD = ClassUtils.getMethod(Callable.class, "call");

	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;


	/**
	 * Creates an instance from the given handler and method.
	 */
	public ServletInvocableHandlerMethod(Object handler, Method method) {
		super(handler, method);
	}

	/**
	 * Create an instance from a {@code HandlerMethod}.
	 */
	public ServletInvocableHandlerMethod(HandlerMethod handlerMethod) {
		super(handlerMethod);
	}


	/**
	 * Register {@link HandlerMethodReturnValueHandler} instances to use to
	 * handle return values.
	 */
	public void setHandlerMethodReturnValueHandlers(HandlerMethodReturnValueHandlerComposite returnValueHandlers) {
		this.returnValueHandlers = returnValueHandlers;
	}


	/**
	 * 调用方法并处理返回值
	 * configured {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}.
	 * @param webRequest the current request
	 * @param mavContainer the ModelAndViewContainer for this request
	 * @param providedArgs 额外参数
	 */
	public void invokeAndHandle(ServletWebRequest webRequest, ModelAndViewContainer mavContainer,
			Object... providedArgs) throws Exception {

		// 解析方法的参数，并执行目标方法，获得返回值
		Object returnValue = invokeForRequest(webRequest, mavContainer, providedArgs);
		// 基于 ResponseStatus 注解设置响应状态
		setResponseStatus(webRequest);

		// 不懂
		if (returnValue == null) {
			if (isRequestNotModified(webRequest) || getResponseStatus() != null || mavContainer.isRequestHandled()) {
				disableContentCachingIfNecessary(webRequest);
				// 标记为处理程序已经处理完毕
				mavContainer.setRequestHandled(true);
				return;
			}
		}
		else if (StringUtils.hasText(getResponseStatusReason())) {
			mavContainer.setRequestHandled(true);
			return;
		}

		// 标记为处理程序还没有处理完毕
		mavContainer.setRequestHandled(false);
		Assert.state(this.returnValueHandlers != null, "No return value handlers");
		try {
			this.returnValueHandlers.handleReturnValue(
					returnValue, getReturnValueType(returnValue), mavContainer, webRequest);
		}
		catch (Exception ex) {
			if (logger.isTraceEnabled()) {
				logger.trace(formatErrorForReturnValue(returnValue), ex);
			}
			throw ex;
		}
	}

	/**
	 * 基于 {@link ResponseStatus} 注解设置响应状态
	 */
	private void setResponseStatus(ServletWebRequest webRequest) throws IOException {
		HttpStatus status = getResponseStatus();
		if (status == null) {
			return;
		}

		HttpServletResponse response = webRequest.getResponse();
		if (response != null) {
			String reason = getResponseStatusReason();
			if (StringUtils.hasText(reason)) {
				response.sendError(status.value(), reason);
			}
			else {
				response.setStatus(status.value());
			}
		}

		// 此属性会由 RedirectView 操作
		webRequest.getRequest().setAttribute(View.RESPONSE_STATUS_ATTRIBUTE, status);
	}

	/**
	 * 请求是否满足未修改的条件，不懂
	 * @see ServletWebRequest#checkNotModified(long)
	 * @see ServletWebRequest#checkNotModified(String)
	 */
	private boolean isRequestNotModified(ServletWebRequest webRequest) {
		return webRequest.isNotModified();
	}

	private void disableContentCachingIfNecessary(ServletWebRequest webRequest) {
		if (isRequestNotModified(webRequest)) {
			HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
			Assert.notNull(response, "Expected HttpServletResponse");
			if (StringUtils.hasText(response.getHeader(HttpHeaders.ETAG))) {
				HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
				Assert.notNull(request, "Expected HttpServletRequest");
			}
		}
	}

	private String formatErrorForReturnValue(@Nullable Object returnValue) {
		return "Error handling return value=[" + returnValue + "]" +
				(returnValue != null ? ", type=" + returnValue.getClass().getName() : "") +
				" in " + toString();
	}

	/**
	 * 创建一个处理异步任务的方法
	 */
	ServletInvocableHandlerMethod wrapConcurrentResult(Object result) {
		return new ConcurrentResultHandlerMethod(result, new ConcurrentResultMethodParameter(result));
	}


	/**
	 * 获得异步任务的返回值的方法
	 */
	private class ConcurrentResultHandlerMethod extends ServletInvocableHandlerMethod {

		/**
		 * 异步任务的返回值类型
		 */
		private final MethodParameter returnType;

		public ConcurrentResultHandlerMethod(final Object result, ConcurrentResultMethodParameter returnType) {
			super((Callable<Object>) () -> {
				if (result instanceof Exception) {
					throw (Exception) result;
				}
				else if (result instanceof Throwable) {
					throw new NestedServletException("Async processing failed", (Throwable) result);
				}
				// 返回异步任务返回值
				return result;
			}, CALLABLE_METHOD);

			// 因为封装了新的处理方法，但是返回值还是要处理的，所有用原方法带的返回值处理器
			if (ServletInvocableHandlerMethod.this.returnValueHandlers != null) {
				setHandlerMethodReturnValueHandlers(ServletInvocableHandlerMethod.this.returnValueHandlers);
			}
			this.returnType = returnType;
		}

		/**
		 * Bridge to actual controller type-level annotations.
		 */
		@Override
		public Class<?> getBeanType() {
			return ServletInvocableHandlerMethod.this.getBeanType();
		}

		/**
		 * Bridge to actual return value or generic type within the declared
		 * async return type, e.g. Foo instead of {@code DeferredResult<Foo>}.
		 */
		@Override
		public MethodParameter getReturnValueType(@Nullable Object returnValue) {
			return this.returnType;
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> A getMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.getMethodAnnotation(annotationType);
		}

		/**
		 * Bridge to controller method-level annotations.
		 */
		@Override
		public <A extends Annotation> boolean hasMethodAnnotation(Class<A> annotationType) {
			return ServletInvocableHandlerMethod.this.hasMethodAnnotation(annotationType);
		}
	}


	/**
	 * 异步任务返回值参数
	 */
	private class ConcurrentResultMethodParameter extends HandlerMethodParameter {

		@Nullable
		private final Object returnValue;

		private final ResolvableType returnType;

		public ConcurrentResultMethodParameter(Object returnValue) {
			super(-1);
			this.returnValue = returnValue;
			this.returnType = (returnValue instanceof ReactiveTypeHandler.CollectedValuesList ?
					((ReactiveTypeHandler.CollectedValuesList) returnValue).getReturnType() :
					ResolvableType.forType(super.getGenericParameterType()).getGeneric());
		}

		public ConcurrentResultMethodParameter(ConcurrentResultMethodParameter original) {
			super(original);
			this.returnValue = original.returnValue;
			this.returnType = original.returnType;
		}

		@Override
		public Class<?> getParameterType() {
			if (this.returnValue != null) {
				return this.returnValue.getClass();
			}
			if (!ResolvableType.NONE.equals(this.returnType)) {
				return this.returnType.toClass();
			}
			return super.getParameterType();
		}

		@Override
		public Type getGenericParameterType() {
			return this.returnType.getType();
		}

		@Override
		public <T extends Annotation> boolean hasMethodAnnotation(Class<T> annotationType) {
			// Ensure @ResponseBody-style handling for values collected from a reactive type
			// even if actual return type is ResponseEntity<Flux<T>>
			return (super.hasMethodAnnotation(annotationType) ||
					(annotationType == ResponseBody.class &&
							this.returnValue instanceof ReactiveTypeHandler.CollectedValuesList));
		}

		@Override
		public ConcurrentResultMethodParameter clone() {
			return new ConcurrentResultMethodParameter(this);
		}
	}

}
