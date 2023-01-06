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

package org.springframework.web.servlet.theme;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * 基于 Session 的主题解析器
 */
public class SessionThemeResolver extends AbstractThemeResolver {

	/**
	 * 保存在会话中的主题的属性名
	 */
	public static final String THEME_SESSION_ATTRIBUTE_NAME = SessionThemeResolver.class.getName() + ".THEME";

	/**
	 * 使用特定的主题还是默认主题
	 * @param request the request to be used for resolution
	 * @return
	 */
	@Override
	public String resolveThemeName(HttpServletRequest request) {
		String themeName = (String) WebUtils.getSessionAttribute(request, THEME_SESSION_ATTRIBUTE_NAME);
		// A specific theme indicated, or do we need to fallback to the default?
		return (themeName != null ? themeName : getDefaultThemeName());
	}

	/**
	 * 将主题保存到会话中，或者说删除主题
	 * @param request the request to be used for theme name modification
	 * @param response the response to be used for theme name modification
	 * @param themeName the new theme name ({@code null} or empty to reset it)
	 */
	@Override
	public void setThemeName(
			HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable String themeName) {

		WebUtils.setSessionAttribute(request, THEME_SESSION_ATTRIBUTE_NAME,
				(StringUtils.hasText(themeName) ? themeName : null));
	}

}
