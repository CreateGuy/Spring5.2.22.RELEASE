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

package org.springframework.web.cors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

/**
 * 默认的Cors处理器
 */
public class DefaultCorsProcessor implements CorsProcessor {

	private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);


	/**
	 * 校验Cors规则
	 * @param config
	 * @param request the current request
	 * @param response the current response
	 * @return
	 * @throws IOException
	 */
	@Override
	@SuppressWarnings("resource")
	public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		//添加一些响应头
		Collection<String> varyHeaders = response.getHeaders(HttpHeaders.VARY);
		if (!varyHeaders.contains(HttpHeaders.ORIGIN)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
		}
		if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
		}
		if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)) {
			response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
		}

		// 不是跨域请求直接放行
		if (!CorsUtils.isCorsRequest(request)) {
			return true;
		}

		if (response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) != null) {
			logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
			return true;
		}

		// 是否是跨域请求
		boolean preFlightRequest = CorsUtils.isPreFlightRequest(request);
		// 如果是预检查请求，但是又没有Cors数据源，就直接返回false
		if (config == null) {
			if (preFlightRequest) {
				rejectRequest(new ServletServerHttpResponse(response));
				return false;
			}
			else {
				return true;
			}
		}

		//  如果是预检查请求检查是否支持，其他类型的直接返回True
		return handleInternal(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), config, preFlightRequest);
	}

	/**
	 * 当一个CORS检查失败时调用
	 */
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		response.setStatusCode(HttpStatus.FORBIDDEN);
		response.getBody().write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
		response.flush();
	}

	/**
	 * 如果是预检查请求检查是否支持，其他类型的直接返回True
	 */
	protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
			CorsConfiguration config, boolean preFlightRequest) throws IOException {

		String requestOrigin = request.getHeaders().getOrigin();

		// 检查来源
		// 获得请求来源
		String allowOrigin = checkOrigin(config, requestOrigin);
		if (allowOrigin == null) {
			logger.debug("Reject: '" + requestOrigin + "' origin is not allowed");
			rejectRequest(response);
			return false;
		}

		HttpHeaders responseHeaders = response.getHeaders();

		// 检查请求方式
		// 拿到此次预检查请求代表的真正请求的请求方式
		HttpMethod requestMethod = getMethodToUse(request, preFlightRequest);
		List<HttpMethod> allowMethods = checkMethods(config, requestMethod);
		// 检查请求方式是否允许，为空就代表不支持
		if (allowMethods == null) {
			logger.debug("Reject: HTTP '" + requestMethod + "' is not allowed");
			rejectRequest(response);
			return false;
		}

		// 检查请求头
		// 拿到预检查请求对应的的真正请求会携带的请求头
		List<String> requestHeaders = getHeadersToUse(request, preFlightRequest);
		// 检查请求头是否允许，为空就代表不支持
		List<String> allowHeaders = checkHeaders(config, requestHeaders);
		if (preFlightRequest && allowHeaders == null) {
			logger.debug("Reject: headers '" + requestHeaders + "' are not allowed");
			rejectRequest(response);
			return false;
		}

		// 告诉客户端允许来自这的请求
		responseHeaders.setAccessControlAllowOrigin(allowOrigin);

		// 是预检查请求, 告诉客户端允许这种请求方式
		if (preFlightRequest) {
			responseHeaders.setAccessControlAllowMethods(allowMethods);
		}

		if (preFlightRequest && !allowHeaders.isEmpty()) {
			responseHeaders.setAccessControlAllowHeaders(allowHeaders);
		}

		if (!CollectionUtils.isEmpty(config.getExposedHeaders())) {
			responseHeaders.setAccessControlExposeHeaders(config.getExposedHeaders());
		}

		// 估计是允许Cookie
		if (Boolean.TRUE.equals(config.getAllowCredentials())) {
			responseHeaders.setAccessControlAllowCredentials(true);
		}

		// 是预检查请求, 设置预检查请求有效期
		if (preFlightRequest && config.getMaxAge() != null) {
			responseHeaders.setAccessControlMaxAge(config.getMaxAge());
		}

		response.flush();
		return true;
	}

	/**
	 * 检查此Cors数据源是否支持这种来源
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected String checkOrigin(CorsConfiguration config, @Nullable String requestOrigin) {
		return config.checkOrigin(requestOrigin);
	}

	/**
	 * 检查此Cors数据源是否支持这种请求方式
	 * {@link org.springframework.web.cors.CorsConfiguration#checkHttpMethod(HttpMethod)}.
	 */
	@Nullable
	protected List<HttpMethod> checkMethods(CorsConfiguration config, @Nullable HttpMethod requestMethod) {
		return config.checkHttpMethod(requestMethod);
	}

	@Nullable
	private HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
		return (isPreFlight ? request.getHeaders().getAccessControlRequestMethod() : request.getMethod());
	}

	/**
	 * 检查此Cors数据源是否支持携带这些请求头
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected List<String> checkHeaders(CorsConfiguration config, List<String> requestHeaders) {
		return config.checkHeaders(requestHeaders);
	}

	/**
	 * 拿到预检查请求对应的的真正请求会携带的请求头
	 * @param request
	 * @param isPreFlight
	 * @return
	 */
	private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
		HttpHeaders headers = request.getHeaders();
		return (isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.keySet()));
	}

}
