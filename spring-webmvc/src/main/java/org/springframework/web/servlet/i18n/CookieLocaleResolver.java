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

package org.springframework.web.servlet.i18n;

import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.CookieGenerator;
import org.springframework.web.util.WebUtils;

/**
 * {@link LocaleResolver} 该实现在自定义设置的情况下使用用户的cookie，并回退到指定的默认区域或请求的accept-header区域。
 *
 * <p>This is particularly useful for stateless applications without user sessions.
 * The cookie may optionally contain an associated time zone value as well;
 * alternatively, you may specify a default time zone.
 *
 * <p>Custom controllers can override the user's locale and time zone by calling
 * {@code #setLocale(Context)} on the resolver, e.g. responding to a locale change
 * request. As a more convenient alternative, consider using
 * {@link org.springframework.web.servlet.support.RequestContext#changeLocale}.
 *
 * @author Juergen Hoeller
 * @author Jean-Pierre Pawlak
 * @since 27.02.2003
 * @see #setDefaultLocale
 * @see #setDefaultTimeZone
 */
public class CookieLocaleResolver extends CookieGenerator implements LocaleContextResolver {

	/**
	 * 请求域中存储Local的键
	 */
	public static final String LOCALE_REQUEST_ATTRIBUTE_NAME = CookieLocaleResolver.class.getName() + ".LOCALE";

	/**
	 * 请求域中存储Time_ZONE的键
	 */
	public static final String TIME_ZONE_REQUEST_ATTRIBUTE_NAME = CookieLocaleResolver.class.getName() + ".TIME_ZONE";

	/**
	 * 默认存储在Cookie中的Local的键
	 */
	public static final String DEFAULT_COOKIE_NAME = CookieLocaleResolver.class.getName() + ".LOCALE";


	private boolean languageTagCompliant = true;

	/**
	 * 是否在解析Local失败后忽略异常
	 */
	private boolean rejectInvalidCookies = true;

	/**
	 * 默认的语言环境
	 */
	@Nullable
	private Locale defaultLocale;

	/**
	 * 默认的时区
	 */
	@Nullable
	private TimeZone defaultTimeZone;


	/**
	 * 设置国际化参数名称
	 */
	public CookieLocaleResolver() {
		setCookieName(DEFAULT_COOKIE_NAME);
	}


	/**
	 * Specify whether this resolver's cookies should be compliant with BCP 47
	 * language tags instead of Java's legacy locale specification format.
	 * <p>The default is {@code true}, as of 5.1. Switch this to {@code false}
	 * for rendering Java's legacy locale specification format. For parsing,
	 * this resolver leniently accepts the legacy {@link Locale#toString}
	 * format as well as BCP 47 language tags in any case.
	 * @since 4.3
	 * @see #parseLocaleValue(String)
	 * @see #toLocaleValue(Locale)
	 * @see Locale#forLanguageTag(String)
	 * @see Locale#toLanguageTag()
	 */
	public void setLanguageTagCompliant(boolean languageTagCompliant) {
		this.languageTagCompliant = languageTagCompliant;
	}

	/**
	 * Return whether this resolver's cookies should be compliant with BCP 47
	 * language tags instead of Java's legacy locale specification format.
	 * @since 4.3
	 */
	public boolean isLanguageTagCompliant() {
		return this.languageTagCompliant;
	}

	/**
	 * Specify whether to reject cookies with invalid content (e.g. invalid format).
	 * <p>The default is {@code true}. Turn this off for lenient handling of parse
	 * failures, falling back to the default locale and time zone in such a case.
	 * @since 5.1.7
	 * @see #setDefaultLocale
	 * @see #setDefaultTimeZone
	 * @see #determineDefaultLocale
	 * @see #determineDefaultTimeZone
	 */
	public void setRejectInvalidCookies(boolean rejectInvalidCookies) {
		this.rejectInvalidCookies = rejectInvalidCookies;
	}

	/**
	 * Return whether to reject cookies with invalid content (e.g. invalid format).
	 * @since 5.1.7
	 */
	public boolean isRejectInvalidCookies() {
		return this.rejectInvalidCookies;
	}

	/**
	 * Set a fixed locale that this resolver will return if no cookie found.
	 */
	public void setDefaultLocale(@Nullable Locale defaultLocale) {
		this.defaultLocale = defaultLocale;
	}

	/**
	 * Return the fixed locale that this resolver will return if no cookie found,
	 * if any.
	 */
	@Nullable
	protected Locale getDefaultLocale() {
		return this.defaultLocale;
	}

	/**
	 * Set a fixed time zone that this resolver will return if no cookie found.
	 * @since 4.0
	 */
	public void setDefaultTimeZone(@Nullable TimeZone defaultTimeZone) {
		this.defaultTimeZone = defaultTimeZone;
	}

