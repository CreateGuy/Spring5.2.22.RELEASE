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

package org.springframework.web.servlet.mvc.annotation;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

/**
 * 处理有关 {@link ResponseStatus @ResponseStatus} 或者 ResponseStatusException 的相关异常的异常解析器
 *
 * <p>This exception resolver is enabled by default in the
 * {@link org.springframework.web.servlet.DispatcherServlet DispatcherServlet}
 * and the MVC Java config and the MVC namespace.
 *
 * <p>As of 4.2 this resolver also looks recursively for {@code @ResponseStatus}
 * present on cause exceptions, and as of 4.2.2 this resolver supports
 * attribute overrides for {@code @ResponseStatus} in custom composed annotations.
 *
 * <p>As of 5.0 this resolver also supports {@link ResponseStatusException}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 3.0
 * @see ResponseStatus
 * @see ResponseStatusException
 */
public class ResponseStatusExceptionResolver extends AbstractHandlerExceptionResolver implements MessageSourceAware {

	@Nullable
	private MessageSource messageSource;


	@Override
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}


	/**
	 * 只能处理下面两种情况的异常，并返回返回表示特定错误页面的 ModelAndView 对象
	 * <ol>
	 *     <li>
	 *         异常是 ResponseStatusException 的实例
	 *     </li>
	 *     <li>
	 *         异常上有 {@code @ResponseStatus} 注解
	 *     </li>
	 *     <li>
	 *         上级异常有符合上面的情况
	 *     </li>
	 * </ol>
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler 处理程序
	 * @param ex 在执行处理程序期间引发的异常
	 * @return
	 */
	@Override
	@Nullable
	protected ModelAndView doResolveException(
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler, Exception ex) {

		try {
			if (ex instanceof ResponseStatusException) {
				// 通过解析 ResponseStatusException 设置错误响应头，响应码，错误信息
				return resolveResponseStatusException((ResponseStatusException) ex, request, response, handler);
			}

			// 查找异常上是否有 @ResponseStatus 注解
			ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
			if (status != null) {
				// 通过解析 @ResponseStatus 注解的值，设置响应码和错误信息
				return resolveResponseStatus(status, request, response, handler, ex);
			}

			// 通过上一级异常，继续递归处理
			if (ex.getCause() instanceof Exception) {
				return doResolveException(request, response, handler, (Exception) ex.getCause());
			}
		}
		catch (Exception resolveEx) {
			if (logger.isWarnEnabled()) {
				logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", resolveEx);
			}
		}
		return null;
	}

	/**
	 * 通过解析 {@code @ResponseStatus} 注解的值，设置响应码和错误信息
	 * @param responseStatus
	 * @param request
	 * @param response
	 * @param handler
	 * @param ex
	 * @return
	 * @throws Exception
	 */
	protected ModelAndView resolveResponseStatus(ResponseStatus responseStatus, HttpServletRequest request,
			HttpServletResponse response, @Nullable Object handler, Exception ex) throws Exception {

		int statusCode = responseStatus.code().value();
		String reason = responseStatus.reason();
		// 将响应码和错误信息写入响应中
		return applyStatusAndReason(statusCode, reason, response);
	}

	/**
	 * 通过解析 ResponseStatusException 设置错误响应头，响应码，错误信息
	 * @param ex
	 * @param request
	 * @param response
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	protected ModelAndView resolveResponseStatusException(ResponseStatusException ex,
			HttpServletRequest request, HttpServletResponse response, @Nullable Object handler) throws Exception {

		// 添加与错误相关的响应头
		ex.getResponseHeaders().forEach((name, values) ->
				values.forEach(value -> response.addHeader(name, value)));

		int statusCode = ex.getStatus().value();
		String reason = ex.getReason();
		// 将响应码和错误信息写入响应中
		return applyStatusAndReason(statusCode, reason, response);
	}

	/**
	 * 将响应码和错误信息写入响应中
	 * @param statusCode the HTTP status code
	 * @param reason the associated reason (may be {@code null} or empty)
	 * @param response current HTTP response
	 * @since 5.0
	 */
	protected ModelAndView applyStatusAndReason(int statusCode, @Nullable String reason, HttpServletResponse response)
			throws IOException {

		// 没有错误原因
		if (!StringUtils.hasLength(reason)) {
			response.sendError(statusCode);
		}
		else {
			// 进行国际化处理
			String resolvedReason = (this.messageSource != null ?
					this.messageSource.getMessage(reason, null, reason, LocaleContextHolder.getLocale()) :
					reason);
			// 写入响应码和错误原因
			response.sendError(statusCode, resolvedReason);
		}
		return new ModelAndView();
	}

}
