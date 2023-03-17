/*
 * Copyright 2002-2021 the original author or authors.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 保存了所有Cors配置规则
 * <p>By default a newly created {@code CorsConfiguration} does not permit any
 * cross-origin requests and must be configured explicitly to indicate what
 * should be allowed. Use {@link #applyPermitDefaultValues()} to flip the
 * initialization model to start with open defaults that permit all cross-origin
 * requests for GET, HEAD, and POST requests.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.2
 * @see <a href="https://www.w3.org/TR/cors/">CORS spec</a>
 */
public class CorsConfiguration {

	/** Wildcard representing <em>all</em> origins, methods, or headers. */
	public static final String ALL = "*";

	private static final List<HttpMethod> DEFAULT_METHODS = Collections.unmodifiableList(
			Arrays.asList(HttpMethod.GET, HttpMethod.HEAD));

	/**
	 * Cors配置默认的允许的请求方式
	 */
	private static final List<String> DEFAULT_PERMIT_METHODS = Collections.unmodifiableList(
			Arrays.asList(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.POST.name()));

	/**
	 * 默认允许全部
	 */
	private static final List<String> DEFAULT_PERMIT_ALL = Collections.singletonList(ALL);

	/**
	 * 允许的来源
	 */
	@Nullable
	private List<String> allowedOrigins;

	/**
	 * 允许的请求方式
	 */
	@Nullable
	private List<String> allowedMethods;

	/**
	 * 允许的请求方式
	 */
	@Nullable
	private List<HttpMethod> resolvedMethods = DEFAULT_METHODS;

	/**
	 * 跨域请求允许携带的请求头
	 */
	@Nullable
	private List<String> allowedHeaders;

	/**
	 * 不懂
	 */
	@Nullable
	private List<String> exposedHeaders;

	/**
	 * 应该是客户端是否允许发送Cookie
	 */
	@Nullable
	private Boolean allowCredentials;

	/**
	 * 预检查请求的有效期
	 */
	@Nullable
	private Long maxAge;


	/**
	 * Construct a new {@code CorsConfiguration} instance with no cross-origin
	 * requests allowed for any origin by default.
	 * @see #applyPermitDefaultValues()
	 */
	public CorsConfiguration() {
	}

	/**
	 * Construct a new {@code CorsConfiguration} instance by copying all
	 * values from the supplied {@code CorsConfiguration}.
	 */
	public CorsConfiguration(CorsConfiguration other) {
		this.allowedOrigins = other.allowedOrigins;
		this.allowedMethods = other.allowedMethods;
		this.resolvedMethods = other.resolvedMethods;
		this.allowedHeaders = other.allowedHeaders;
		this.exposedHeaders = other.exposedHeaders;
		this.allowCredentials = other.allowCredentials;
		this.maxAge = other.maxAge;
	}


	/**
	 * Set the origins to allow, e.g. {@code "https://domain1.com"}.
	 * <p>The special value {@code "*"} allows all domains.
	 * <p>By default this is not set.
	 */
	public void setAllowedOrigins(@Nullable List<String> allowedOrigins) {
		this.allowedOrigins = (allowedOrigins != null ? new ArrayList<>(allowedOrigins) : null);
	}

	/**
	 * Return the configured origins to allow, or {@code null} if none.
	 * @see #addAllowedOrigin(String)
	 * @see #setAllowedOrigins(List)
	 */
	@Nullable
	public List<String> getAllowedOrigins() {
		return this.allowedOrigins;
	}

	/**
	 * Add an origin to allow.
	 */
	public void addAllowedOrigin(String origin) {
		if (this.allowedOrigins == null) {
			this.allowedOrigins = new ArrayList<>(4);
		}
		else if (this.allowedOrigins == DEFAULT_PERMIT_ALL) {
			setAllowedOrigins(DEFAULT_PERMIT_ALL);
		}
		this.allowedOrigins.add(origin);
	}

