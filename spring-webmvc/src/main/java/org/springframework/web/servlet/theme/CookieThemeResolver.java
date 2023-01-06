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

package org.springframework.web.servlet.theme;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ThemeResolver;
import org.springframework.web.util.CookieGenerator;
import org.springframework.web.util.WebUtils;

/**
 * 基于 Cookie 的主题解析器
 */
public class CookieThemeResolver extends CookieGenerator implements ThemeResolver {

	/**
	 * The default theme name used if no alternative is provided.
	 */
	public static final String ORIGINAL_DEFAULT_THEME_NAME = "theme";

	/**
	 * 主题名称在请求域中的名称
	 * @see org.springframework.web.servlet.support.RequestContext#getTheme
	 */
	public static final String THEME_REQUEST_ATTRIBUTE_NAME = CookieThemeResolver.class.getName() + ".THEME";

	/**
	 * The default name of the cookie that holds the theme name.
	 */
	public static final String DEFAULT_COOKIE_NAME = CookieThemeResolver.class.getName() + ".THEME";


	private String defaultThemeName = ORIGINAL_DEFAULT_THEME_NAME;


	public CookieThemeResolver() {
		setCookieName(DEFAULT_COOKIE_NAME);
	}


	/**
	 * Set the name of the default theme.
	 */
	public void setDefaultThemeName(String defaultThemeName) {
		this.defaultThemeName = defaultThemeName;
	}

	/**
	 * Return the name of the default theme.
	 */
	public String getDefaultThemeName() {
		return this.defaultThemeName;
	}


	@Override
	public String resolveThemeName(HttpServletRequest request) {
		// 先检查请求域中的主题
		String themeName = (String) request.getAttribute(THEME_REQUEST_ATTRIBUTE_NAME);
		if (themeName != null) {
			return themeName;
		}

		// 后检查Cookie中的主题
		String cookieName = getCookieName();
		if (cookieName != null) {
			Cookie cookie = WebUtils.getCookie(request, cookieName);
			if (cookie != null) {
				String value = cookie.getValue();
				if (StringUtils.hasText(value)) {
					themeName = value;
				}
			}
		}

		// 请求域和Cookie中都没有，使用默认主题
		if (themeName == null) {
			themeName = getDefaultThemeName();
		}
		request.setAttribute(THEME_REQUEST_ATTRIBUTE_NAME, themeName);
		return themeName;
	}

	/**
	 * 设置主题
	 * @param request the request to be used for theme name modification
	 * @param response the response to be used for theme name modification
	 * @param themeName the new theme name ({@code null} or empty to reset it)
	 */
	@Override
	public void setThemeName(
			HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable String themeName) {

		Assert.notNull(response, "HttpServletResponse is required for CookieThemeResolver");

		if (StringUtils.hasText(themeName)) {
			// 将主题设置到请求域中并添加到cookie中
			request.setAttribute(THEME_REQUEST_ATTRIBUTE_NAME, themeName);
			addCookie(response, themeName);
		}
		else {
			// 将主题设置到请求域中并删除cookie中的属性
			request.setAttribute(THEME_REQUEST_ATTRIBUTE_NAME, getDefaultThemeName());
			removeCookie(response);
		}
	}

}
