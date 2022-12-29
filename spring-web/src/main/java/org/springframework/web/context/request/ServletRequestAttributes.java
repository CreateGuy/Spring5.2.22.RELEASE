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

package org.springframework.web.context.request;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * 从请求域和Session域中获取对象
 */
public class ServletRequestAttributes extends AbstractRequestAttributes {

	/**
	 * 请求完成需要执行的Session级别的回调的键前缀
	 */
	public static final String DESTRUCTION_CALLBACK_NAME_PREFIX =
			ServletRequestAttributes.class.getName() + ".DESTRUCTION_CALLBACK.";

	/**
	 * 简单变量类型
	 */
	protected static final Set<Class<?>> immutableValueTypes = new HashSet<>(16);

	static {
		immutableValueTypes.addAll(NumberUtils.STANDARD_NUMBER_TYPES);
		immutableValueTypes.add(Boolean.class);
		immutableValueTypes.add(Character.class);
		immutableValueTypes.add(String.class);
	}


	private final HttpServletRequest request;

	@Nullable
	private HttpServletResponse response;

	@Nullable
	private volatile HttpSession session;

	/**
	 * 要更新的会话属性
	 */
	private final Map<String, Object> sessionAttributesToUpdate = new ConcurrentHashMap<>(1);


	/**
	 * Create a new ServletRequestAttributes instance for the given request.
	 * @param request current HTTP request
	 */
	public ServletRequestAttributes(HttpServletRequest request) {
		Assert.notNull(request, "Request must not be null");
		this.request = request;
	}

	/**
	 * Create a new ServletRequestAttributes instance for the given request.
	 * @param request current HTTP request
	 * @param response current HTTP response (for optional exposure)
	 */
	public ServletRequestAttributes(HttpServletRequest request, @Nullable HttpServletResponse response) {
		this(request);
		this.response = response;
	}


	/**
	 * Exposes the native {@link HttpServletRequest} that we're wrapping.
	 */
	public final HttpServletRequest getRequest() {
		return this.request;
	}

	/**
	 * Exposes the native {@link HttpServletResponse} that we're wrapping (if any).
	 */
	@Nullable
	public final HttpServletResponse getResponse() {
		return this.response;
	}

	/***
	 * 获得Session
	 * @param allowCreate
	 * @return 如果没有Session是否强制创建会话
	 */
	@Nullable
	protected final HttpSession getSession(boolean allowCreate) {
		// 确定原始请求是否仍处于活动状态
		if (isRequestActive()) {
			HttpSession session = this.request.getSession(allowCreate);
			this.session = session;
			return session;
		}
		else {
			// Access through stored session reference, if any...
			HttpSession session = this.session;
			// 无法创建会话了
			if (session == null) {
				// 是直接抛出异常还是强制创建Session
				if (allowCreate) {
					throw new IllegalStateException(
							"No session found and request already completed - cannot create new session!");
				}
				else {
					session = this.request.getSession(false);
					this.session = session;
				}
			}
			return session;
		}
	}

	/**
	 * 获得Session
	 * @return
	 */
	private HttpSession obtainSession() {
		HttpSession session = getSession(true);
		Assert.state(session != null, "No HttpSession");
		return session;
	}


	/**
	 * 从请求域或者会话域获取属性
	 * @param name the name of the attribute
	 * @param scope 从何处获取属性
	 * @return
	 */
	@Override
	public Object getAttribute(String name, int scope) {
		if (scope == SCOPE_REQUEST) {
			if (!isRequestActive()) {
				throw new IllegalStateException(
						"Cannot ask for request attribute - request is not active anymore!");
			}
			return this.request.getAttribute(name);
		}
		else {
			HttpSession session = getSession(false);
			if (session != null) {
				try {
					Object value = session.getAttribute(name);
					if (value != null) {
						// 表明此属性是一个要更新的会话信息
						this.sessionAttributesToUpdate.put(name, value);
					}
					return value;
				}
				catch (IllegalStateException ex) {
					// Session invalidated - shouldn't usually happen.
				}
			}
			return null;
		}
	}

	/**
	 * 在某个域中 设置某个属性
	 * @param name the name of the attribute
	 * @param scope the scope identifier
	 */
	@Override
	public void setAttribute(String name, Object value, int scope) {
		// 请求域
		if (scope == SCOPE_REQUEST) {
			if (!isRequestActive()) {
				throw new IllegalStateException(
						"Cannot set request attribute - request is not active anymore!");
			}
			this.request.setAttribute(name, value);
		}
		else {
			// 会话域
			HttpSession session = obtainSession();
			this.sessionAttributesToUpdate.remove(name);
			session.setAttribute(name, value);
		}
	}