	/**
	 * Set the HTTP methods to allow, e.g. {@code "GET"}, {@code "POST"},
	 * {@code "PUT"}, etc.
	 * <p>The special value {@code "*"} allows all methods.
	 * <p>If not set, only {@code "GET"} and {@code "HEAD"} are allowed.
	 * <p>By default this is not set.
	 * <p><strong>Note:</strong> CORS checks use values from "Forwarded"
	 * (<a href="https://tools.ietf.org/html/rfc7239">RFC 7239</a>),
	 * "X-Forwarded-Host", "X-Forwarded-Port", and "X-Forwarded-Proto" headers,
	 * if present, in order to reflect the client-originated address.
	 * Consider using the {@code ForwardedHeaderFilter} in order to choose from a
	 * central place whether to extract and use, or to discard such headers.
	 * See the Spring Framework reference for more on this filter.
	 */
	public void setAllowedMethods(@Nullable List<String> allowedMethods) {
		this.allowedMethods = (allowedMethods != null ? new ArrayList<>(allowedMethods) : null);
		if (!CollectionUtils.isEmpty(allowedMethods)) {
			this.resolvedMethods = new ArrayList<>(allowedMethods.size());
			for (String method : allowedMethods) {
				if (ALL.equals(method)) {
					this.resolvedMethods = null;
					break;
				}
				this.resolvedMethods.add(HttpMethod.resolve(method));
			}
		}
		else {
			this.resolvedMethods = DEFAULT_METHODS;
		}
	}

	/**
	 * Return the allowed HTTP methods, or {@code null} in which case
	 * only {@code "GET"} and {@code "HEAD"} allowed.
	 * @see #addAllowedMethod(HttpMethod)
	 * @see #addAllowedMethod(String)
	 * @see #setAllowedMethods(List)
	 */
	@Nullable
	public List<String> getAllowedMethods() {
		return this.allowedMethods;
	}

	/**
	 * Add an HTTP method to allow.
	 */
	public void addAllowedMethod(HttpMethod method) {
		addAllowedMethod(method.name());
	}

	/**
	 * Add an HTTP method to allow.
	 */
	public void addAllowedMethod(String method) {
		if (StringUtils.hasText(method)) {
			if (this.allowedMethods == null) {
				this.allowedMethods = new ArrayList<>(4);
				this.resolvedMethods = new ArrayList<>(4);
			}
			else if (this.allowedMethods == DEFAULT_PERMIT_METHODS) {
				setAllowedMethods(DEFAULT_PERMIT_METHODS);
			}
			this.allowedMethods.add(method);
			if (ALL.equals(method)) {
				this.resolvedMethods = null;
			}
			else if (this.resolvedMethods != null) {
				this.resolvedMethods.add(HttpMethod.resolve(method));
			}
		}
	}

	/**
	 * Set the list of headers that a pre-flight request can list as allowed
	 * for use during an actual request.
	 * <p>The special value {@code "*"} allows actual requests to send any
	 * header.
	 * <p>A header name is not required to be listed if it is one of:
	 * {@code Cache-Control}, {@code Content-Language}, {@code Expires},
	 * {@code Last-Modified}, or {@code Pragma}.
	 * <p>By default this is not set.
	 */
	public void setAllowedHeaders(@Nullable List<String> allowedHeaders) {
		this.allowedHeaders = (allowedHeaders != null ? new ArrayList<>(allowedHeaders) : null);
	}

	/**
	 * Return the allowed actual request headers, or {@code null} if none.
	 * @see #addAllowedHeader(String)
	 * @see #setAllowedHeaders(List)
	 */
	@Nullable
	public List<String> getAllowedHeaders() {
		return this.allowedHeaders;
	}

	/**
	 * Add an actual request header to allow.
	 */
	public void addAllowedHeader(String allowedHeader) {
		if (this.allowedHeaders == null) {
			this.allowedHeaders = new ArrayList<>(4);
		}
		else if (this.allowedHeaders == DEFAULT_PERMIT_ALL) {
			setAllowedHeaders(DEFAULT_PERMIT_ALL);
		}
		this.allowedHeaders.add(allowedHeader);
	}

	/**
	 * Set the list of response headers other than simple headers (i.e.
	 * {@code Cache-Control}, {@code Content-Language}, {@code Content-Type},
	 * {@code Expires}, {@code Last-Modified}, or {@code Pragma}) that an
	 * actual response might have and can be exposed.
	 * <p>The special value {@code "*"} allows all headers to be exposed for
	 * non-credentialed requests.
	 * <p>By default this is not set.
	 */
	public void setExposedHeaders(@Nullable List<String> exposedHeaders) {
		this.exposedHeaders = (exposedHeaders != null ? new ArrayList<>(exposedHeaders) : null);
	}

