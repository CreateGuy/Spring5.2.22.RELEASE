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

import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * A Servlet-specific {@link ModelAttributeMethodProcessor} that applies data
 * binding through a WebDataBinder of type {@link ServletRequestDataBinder}.
 *
 * <p>Also adds a fall-back strategy to instantiate the model attribute from a
 * URI template variable or from a request parameter if the name matches the
 * model attribute name and there is an appropriate type conversion strategy.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */
public class ServletModelAttributeMethodProcessor extends ModelAttributeMethodProcessor {

	/**
	 * Class constructor.
	 * @param annotationNotRequired if "true", non-simple method arguments and
	 * return values are considered model attributes with or without a
	 * {@code @ModelAttribute} annotation
	 */
	public ServletModelAttributeMethodProcessor(boolean annotationNotRequired) {
		super(annotationNotRequired);
	}


	/**
	 * 获得指定参数的值，然后转换为指定类型
	 */
	@Override
	protected final Object createAttribute(String attributeName, MethodParameter parameter,
			WebDataBinderFactory binderFactory, NativeWebRequest request) throws Exception {

		// 从Rest或者QueryString中获得指定属性
		String value = getRequestValueForAttribute(attributeName, request);
		if (value != null) {
			// 将原参数值转换为指定类型
			Object attribute = createAttributeFromRequestValue(
					value, attributeName, parameter, binderFactory, request);
			if (attribute != null) {
				return attribute;
			}
		}
		// 创建指定的类型的实例
		return super.createAttribute(attributeName, parameter, binderFactory, request);
	}

	/**
	 * 从Rest或者QueryString中获得指定属性
	 * @param attributeName
	 * @param request
	 * @return
	 */
	@Nullable
	protected String getRequestValueForAttribute(String attributeName, NativeWebRequest request) {
		// 获得Rest变量
		Map<String, String> variables = getUriTemplateVariables(request);
		String variableValue = variables.get(attributeName);
		if (StringUtils.hasText(variableValue)) {
			return variableValue;
		}
		// 从请求上拿到指定的QueryString变量
		String parameterValue = request.getParameter(attributeName);
		if (StringUtils.hasText(parameterValue)) {
			return parameterValue;
		}
		return null;
	}

	/**
	 * 获得Rest变量
	 * @param request
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected final Map<String, String> getUriTemplateVariables(NativeWebRequest request) {
		Map<String, String> variables = (Map<String, String>) request.getAttribute(
				HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		return (variables != null ? variables : Collections.emptyMap());
	}

	/**
	 * 将原参数值转换为指定类型
	 * @param sourceValue 源值
	 * @param attributeName 参数名称
	 * @param parameter
	 * @param binderFactory
	 * @param request
	 * @return 转换后的值
	 * @throws Exception
	 */
	@Nullable
	protected Object createAttributeFromRequestValue(String sourceValue, String attributeName,
			MethodParameter parameter, WebDataBinderFactory binderFactory, NativeWebRequest request)
			throws Exception {

		// 创建专属于此参数的数据绑定器
		DataBinder binder = binderFactory.createBinder(request, null, attributeName);
		ConversionService conversionService = binder.getConversionService();
		if (conversionService != null) {
			TypeDescriptor source = TypeDescriptor.valueOf(String.class);
			TypeDescriptor target = new TypeDescriptor(parameter);
			// 看能否有任何一个 ConversionService 能进行转换
			if (conversionService.canConvert(source, target)) {
				return binder.convertIfNecessary(sourceValue, parameter.getParameterType(), parameter);
			}
		}
		return null;
	}

	/**
	 * This implementation downcasts {@link WebDataBinder} to
	 * {@link ServletRequestDataBinder} before binding.
	 * @see ServletRequestDataBinderFactory
	 */
	@Override
	protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
		ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
		Assert.state(servletRequest != null, "No ServletRequest");
		ServletRequestDataBinder servletBinder = (ServletRequestDataBinder) binder;
		servletBinder.bind(servletRequest);
	}

}