	/**
	 * 从某个域，移除某个属性
	 * @param name the name of the attribute
	 * @param scope the scope identifier
	 */
	@Override
	public void removeAttribute(String name, int scope) {
		// 请求域
		if (scope == SCOPE_REQUEST) {
			if (isRequestActive()) {
				removeRequestDestructionCallback(name);
				this.request.removeAttribute(name);
			}
		}
		else {
			// 会话域
			HttpSession session = getSession(false);
			if (session != null) {
				this.sessionAttributesToUpdate.remove(name);
				try {
					session.removeAttribute(DESTRUCTION_CALLBACK_NAME_PREFIX + name);
					session.removeAttribute(name);
				}
				catch (IllegalStateException ex) {
					// Session invalidated - shouldn't usually happen.
				}
			}
		}
	}

	/**
	 * 获得某个域的所有属性名称
	 * @param scope the scope identifier
	 * @return
	 */
	@Override
	public String[] getAttributeNames(int scope) {
		// 请求域
		if (scope == SCOPE_REQUEST) {
			if (!isRequestActive()) {
				throw new IllegalStateException(
						"Cannot ask for request attributes - request is not active anymore!");
			}
			return StringUtils.toStringArray(this.request.getAttributeNames());
		}
		else {
			// 会话域
			HttpSession session = getSession(false);
			if (session != null) {
				try {
					return StringUtils.toStringArray(session.getAttributeNames());
				}
				catch (IllegalStateException ex) {
					// Session invalidated - shouldn't usually happen.
				}
			}
			return new String[0];
		}
	}

	/**
	 * 注册一个回调，在给定范围内的指定属性销毁时执行
	 * @param name the name of the attribute to register the callback for
	 * @param callback the destruction callback to be executed
	 * @param scope the scope identifier
	 */
	@Override
	public void registerDestructionCallback(String name, Runnable callback, int scope) {
		if (scope == SCOPE_REQUEST) {
			// 注册一个当前请求域的回调
			registerRequestDestructionCallback(name, callback);
		}
		else {
			// 注册一个会话域的回调
			registerSessionDestructionCallback(name, callback);
		}
	}

	/**
	 * 解析键然后返回具体的域对象，一般是请求域和会话域
	 * @param key the contextual key
	 * @return
	 */
	@Override
	public Object resolveReference(String key) {
		if (REFERENCE_REQUEST.equals(key)) {
			return this.request;
		}
		else if (REFERENCE_SESSION.equals(key)) {
			return getSession(true);
		}
		else {
			return null;
		}
	}

	@Override
	public String getSessionId() {
		return obtainSession().getId();
	}

	@Override
	public Object getSessionMutex() {
		return WebUtils.getSessionMutex(obtainSession());
	}


	/**
	 * 更新复杂对象
	 */
	@Override
	protected void updateAccessedSessionAttributes() {
		if (!this.sessionAttributesToUpdate.isEmpty()) {
			// 更新所有受影响的会话属性
			HttpSession session = getSession(false);
			if (session != null) {
				try {
					for (Map.Entry<String, Object> entry : this.sessionAttributesToUpdate.entrySet()) {
						String name = entry.getKey();
						Object newValue = entry.getValue();
						Object oldValue = session.getAttribute(name);
						// 新值和旧值要相同，而且要是复杂类型，比如说User对象，将id变了，但是User的地址不会变
						if (oldValue == newValue && !isImmutableSessionAttribute(name, newValue)) {
							// 更新这个复杂对象
							session.setAttribute(name, newValue);
						}
					}
				}
				catch (IllegalStateException ex) {
					// Session invalidated - shouldn't usually happen.
				}
			}
			this.sessionAttributesToUpdate.clear();
		}
	}

	/**
	 * 确定给定的值是否被认为是不可变的会话属性
	 * <ul>
	 *     <li>
	 *         比如说User对象，将id变了，但是User的地址不会变
	 *     </li>
	 * </ul>
	 * @param name
	 * @param value
	 * @return
	 */
	protected boolean isImmutableSessionAttribute(String name, @Nullable Object value) {
		return (value == null || immutableValueTypes.contains(value.getClass()));
	}

	/**
	 * 注册一个会话的回调，会在会话终止后执行
	 * <li>
	 *     没看到哪里调用了DESTRUCTION_CALLBACK_NAME_PREFIX这个键前缀
	 * </li>
	 * @param name the name of the attribute to register the callback for
	 * @param callback the callback to be executed for destruction
	 */
	protected void registerSessionDestructionCallback(String name, Runnable callback) {
		HttpSession session = obtainSession();
		session.setAttribute(DESTRUCTION_CALLBACK_NAME_PREFIX + name,
				new DestructionCallbackBindingListener(callback));
	}


	@Override
	public String toString() {
		return this.request.toString();
	}

}
