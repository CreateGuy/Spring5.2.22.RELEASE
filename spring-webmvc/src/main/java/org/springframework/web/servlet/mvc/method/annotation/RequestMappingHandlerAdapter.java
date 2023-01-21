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

package org.springframework.web.servlet.mvc.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ErrorsMethodArgumentResolver;
import org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.annotation.SessionAttributesHandler;
import org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

/**
 * 扩展至 {@link AbstractHandlerMethodAdapter}，支持标注了
 * {@link RequestMapping} 注解的 {@link HandlerMethod HandlerMethods}
 * <li>与 {@link RequestMappingHandlerMapping} 配合使用</li>
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * 筛选 {@link InitBinder @InitBinder} 方法的
	 */
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			AnnotatedElementUtils.hasAnnotation(method, InitBinder.class);

	/**
	 * 筛选有 {@link ModelAttribute} 并且没有 {@link RequestMapping} 的方法
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class) &&
					AnnotatedElementUtils.hasAnnotation(method, ModelAttribute.class));


	/**
	 * 自定义参数解析器
	 */
	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	/**
	 * 最终要使用的参数解析器集合
	 */
	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	/**
	 * 适用于标注了 @InitBinder 注解的的参数解析器
	 */
	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;

	/**
	 * 自定义返回值处理器
	 */
	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	/**
	 * 最终要使用返回值处理器
	 */
	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	/**
	 * 特定返回值的解析器
	 */
	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;

	/**
	 * 内容协商管理器
	 */
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	/**
	 * 用于从HTTP请求和响应转换到HTTP请求和响应的
	 */
	private List<HttpMessageConverter<?>> messageConverters;

	/**
	 * @ControllerAdvice + RequestBodyAdvice的所有bean
	 */
	private final List<Object> requestResponseBodyAdvice = new ArrayList<>();

	/**
	 * 初始化WebDataBinder的回调接口
	 */
	@Nullable
	private WebBindingInitializer webBindingInitializer;

	/**
	 * 支持异步任务的线程池
	 */
	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	/**
	 * 指定并发处理超时的时间量(以毫秒为单位)
	 * 在Servlet 3中，超时开始于主请求处理线程退出后，结束于请求再次被分派，以进一步处理并发产生的结果。如果未设置此值，则使用底层实现的默认超时。
	 */
	@Nullable
	private Long asyncRequestTimeout;

	/**
	 * 异步任务执行前后的回调接口，和下面的区别在于不同的异步任务是执行回调接口
	 * <p>比如说返回值是 WebAsyncTask 那么就是执行此回调接口</p>
	 */
	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	/**
	 * 异步任务执行前后的回调接口
	 */
	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	/**
	 * 有关 Reactive 的
	 */
	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	/**
	 * 默认情况下，默认模型的内容在渲染和重定向场景中都使用
	 * <ul>
	 *     <li>
	 *         将此标志设置为true可以确保在重定向场景中永远不会使用默认模型，即使没有声明RedirectAttributes参数
	 *     </li>
	 *     <li>
	 *         将其设置为false意味着如果控制器方法没有声明RedirectAttributes参数，则在重定向中使用默认模型
	 *     </li>
	 * </ul>
	 */
	private boolean ignoreDefaultModelOnRedirect = false;

	/**
	 * 浏览器缓存的过期时间
	 */
	private int cacheSecondsForSessionAttributeHandlers = 0;

	/**
	 * 是否需要在同步代码块中执行代码
	 */
	private boolean synchronizeOnSession = false;

	/**
	 * 用于读取和保存会话属性的
	 */
	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	/**
	 * 参数名发现器
	 */
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/**
	 * 一般情况下都是 {@link org.springframework.beans.factory.support.DefaultListableBeanFactory}
	 */
	@Nullable
	private ConfigurableBeanFactory beanFactory;

	/**
	 * Controller的Class和类上的 @SessionAttributes的帮助类的映射关系
	 */
	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	/**
	 * Class和Class中标志了@InitBinder注解方法的映射关系
	 */
	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	/**
	 * 标注了@ControllerAdvice的bean和内部标注了@InitBinder的方法
	 */
	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	/**
	 * Class和Class中标志了 @ModelAttribute 并且没有标注 @RequestMapping 的方法
	 */
	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	/**
	 * 标注了 @ControllerAdvice 的bean和内部标注了 {@link ModelAttribute} 并且没有 {@link RequestMapping} 的方法
	 */
	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();


	public RequestMappingHandlerAdapter() {
		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		try {
			this.messageConverters.add(new SourceHttpMessageConverter<>());
		}
		catch (Error err) {
			// Ignore when no TransformerFactory implementation is available
		}
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver ModelAndViewResolvers}.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver ModelAndViewResolvers}, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		// 初始化 @ControllerAdvice
		initControllerAdviceCache();

		// 设置参数解析器
		if (this.argumentResolvers == null) {
			// 返回要使用的参数解析器列表
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			// 转换为一个混合的
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}

		// 设置适用于 @InitBinder 的参数解析器
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}

		// 设置返回值处理器
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}

		// 找到所有标注了 @ControllerAdvice 注解的bean
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		// 遍历所有遍历了 ControllerAdvice
		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			// 类中有 @ModelAttribute 方法的情况，并注册到指定地方
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType, MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
			}

			// 类中有 @InitBinder 方法的情况，并注册到指定地方
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType, INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
			}

			// 类实现了 RequestBodyAdvice 和 ResponseBodyAdvice 的情况，并注册到指定地方
			if (RequestBodyAdvice.class.isAssignableFrom(beanType) || ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
				requestResponseBodyAdviceBeans.add(adviceBean);
			}
		}

		// 注册到指定位置
		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}

		if (logger.isDebugEnabled()) {
			int modelSize = this.modelAttributeAdviceCache.size();
			int binderSize = this.initBinderAdviceCache.size();
			int reqCount = getBodyAdviceCount(RequestBodyAdvice.class);
			int resCount = getBodyAdviceCount(ResponseBodyAdvice.class);
			if (modelSize == 0 && binderSize == 0 && reqCount == 0 && resCount == 0) {
				logger.debug("ControllerAdvice beans: none");
			}
			else {
				logger.debug("ControllerAdvice beans: " + modelSize + " @ModelAttribute, " + binderSize +
						" @InitBinder, " + reqCount + " RequestBodyAdvice, " + resCount + " ResponseBodyAdvice");
			}
		}
	}

	// Count all advice, including explicit registrations..

	private int getBodyAdviceCount(Class<?> adviceType) {
		List<Object> advice = this.requestResponseBodyAdvice;
		return RequestBodyAdvice.class.isAssignableFrom(adviceType) ?
				RequestResponseBodyAdviceChain.getAdviceByType(advice, RequestBodyAdvice.class).size() :
				RequestResponseBodyAdviceChain.getAdviceByType(advice, ResponseBodyAdvice.class).size();
	}

	/**
	 * 返回要使用的参数解析器列表，包括默认的和自定义的
	 * @return
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(30);

		// 注册基于注解的参数解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// 注册基于类型的参数解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());

		// 注册自定义的参数解析器
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * 返回适用 {@code @InitBinder} 参数解析器列表，包括默认和自定义解析器
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>(20);

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * 返回默认的返回值处理器，包含默认的和自定义的
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(20);

		// Single-purpose return value types
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(),
				this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		handlers.add(new HttpHeadersReturnValueHandler());
		handlers.add(new CallableMethodReturnValueHandler());
		handlers.add(new DeferredResultMethodReturnValueHandler());
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		// 基于注解的返回值处理器
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		// 自定义的返回值处理器
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		// 检查请求方式和会话
		checkRequest(request);

		// 如果需要，在同步代码块中执行 invokeHandlerMethod 方法
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				// 返回该会话的互斥锁
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// 没有会话，无法使用互斥锁
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			// 没有同步的要求
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		// 是否需要让浏览器开始缓存
		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			// 是否需要设置会话属性
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				// 设置相关响应头
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				// 设置相关响应头
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * 返回处理程序的包含类上对应的 SessionAttributesHandler
	 * @param handlerMethod 处理程序
	 * @return
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		return this.sessionAttributesHandlerCache.computeIfAbsent(
				handlerMethod.getBeanType(),
				type -> new SessionAttributesHandler(type, this.sessionAttributeStore));
	}

	/**
	 * 执行处理程序，并返回 {@link ModelAndView}
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		// 包装请求
		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			// 获得 WebDataBinderFactory
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
			// 获得 ModelFactory
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
			// 设置参数解析器
			if (this.argumentResolvers != null) {
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			// 设置返回值处理器
			if (this.returnValueHandlers != null) {
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			invocableMethod.setDataBinderFactory(binderFactory);
			// 设置参数名发现器
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			// 设置InputFlashMap参数值
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			// 初始化Model
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

			// 将请求包装为 AsyncWebRequest
			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);

			// 初始化 WebAsyncManager
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			// 异步任务已经有了返回值了,指的是异步任务进行了派发
			if (asyncManager.hasConcurrentResult()) {
				// 拿到异步任务的返回值
				Object result = asyncManager.getConcurrentResult();
				// 用上次开启异步任务的 ModelAndViewContainer
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				LogFormatUtils.traceDebug(logger, traceOn -> {
					String formatted = LogFormatUtils.formatValue(result, !traceOn);
					return "Resume with async result [" + formatted + "]";
				});
				// 重点：重新包装处理方法
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}

			// 调用方法并处理返回值
			invocableMethod.invokeAndHandle(webRequest, mavContainer);
			// 判断是否是异步任务
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}
			// 获得 ModelAndView 对象
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {
			// 标志请求已经完成
			webRequest.requestCompleted();
		}
	}

	/**
	 * 利用 {@link HandlerMethod} 创建 {@link ServletInvocableHandlerMethod} 实例
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	/**
	 * 获得 ModelFactory
	 * @param handlerMethod
	 * @param binderFactory
	 * @return
	 */
	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		// 返回处理程序的包含类上对应的 SessionAttributesHandler
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		// 包含处理方法的对象的Class
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			// 筛选有 ModelAttribute 并且没有 RequestMapping 的方法
			methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// 从全局的 @ControllerAdvice 中获取符合规则的方法
		this.modelAttributeAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			// 检查给定的类是否满足切入规则
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				Object bean = controllerAdviceBean.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});

		// 包装
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}

	/**
	 * 创建有关 @ModelAttribute 的 InvocableHandlerMethod
	 * @param factory
	 * @param bean
	 * @param method
	 * @return
	 */
	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		// 设置适用于标注了 @ModelAttribute 注解的的参数解析器
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		// 设置参数名发现器
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	/**
	 * 获得 WebDataBinderFactory
	 * @param handlerMethod
	 * @return
	 * @throws Exception
	 */
	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		// 获得bean的类型
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
			// 处理程序的类中中标注了@InitBinder注解的方法
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();

		// 处理 @ControllerAdvice 中包含@InitBinder注解方法的情况
		this.initBinderAdviceCache.forEach((controllerAdviceBean, methodSet) -> {
			// 检查给定的类是否满足切入规则
			if (controllerAdviceBean.isApplicableToBeanType(handlerType)) {
				// 获取这个ControllerAdviseBean的bean实例
				Object bean = controllerAdviceBean.resolveBean();
				//
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});

		// 将有@InitBinder的方法和对应实例添加到initBinderMethods中
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		return createDataBinderFactory(initBinderMethods);
	}

	/**
	 * 创建有关 @InitBinder 的 {@code InvocableHandlerMethod}
	 * @param bean
	 * @param method
	 * @return
	 */
	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		// 设置适用于标注了 @InitBinder 注解的的参数解析器
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		// 设置参数名发现器
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	/**
	 * 获得 ModelAndView 对象
	 * @param mavContainer
	 * @param modelFactory
	 * @param webRequest
	 * @return
	 * @throws Exception
	 */
	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {

		// 将 model 中有关 @SessionAttributes 的属性保存在会话中
		modelFactory.updateModel(webRequest, mavContainer);
		// 比如说使用了@Response注解，那么就不需要视图了，这里就会直接返回
		if (mavContainer.isRequestHandled()) {
			return null;
		}

		// 创建 ModelAndView 以及配置视图名称
		ModelMap model = mavContainer.getModel();
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}

		// 如果是使用重定向模型
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				// 将FlashAttributes 保存在 FlashMap 中,后面处理重定向视图的时候，会读取FlashMap然后保存在会话中的
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;
	}

}
