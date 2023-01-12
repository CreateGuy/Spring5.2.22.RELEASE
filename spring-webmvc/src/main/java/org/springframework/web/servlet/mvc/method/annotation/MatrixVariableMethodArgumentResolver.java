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

package org.springframework.web.servlet.mvc.method.annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingMatrixVariableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.annotation.AbstractNamedValueMethodArgumentResolver;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 解析使用了 @{@link MatrixVariable @MatrixVariable} 的参数
 * <ul>
 *     <li>是解析矩阵值，eg：/request;username=admin;password=123456;age=20</li>
 *     <li>如果方法参数是Map类型，它将被 {@link MatrixVariableMapMethodArgumentResolver}解析</li>
 *     <li>除非注解指定了一个名称，在这种情况下，它被认为是解析出来的参数将会被放到Map类型的单个属性中</li>
 * </ul>
 */
public class MatrixVariableMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver {

	public MatrixVariableMethodArgumentResolver() {
		super(null);
	}

	/**
	 * 此参数解析器只能解析 {@code MatrixVariable} 或者是解析为Map中的单个参数
	 * @param parameter
	 * @return
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		if (!parameter.hasParameterAnnotation(MatrixVariable.class)) {
			return false;
		}
		// 如果是Map看泛型对应是否有指定@MatrixVariable
		if (Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
			MatrixVariable matrixVariable = parameter.getParameterAnnotation(MatrixVariable.class);
			return (matrixVariable != null && StringUtils.hasText(matrixVariable.name()));
		}
		return true;
	}

	/**
	 * 创建对应的 {@code NamedValueInfo}
	 * @param parameter
	 * @return
	 */
	@Override
	protected NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
		MatrixVariable ann = parameter.getParameterAnnotation(MatrixVariable.class);
		Assert.state(ann != null, "No MatrixVariable annotation");
		return new MatrixVariableNamedValueInfo(ann);
	}

	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	protected Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
		// 拿到通过 RequestMappingHandlerMapping 设置过的矩阵变量值
		Map<String, MultiValueMap<String, String>> pathParameters = (Map<String, MultiValueMap<String, String>>)
				request.getAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (CollectionUtils.isEmpty(pathParameters)) {
			return null;
		}

		MatrixVariable ann = parameter.getParameterAnnotation(MatrixVariable.class);
		Assert.state(ann != null, "No MatrixVariable annotation");
		String pathVar = ann.pathVar();
		List<String> paramValues = null;

		// 如果指定的参数的位置的话，从指定位置读取值，比如说user.id
		if (!pathVar.equals(ValueConstants.DEFAULT_NONE)) {
			if (pathParameters.containsKey(pathVar)) {
				paramValues = pathParameters.get(pathVar).get(name);
			}
		}
		else {
			boolean found = false;
			paramValues = new ArrayList<>();
			for (MultiValueMap<String, String> params : pathParameters.values()) {
				if (params.containsKey(name)) {
					// 如果出现了重复的参数名
					if (found) {
						String paramType = parameter.getNestedParameterType().getName();
						throw new ServletRequestBindingException(
								"Found more than one match for URI path parameter '" + name +
								"' for parameter type [" + paramType + "]. Use 'pathVar' attribute to disambiguate.");
					}
					paramValues.addAll(params.get(name));
					found = true;
				}
			}
		}

		if (CollectionUtils.isEmpty(paramValues)) {
			return null;
		}
		else if (paramValues.size() == 1) {
			return paramValues.get(0);
		}
		else {
			return paramValues;
		}
	}

	/**
	 * 未找到具体参数值，就直接抛出异常
	 * @param name
	 * @param parameter
	 * @throws ServletRequestBindingException
	 */
	@Override
	protected void handleMissingValue(String name, MethodParameter parameter) throws ServletRequestBindingException {
		throw new MissingMatrixVariableException(name, parameter);
	}

	/**
	 * 此参数解析器对应的 {@link NamedValueInfo}
	 */
	private static final class MatrixVariableNamedValueInfo extends NamedValueInfo {

		private MatrixVariableNamedValueInfo(MatrixVariable annotation) {
			super(annotation.name(), annotation.required(), annotation.defaultValue());
		}
	}

}
