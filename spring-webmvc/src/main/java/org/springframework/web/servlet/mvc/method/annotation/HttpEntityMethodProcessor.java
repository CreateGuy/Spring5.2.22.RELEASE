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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * <ul>
 *     <li>解析 {@link HttpEntity} 和 {@link RequestEntity} 方法参数</li>
 *     <li>处理 {@link HttpEntity} 和 {@link ResponseEntity} 的返回值</li>
 * </ul>
 * <p>An {@link HttpEntity} return type has a specific purpose. Therefore this
 * handler should be configured ahead of handlers that support any return
 * value type annotated with {@code @ModelAttribute} or {@code @ResponseBody}
 * to ensure they don't take over.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @since 3.1
 */
public class HttpEntityMethodProcessor extends AbstractMessageConverterMethodProcessor {

	/**
	 * Basic constructor with converters only. Suitable for resolving
	 * {@code HttpEntity}. For handling {@code ResponseEntity} consider also
	 * providing a {@code ContentNegotiationManager}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters) {
		super(converters);
	}

	/**
	 * Basic constructor with converters and {@code ContentNegotiationManager}.
	 * Suitable for resolving {@code HttpEntity} and handling {@code ResponseEntity}
	 * without {@code Request~} or {@code ResponseBodyAdvice}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager) {

		super(converters, manager);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} method arguments.
	 * For handling {@code ResponseEntity} consider also providing a
	 * {@code ContentNegotiationManager}.
	 * @since 4.2
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			List<Object> requestResponseBodyAdvice) {

		super(converters, null, requestResponseBodyAdvice);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} and handling
	 * {@code ResponseEntity}.
	 */
	public HttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, List<Object> requestResponseBodyAdvice) {

		super(converters, manager, requestResponseBodyAdvice);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (HttpEntity.class == parameter.getParameterType() ||
				RequestEntity.class == parameter.getParameterType());
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (HttpEntity.class.isAssignableFrom(returnType.getParameterType()) &&
				!RequestEntity.class.isAssignableFrom(returnType.getParameterType()));
	}

	@Override
	@Nullable
	public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory)
			throws IOException, HttpMediaTypeNotSupportedException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		// 拿到设置的 HttpEntity 的类型
		Type paramType = getHttpEntityType(parameter);
		if (paramType == null) {
			throw new IllegalArgumentException("HttpEntity parameter '" + parameter.getParameterName() +
					"' in method " + parameter.getMethod() + " is not parameterized");
		}

		// 读取请求体然后转换为指定类型
		Object body = readWithMessageConverters(webRequest, parameter, paramType);
		// 转换为更加详细的HttpEntity
		if (RequestEntity.class == parameter.getParameterType()) {
			return new RequestEntity<>(body, inputMessage.getHeaders(),
					inputMessage.getMethod(), inputMessage.getURI());
		}
		else {
			return new HttpEntity<>(body, inputMessage.getHeaders());
		}
	}

	/**
	 * 拿到设置的 HttpEntity 的类型
	 * @param parameter
	 * @return
	 */
	@Nullable
	private Type getHttpEntityType(MethodParameter parameter) {
		Assert.isAssignable(HttpEntity.class, parameter.getParameterType());
		Type parameterType = parameter.getGenericParameterType();
		// 是否是参数化类型，就是泛型
		if (parameterType instanceof ParameterizedType) {
			ParameterizedType type = (ParameterizedType) parameterType;
			if (type.getActualTypeArguments().length != 1) {
				throw new IllegalArgumentException("Expected single generic parameter on '" +
						parameter.getParameterName() + "' in method " + parameter.getMethod());
			}
			// 取第一个泛型
			return type.getActualTypeArguments()[0];
		}
		else if (parameterType instanceof Class) {
			return Object.class;
		}
		else {
			return null;
		}
	}

	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		// 表示请求已经处理完毕
		mavContainer.setRequestHandled(true);
		if (returnValue == null) {
			return;
		}

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

		Assert.isInstanceOf(HttpEntity.class, returnValue);
		HttpEntity<?> responseEntity = (HttpEntity<?>) returnValue;

		HttpHeaders outputHeaders = outputMessage.getHeaders();
		HttpHeaders entityHeaders = responseEntity.getHeaders();
		// 合并响应头部信息
		if (!entityHeaders.isEmpty()) {
			entityHeaders.forEach((key, value) -> {
				// 特殊的VARY值，进行合并VARY
				if (HttpHeaders.VARY.equals(key) && outputHeaders.containsKey(HttpHeaders.VARY)) {
					List<String> values = getVaryRequestHeadersToAdd(outputHeaders, entityHeaders);
					if (!values.isEmpty()) {
						outputHeaders.setVary(values);
					}
				}
				else {
					// 普通的头部直接就添加了
					outputHeaders.put(key, value);
				}
			});
		}

		// 如果是 ResponseEntity 做更加详细的配置
		if (responseEntity instanceof ResponseEntity) {
			// 设置响应码
			int returnStatus = ((ResponseEntity<?>) responseEntity).getStatusCodeValue();
			outputMessage.getServletResponse().setStatus(returnStatus);
			if (returnStatus == 200) {
				HttpMethod method = inputMessage.getMethod();
				if ((HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))
						&& isResourceNotModified(inputMessage, outputMessage)) {
					outputMessage.flush();
					return;
				}
			}
			// 如果是重定向的情况
			else if (returnStatus / 100 == 3) {
				// 拿到要跳转的地址
				String location = outputHeaders.getFirst("location");
				if (location != null) {
					// 保存重定向参数
					saveFlashAttributes(mavContainer, webRequest, location);
				}
			}
		}

		// 将HttpEntity中响应体的数据写入响应中. ResponseBodyAdvice 也会调用
		writeWithMessageConverters(responseEntity.getBody(), returnType, inputMessage, outputMessage);

		// 即使没有写入正文，也会刷新头部。
		outputMessage.flush();
	}

	/**
	 * 合并VARY值
	 * @param responseHeaders
	 * @param entityHeaders
	 * @return
	 */
	private List<String> getVaryRequestHeadersToAdd(HttpHeaders responseHeaders, HttpHeaders entityHeaders) {
		List<String> entityHeadersVary = entityHeaders.getVary();
		List<String> vary = responseHeaders.get(HttpHeaders.VARY);
		if (vary != null) {
			List<String> result = new ArrayList<>(entityHeadersVary);
			for (String header : vary) {
				for (String existing : StringUtils.tokenizeToStringArray(header, ",")) {
					if ("*".equals(existing)) {
						return Collections.emptyList();
					}
					for (String value : entityHeadersVary) {
						if (value.equalsIgnoreCase(existing)) {
							result.remove(value);
						}
					}
				}
			}
			return result;
		}
		return entityHeadersVary;
	}

	/**
	 * 检查资源是否没有被修改过
	 * @param request
	 * @param response
	 * @return
	 */
	private boolean isResourceNotModified(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		ServletWebRequest servletWebRequest =
				new ServletWebRequest(request.getServletRequest(), response.getServletResponse());
		HttpHeaders responseHeaders = response.getHeaders();
		String etag = responseHeaders.getETag();
		// 获得资源最后一次修改的时间
		long lastModifiedTimestamp = responseHeaders.getLastModified();
		// 清空缓存相关响应头
		if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD) {
			responseHeaders.remove(HttpHeaders.ETAG);
			responseHeaders.remove(HttpHeaders.LAST_MODIFIED);
		}

		return servletWebRequest.checkNotModified(etag, lastModifiedTimestamp);
	}

	/**
	 * 保存重定向参数
	 * @param mav
	 * @param request
	 * @param location 重定向Url
	 */
	private void saveFlashAttributes(ModelAndViewContainer mav, NativeWebRequest request, String location) {
		// 表示是重定向
		mav.setRedirectModelScenario(true);
		ModelMap model = mav.getModel();
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			// 保存重定向参数
			if (!CollectionUtils.isEmpty(flashAttributes)) {
				HttpServletRequest req = request.getNativeRequest(HttpServletRequest.class);
				HttpServletResponse res = request.getNativeResponse(HttpServletResponse.class);
				if (req != null) {
					RequestContextUtils.getOutputFlashMap(req).putAll(flashAttributes);
					if (res != null) {
						RequestContextUtils.saveOutputFlashMap(location, req, res);
					}
				}
			}
		}
	}

	@Override
	protected Class<?> getReturnValueType(@Nullable Object returnValue, MethodParameter returnType) {
		if (returnValue != null) {
			return returnValue.getClass();
		}
		else {
			Type type = getHttpEntityType(returnType);
			type = (type != null ? type : Object.class);
			return ResolvableType.forMethodParameter(returnType, type).toClass();
		}
	}

}
