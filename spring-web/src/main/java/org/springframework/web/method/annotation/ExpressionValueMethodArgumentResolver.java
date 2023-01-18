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

import javax.servlet.ServletException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 解析 @{@link Value} 的参数解析器
 * <p>eg：@Value("#{systemProperties['java.vm.version']}") String version</p>
 */
public class ExpressionValueMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {

	/**
	 * Create a new {@link ExpressionValueMethodArgumentResolver} instance.
	 * @param beanFactory a bean factory to use for resolving  ${...}
	 * placeholder and #{...} SpEL expressions in default values;
	 * or {@code null} if default values are not expected to contain expressions
	 */
	public ExpressionValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
		super(beanFactory);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(Value.class);
	}

	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		Value ann = parameter.getParameterAnnotation(Value.class);
		Assert.state(ann != null, "No Value annotation");
		return new ExpressionValueNamedValueInfo(ann);
	}

	@Override
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest webRequest) throws Exception {
		// 此参数解析器不依靠此方法进行解析，而是通过 resolveEmbeddedValuesAndExpressions 去解析，所以这里返回空
		return null;
	}

	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
		throw new UnsupportedOperationException("@Value is never required: " + parameter.getMethod());
	}


	private static final class ExpressionValueNamedValueInfo extends NamedValueInfo {

		private ExpressionValueNamedValueInfo(Value annotation) {
			super("@Value", false, annotation.value());
		}
	}

}
