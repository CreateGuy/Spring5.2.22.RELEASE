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

package org.springframework.web.servlet.config.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import javax.servlet.ServletContext;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.JsonbHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PathMatcher;
import org.springframework.validation.Errors;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.Validator;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.method.support.CompositeUriComponentsContributor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.function.support.HandlerFunctionAdapter;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor;
import org.springframework.web.servlet.handler.HandlerExceptionResolverComposite;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.JsonViewRequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.JsonViewResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.ViewResolverComposite;
import org.springframework.web.util.UrlPathHelper;

/**
 * This is the main class providing the configuration behind the MVC Java config.
 * It is typically imported by adding {@link EnableWebMvc @EnableWebMvc} to an
 * application {@link Configuration @Configuration} class. An alternative more
 * advanced option is to extend directly from this class and override methods as
 * necessary, remembering to add {@link Configuration @Configuration} to the
 * subclass and {@link Bean @Bean} to overridden {@link Bean @Bean} methods.
 * For more details see the javadoc of {@link EnableWebMvc @EnableWebMvc}.
 *
 * <p>This class registers the following {@link HandlerMapping HandlerMappings}:</p>
 * <ul>
 * <li>{@link RequestMappingHandlerMapping}
 * ordered at 0 for mapping requests to annotated controller methods.
 * <li>{@link HandlerMapping}
 * ordered at 1 to map URL paths directly to view names.
 * <li>{@link BeanNameUrlHandlerMapping}
 * ordered at 2 to map URL paths to controller bean names.
 * <li>{@link HandlerMapping}
 * ordered at {@code Integer.MAX_VALUE-1} to serve static resource requests.
 * <li>{@link HandlerMapping}
 * ordered at {@code Integer.MAX_VALUE} to forward requests to the default servlet.
 * </ul>
 *
 * <p>Registers these {@link HandlerAdapter HandlerAdapters}:
 * <ul>
 * <li>{@link RequestMappingHandlerAdapter}
 * for processing requests with annotated controller methods.
 * <li>{@link HttpRequestHandlerAdapter}
 * for processing requests with {@link HttpRequestHandler HttpRequestHandlers}.
 * <li>{@link SimpleControllerHandlerAdapter}
 * for processing requests with interface-based {@link Controller Controllers}.
 * </ul>
 *
 * <p>Registers a {@link HandlerExceptionResolverComposite} with this chain of
 * exception resolvers:
 * <ul>
 * <li>{@link ExceptionHandlerExceptionResolver} for handling exceptions through
 * {@link org.springframework.web.bind.annotation.ExceptionHandler} methods.
 * <li>{@link ResponseStatusExceptionResolver} for exceptions annotated with
 * {@link org.springframework.web.bind.annotation.ResponseStatus}.
 * <li>{@link DefaultHandlerExceptionResolver} for resolving known Spring
 * exception types
 * </ul>
 *
 * <p>Registers an {@link AntPathMatcher} and a {@link UrlPathHelper}
 * to be used by:
 * <ul>
 * <li>the {@link RequestMappingHandlerMapping},
 * <li>the {@link HandlerMapping} for ViewControllers
 * <li>and the {@link HandlerMapping} for serving resources
 * </ul>
 * Note that those beans can be configured with a {@link PathMatchConfigurer}.
 *
 * <p>Both the {@link RequestMappingHandlerAdapter} and the
 * {@link ExceptionHandlerExceptionResolver} are configured with default
 * instances of the following by default:
 * <ul>
 * <li>a {@link ContentNegotiationManager}
 * <li>a {@link DefaultFormattingConversionService}
 * <li>an {@link org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean}
 * if a JSR-303 implementation is available on the classpath
 * <li>a range of {@link HttpMessageConverter HttpMessageConverters} depending on the third-party
 * libraries available on the classpath.
 * </ul>
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Sebastien Deleuze
 * @since 3.1
 * @see EnableWebMvc
 * @see WebMvcConfigurer
 */
public class WebMvcConfigurationSupport implements ApplicationContextAware, ServletContextAware {

	/**
	 * 下面的参数都是有关媒体类型映射关系的标志位
	 */
	private static final boolean romePresent;

	private static final boolean jaxb2Present;

	private static final boolean jackson2Present;

	private static final boolean jackson2XmlPresent;

	private static final boolean jackson2SmilePresent;

	private static final boolean jackson2CborPresent;

	private static final boolean gsonPresent;

	private static final boolean jsonbPresent;