	/**
	 * Return the fixed time zone that this resolver will return if no cookie found,
	 * if any.
	 * @since 4.0
	 */
	@Nullable
	protected TimeZone getDefaultTimeZone() {
		return this.defaultTimeZone;
	}


	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		parseLocaleCookieIfNecessary(request);
		return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * 从Cookie中解析LocaleContext
	 * @param request the request to resolve the locale context for
	 * @return
	 */
	@Override
	public LocaleContext resolveLocaleContext(final HttpServletRequest request) {
		// 从Cookie中解析LocaleContext
		parseLocaleCookieIfNecessary(request);
		return new TimeZoneAwareLocaleContext() {
			@Override
			@Nullable
			public Locale getLocale() {
				return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
			}
			@Override
			@Nullable
			public TimeZone getTimeZone() {
				return (TimeZone) request.getAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME);
			}
		};
	}

	/**
	 * 从Cookie中解析Local
	 * @param request
	 */
	private void parseLocaleCookieIfNecessary(HttpServletRequest request) {
		// 当请求域中没有的时候
		if (request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME) == null) {
			// 语言环境
			Locale locale = null;
			// 时区
			TimeZone timeZone = null;

			// 获得国际化的Cookie名称
			String cookieName = getCookieName();
			if (cookieName != null) {
				// 获得指定Cookie
				Cookie cookie = WebUtils.getCookie(request, cookieName);
				if (cookie != null) {
					String value = cookie.getValue();
					String localePart = value;
					String timeZonePart = null;
					// 支持Local和TimeZone用 '/' 或者 ' '分隔
					int separatorIndex = localePart.indexOf('/');
					if (separatorIndex == -1) {
						// Leniently accept older cookies separated by a space...
						separatorIndex = localePart.indexOf(' ');
					}

					// 进行切分
					if (separatorIndex >= 0) {
						localePart = value.substring(0, separatorIndex);
						timeZonePart = value.substring(separatorIndex + 1);
					}
					try {
						// 转换Locale
						locale = (!"-".equals(localePart) ? parseLocaleValue(localePart) : null);
						if (timeZonePart != null) {
							// 转换TimeZone
							timeZone = StringUtils.parseTimeZoneString(timeZonePart);
						}
					}
					catch (IllegalArgumentException ex) {
						// 是否忽略异常
						if (isRejectInvalidCookies() &&
								request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == null) {
							throw new IllegalStateException("Encountered invalid locale cookie '" +
									cookieName + "': [" + value + "] due to: " + ex.getMessage());
						}
						else {
							// Lenient handling (e.g. error dispatch): ignore locale/timezone parse exceptions
							if (logger.isDebugEnabled()) {
								logger.debug("Ignoring invalid locale cookie '" + cookieName +
										"': [" + value + "] due to: " + ex.getMessage());
							}
						}
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Parsed cookie value [" + cookie.getValue() + "] into locale '" + locale +
								"'" + (timeZone != null ? " and time zone '" + timeZone.getID() + "'" : ""));
					}
				}
			}
			// 将Locale和TimeZone设置到请求域中
			request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME,
					(locale != null ? locale : determineDefaultLocale(request)));
			request.setAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME,
					(timeZone != null ? timeZone : determineDefaultTimeZone(request)));
		}
	}

	@Override
	public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
		setLocaleContext(request, response, (locale != null ? new SimpleLocaleContext(locale) : null));
	}

	/**
	 * 将传入的Local设置到Cookie中
	 * @param request the request to be used for locale modification
	 * @param response the response to be used for locale modification
	 * @param localeContext the new locale context, or {@code null} to clear the locale
	 */
	@Override
	public void setLocaleContext(HttpServletRequest request, @Nullable HttpServletResponse response,
			@Nullable LocaleContext localeContext) {

		Assert.notNull(response, "HttpServletResponse is required for CookieLocaleResolver");

		Locale locale = null;
		TimeZone timeZone = null;
		if (localeContext != null) {
			locale = localeContext.getLocale();
			if (localeContext instanceof TimeZoneAwareLocaleContext) {
				timeZone = ((TimeZoneAwareLocaleContext) localeContext).getTimeZone();
			}
			// 使用此生成器的cookie设置，将传入的值添加到响应中的Cookie中
			addCookie(response,
					(locale != null ? toLocaleValue(locale) : "-") + (timeZone != null ? '/' + timeZone.getID() : ""));
		}
		else {
			removeCookie(response);
		}
		request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME,
				(locale != null ? locale : determineDefaultLocale(request)));
		request.setAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME,
				(timeZone != null ? timeZone : determineDefaultTimeZone(request)));
	}


	/**
	 * 解析传入的localeValue值，转为Local
	 * @param localeValue
	 * @return
	 */
	@Nullable
	protected Locale parseLocaleValue(String localeValue) {
		return StringUtils.parseLocale(localeValue);
	}

	/**
	 * Render the given locale as a text value for inclusion in a cookie.
	 * <p>The default implementation calls {@link Locale#toString()}
	 * or JDK 7's {@link Locale#toLanguageTag()}, depending on the
	 * {@link #setLanguageTagCompliant "languageTagCompliant"} configuration property.
	 * @param locale the locale to stringify
	 * @return a String representation for the given locale
	 * @since 4.3
	 * @see #isLanguageTagCompliant()
	 */
	protected String toLocaleValue(Locale locale) {
		return (isLanguageTagCompliant() ? locale.toLanguageTag() : locale.toString());
	}

	/**
	 * 返回的默认语言环境
	 * @param request
	 * @return
	 */
	protected Locale determineDefaultLocale(HttpServletRequest request) {
		Locale defaultLocale = getDefaultLocale();
		if (defaultLocale == null) {
			defaultLocale = request.getLocale();
		}
		return defaultLocale;
	}

	/**
	 * 返回默认的时区
	 * @param request
	 * @return
	 */
	@Nullable
	protected TimeZone determineDefaultTimeZone(HttpServletRequest request) {
		return getDefaultTimeZone();
	}

}
