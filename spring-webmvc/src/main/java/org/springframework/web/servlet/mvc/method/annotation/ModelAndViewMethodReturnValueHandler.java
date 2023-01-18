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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.View;

/**
 * 处理 {@link ModelAndView}类型的返回值，将视图和模型复制到 {@link ModelAndViewContainer} 中
 *
 * <p>If the return value is {@code null}, the
 * {@link ModelAndViewContainer#setRequestHandled(boolean)} flag is set to
 * {@code true} to indicate the request was handled directly.
 *
 * <p>A {@link ModelAndView} return type has a set purpose. Therefore this
 * handler should be configured ahead of handlers that support any return
 * value type annotated with {@code @ModelAttribute} or {@code @ResponseBody}
 * to ensure they don't take over.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class ModelAndViewMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

	/**
	 * 判断是否是重定向视图的表达式
	 */
	@Nullable
	private String[] redirectPatterns;


	/**
	 * Configure one more simple patterns (as described in {@link PatternMatchUtils#simpleMatch})
	 * to use in order to recognize custom redirect prefixes in addition to "redirect:".
	 * <p>Note that simply configuring this property will not make a custom redirect prefix work.
	 * There must be a custom {@link View} that recognizes the prefix as well.
	 * @since 4.1
	 */
	public void setRedirectPatterns(@Nullable String... redirectPatterns) {
		this.redirectPatterns = redirectPatterns;
	}

	/**
	 * Return the configured redirect patterns, if any.
	 * @since 4.1
	 */
	@Nullable
	public String[] getRedirectPatterns() {
		return this.redirectPatterns;
	}


	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return ModelAndView.class.isAssignableFrom(returnType.getParameterType());
	}

	/**
	 * 将返回的 {@code ModelAndView} 的参数填充进 {@code ModelAndViewContainer} 中
	 * @param returnValue the value returned from the handler method
	 * @param returnType the type of the return value. This type must have
	 * previously been passed to {@link #supportsReturnType} which must
	 * have returned {@code true}.
	 * @param mavContainer the ModelAndViewContainer for the current request
	 * @param webRequest the current request
	 * @throws Exception
	 */
	@Override
	public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		if (returnValue == null) {
			// 表明请求已经完成
			mavContainer.setRequestHandled(true);
			return;
		}

		ModelAndView mav = (ModelAndView) returnValue;
		// 是否使用视图
		if (mav.isReference()) {
			String viewName = mav.getViewName();
			mavContainer.setViewName(viewName);
			if (viewName != null && isRedirectViewName(viewName)) {
				mavContainer.setRedirectModelScenario(true);
			}
		}
		else {
			View view = mav.getView();
			mavContainer.setView(view);
			if (view instanceof SmartView && ((SmartView) view).isRedirectView()) {
				mavContainer.setRedirectModelScenario(true);
			}
		}
		mavContainer.setStatus(mav.getStatus());
		mavContainer.addAllAttributes(mav.getModel());
	}

	/**
	 * 是否是重定向视图名，规则如下：
	 * <ol>
	 *     <li>以某种表达式去匹配</li>
	 *     <li>视图名直接以 redirect: 开头</li>
	 * </ol>
	 */
	protected boolean isRedirectViewName(String viewName) {
		return (PatternMatchUtils.simpleMatch(this.redirectPatterns, viewName) || viewName.startsWith("redirect:"));
	}

}