	/**
	 * 初始化
	 */
	static {
		ClassLoader classLoader = WebMvcConfigurationSupport.class.getClassLoader();
		romePresent = ClassUtils.isPresent("com.rometools.rome.feed.WireFeed", classLoader);
		jaxb2Present = ClassUtils.isPresent("javax.xml.bind.Binder", classLoader);
		jackson2Present = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
				ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);
		jackson2XmlPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper", classLoader);
		jackson2SmilePresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory", classLoader);
		jackson2CborPresent = ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory", classLoader);
		gsonPresent = ClassUtils.isPresent("com.google.gson.Gson", classLoader);
		jsonbPresent = ClassUtils.isPresent("javax.json.bind.Jsonb", classLoader);
	}


	@Nullable
	private ApplicationContext applicationContext;

	@Nullable
	private ServletContext servletContext;

	/**
	 * 拦截器列表
	 */
	@Nullable
	private List<Object> interceptors;

	/**
	 * 路径匹配器配置类
	 */
	@Nullable
	private PathMatchConfigurer pathMatchConfigurer;

	/**
	 * 内容协商管理器
	 */
	@Nullable
	private ContentNegotiationManager contentNegotiationManager;

	/**
	 * 自定义参数解析器
	 */
	@Nullable
	private List<HandlerMethodArgumentResolver> argumentResolvers;

	/**
	 * 自定义返回值处理器
	 */
	@Nullable
	private List<HandlerMethodReturnValueHandler> returnValueHandlers;

	/**
	 * 自定义 {@link HttpMessageConverter}
	 */
	@Nullable
	private List<HttpMessageConverter<?>> messageConverters;

	/**
	 * 自定义Cors数据源
	 */
	@Nullable
	private Map<String, CorsConfiguration> corsConfigurations;


	/**
	 * Set the Spring {@link ApplicationContext}, e.g. for resource loading.
	 */
	@Override
	public void setApplicationContext(@Nullable ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * Return the associated Spring {@link ApplicationContext}.
	 * @since 4.2
	 */
	@Nullable
	public final ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	/**
	 * Set the {@link javax.servlet.ServletContext}, e.g. for resource handling,
	 * looking up file extensions, etc.
	 */
	@Override
	public void setServletContext(@Nullable ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	/**
	 * Return the associated {@link javax.servlet.ServletContext}.
	 * @since 4.2
	 */
	@Nullable
	public final ServletContext getServletContext() {
		return this.servletContext;
	}


	/**
	 * 注册 {@link RequestMappingHandlerMapping}
	 */
	@Bean
	@SuppressWarnings("deprecation")
	public RequestMappingHandlerMapping requestMappingHandlerMapping(
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager,
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {

		RequestMappingHandlerMapping mapping = createRequestMappingHandlerMapping();
		// 表明当前HandlerMapping是在最前面的
		mapping.setOrder(0);
		// 注册能拦截器
		mapping.setInterceptors(getInterceptors(conversionService, resourceUrlProvider));
		// 设置内容协商管理器
		mapping.setContentNegotiationManager(contentNegotiationManager);
		// 设置Cors配置源
		mapping.setCorsConfigurations(getCorsConfigurations());

		PathMatchConfigurer configurer = getPathMatchConfigurer();

		// 是否开启后缀匹配
		Boolean useSuffixPatternMatch = configurer.isUseSuffixPatternMatch();
		if (useSuffixPatternMatch != null) {
			mapping.setUseSuffixPatternMatch(useSuffixPatternMatch);
		}
		Boolean useRegisteredSuffixPatternMatch = configurer.isUseRegisteredSuffixPatternMatch();
		if (useRegisteredSuffixPatternMatch != null) {
			mapping.setUseRegisteredSuffixPatternMatch(useRegisteredSuffixPatternMatch);
		}

		// 是否匹配url，而不考虑后面是否有斜杠
		Boolean useTrailingSlashMatch = configurer.isUseTrailingSlashMatch();
		if (useTrailingSlashMatch != null) {
			mapping.setUseTrailingSlashMatch(useTrailingSlashMatch);
		}

		UrlPathHelper pathHelper = configurer.getUrlPathHelper();
		if (pathHelper != null) {
			mapping.setUrlPathHelper(pathHelper);
		}

		// 设置路径匹配器
		PathMatcher pathMatcher = configurer.getPathMatcher();
		if (pathMatcher != null) {
			mapping.setPathMatcher(pathMatcher);
		}

		// 设置路径前缀
		Map<String, Predicate<Class<?>>> pathPrefixes = configurer.getPathPrefixes();
		if (pathPrefixes != null) {
			mapping.setPathPrefixes(pathPrefixes);
		}

		return mapping;
	}

	/**
	 * Protected method for plugging in a custom subclass of
	 * {@link RequestMappingHandlerMapping}.
	 * @since 4.0
	 */
	protected RequestMappingHandlerMapping createRequestMappingHandlerMapping() {
		return new RequestMappingHandlerMapping();
	}

	/**
	 * 注册能拦截器
	 * {@link HandlerMapping} instances with.
	 * <p>This method cannot be overridden; use {@link #addInterceptors} instead.
	 */
	protected final Object[] getInterceptors(
			FormattingConversionService mvcConversionService,
			ResourceUrlProvider mvcResourceUrlProvider) {
		if (this.interceptors == null) {
			InterceptorRegistry registry = new InterceptorRegistry();
			// 添加自定义拦截器
			addInterceptors(registry);
			// 默认的拦截器
			registry.addInterceptor(new ConversionServiceExposingInterceptor(mvcConversionService));
			registry.addInterceptor(new ResourceUrlProviderExposingInterceptor(mvcResourceUrlProvider));
			this.interceptors = registry.getInterceptors();
		}
		return this.interceptors.toArray();
	}

	/**
	 * Override this method to add Spring MVC interceptors for
	 * pre- and post-processing of controller invocation.
	 * @see InterceptorRegistry
	 */
	protected void addInterceptors(InterceptorRegistry registry) {
	}

	/**
	 * Callback for building the {@link PathMatchConfigurer}.
	 * Delegates to {@link #configurePathMatch}.
	 * @since 4.1
	 */
	protected PathMatchConfigurer getPathMatchConfigurer() {
		if (this.pathMatchConfigurer == null) {
			this.pathMatchConfigurer = new PathMatchConfigurer();
			// 自定义配置
			configurePathMatch(this.pathMatchConfigurer);
		}
		return this.pathMatchConfigurer;
	}

	/**
	 * Override this method to configure path matching options.
	 * @since 4.0.3
	 * @see PathMatchConfigurer
	 */
	protected void configurePathMatch(PathMatchConfigurer configurer) {
	}

	/**
	 * Return a global {@link PathMatcher} instance for path matching
	 * patterns in {@link HandlerMapping HandlerMappings}.
	 * This instance can be configured using the {@link PathMatchConfigurer}
	 * in {@link #configurePathMatch(PathMatchConfigurer)}.
	 * @since 4.1
	 */
	@Bean
	public PathMatcher mvcPathMatcher() {
		PathMatcher pathMatcher = getPathMatchConfigurer().getPathMatcher();
		return (pathMatcher != null ? pathMatcher : new AntPathMatcher());
	}

	/**
	 * Return a global {@link UrlPathHelper} instance for path matching
	 * patterns in {@link HandlerMapping HandlerMappings}.
	 * This instance can be configured using the {@link PathMatchConfigurer}
	 * in {@link #configurePathMatch(PathMatchConfigurer)}.
	 * @since 4.1
	 */
	@Bean
	public UrlPathHelper mvcUrlPathHelper() {
		UrlPathHelper pathHelper = getPathMatchConfigurer().getUrlPathHelper();
		return (pathHelper != null ? pathHelper : new UrlPathHelper());
	}

	/**
	 * 注入内容协商管理器
	 */
	@Bean
	public ContentNegotiationManager mvcContentNegotiationManager() {
		if (this.contentNegotiationManager == null) {
			ContentNegotiationConfigurer configurer = new ContentNegotiationConfigurer(this.servletContext);
			// 设置默认的媒体类型映射关系
			configurer.mediaTypes(getDefaultMediaTypes());
			// 自定义配置
			configureContentNegotiation(configurer);
			// 创建内容协商管理器，包括内部的协商策略
			this.contentNegotiationManager = configurer.buildContentNegotiationManager();
		}
		return this.contentNegotiationManager;
	}

	/**
	 * 获得默认的文件扩展名到媒体类型的映射关系
	 * <ul>
	 *     <li>默认也只有xml和json</li>
	 * </ul>
	 * @return
	 */
	protected Map<String, MediaType> getDefaultMediaTypes() {
		Map<String, MediaType> map = new HashMap<>(4);
		if (romePresent) {
			map.put("atom", MediaType.APPLICATION_ATOM_XML);
			map.put("rss", MediaType.APPLICATION_RSS_XML);
		}
		if (jaxb2Present || jackson2XmlPresent) {
			map.put("xml", MediaType.APPLICATION_XML);
		}
		if (jackson2Present || gsonPresent || jsonbPresent) {
			map.put("json", MediaType.APPLICATION_JSON);
		}
		if (jackson2SmilePresent) {
			map.put("smile", MediaType.valueOf("application/x-jackson-smile"));
		}
		if (jackson2CborPresent) {
			map.put("cbor", MediaType.APPLICATION_CBOR);
		}
		return map;
	}

	/**
	 * Override this method to configure content negotiation.
	 * @see DefaultServletHandlerConfigurer
	 */
	protected void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
	}

	/**
	 * 返回一个顺序为1的 {@link HandlerMapping}，是将URL路径直接映射到视图名称
	 */
	@Bean
	@Nullable
	public HandlerMapping viewControllerHandlerMapping(
			@Qualifier("mvcPathMatcher") PathMatcher pathMatcher,
			@Qualifier("mvcUrlPathHelper") UrlPathHelper urlPathHelper,
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {
		ViewControllerRegistry registry = new ViewControllerRegistry(this.applicationContext);
		addViewControllers(registry);

		// 构造 SimpleUrlHandlerMapping
		AbstractHandlerMapping handlerMapping = registry.buildHandlerMapping();
		if (handlerMapping == null) {
			return null;
		}

		// 配置属性
		handlerMapping.setPathMatcher(pathMatcher);
		handlerMapping.setUrlPathHelper(urlPathHelper);
		handlerMapping.setInterceptors(getInterceptors(conversionService, resourceUrlProvider));
		handlerMapping.setCorsConfigurations(getCorsConfigurations());
		return handlerMapping;
	}

	/**
	 * Override this method to add view controllers.
	 * @see ViewControllerRegistry
	 */
	protected void addViewControllers(ViewControllerRegistry registry) {
	}

	/**
	 * 注入一个 {@link BeanNameUrlHandlerMapping}
	 */
	@Bean
	public BeanNameUrlHandlerMapping beanNameHandlerMapping(
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {

		BeanNameUrlHandlerMapping mapping = new BeanNameUrlHandlerMapping();
		// 指定位置
		mapping.setOrder(2);
		// 注册能拦截器
		mapping.setInterceptors(getInterceptors(conversionService, resourceUrlProvider));
		// 注册Cors配置源
		mapping.setCorsConfigurations(getCorsConfigurations());
		return mapping;
	}

	/**
	 * 注入 {@link RouterFunctionMapping} 实例
	 * {@linkplain org.springframework.web.servlet.function.RouterFunction router functions}.
	 * Consider overriding one of these other more fine-grained methods:
	 * <ul>
	 * <li>{@link #addInterceptors} for adding handler interceptors.
	 * <li>{@link #addCorsMappings} to configure cross origin requests processing.
	 * <li>{@link #configureMessageConverters} for adding custom message converters.
	 * </ul>
	 * @since 5.2
	 */
	@Bean
	public RouterFunctionMapping routerFunctionMapping(
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {

		RouterFunctionMapping mapping = new RouterFunctionMapping();
		// 确定此 HandlerMapping 的顺序
		mapping.setOrder(3);
		// 注册拦截器
		mapping.setInterceptors(getInterceptors(conversionService, resourceUrlProvider));
		// 注册Cors数据源
		mapping.setCorsConfigurations(getCorsConfigurations());
		// 注册消息转换器
		mapping.setMessageConverters(getMessageConverters());
		return mapping;
	}

	/**
	 * 注入一个 SimpleUrlHandlerMapping。是为资源服务的
	 */
	@Bean
	@Nullable
	public HandlerMapping resourceHandlerMapping(
			@Qualifier("mvcUrlPathHelper") UrlPathHelper urlPathHelper,
			@Qualifier("mvcPathMatcher") PathMatcher pathMatcher,
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager,
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider) {

		Assert.state(this.applicationContext != null, "No ApplicationContext set");
		Assert.state(this.servletContext != null, "No ServletContext set");

		ResourceHandlerRegistry registry = new ResourceHandlerRegistry(this.applicationContext,
				this.servletContext, contentNegotiationManager, urlPathHelper);
		addResourceHandlers(registry);

		AbstractHandlerMapping handlerMapping = registry.getHandlerMapping();
		if (handlerMapping == null) {
			return null;
		}
		handlerMapping.setPathMatcher(pathMatcher);
		handlerMapping.setUrlPathHelper(urlPathHelper);
		handlerMapping.setInterceptors(getInterceptors(conversionService, resourceUrlProvider));
		handlerMapping.setCorsConfigurations(getCorsConfigurations());
		return handlerMapping;
	}

	/**
	 * Override this method to add resource handlers for serving static resources.
	 * @see ResourceHandlerRegistry
	 */
	protected void addResourceHandlers(ResourceHandlerRegistry registry) {
	}

	/**
	 * A {@link ResourceUrlProvider} bean for use with the MVC dispatcher.
	 * @since 4.1
	 */
	@Bean
	public ResourceUrlProvider mvcResourceUrlProvider() {
		ResourceUrlProvider urlProvider = new ResourceUrlProvider();
		UrlPathHelper pathHelper = getPathMatchConfigurer().getUrlPathHelper();
		if (pathHelper != null) {
			urlProvider.setUrlPathHelper(pathHelper);
		}
		PathMatcher pathMatcher = getPathMatchConfigurer().getPathMatcher();
		if (pathMatcher != null) {
			urlProvider.setPathMatcher(pathMatcher);
		}
		return urlProvider;
	}

	/**
	 * 貌似是注入用于处理 "/" 的 {@link HandlerMapping}
	 * <p>默认返回值是空的</p>
	 */
	@Bean
	@Nullable
	public HandlerMapping defaultServletHandlerMapping() {
		Assert.state(this.servletContext != null, "No ServletContext set");
		DefaultServletHandlerConfigurer configurer = new DefaultServletHandlerConfigurer(this.servletContext);
		configureDefaultServletHandling(configurer);
		return configurer.buildHandlerMapping();
	}

	/**
	 * Override this method to configure "default" Servlet handling.
	 * @see DefaultServletHandlerConfigurer
	 */
	protected void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
	}

	/**
	 * 注入 {@link RequestMappingHandlerAdapter}
	 * <ul>
	 * <li>{@link #addArgumentResolvers} for adding custom argument resolvers.
	 * <li>{@link #addReturnValueHandlers} for adding custom return value handlers.
	 * <li>{@link #configureMessageConverters} for adding custom message converters.
	 * </ul>
	 */
	@Bean
	public RequestMappingHandlerAdapter requestMappingHandlerAdapter(
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager,
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("mvcValidator") Validator validator) {

		// 创建RequestMappingHandlerAdapter
		RequestMappingHandlerAdapter adapter = createRequestMappingHandlerAdapter();
		// 设置内容协商管理器
		adapter.setContentNegotiationManager(contentNegotiationManager);
		// 设置消息转换器
		adapter.setMessageConverters(getMessageConverters());
		// 设置WebDataBinder初始化器
		adapter.setWebBindingInitializer(getConfigurableWebBindingInitializer(conversionService, validator));
		// 设置自定义参数解析器
		adapter.setCustomArgumentResolvers(getArgumentResolvers());
		// 设置自定义返回值处理器
		adapter.setCustomReturnValueHandlers(getReturnValueHandlers());

		// 当 ObjectMapper 可以加载时候注册 Advice
		if (jackson2Present) {
			adapter.setRequestBodyAdvice(Collections.singletonList(new JsonViewRequestBodyAdvice()));
			adapter.setResponseBodyAdvice(Collections.singletonList(new JsonViewResponseBodyAdvice()));
		}

		AsyncSupportConfigurer configurer = new AsyncSupportConfigurer();
		// 配置 异步管理器配置类
		configureAsyncSupport(configurer);

		// 设置异步线的程线程池
		if (configurer.getTaskExecutor() != null) {
			adapter.setTaskExecutor(configurer.getTaskExecutor());
		}

		// 设置异步线程超时时间
		if (configurer.getTimeout() != null) {
			adapter.setAsyncRequestTimeout(configurer.getTimeout());
		}

		// 设置拦截器(回调方法)
		adapter.setCallableInterceptors(configurer.getCallableInterceptors());
		adapter.setDeferredResultInterceptors(configurer.getDeferredResultInterceptors());

		return adapter;
	}

	/**
	 * 创建 {@link RequestMappingHandlerAdapter}.
	 */
	protected RequestMappingHandlerAdapter createRequestMappingHandlerAdapter() {
		return new RequestMappingHandlerAdapter();
	}

	/**
	 * Returns a {@link HandlerFunctionAdapter} for processing requests through
	 * {@linkplain org.springframework.web.servlet.function.HandlerFunction handler functions}.
	 * @since 5.2
	 */
	@Bean
	public HandlerFunctionAdapter handlerFunctionAdapter() {
		return new HandlerFunctionAdapter();
	}

	/**
	 * 返回 {@link ConfigurableWebBindingInitializer}
	 */
	protected ConfigurableWebBindingInitializer getConfigurableWebBindingInitializer(
			FormattingConversionService mvcConversionService, Validator mvcValidator) {

		ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
		initializer.setConversionService(mvcConversionService);
		initializer.setValidator(mvcValidator);
		MessageCodesResolver messageCodesResolver = getMessageCodesResolver();
		if (messageCodesResolver != null) {
			initializer.setMessageCodesResolver(messageCodesResolver);
		}
		return initializer;
	}

	/**
	 * Override this method to provide a custom {@link MessageCodesResolver}.
	 */
	@Nullable
	protected MessageCodesResolver getMessageCodesResolver() {
		return null;
	}

	/**
	 * Override this method to configure asynchronous request processing options.
	 * @see AsyncSupportConfigurer
	 */
	protected void configureAsyncSupport(AsyncSupportConfigurer configurer) {
	}

	/**
	 * 注入 a {@link FormattingConversionService}
	 * <p>我看SpringBoot已经使用了 {@code WebConversionService}, 也会执行 addFormatters 方法</p>
	 */
	@Bean
	public FormattingConversionService mvcConversionService() {
		FormattingConversionService conversionService = new DefaultFormattingConversionService();
		addFormatters(conversionService);
		return conversionService;
	}

	/**
	 * Override this method to add custom {@link Converter} and/or {@link Formatter}
	 * delegates to the common {@link FormattingConversionService}.
	 * @see #mvcConversionService()
	 */
	protected void addFormatters(FormatterRegistry registry) {
	}

	/**
	 * Return a global {@link Validator} instance for example for validating
	 * {@code @ModelAttribute} and {@code @RequestBody} method arguments.
	 * Delegates to {@link #getValidator()} first and if that returns {@code null}
	 * checks the classpath for the presence of a JSR-303 implementations
	 * before creating a {@code OptionalValidatorFactoryBean}.If a JSR-303
	 * implementation is not available, a no-op {@link Validator} is returned.
	 */
	@Bean
	public Validator mvcValidator() {
		Validator validator = getValidator();
		if (validator == null) {
			if (ClassUtils.isPresent("javax.validation.Validator", getClass().getClassLoader())) {
				Class<?> clazz;
				try {
					String className = "org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean";
					clazz = ClassUtils.forName(className, WebMvcConfigurationSupport.class.getClassLoader());
				}
				catch (ClassNotFoundException | LinkageError ex) {
					throw new BeanInitializationException("Failed to resolve default validator class", ex);
				}
				validator = (Validator) BeanUtils.instantiateClass(clazz);
			}
			else {
				validator = new NoOpValidator();
			}
		}
		return validator;
	}

	/**
	 * Override this method to provide a custom {@link Validator}.
	 */
	@Nullable
	protected Validator getValidator() {
		return null;
	}

	/**
	 * 返回自定义参数解析器
	 */
	protected final List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		if (this.argumentResolvers == null) {
			this.argumentResolvers = new ArrayList<>();
			// 自定义配置
			addArgumentResolvers(this.argumentResolvers);
		}
		return this.argumentResolvers;
	}

	/**
	 * 注解自定义参数解析器 {@link HandlerMethodArgumentResolver}
	 */
	protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
	}

	/**
	 * 返回自定义返回值处理器
	 */
	protected final List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		if (this.returnValueHandlers == null) {
			this.returnValueHandlers = new ArrayList<>();
			// 自定义配置
			addReturnValueHandlers(this.returnValueHandlers);
		}
		return this.returnValueHandlers;
	}

	/**
	 * Add custom {@link HandlerMethodReturnValueHandler HandlerMethodReturnValueHandlers}
	 * in addition to the ones registered by default.
	 * <p>Custom return value handlers are invoked before built-in ones except for
	 * those that rely on the presence of annotations (e.g. {@code @ResponseBody},
	 * {@code @ModelAttribute}, etc). The latter can be customized by configuring the
	 * {@link RequestMappingHandlerAdapter} directly.
	 * @param returnValueHandlers the list of custom handlers (initially an empty list)
	 */
	protected void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
	}

	/**
	 * 返回 {@link HttpMessageConverter}
	 * used by the {@link RequestMappingHandlerAdapter} and the
	 * {@link ExceptionHandlerExceptionResolver}.
	 * <p>This method cannot be overridden; use {@link #configureMessageConverters} instead.
	 * Also see {@link #addDefaultHttpMessageConverters} for adding default message converters.
	 */
	protected final List<HttpMessageConverter<?>> getMessageConverters() {
		if (this.messageConverters == null) {
			this.messageConverters = new ArrayList<>();
			// 自定义配置
			configureMessageConverters(this.messageConverters);
			if (this.messageConverters.isEmpty()) {
				// 添加默认的
				addDefaultHttpMessageConverters(this.messageConverters);
			}
			// 钩子方法，继续扩展
			extendMessageConverters(this.messageConverters);
		}
		return this.messageConverters;
	}

	/**
	 * Override this method to add custom {@link HttpMessageConverter HttpMessageConverters}
	 * to use with the {@link RequestMappingHandlerAdapter} and the
	 * {@link ExceptionHandlerExceptionResolver}.
	 * <p>Adding converters to the list turns off the default converters that would
	 * otherwise be registered by default. Also see {@link #addDefaultHttpMessageConverters}
	 * for adding default message converters.
	 * @param converters a list to add message converters to (initially an empty list)
	 */
	protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * 钩子方法，用于在配置后扩展或修改 {@link HttpMessageConverter} 列表
	 */
	protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * 添加默认 {@link HttpMessageConverter} 实例添加到给定列表
	 * Subclasses can call this method from {@link #configureMessageConverters}.
	 * @param messageConverters the list to add the default message converters to
	 */
	protected final void addDefaultHttpMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		messageConverters.add(new ByteArrayHttpMessageConverter());
		messageConverters.add(new StringHttpMessageConverter());
		messageConverters.add(new ResourceHttpMessageConverter());
		messageConverters.add(new ResourceRegionHttpMessageConverter());
		try {
			messageConverters.add(new SourceHttpMessageConverter<>());
		}
		catch (Throwable ex) {
			// Ignore when no TransformerFactory implementation is available...
		}
		messageConverters.add(new AllEncompassingFormHttpMessageConverter());

		if (romePresent) {
			messageConverters.add(new AtomFeedHttpMessageConverter());
			messageConverters.add(new RssChannelHttpMessageConverter());
		}

		if (jackson2XmlPresent) {
			Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.xml();
			if (this.applicationContext != null) {
				builder.applicationContext(this.applicationContext);
			}
			messageConverters.add(new MappingJackson2XmlHttpMessageConverter(builder.build()));
		}
		else if (jaxb2Present) {
			messageConverters.add(new Jaxb2RootElementHttpMessageConverter());
		}

		if (jackson2Present) {
			Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
			if (this.applicationContext != null) {
				builder.applicationContext(this.applicationContext);
			}
			messageConverters.add(new MappingJackson2HttpMessageConverter(builder.build()));
		}
		else if (gsonPresent) {
			messageConverters.add(new GsonHttpMessageConverter());
		}
		else if (jsonbPresent) {
			messageConverters.add(new JsonbHttpMessageConverter());
		}

		if (jackson2SmilePresent) {
			Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.smile();
			if (this.applicationContext != null) {
				builder.applicationContext(this.applicationContext);
			}
			messageConverters.add(new MappingJackson2SmileHttpMessageConverter(builder.build()));
		}
		if (jackson2CborPresent) {
			Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.cbor();
			if (this.applicationContext != null) {
				builder.applicationContext(this.applicationContext);
			}
			messageConverters.add(new MappingJackson2CborHttpMessageConverter(builder.build()));
		}
	}

	/**
	 * Return an instance of {@link CompositeUriComponentsContributor} for use with
	 * {@link org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder}.
	 * @since 4.0
	 */
	@Bean
	public CompositeUriComponentsContributor mvcUriComponentsContributor(
			@Qualifier("mvcConversionService") FormattingConversionService conversionService,
			@Qualifier("requestMappingHandlerAdapter") RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
		return new CompositeUriComponentsContributor(
				requestMappingHandlerAdapter.getArgumentResolvers(), conversionService);
	}

	/**
	 * Returns a {@link HttpRequestHandlerAdapter} for processing requests
	 * with {@link HttpRequestHandler HttpRequestHandlers}.
	 */
	@Bean
	public HttpRequestHandlerAdapter httpRequestHandlerAdapter() {
		return new HttpRequestHandlerAdapter();
	}

	/**
	 * Returns a {@link SimpleControllerHandlerAdapter} for processing requests
	 * with interface-based controllers.
	 */
	@Bean
	public SimpleControllerHandlerAdapter simpleControllerHandlerAdapter() {
		return new SimpleControllerHandlerAdapter();
	}

	/**
	 * 注入 {@link HandlerExceptionResolver}
	 */
	@Bean
	public HandlerExceptionResolver handlerExceptionResolver(
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager) {
		List<HandlerExceptionResolver> exceptionResolvers = new ArrayList<>();
		// 自定义配置
		configureHandlerExceptionResolvers(exceptionResolvers);
		if (exceptionResolvers.isEmpty()) {
			// 添加默认的
			addDefaultHandlerExceptionResolvers(exceptionResolvers, contentNegotiationManager);
		}
		// 钩子方法
		extendHandlerExceptionResolvers(exceptionResolvers);

		// 包装为混合类，并且设置为最高级
		HandlerExceptionResolverComposite composite = new HandlerExceptionResolverComposite();
		composite.setOrder(0);
		composite.setExceptionResolvers(exceptionResolvers);
		return composite;
	}

	/**
	 * Override this method to configure the list of
	 * {@link HandlerExceptionResolver HandlerExceptionResolvers} to use.
	 * <p>Adding resolvers to the list turns off the default resolvers that would otherwise
	 * be registered by default. Also see {@link #addDefaultHandlerExceptionResolvers}
	 * that can be used to add the default exception resolvers.
	 * @param exceptionResolvers a list to add exception resolvers to (initially an empty list)
	 */
	protected void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
	}

	/**
	 * Override this method to extend or modify the list of
	 * {@link HandlerExceptionResolver HandlerExceptionResolvers} after it has been configured.
	 * <p>This may be useful for example to allow default resolvers to be registered
	 * and then insert a custom one through this method.
	 * @param exceptionResolvers the list of configured resolvers to extend.
	 * @since 4.3
	 */
	protected void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
	}

	/**
	 * 添加默认的 {@link HandlerExceptionResolver}
	 * <ul>
	 * <li>{@link ExceptionHandlerExceptionResolver} for handling exceptions through
	 * {@link org.springframework.web.bind.annotation.ExceptionHandler} methods.
	 * <li>{@link ResponseStatusExceptionResolver} for exceptions annotated with
	 * {@link org.springframework.web.bind.annotation.ResponseStatus}.
	 * <li>{@link DefaultHandlerExceptionResolver} for resolving known Spring exception types
	 * </ul>
	 */
	protected final void addDefaultHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers,
			ContentNegotiationManager mvcContentNegotiationManager) {

		// 用@ExceptionHandler来处理异常的异常解析器
		ExceptionHandlerExceptionResolver exceptionHandlerResolver = createExceptionHandlerExceptionResolver();
		exceptionHandlerResolver.setContentNegotiationManager(mvcContentNegotiationManager);
		exceptionHandlerResolver.setMessageConverters(getMessageConverters());
		exceptionHandlerResolver.setCustomArgumentResolvers(getArgumentResolvers());
		exceptionHandlerResolver.setCustomReturnValueHandlers(getReturnValueHandlers());
		if (jackson2Present) {
			exceptionHandlerResolver.setResponseBodyAdvice(
					Collections.singletonList(new JsonViewResponseBodyAdvice()));
		}
		if (this.applicationContext != null) {
			exceptionHandlerResolver.setApplicationContext(this.applicationContext);
		}
		exceptionHandlerResolver.afterPropertiesSet();
		exceptionResolvers.add(exceptionHandlerResolver);

		// 用@ResponseStatus来处理异常的异常解析器
		ResponseStatusExceptionResolver responseStatusResolver = new ResponseStatusExceptionResolver();
		responseStatusResolver.setMessageSource(this.applicationContext);
		exceptionResolvers.add(responseStatusResolver);

		exceptionResolvers.add(new DefaultHandlerExceptionResolver());
	}

	/**
	 * Protected method for plugging in a custom subclass of
	 * {@link ExceptionHandlerExceptionResolver}.
	 * @since 4.3
	 */
	protected ExceptionHandlerExceptionResolver createExceptionHandlerExceptionResolver() {
		return new ExceptionHandlerExceptionResolver();
	}

	/**
	 * 注册 a {@link ViewResolverComposite}
	 * By default this resolver is ordered at 0 unless content negotiation view
	 * resolution is used in which case the order is raised to
	 * {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE
	 * Ordered.HIGHEST_PRECEDENCE}.
	 * <p>If no other resolvers are configured,
	 * {@link ViewResolverComposite#resolveViewName(String, Locale)} returns null in order
	 * to allow other potential {@link ViewResolver} beans to resolve views.
	 * @since 4.1
	 */
	@Bean
	public ViewResolver mvcViewResolver(
			@Qualifier("mvcContentNegotiationManager") ContentNegotiationManager contentNegotiationManager) {
		ViewResolverRegistry registry =
				new ViewResolverRegistry(contentNegotiationManager, this.applicationContext);
		// 自定义配置
		configureViewResolvers(registry);

		// 如果用户没有通过 WebMvcConfigurer 设置，那么就从容器中查找
		if (registry.getViewResolvers().isEmpty() && this.applicationContext != null) {
			String[] names = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.applicationContext, ViewResolver.class, true, false);
			if (names.length == 1) {
				registry.getViewResolvers().add(new InternalResourceViewResolver());
			}
		}

		// 创建ViewResolverComposite
		ViewResolverComposite composite = new ViewResolverComposite();
		composite.setOrder(registry.getOrder());
		composite.setViewResolvers(registry.getViewResolvers());
		if (this.applicationContext != null) {
			composite.setApplicationContext(this.applicationContext);
		}
		if (this.servletContext != null) {
			composite.setServletContext(this.servletContext);
		}
		return composite;
	}

	/**
	 * Override this method to configure view resolution.
	 * @see ViewResolverRegistry
	 */
	protected void configureViewResolvers(ViewResolverRegistry registry) {
	}

	/**
	 * 返回Cors配置源
	 */
	protected final Map<String, CorsConfiguration> getCorsConfigurations() {
		if (this.corsConfigurations == null) {
			CorsRegistry registry = new CorsRegistry();
			// 自定义配置
			addCorsMappings(registry);
			this.corsConfigurations = registry.getCorsConfigurations();
		}
		return this.corsConfigurations;
	}

	/**
	 * Override this method to configure cross origin requests processing.
	 * @since 4.2
	 * @see CorsRegistry
	 */
	protected void addCorsMappings(CorsRegistry registry) {
	}

	@Bean
	@Lazy
	public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
		return new HandlerMappingIntrospector();
	}


	private static final class NoOpValidator implements Validator {

		@Override
		public boolean supports(Class<?> clazz) {
			return false;
		}

		@Override
		public void validate(@Nullable Object target, Errors errors) {
		}
	}

}
