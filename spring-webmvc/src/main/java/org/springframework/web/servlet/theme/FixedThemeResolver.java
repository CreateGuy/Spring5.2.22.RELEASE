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

/**
 * 固定主题的主题解析器
 * @see #setDefaultThemeName
 */
public class FixedThemeResolver extends AbstractThemeResolver {

	/**
	 * 固定返回默认主题
	 * @param request the request to be used for resolution
	 * @return
	 */
	@Override
	public String resolveThemeName(HttpServletRequest request) {
		return getDefaultThemeName();
	}

	/**
	 * 固定主题，不能重新设置主题
	 * @param request the request to be used for theme name modification
	 * @param response the response to be used for theme name modification
	 * @param themeName the new theme name ({@code null} or empty to reset it)
	 */
	@Override
	public void setThemeName(
			HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable String themeName) {

		throw new UnsupportedOperationException("Cannot change theme - use a different theme resolution strategy");
	}

}