	/**
	 * Return the configured response headers to expose, or {@code null} if none.
	 * @see #addExposedHeader(String)
	 * @see #setExposedHeaders(List)
	 */
	@Nullable
	public List<String> getExposedHeaders() {
		return this.exposedHeaders;
	}

	/**
	 * Add a response header to expose.
	 * <p>The special value {@code "*"} allows all headers to be exposed for
	 * non-credentialed requests.
	 */
	public void addExposedHeader(String exposedHeader) {
		if (this.exposedHeaders == null) {
			this.exposedHeaders = new ArrayList<>(4);
		}
		this.exposedHeaders.add(exposedHeader);
	}

	/**
	 * Whether user credentials are supported.
	 * <p>By default this is not set (i.e. user credentials are not supported).
	 */
	public void setAllowCredentials(@Nullable Boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}

	/**
	 * Return the configured {@code allowCredentials} flag, or {@code null} if none.
	 * @see #setAllowCredentials(Boolean)
	 */
	@Nullable
	public Boolean getAllowCredentials() {
		return this.allowCredentials;
	}

	/**
	 * Configure how long, as a duration, the response from a pre-flight request
	 * can be cached by clients.
	 * @since 5.2
	 * @see #setMaxAge(Long)
	 */
	public void setMaxAge(Duration maxAge) {
		this.maxAge = maxAge.getSeconds();
	}

	/**
	 * Configure how long, in seconds, the response from a pre-flight request
	 * can be cached by clients.
	 * <p>By default this is not set.
	 */
	public void setMaxAge(@Nullable Long maxAge) {
		this.maxAge = maxAge;
	}

	/**
	 * Return the configured {@code maxAge} value, or {@code null} if none.
	 * @see #setMaxAge(Long)
	 */
	@Nullable
	public Long getMaxAge() {
		return this.maxAge;
	}


	/**
	 * 如果必要的Cors参数为空，就设置默认的
	 */
	public CorsConfiguration applyPermitDefaultValues() {
		if (this.allowedOrigins == null) {
			this.allowedOrigins = DEFAULT_PERMIT_ALL;
		}
		if (this.allowedMethods == null) {
			this.allowedMethods = DEFAULT_PERMIT_METHODS;
			this.resolvedMethods = DEFAULT_PERMIT_METHODS
					.stream().map(HttpMethod::resolve).collect(Collectors.toList());
		}
		if (this.allowedHeaders == null) {
			this.allowedHeaders = DEFAULT_PERMIT_ALL;
		}
		if (this.maxAge == null) {
			this.maxAge = 1800L;
		}
		return this;
	}

	/**
	 * 结合所有非空的Cors属性
	 * {@code CorsConfiguration} with this one.
	 * <p>When combining single values like {@code allowCredentials} or
	 * {@code maxAge}, {@code this} properties are overridden by non-null
	 * {@code other} properties if any.
	 * <p>Combining lists like {@code allowedOrigins}, {@code allowedMethods},
	 * {@code allowedHeaders} or {@code exposedHeaders} is done in an additive
	 * way. For example, combining {@code ["GET", "POST"]} with
	 * {@code ["PATCH"]} results in {@code ["GET", "POST", "PATCH"]}, but keep
	 * in mind that combining {@code ["GET", "POST"]} with {@code ["*"]}
	 * results in {@code ["*"]}.
	 * <p>Notice that default permit values set by
	 * {@link CorsConfiguration#applyPermitDefaultValues()} are overridden by
	 * any value explicitly defined.
	 * @return the combined {@code CorsConfiguration}, or {@code this}
	 * configuration if the supplied configuration is {@code null}
	 */
	@Nullable
	public CorsConfiguration combine(@Nullable CorsConfiguration other) {
		if (other == null) {
			return this;
		}
		CorsConfiguration config = new CorsConfiguration(this);
		config.setAllowedOrigins(combine(getAllowedOrigins(), other.getAllowedOrigins()));
		config.setAllowedMethods(combine(getAllowedMethods(), other.getAllowedMethods()));
		config.setAllowedHeaders(combine(getAllowedHeaders(), other.getAllowedHeaders()));
		config.setExposedHeaders(combine(getExposedHeaders(), other.getExposedHeaders()));
		Boolean allowCredentials = other.getAllowCredentials();
		if (allowCredentials != null) {
			config.setAllowCredentials(allowCredentials);
		}
		Long maxAge = other.getMaxAge();
		if (maxAge != null) {
			config.setMaxAge(maxAge);
		}
		return config;
	}

