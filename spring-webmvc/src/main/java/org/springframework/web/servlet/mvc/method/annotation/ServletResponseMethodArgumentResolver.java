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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.servlet.ServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 servlet 支持的响应相关方法参数。支持以下类型的值:
 * <ul>
 * <li>{@link ServletResponse}
 * <li>{@link OutputStream}
 * <li>{@link Writer}
 * </ul>
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletResponseMethodArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> paramType = parameter.getParameterType();
		return (ServletResponse.class.isAssignableFrom(paramType) ||
				OutputStream.class.isAssignableFrom(paramType) ||
				Writer.class.isAssignableFrom(paramType));
	}

	/**
	 * Set {@link ModelAndViewContainer#setRequestHandled(boolean)} to
	 * {@code false} to indicate that the method signature provides access
	 * to the response. If subsequently the underlying method returns
	 * {@code null}, the request is considered directly handled.
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {

		// 解析了有关响应的参数，直接设置请求已经完成？
		if (mavContainer != null) {
			mavContainer.setRequestHandled(true);
		}

		Class<?> paramType = parameter.getParameterType();

		// ServletResponse, HttpServletResponse
		if (ServletResponse.class.isAssignableFrom(paramType)) {
			return resolveNativeResponse(webRequest, paramType);
		}

		// ServletResponse 可以派生的参数
		return resolveArgument(paramType, resolveNativeResponse(webRequest, ServletResponse.class));
	}

	/**
	 * 返回指定类型的请求
	 * @param webRequest
	 * @param requiredType
	 * @param <T>
	 * @return
	 */
	private <T> T resolveNativeResponse(NativeWebRequest webRequest, Class<T> requiredType) {
		T nativeResponse = webRequest.getNativeResponse(requiredType);
		if (nativeResponse == null) {
			throw new IllegalStateException(
					"Current response is not of type [" + requiredType.getName() + "]: " + webRequest);
		}
		return nativeResponse;
	}

	/**
	 * ServletResponse 可以派生的参数
	 * @param paramType
	 * @param response
	 * @return
	 * @throws IOException
	 */
	private Object resolveArgument(Class<?> paramType, ServletResponse response) throws IOException {
		if (OutputStream.class.isAssignableFrom(paramType)) {
			return response.getOutputStream();
		}
		else if (Writer.class.isAssignableFrom(paramType)) {
			return response.getWriter();
		}

		// 不应该
		throw new UnsupportedOperationException("Unknown parameter type: " + paramType);
	}

}
