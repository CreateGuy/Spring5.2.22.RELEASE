/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.servlet.mvc.annotation;

import java.lang.reflect.Method;

import org.springframework.lang.Nullable;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * 用于将特定的返回值解析为 ModelAndView 对象
 * @see org.springframework.web.servlet.mvc.method.annotation.ModelAndViewResolverMethodReturnValueHandler
 */
public interface ModelAndViewResolver {

	/**
	 * 当解析器不知道如何处理给定的方法参数时返回的ModelAndView对象
	 */
	ModelAndView UNRESOLVED = new ModelAndView();


	ModelAndView resolveModelAndView(Method handlerMethod, Class<?> handlerType,
			@Nullable Object returnValue, ExtendedModelMap implicitModel, NativeWebRequest webRequest);

}
