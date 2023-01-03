/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.context.support;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.WebApplicationContext;

/**
 * HttpServletRequest包装，
 * 它通过使用 WebApplicationContext 让所有Spring bean作为请求属性可访问
 */
public class ContextExposingHttpServletRequest extends HttpServletRequestWrapper {

	private final WebApplicationContext webApplicationContext;

	/**
	 * 可以暴露bean的名称
	 */
	@Nullable
	private final Set<String> exposedContextBeanNames;

	/**
	 * 显式属性
	 */
	@Nullable
	private Set<String> explicitAttributes;


	/**
	 * Create a new ContextExposingHttpServletRequest for the given request.
	 * @param originalRequest the original HttpServletRequest
	 * @param context the WebApplicationContext that this request runs in
	 */
	public ContextExposingHttpServletRequest(HttpServletRequest originalRequest, WebApplicationContext context) {
		this(originalRequest, context, null);
	}

	/**
	 * Create a new ContextExposingHttpServletRequest for the given request.
	 * @param originalRequest the original HttpServletRequest
	 * @param context the WebApplicationContext that this request runs in
	 * @param exposedContextBeanNames the names of beans in the context which
	 * are supposed to be exposed (if this is non-null, only the beans in this
	 * Set are eligible for exposure as attributes)
	 */
	public ContextExposingHttpServletRequest(HttpServletRequest originalRequest, WebApplicationContext context,
			@Nullable Set<String> exposedContextBeanNames) {

		super(originalRequest);
		Assert.notNull(context, "WebApplicationContext must not be null");
		this.webApplicationContext = context;
		this.exposedContextBeanNames = exposedContextBeanNames;
	}


	/**
	 * Return the WebApplicationContext that this request runs in.
	 */
	public final WebApplicationContext getWebApplicationContext() {
		return this.webApplicationContext;
	}


	/**
	 * 获得指定属性
	 * @param name
	 * @return
	 */
	@Override
	@Nullable
	public Object getAttribute(String name) {
		// 1、要么没有显式属性，要么显式属性中没有包含指定的内容
		// 2、如果设置了可以暴露bean的名称的话，必须要求在这个里面
		// 3、要求容器中有这个bean
		// 满足以三个条件才会从容器中获得bean，反正就从请求域中获取
		if ((this.explicitAttributes == null || !this.explicitAttributes.contains(name)) &&
				(this.exposedContextBeanNames == null || this.exposedContextBeanNames.contains(name)) &&
				this.webApplicationContext.containsBean(name)) {
			return this.webApplicationContext.getBean(name);
		}
		else {
			// 从请求域中获取参数
			return super.getAttribute(name);
		}
	}

	/**
	 * 设置指定属性
	 * @param name
	 * @param value
	 */
	@Override
	public void setAttribute(String name, Object value) {
		// 设置到请求域中
		super.setAttribute(name, value);
		if (this.explicitAttributes == null) {
			this.explicitAttributes = new HashSet<>(8);
		}
		this.explicitAttributes.add(name);
	}

}
