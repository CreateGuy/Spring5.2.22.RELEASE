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

package org.springframework.web.servlet.config.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

/**
 * 预先配置Uri和 状态代码/或视图的关系
 * <ul>
 *     <li>是和 {@link SimpleUrlHandlerMapping} 配合使用的</li>
 *     <li>比如说访问1hello，直接返回login.html</li>
 * </ul>
 */
public class ViewControllerRegistry {

	@Nullable
	private ApplicationContext applicationContext;

	/**
	 * 已经注册的映射关系
	 */
	private final List<ViewControllerRegistration> registrations = new ArrayList<>(4);

	/**
	 * 已经注册的重定向映射关系
	 */
	private final List<RedirectViewControllerRegistration> redirectRegistrations = new ArrayList<>(10);

	/**
	 * {@link SimpleUrlHandlerMapping} 在整个 {@link org.springframework.web.servlet.HandlerMapping}的位置
	 */
	private int order = 1;


	/**
	 * Class constructor with {@link ApplicationContext}.
	 * @since 4.3.12
	 */
	public ViewControllerRegistry(@Nullable ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	/**
	 * 将Url映射到指定的 {@link org.springframework.web.servlet.mvc.Controller}，以呈现带有预先配置的状态代码和视图
	 * <p>Patterns like {@code "/admin/**"} or {@code "/articles/{articlename:\\w+}"}
	 * are allowed. See {@link org.springframework.util.AntPathMatcher} for more details on the
	 * syntax.
	 * <p><strong>Note:</strong> If an {@code @RequestMapping} method is mapped
	 * to a URL for any HTTP method then a view controller cannot handle the
	 * same URL. For this reason it is recommended to avoid splitting URL
	 * handling across an annotated controller and a view controller.
	 */
	public ViewControllerRegistration addViewController(String urlPath) {
		ViewControllerRegistration registration = new ViewControllerRegistration(urlPath);
		registration.setApplicationContext(this.applicationContext);
		this.registrations.add(registration);
		return registration;
	}

	/**
	 *
	 */
	public RedirectViewControllerRegistration addRedirectViewController(String urlPath, String redirectUrl) {
		RedirectViewControllerRegistration registration = new RedirectViewControllerRegistration(urlPath, redirectUrl);
		registration.setApplicationContext(this.applicationContext);
		this.redirectRegistrations.add(registration);
		return registration;
	}

	/**
	 * 将Url映射到指定的 {@link org.springframework.web.servlet.mvc.Controller}，以呈现带有预先配置的状态代码和视图
	 */
	public void addStatusController(String urlPath, HttpStatus statusCode) {
		ViewControllerRegistration registration = new ViewControllerRegistration(urlPath);
		registration.setApplicationContext(this.applicationContext);
		registration.setStatusCode(statusCode);
		registration.getViewController().setStatusOnly(true);
		this.registrations.add(registration);
	}

	/**
	 * 设置 {@link SimpleUrlHandlerMapping} 在整个 {@link org.springframework.web.servlet.HandlerMapping}的位置
	 */
	public void setOrder(int order) {
		this.order = order;
	}


	/**
	 * 返回注册了指定Uri和 {@link org.springframework.web.servlet.mvc.Controller} 的关系的 {@link org.springframework.web.servlet.HandlerMapping}
	 */
	@Nullable
	protected SimpleUrlHandlerMapping buildHandlerMapping() {
		if (this.registrations.isEmpty() && this.redirectRegistrations.isEmpty()) {
			return null;
		}

		Map<String, Object> urlMap = new LinkedHashMap<>();
		for (ViewControllerRegistration registration : this.registrations) {
			urlMap.put(registration.getUrlPath(), registration.getViewController());
		}
		for (RedirectViewControllerRegistration registration : this.redirectRegistrations) {
			urlMap.put(registration.getUrlPath(), registration.getViewController());
		}

		return new SimpleUrlHandlerMapping(urlMap, this.order);
	}

}
