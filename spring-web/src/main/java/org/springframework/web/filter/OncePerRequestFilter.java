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

package org.springframework.web.filter;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.util.WebUtils;

/**
 * 是为了每次请求同样一个过滤器都只执行一次
 * <p>As of Servlet 3.0, a filter may be invoked as part of a
 * {@link javax.servlet.DispatcherType#REQUEST REQUEST} or
 * {@link javax.servlet.DispatcherType#ASYNC ASYNC} dispatches that occur in
 * separate threads. A filter can be configured in {@code web.xml} whether it
 * should be involved in async dispatches. However, in some cases servlet
 * containers assume different default configuration. Therefore sub-classes can
 * override the method {@link #shouldNotFilterAsyncDispatch()} to declare
 * statically if they should indeed be invoked, <em>once</em>, during both types
 * of dispatches in order to provide thread initialization, logging, security,
 * and so on. This mechanism complements and does not replace the need to
 * configure a filter in {@code web.xml} with dispatcher types.
 *
 * <p>Subclasses may use {@link #isAsyncDispatch(HttpServletRequest)} to
 * determine when a filter is invoked as part of an async dispatch, and use
 * {@link #isAsyncStarted(HttpServletRequest)} to determine when the request
 * has been placed in async mode and therefore the current dispatch won't be
 * the last one for the given request.
 *
 * <p>Yet another dispatch type that also occurs in its own thread is
 * {@link javax.servlet.DispatcherType#ERROR ERROR}. Subclasses can override
 * {@link #shouldNotFilterErrorDispatch()} if they wish to declare statically
 * if they should be invoked <em>once</em> during error dispatches.
 *
 * <p>The {@link #getAlreadyFilteredAttributeName} method determines how to
 * identify that a request is already filtered. The default implementation is
 * based on the configured name of the concrete filter instance.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 06.12.2003
 */
public abstract class OncePerRequestFilter extends GenericFilterBean {

	/**
	 * 用过滤名称 + 此参数 就可以表示是否已经执行过某个过滤器了
	 */
	public static final String ALREADY_FILTERED_SUFFIX = ".FILTERED";


	/**
	 * This {@code doFilter} implementation stores a request attribute for
	 * "already filtered", proceeding without filtering again if the
	 * attribute is already there.
	 * @see #getAlreadyFilteredAttributeName
	 * @see #shouldNotFilter
	 * @see #doFilterInternal
	 */
	@Override
	public final void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
			throw new ServletException("OncePerRequestFilter just supports HTTP requests");
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();
		boolean hasAlreadyFilteredAttribute = request.getAttribute(alreadyFilteredAttributeName) != null;

