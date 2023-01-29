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

package org.springframework.web.servlet.config.annotation;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * SpringMvc配置类
 * <p>Defines callback methods to customize the Java-based configuration for
 *  Spring MVC enabled via {@code @EnableWebMvc}.</p>
 * <p>{@code @EnableWebMvc}-annotated configuration classes may implement
 * this interface to be called back and given a chance to customize the
 * default configuration.
 *
 * @author Rossen Stoyanchev
 * @author Keith Donald
 * @author David Syer
 * @since 3.1
 */
public interface WebMvcConfigurer {

	/**
	 * <p>配置一些访问路径的东西，比如项目路径前缀，是否忽略尾部斜杠</p>
	 * Helps with configuring HandlerMappings path matching options such as trailing slash match,
	 * suffix registration, path matcher and path helper.
	 * Configured path matcher and path helper instances are shared for:
	 * <ul>
	 * <li>RequestMappings</li>
	 * <li>ViewControllerMappings</li>
	 * <li>ResourcesMappings</li>
	 * </ul>
	 * @since 4.0.3
	 */
	default void configurePathMatch(PathMatchConfigurer configurer) {
	}

	/**
	 * 配置内容协商属性
	 */
	default void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
	}

	/**
	 * 配置异步请求处理选项
	 */
	default void configureAsyncSupport(AsyncSupportConfigurer configurer) {
	}

	/**
	 * Configure a handler to delegate unhandled requests by forwarding to the
	 * Servlet container's "default" servlet. A common use case for this is when
	 * the {@link DispatcherServlet} is mapped to "/" thus overriding the
	 * Servlet container's default handling of static resources.
	 */
	default void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
	}

	/**
	 * 添加 {@link Converter Converters} and {@link Formatter Formatters} 到 {@link FormatterRegistry}
	 * <p>在SpringBoot的情况下这个也会被添加到 {@link WebConversionService}中</p>
	 */
	default void addFormatters(FormatterRegistry registry) {
	}

	/**
	 * 添加 Spring MVC 的拦截器
	 * <p>后续会被添加到 {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping} 中</p>
	 */
	default void addInterceptors(InterceptorRegistry registry) {
	}

	/**
	 * Add handlers to serve static resources such as images, js, and, css
	 * files from specific locations under web application root, the classpath,
	 * and others.
	 */
	default void addResourceHandlers(ResourceHandlerRegistry registry) {
	}

	/**
	 * 配置跨源规则
	 * @since 4.2
	 */
	default void addCorsMappings(CorsRegistry registry) {
	}

	/**
	 * 配置使用响应状态代码和/或视图预配置的简单自动控制器，以呈现响应体。
	 */
	default void addViewControllers(ViewControllerRegistry registry) {
	}

	/**
	 * 配置视图解析器，将控制器返回的字符串类型的视图名称转换为具体的 {@link org.springframework.web.servlet.View}
	 */
	default void configureViewResolvers(ViewResolverRegistry registry) {
	}

	/**
	 * 添加参数解析器
	 * <p>但这不会覆盖框架内部的，可以直接操作 {@link RequestMappingHandlerAdapter}</p>
	 */
	default void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
	}

	/**
	 * 添加返回值处理器
	 * <p>但这不会覆盖框架内部的，可以直接操作 {@link RequestMappingHandlerAdapter}</p>
	 */
	default void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
	}

	/**
	 * 配置 {@link HttpMessageConverter}
	 */
	default void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * 钩子方法，用于在配置后扩展或修改 {@link HttpMessageConverter} 列表
	 * @param converters 已经注册好的 {@link HttpMessageConverter} 列表
	 */
	default void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * 配置异常解析器
	 * <p>The given list starts out empty. If it is left empty, the framework
	 * configures a default set of resolvers, see
	 * {@link WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)}.
	 * Or if any exception resolvers are added to the list, then the application
	 * effectively takes over and must provide, fully initialized, exception
	 * resolvers.
	 * <p>Alternatively you can use
	 * {@link #extendHandlerExceptionResolvers(List)} which allows you to extend
	 * or modify the list of exception resolvers configured by default.
	 * @param resolvers initially an empty list
	 * @see #extendHandlerExceptionResolvers(List)
	 * @see WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)
	 */
	default void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
	}

	/**
	 * 钩子方法，用于在配置后扩展或修改 {@link HandlerExceptionResolver} 列表
	 * @param resolvers the list of configured resolvers to extend
	 * @since 4.3
	 * @see WebMvcConfigurationSupport#addDefaultHandlerExceptionResolvers(List, org.springframework.web.accept.ContentNegotiationManager)
	 */
	default void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
	}

	/**
	 * 配置 {@link Validator}
	 */
	@Nullable
	default Validator getValidator() {
		return null;
	}

	/**
	 * 配置 {@link MessageCodesResolver}
	 */
	@Nullable
	default MessageCodesResolver getMessageCodesResolver() {
		return null;
	}

}
