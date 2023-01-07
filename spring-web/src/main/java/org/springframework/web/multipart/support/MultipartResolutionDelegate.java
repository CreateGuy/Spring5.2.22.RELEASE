/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.multipart.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.util.WebUtils;

/**
 * A common delegate for {@code HandlerMethodArgumentResolver} implementations
 * which need to resolve {@link MultipartFile} and {@link Part} arguments.
 *
 * @author Juergen Hoeller
 * @since 4.3
 */
public final class MultipartResolutionDelegate {

	/**
	 * 无效值
	 */
	public static final Object UNRESOLVABLE = new Object();


	private MultipartResolutionDelegate() {
	}


	@Nullable
	public static MultipartRequest resolveMultipartRequest(NativeWebRequest webRequest) {
		MultipartRequest multipartRequest = webRequest.getNativeRequest(MultipartRequest.class);
		if (multipartRequest != null) {
			return multipartRequest;
		}
		HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		if (servletRequest != null && isMultipartContent(servletRequest)) {
			return new StandardMultipartHttpServletRequest(servletRequest);
		}
		return null;
	}

	public static boolean isMultipartRequest(HttpServletRequest request) {
		return (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null ||
				isMultipartContent(request));
	}

	/**
	 * 客户端是否要求服务端以 multipart 进行解析
	 * @param request
	 * @return
	 */
	private static boolean isMultipartContent(HttpServletRequest request) {
		String contentType = request.getContentType();
		return (contentType != null && contentType.toLowerCase().startsWith("multipart/"));
	}

	static MultipartHttpServletRequest asMultipartHttpServletRequest(HttpServletRequest request) {
		MultipartHttpServletRequest unwrapped = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
		if (unwrapped != null) {
			return unwrapped;
		}
		return new StandardMultipartHttpServletRequest(request);
	}

	/**
	 * 是否是 Multipart 参数
	 * @param parameter
	 * @return
	 */
	public static boolean isMultipartArgument(MethodParameter parameter) {
		Class<?> paramType = parameter.getNestedParameterType();
		return (MultipartFile.class == paramType ||
				isMultipartFileCollection(parameter) || isMultipartFileArray(parameter) ||
				(Part.class == paramType || isPartCollection(parameter) || isPartArray(parameter)));
	}

	/**
	 * 如果是 {@code Multipart} 参数，那么就进行解析
	 * @param name
	 * @param parameter
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@Nullable
	public static Object resolveMultipartArgument(String name, MethodParameter parameter, HttpServletRequest request)
			throws Exception {

		MultipartHttpServletRequest multipartRequest =
				WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
		// 客户端是否要求服务端以 multipart 进行解析
		boolean isMultipart = (multipartRequest != null || isMultipartContent(request));

		// 参数是否是 multipart 类型
		if (MultipartFile.class == parameter.getNestedParameterType()) {
			if (multipartRequest == null && isMultipart) {
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			return (multipartRequest != null ? multipartRequest.getFile(name) : null);
		}
		// 如果参数是集合那么泛型是否是 MultipartFile 类型
		else if (isMultipartFileCollection(parameter)) {
			if (multipartRequest == null && isMultipart) {
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			return (multipartRequest != null ? multipartRequest.getFiles(name) : null);
		}
		// 判断参数是数组那么判断组件类型是否是 MultipartFile 类型
		else if (isMultipartFileArray(parameter)) {
			if (multipartRequest == null && isMultipart) {
				multipartRequest = new StandardMultipartHttpServletRequest(request);
			}
			if (multipartRequest != null) {
				List<MultipartFile> multipartFiles = multipartRequest.getFiles(name);
				return multipartFiles.toArray(new MultipartFile[0]);
			}
			else {
				return null;
			}
		}
		// 判断参数是否是 Part 类型
		else if (Part.class == parameter.getNestedParameterType()) {
			return (isMultipart ? request.getPart(name): null);
		}
		// 判断参数是集合那么泛型是否是 Part 类型
		else if (isPartCollection(parameter)) {
			return (isMultipart ? resolvePartList(request, name) : null);
		}
		// 判断参数是数组那么判断组件类型是否是 Part 类型
		else if (isPartArray(parameter)) {
			return (isMultipart ? resolvePartList(request, name).toArray(new Part[0]) : null);
		}
		else {
			return UNRESOLVABLE;
		}
	}

	/**
	 * 判断参数是集合那么泛型是否是 {@code MultipartFile}
	 * @param methodParam
	 * @return
	 */
	private static boolean isMultipartFileCollection(MethodParameter methodParam) {
		return (MultipartFile.class == getCollectionParameterType(methodParam));
	}

	/**
	 * 判断参数是数组那么判断组件类型是否是 {@code MultipartFile}
	 * @param methodParam
	 * @return
	 */
	private static boolean isMultipartFileArray(MethodParameter methodParam) {
		return (MultipartFile.class == methodParam.getNestedParameterType().getComponentType());
	}

	/**
	 * 判断参数是集合那么泛型是否是 {@code Part}
	 * @param methodParam
	 * @return
	 */
	private static boolean isPartCollection(MethodParameter methodParam) {
		return (Part.class == getCollectionParameterType(methodParam));
	}

	/**
	 * 判断参数是数组那么判断组件类型是否是 {@code Part}
	 * @param methodParam
	 * @return
	 */
	private static boolean isPartArray(MethodParameter methodParam) {
		return (Part.class == methodParam.getNestedParameterType().getComponentType());
	}

	/**
	 * 返回集合的类型(泛型)
	 * @param methodParam
	 * @return
	 */
	@Nullable
	private static Class<?> getCollectionParameterType(MethodParameter methodParam) {
		Class<?> paramType = methodParam.getNestedParameterType();
		if (Collection.class == paramType || List.class.isAssignableFrom(paramType)){
			Class<?> valueType = ResolvableType.forMethodParameter(methodParam).asCollection().resolveGeneric();
			if (valueType != null) {
				return valueType;
			}
		}
		return null;
	}

	private static List<Part> resolvePartList(HttpServletRequest request, String name) throws Exception {
		Collection<Part> parts = request.getParts();
		List<Part> result = new ArrayList<>(parts.size());
		for (Part part : parts) {
			if (part.getName().equals(name)) {
				result.add(part);
			}
		}
		return result;
	}

}