		// 是否应该跳过当前过滤器
		if (skipDispatch(httpRequest) || shouldNotFilter(httpRequest)) {

			// Proceed without invoking this filter...
			filterChain.doFilter(request, response);
		}
		// 已经执行过此过滤器了
		else if (hasAlreadyFilteredAttribute) {
			// 通过作为Error的派发情况下，过滤器是需要重新开始执行的
			if (DispatcherType.ERROR.equals(request.getDispatcherType())) {
				doFilterNestedErrorDispatch(httpRequest, httpResponse, filterChain);
				return;
			}

			// Proceed without invoking this filter...
			filterChain.doFilter(request, response);
		}
		else {
			// 设置此请求域参数，表明已经执行过此过滤器了
			request.setAttribute(alreadyFilteredAttributeName, Boolean.TRUE);
			try {
				// 执行真正的过滤器
				doFilterInternal(httpRequest, httpResponse, filterChain);
			}
			finally {
				// 删除此请求的“已过滤”请求属性，不懂为什么这里要删除
				request.removeAttribute(alreadyFilteredAttributeName);
			}
		}
	}

	/**
	 * 是否跳过
	 * @param request
	 * @return
	 */
	private boolean skipDispatch(HttpServletRequest request) {
		// 以是否是异步任务判断
		if (isAsyncDispatch(request) && shouldNotFilterAsyncDispatch()) {
			return true;
		}
		// 以异常作为条件判断
		if (request.getAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE) != null && shouldNotFilterErrorDispatch()) {
			return true;
		}
		return false;
	}

	/**
	 * 派发类型为 {@code DispatcherType}
	 * <p>Servlet 3.0中引入的ASYNC意味着过滤器可以在单个请求中被多个线程中调用</p>
	 * <p>如果过滤器器当前是在异步执行，此方法返回true</p>
	 */
	protected boolean isAsyncDispatch(HttpServletRequest request) {
		return WebAsyncUtils.getAsyncManager(request).hasConcurrentResult();
	}

	/**
	 * 是否已经开始执行异步任务了
	 * @param request the current request
	 * @since 3.2
	 * @see WebAsyncManager#isConcurrentHandlingStarted()
	 */
	protected boolean isAsyncStarted(HttpServletRequest request) {
		return WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted();
	}

	/**
	 * Return the name of the request attribute that identifies that a request
	 * is already filtered.
	 * <p>The default implementation takes the configured name of the concrete filter
	 * instance and appends ".FILTERED". If the filter is not fully initialized,
	 * it falls back to its class name.
	 * @see #getFilterName
	 * @see #ALREADY_FILTERED_SUFFIX
	 */
	protected String getAlreadyFilteredAttributeName() {
		String name = getFilterName();
		if (name == null) {
			name = getClass().getName();
		}
		return name + ALREADY_FILTERED_SUFFIX;
	}

	/**
	 * 表明此次请求是否应该跳过改过滤器
	 * <p>The default implementation always returns {@code false}.
	 * @param request current HTTP request
	 * @return whether the given request should <i>not</i> be filtered
	 * @throws ServletException in case of errors
	 */
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		return false;
	}

	/**
	 * 是否不考虑异步的情况
	 * The dispatcher type {@code javax.servlet.DispatcherType.ASYNC} introduced
	 * in Servlet 3.0 means a filter can be invoked in more than one thread
	 * over the course of a single request. Some filters only need to filter
	 * the initial thread (e.g. request wrapping) while others may need
	 * to be invoked at least once in each additional thread for example for
	 * setting up thread locals or to perform final processing at the very end.
	 * <p>Note that although a filter can be mapped to handle specific dispatcher
	 * types via {@code web.xml} or in Java through the {@code ServletContext},
	 * servlet containers may enforce different defaults with regards to
	 * dispatcher types. This flag enforces the design intent of the filter.
	 * <p>The default return value is "true", which means the filter will not be
	 * invoked during subsequent async dispatches. If "false", the filter will
	 * be invoked during async dispatches with the same guarantees of being
	 * invoked only once during a request within a single thread.
	 * @since 3.2
	 */
	protected boolean shouldNotFilterAsyncDispatch() {
		return true;
	}

	/**
	 * 在本次请求已经出现异常的情况下，是否无视错误的情况
	 */
	protected boolean shouldNotFilterErrorDispatch() {
		return true;
	}


	/**
	 * Same contract as for {@code doFilter}, but guaranteed to be
	 * just invoked once per request within a single request thread.
	 * See {@link #shouldNotFilterAsyncDispatch()} for details.
	 * <p>Provides HttpServletRequest and HttpServletResponse arguments instead of the
	 * default ServletRequest and ServletResponse ones.
	 */
	protected abstract void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException;

	/**
	 * <ol>
	 *     <li>通常，ERROR分派发生在REQUEST分派完成之后，过滤器链重新开始</li>
	 *     <li>然而，在一些服务器上，ERROR分派可能嵌套在REQUEST分派中</li>
	 *     <li>例如，作为对响应调用sendError的结果。在这种情况下，我们仍然在过滤器链中，在同一个线程上，但是请求和响应已经切换到原始的、未包装的响应</li>
	 * </ol>
	 * <p>Sub-classes may use this method to filter such nested ERROR dispatches
	 * and re-apply wrapping on the request or response. {@code ThreadLocal}
	 * context, if any, should still be active as we are still nested within
	 * the filter chain.
	 * @since 5.1.9
	 */
	protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		filterChain.doFilter(request, response);
	}

}
