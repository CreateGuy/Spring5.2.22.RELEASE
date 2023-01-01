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

package org.springframework.web.method.annotation;

import java.util.Collections;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;

/**
 * Adds initialization to a WebDataBinder via {@code @InitBinder} methods.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class InitBinderDataBinderFactory extends DefaultDataBinderFactory {

	/**
	 * 符合条件要执行的 @InitBinder 方法
	 */
	private final List<InvocableHandlerMethod> binderMethods;


	/**
	 * Create a new InitBinderDataBinderFactory instance.
	 * @param binderMethods {@code @InitBinder} methods
	 * @param initializer for global data binder initialization
	 */
	public InitBinderDataBinderFactory(@Nullable List<InvocableHandlerMethod> binderMethods,
			@Nullable WebBindingInitializer initializer) {

		super(initializer);
		this.binderMethods = (binderMethods != null ? binderMethods : Collections.emptyList());
	}


	/**
	 * 执行标注了 {@code @InitBinder} 的方法
	 * @param dataBinder the data binder instance to customize
	 * @param request
	 * @throws Exception
	 */
	@Override
	public void initBinder(WebDataBinder dataBinder, NativeWebRequest request) throws Exception {
		for (InvocableHandlerMethod binderMethod : this.binderMethods) {
			// 判断是否需要执行 @InitBinder 方法
			if (isBinderMethodApplicable(binderMethod, dataBinder)) {
				Object returnValue = binderMethod.invokeForRequest(request, null, dataBinder);
				// 要求 @InitBinder 方法返回值必须为Void
				if (returnValue != null) {
					throw new IllegalStateException(
							"@InitBinder methods must not return a value (should be void): " + binderMethod);
				}
			}
		}
	}

	/**
	 * 判断WebDataBinder对应的参数是否应该执行标注了 {@code InitBinder} 的方法
	 * @param initBinderMethod
	 * @param dataBinder
	 * @return
	 */
	protected boolean isBinderMethodApplicable(HandlerMethod initBinderMethod, WebDataBinder dataBinder) {
		InitBinder ann = initBinderMethod.getMethodAnnotation(InitBinder.class);
		Assert.state(ann != null, "No InitBinder annotation");
		String[] names = ann.value();
		return (ObjectUtils.isEmpty(names) || ObjectUtils.containsElement(names, dataBinder.getObjectName()));
	}

}