	private List<String> combine(@Nullable List<String> source, @Nullable List<String> other) {
		if (other == null) {
			return (source != null ? source : Collections.emptyList());
		}
		if (source == null) {
			return other;
		}
		if (source == DEFAULT_PERMIT_ALL || source == DEFAULT_PERMIT_METHODS) {
			return other;
		}
		if (other == DEFAULT_PERMIT_ALL || other == DEFAULT_PERMIT_METHODS) {
			return source;
		}
		if (source.contains(ALL) || other.contains(ALL)) {
			return new ArrayList<>(Collections.singletonList(ALL));
		}
		Set<String> combined = new LinkedHashSet<>(source);
		combined.addAll(other);
		return new ArrayList<>(combined);
	}

	/**
	 * 检查来源
	 * @param requestOrigin the origin to check
	 * @return the origin to use for the response, or {@code null} which
	 * means the request origin is not allowed
	 */
	@Nullable
	public String checkOrigin(@Nullable String requestOrigin) {
		if (!StringUtils.hasText(requestOrigin)) {
			return null;
		}
		if (ObjectUtils.isEmpty(this.allowedOrigins)) {
			return null;
		}

		// 如果来源都允许
		if (this.allowedOrigins.contains(ALL)) {
			if (this.allowCredentials != Boolean.TRUE) {
				return ALL;
			}
			else {
				return requestOrigin;
			}
		}

		// 忽略大小写的比较
		for (String allowedOrigin : this.allowedOrigins) {
			if (requestOrigin.equalsIgnoreCase(allowedOrigin)) {
				return requestOrigin;
			}
		}

		return null;
	}

	/**
	 * 检查当前Cors数据源是否支持这种请求方式
	 * @param requestMethod the HTTP request method to check
	 * @return the list of HTTP methods to list in the response of a pre-flight
	 * request, or {@code null} if the supplied {@code requestMethod} is not allowed
	 */
	@Nullable
	public List<HttpMethod> checkHttpMethod(@Nullable HttpMethod requestMethod) {
		if (requestMethod == null) {
			return null;
		}
		if (this.resolvedMethods == null) {
			return Collections.singletonList(requestMethod);
		}
		return (this.resolvedMethods.contains(requestMethod) ? this.resolvedMethods : null);
	}

	/**
	 * 检查当前Cors数据源是否支持携带这些请求头
	 * {@code Access-Control-Request-Headers} of a pre-flight request) against
	 * the configured allowed headers.
	 * @param requestHeaders the request headers to check
	 * @return 支持的请求头
	 */
	@Nullable
	public List<String> checkHeaders(@Nullable List<String> requestHeaders) {
		if (requestHeaders == null) {
			return null;
		}
		if (requestHeaders.isEmpty()) {
			return Collections.emptyList();
		}
		if (ObjectUtils.isEmpty(this.allowedHeaders)) {
			return null;
		}

		boolean allowAnyHeader = this.allowedHeaders.contains(ALL);
		List<String> result = new ArrayList<>(requestHeaders.size());
		for (String requestHeader : requestHeaders) {
			if (StringUtils.hasText(requestHeader)) {
				requestHeader = requestHeader.trim();
				// ALL的情况都支持
				if (allowAnyHeader) {
					result.add(requestHeader);
				}
				else {
					for (String allowedHeader : this.allowedHeaders) {
						if (requestHeader.equalsIgnoreCase(allowedHeader)) {
							result.add(requestHeader);
							break;
						}
					}
				}
			}
		}
		return (result.isEmpty() ? null : result);
	}

}
