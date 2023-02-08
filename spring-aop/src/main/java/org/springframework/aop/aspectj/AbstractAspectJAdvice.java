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

package org.springframework.aop.aspectj;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.aspectj.weaver.tools.PointcutParameter;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for AOP Alliance {@link org.aopalliance.aop.Advice} classes
 * wrapping an AspectJ aspect or an AspectJ-annotated advice method.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.0
 */
@SuppressWarnings("serial")
public abstract class AbstractAspectJAdvice implements Advice, AspectJPrecedenceInformation, Serializable {

	/**
	 * Key used in ReflectiveMethodInvocation userAttributes map for the current joinpoint.
	 */
	protected static final String JOIN_POINT_KEY = JoinPoint.class.getName();


	/**
	 * Lazily instantiate joinpoint for the current invocation.
	 * Requires MethodInvocation to be bound with ExposeInvocationInterceptor.
	 * <p>Do not use if access is available to the current ReflectiveMethodInvocation
	 * (in an around advice).
	 * @return current AspectJ joinpoint, or through an exception if we're not in a
	 * Spring AOP invocation.
	 */
	public static JoinPoint currentJoinPoint() {
		MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		ProxyMethodInvocation pmi = (ProxyMethodInvocation) mi;
		JoinPoint jp = (JoinPoint) pmi.getUserAttribute(JOIN_POINT_KEY);
		if (jp == null) {
			jp = new MethodInvocationProceedingJoinPoint(pmi);
			pmi.setUserAttribute(JOIN_POINT_KEY, jp);
		}
		return jp;
	}


	private final Class<?> declaringClass;

	private final String methodName;

	/**
	 * 此Advice的参数列表
	 */
	private final Class<?>[] parameterTypes;

	/**
	 * 定义的 {@link Advice} 方法
	 * <li>比如说是标注了@Before等等注解的方法</li>
	 */
	protected transient Method aspectJAdviceMethod;

	private final AspectJExpressionPointcut pointcut;

	private final AspectInstanceFactory aspectInstanceFactory;

	/**
	 * The name of the aspect (ref bean) in which this advice was defined
	 * (used when determining advice precedence so that we can determine
	 * whether two pieces of advice come from the same aspect).
	 */
	private String aspectName = "";

	/**
	 * The order of declaration of this advice within the aspect.
	 */
	private int declarationOrder;

	/**
	 * 是Advice方法的参数列表
	 */
	@Nullable
	private String[] argumentNames;

	/** Non-null if after throwing advice binds the thrown value. */
	@Nullable
	private String throwingName;

	/**
	 * 是否通过 {@link org.aspectj.lang.annotation.AfterReturning @AfterReturning} 配置了返回参数的名称
	 * <li>意思是AfterReturningAdvice方法中会有一个入参，是真实处理方法的返回值</li>
	 */
	@Nullable
	private String returningName;

	/**
	 * 作为AfterReturningAdvice的方法中配置了返回值参数，当执行AfterReturningAdvice方法的时候，要确定真实处理方法的返回值是否匹配才能切入</li>
	 */
	private Class<?> discoveredReturningType = Object.class;

	/**
	 * 作为AfterThrowingAdvice的方法中配置了异常参数，当执行AfterThrowingAdvice方法的时候，要确定真实处理方法的返回值是否匹配才能切入</li>
	 */
	private Class<?> discoveredThrowingType = Object.class;

	/**
	 * {@link JoinPoint} 参数的下标(目前只支持索引θ)
	 */
	private int joinPointArgumentIndex = -1;

	 /**
	 * {@link JoinPoint.StaticPart} 参数的下标(目前只支持索引θ)
	 */
	private int joinPointStaticPartArgumentIndex = -1;

	/**
	 * 是Advice方法中需要绑定的参数列表
	 * <li>是非代理相关的参数</li>
	 */
	@Nullable
	private Map<String, Integer> argumentBindings;

	/**
	 * 参数的是否已经进行了自我反省
	 */
	private boolean argumentsIntrospected = false;

	/**
	 * 作为AfterReturningAdvice的方法中配置了返回值的泛型参数，当执行AfterReturningAdvice方法的时候，要确定真实处理方法的返回值泛型是否匹配才能切入</li>
	 */
	@Nullable
	private Type discoveredReturningGenericType;
	// Note: Unlike return type, no such generic information is needed for the throwing type,
	// since Java doesn't allow exception types to be parameterized.


	/**
	 * Create a new AbstractAspectJAdvice for the given advice method.
	 * @param aspectJAdviceMethod the AspectJ-style advice method
	 * @param pointcut the AspectJ expression pointcut
	 * @param aspectInstanceFactory the factory for aspect instances
	 */
	public AbstractAspectJAdvice(
			Method aspectJAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aspectInstanceFactory) {

		Assert.notNull(aspectJAdviceMethod, "Advice method must not be null");
		this.declaringClass = aspectJAdviceMethod.getDeclaringClass();
		this.methodName = aspectJAdviceMethod.getName();
		this.parameterTypes = aspectJAdviceMethod.getParameterTypes();
		this.aspectJAdviceMethod = aspectJAdviceMethod;
		this.pointcut = pointcut;
		this.aspectInstanceFactory = aspectInstanceFactory;
	}


	/**
	 * Return the AspectJ-style advice method.
	 */
	public final Method getAspectJAdviceMethod() {
		return this.aspectJAdviceMethod;
	}

	/**
	 * Return the AspectJ expression pointcut.
	 */
	public final AspectJExpressionPointcut getPointcut() {
		calculateArgumentBindings();
		return this.pointcut;
	}

	/**
	 * Build a 'safe' pointcut that excludes the AspectJ advice method itself.
	 * @return a composable pointcut that builds on the original AspectJ expression pointcut
	 * @see #getPointcut()
	 */
	public final Pointcut buildSafePointcut() {
		Pointcut pc = getPointcut();
		MethodMatcher safeMethodMatcher = MethodMatchers.intersection(
				new AdviceExcludingMethodMatcher(this.aspectJAdviceMethod), pc.getMethodMatcher());
		return new ComposablePointcut(pc.getClassFilter(), safeMethodMatcher);
	}

	/**
	 * Return the factory for aspect instances.
	 */
	public final AspectInstanceFactory getAspectInstanceFactory() {
		return this.aspectInstanceFactory;
	}

	/**
	 * Return the ClassLoader for aspect instances.
	 */
	@Nullable
	public final ClassLoader getAspectClassLoader() {
		return this.aspectInstanceFactory.getAspectClassLoader();
	}

	@Override
	public int getOrder() {
		return this.aspectInstanceFactory.getOrder();
	}


	/**
	 * Set the name of the aspect (bean) in which the advice was declared.
	 */
	public void setAspectName(String name) {
		this.aspectName = name;
	}

	@Override
	public String getAspectName() {
		return this.aspectName;
	}

	/**
	 * Set the declaration order of this advice within the aspect.
	 */
	public void setDeclarationOrder(int order) {
		this.declarationOrder = order;
	}

	@Override
	public int getDeclarationOrder() {
		return this.declarationOrder;
	}

	/**
	 * Set by creator of this advice object if the argument names are known.
	 * <p>This could be for example because they have been explicitly specified in XML,
	 * or in an advice annotation.
	 * @param argNames comma delimited list of arg names
	 */
	public void setArgumentNames(String argNames) {
		String[] tokens = StringUtils.commaDelimitedListToStringArray(argNames);
		setArgumentNamesFromStringArray(tokens);
	}

	public void setArgumentNamesFromStringArray(String... args) {
		this.argumentNames = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			this.argumentNames[i] = StringUtils.trimWhitespace(args[i]);
			if (!isVariableName(this.argumentNames[i])) {
				throw new IllegalArgumentException(
						"'argumentNames' property of AbstractAspectJAdvice contains an argument name '" +
						this.argumentNames[i] + "' that is not a valid Java identifier");
			}
		}
		if (this.argumentNames != null) {
			if (this.aspectJAdviceMethod.getParameterCount() == this.argumentNames.length + 1) {
				// May need to add implicit join point arg name...
				Class<?> firstArgType = this.aspectJAdviceMethod.getParameterTypes()[0];
				if (firstArgType == JoinPoint.class ||
						firstArgType == ProceedingJoinPoint.class ||
						firstArgType == JoinPoint.StaticPart.class) {
					String[] oldNames = this.argumentNames;
					this.argumentNames = new String[oldNames.length + 1];
					this.argumentNames[0] = "THIS_JOIN_POINT";
					System.arraycopy(oldNames, 0, this.argumentNames, 1, oldNames.length);
				}
			}
		}
	}

	public void setReturningName(String name) {
		throw new UnsupportedOperationException("Only afterReturning advice can be used to bind a return value");
	}

	/**
	 * We need to hold the returning name at this level for argument binding calculations,
	 * this method allows the afterReturning advice subclass to set the name.
	 */
	protected void setReturningNameNoCheck(String name) {
		// name could be a variable or a type...
		if (isVariableName(name)) {
			this.returningName = name;
		}
		else {
			// assume a type
			try {
				this.discoveredReturningType = ClassUtils.forName(name, getAspectClassLoader());
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Returning name '" + name  +
						"' is neither a valid argument name nor the fully-qualified " +
						"name of a Java type on the classpath. Root cause: " + ex);
			}
		}
	}

	protected Class<?> getDiscoveredReturningType() {
		return this.discoveredReturningType;
	}

	@Nullable
	protected Type getDiscoveredReturningGenericType() {
		return this.discoveredReturningGenericType;
	}

	public void setThrowingName(String name) {
		throw new UnsupportedOperationException("Only afterThrowing advice can be used to bind a thrown exception");
	}

	/**
	 * We need to hold the throwing name at this level for argument binding calculations,
	 * this method allows the afterThrowing advice subclass to set the name.
	 */
	protected void setThrowingNameNoCheck(String name) {
		// name could be a variable or a type...
		if (isVariableName(name)) {
			this.throwingName = name;
		}
		else {
			// assume a type
			try {
				this.discoveredThrowingType = ClassUtils.forName(name, getAspectClassLoader());
			}
			catch (Throwable ex) {
				throw new IllegalArgumentException("Throwing name '" + name  +
						"' is neither a valid argument name nor the fully-qualified " +
						"name of a Java type on the classpath. Root cause: " + ex);
			}
		}
	}

	protected Class<?> getDiscoveredThrowingType() {
		return this.discoveredThrowingType;
	}

	private boolean isVariableName(String name) {
		char[] chars = name.toCharArray();
		if (!Character.isJavaIdentifierStart(chars[0])) {
			return false;
		}
		for (int i = 1; i < chars.length; i++) {
			if (!Character.isJavaIdentifierPart(chars[i])) {
				return false;
			}
		}
		return true;
	}


	/**
	 * 将参数绑定起来，方便后续Advice执行可以尽可能快
	 * <p>If the first argument is of type JoinPoint or ProceedingJoinPoint then we
	 * pass a JoinPoint in that position (ProceedingJoinPoint for around advice).
	 * <p>If the first argument is of type {@code JoinPoint.StaticPart}
	 * then we pass a {@code JoinPoint.StaticPart} in that position.
	 * <p>Remaining arguments have to be bound by pointcut evaluation at
	 * a given join point. We will get back a map from argument name to
	 * value. We need to calculate which advice parameter needs to be bound
	 * to which argument name. There are multiple strategies for determining
	 * this binding, which are arranged in a ChainOfResponsibility.
	 */
	public final synchronized void calculateArgumentBindings() {
		// 简单的参数，不需要绑定
		if (this.argumentsIntrospected || this.parameterTypes.length == 0) {
			return;
		}

		int numUnboundArgs = this.parameterTypes.length;
		Class<?>[] parameterTypes = this.aspectJAdviceMethod.getParameterTypes();
		// 基于
		if (maybeBindJoinPoint(parameterTypes[0]) || maybeBindProceedingJoinPoint(parameterTypes[0]) ||
				maybeBindJoinPointStaticPart(parameterTypes[0])) {
			numUnboundArgs--;
		}

		// 有其他类型的参数
		if (numUnboundArgs > 0) {
			// 确定异常和返回值以及Pointcut的参数名
			bindArgumentsByName(numUnboundArgs);
		}

		this.argumentsIntrospected = true;
	}

	/**
	 * 是否是 {@link JoinPoint} 类型
	 * @param candidateParameterType
	 * @return
	 */
	private boolean maybeBindJoinPoint(Class<?> candidateParameterType) {
		if (JoinPoint.class == candidateParameterType) {
			this.joinPointArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * 是否是 {@link ProceedingJoinPoint} 类型
	 * @param candidateParameterType
	 * @return
	 */
	private boolean maybeBindProceedingJoinPoint(Class<?> candidateParameterType) {
		if (ProceedingJoinPoint.class == candidateParameterType) {
			if (!supportsProceedingJoinPoint()) {
				throw new IllegalArgumentException("ProceedingJoinPoint is only supported for around advice");
			}
			this.joinPointArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	protected boolean supportsProceedingJoinPoint() {
		return false;
	}

	/**
	 * 是否是 {@link JoinPoint.StaticPart} 类型
	 * @param candidateParameterType
	 * @return
	 */
	private boolean maybeBindJoinPointStaticPart(Class<?> candidateParameterType) {
		if (JoinPoint.StaticPart.class == candidateParameterType) {
			this.joinPointStaticPartArgumentIndex = 0;
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * 确定异常和返回值以及Pointcut的参数名
	 * @param numArgumentsExpectingToBind
	 */
	private void bindArgumentsByName(int numArgumentsExpectingToBind) {
		if (this.argumentNames == null) {
			this.argumentNames = createParameterNameDiscoverer().getParameterNames(this.aspectJAdviceMethod);
		}
		if (this.argumentNames != null) {
			// 确定异常和返回值以及Pointcut的参数名
			bindExplicitArguments(numArgumentsExpectingToBind);
		}
		else {
			throw new IllegalStateException("Advice method [" + this.aspectJAdviceMethod.getName() + "] " +
					"requires " + numArgumentsExpectingToBind + " arguments to be bound by name, but " +
					"the argument names were not specified and could not be discovered.");
		}
	}

	/**
	 * Create a ParameterNameDiscoverer to be used for argument binding.
	 * <p>The default implementation creates a {@link DefaultParameterNameDiscoverer}
	 * and adds a specifically configured {@link AspectJAdviceParameterNameDiscoverer}.
	 */
	protected ParameterNameDiscoverer createParameterNameDiscoverer() {
		// We need to discover them, or if that fails, guess,
		// and if we can't guess with 100% accuracy, fail.
		DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
		AspectJAdviceParameterNameDiscoverer adviceParameterNameDiscoverer =
				new AspectJAdviceParameterNameDiscoverer(this.pointcut.getExpression());
		adviceParameterNameDiscoverer.setReturningName(this.returningName);
		adviceParameterNameDiscoverer.setThrowingName(this.throwingName);
		// Last in chain, so if we're called and we fail, that's bad...
		adviceParameterNameDiscoverer.setRaiseExceptions(true);
		discoverer.addDiscoverer(adviceParameterNameDiscoverer);
		return discoverer;
	}

	/**
	 * 将一些其他参数绑定在当前 {@link Advice} 中
	 * <li>比如说在目标方法抛出异常的情况下，需要将目标方法的某个入参当做 AfterThrowingAdvice 的入参</li>
	 * @param numArgumentsLeftToBind 需要绑定的参数数量
	 */
	private void bindExplicitArguments(int numArgumentsLeftToBind) {
		Assert.state(this.argumentNames != null, "No argument names available");
		this.argumentBindings = new HashMap<>();

		int numExpectedArgumentNames = this.aspectJAdviceMethod.getParameterCount();
		// 不懂为什么会不一样
		if (this.argumentNames.length != numExpectedArgumentNames) {
			throw new IllegalStateException("Expecting to find " + numExpectedArgumentNames +
					" arguments to bind by name in advice, but actually found " +
					this.argumentNames.length + " arguments.");
		}

		// 保存可能需要绑定的参数列表
		int argumentIndexOffset = this.parameterTypes.length - numArgumentsLeftToBind;
		for (int i = argumentIndexOffset; i < this.argumentNames.length; i++) {
			this.argumentBindings.put(this.argumentNames[i], i);
		}

		// 如果指定了返回参数的名称
		if (this.returningName != null) {
			// 在注解中设置的返回参数名称都没有在AfterReturningAdvice方法中，肯定要抛出异常
			if (!this.argumentBindings.containsKey(this.returningName)) {
				throw new IllegalStateException("Returning argument name '" + this.returningName +
						"' was not bound in advice arguments");
			}
			else {
				// 确定AfterReturningAdvice的切入规则
				Integer index = this.argumentBindings.get(this.returningName);
				this.discoveredReturningType = this.aspectJAdviceMethod.getParameterTypes()[index];
				this.discoveredReturningGenericType = this.aspectJAdviceMethod.getGenericParameterTypes()[index];
			}
		}

		// 如果指定了异常的名称
		if (this.throwingName != null) {
			// 在注解中设置的异常名称都没有在AfterThrowingAdvice方法中，肯定要抛出异常
			if (!this.argumentBindings.containsKey(this.throwingName)) {
				throw new IllegalStateException("Throwing argument name '" + this.throwingName +
						"' was not bound in advice arguments");
			}
			else {
				// 确定AfterReturningAdvice的切入规则
				Integer index = this.argumentBindings.get(this.throwingName);
				this.discoveredThrowingType = this.aspectJAdviceMethod.getParameterTypes()[index];
			}
		}

		// 配置切入点表达式
		configurePointcutParameters(this.argumentNames, argumentIndexOffset);
	}

	/**
	 * 配置 {@link Pointcut} 的参数，在 argumentIndexOffset 之后的都是候选参数
	 */
	private void configurePointcutParameters(String[] argumentNames, int argumentIndexOffset) {
		int numParametersToRemove = argumentIndexOffset;

		// 不对返回值和异常的参数做处理
		if (this.returningName != null) {
			numParametersToRemove++;
		}
		if (this.throwingName != null) {
			numParametersToRemove++;
		}
		String[] pointcutParameterNames = new String[argumentNames.length - numParametersToRemove];
		Class<?>[] pointcutParameterTypes = new Class<?>[pointcutParameterNames.length];
		Class<?>[] methodParameterTypes = this.aspectJAdviceMethod.getParameterTypes();

		int index = 0;
		for (int i = 0; i < argumentNames.length; i++) {
			if (i < argumentIndexOffset) {
				continue;
			}
			// 排除返回值和异常的参数
			if (argumentNames[i].equals(this.returningName) ||
				argumentNames[i].equals(this.throwingName)) {
				continue;
			}
			pointcutParameterNames[index] = argumentNames[i];
			pointcutParameterTypes[index] = methodParameterTypes[i];
			index++;
		}

		// 设置参数名和类型
		this.pointcut.setParameterNames(pointcutParameterNames);
		this.pointcut.setParameterTypes(pointcutParameterTypes);
	}

	/**
	 * 将方法参数的类型和下标进行绑定，并返回Advice方法需要的参数列表
	 * @param jp the current JoinPoint
	 * @param jpMatch the join point match that matched this execution join point
	 * @param returnValue the return value from the method execution (may be null)
	 * @param ex the exception thrown by the method execution (may be null)
	 * @return the empty array if there are no arguments
	 */
	protected Object[] argBinding(JoinPoint jp, @Nullable JoinPointMatch jpMatch,
			@Nullable Object returnValue, @Nullable Throwable ex) {

		// 将特定参数绑定起来，方便后续Advice执行可以尽可能快
		calculateArgumentBindings();

		// AMC start
		Object[] adviceInvocationArgs = new Object[this.parameterTypes.length];
		int numBound = 0;

		// 设置JoinPoint类型的参数
		if (this.joinPointArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointArgumentIndex] = jp;
			numBound++;
		}
		else if (this.joinPointStaticPartArgumentIndex != -1) {
			adviceInvocationArgs[this.joinPointStaticPartArgumentIndex] = jp.getStaticPart();
			numBound++;
		}

		if (!CollectionUtils.isEmpty(this.argumentBindings)) {
			// 是的从切入点匹配绑定
			if (jpMatch != null) {
				PointcutParameter[] parameterBindings = jpMatch.getParameterBindings();
				for (PointcutParameter parameter : parameterBindings) {
					String name = parameter.getName();
					Integer index = this.argumentBindings.get(name);
					adviceInvocationArgs[index] = parameter.getBinding();
					numBound++;
				}
			}
			// 从返回值进行匹配
			if (this.returningName != null) {
				Integer index = this.argumentBindings.get(this.returningName);
				adviceInvocationArgs[index] = returnValue;
				numBound++;
			}
			// 从抛出的异常进行匹配
			if (this.throwingName != null) {
				Integer index = this.argumentBindings.get(this.throwingName);
				adviceInvocationArgs[index] = ex;
				numBound++;
			}
		}

		// 参数列表不匹配
		if (numBound != this.parameterTypes.length) {
			throw new IllegalStateException("Required to bind " + this.parameterTypes.length +
					" arguments, but only bound " + numBound + " (JoinPointMatch " +
					(jpMatch == null ? "was NOT" : "WAS") + " bound in invocation)");
		}

		return adviceInvocationArgs;
	}


	/**
	 * 执行Advice方法
	 * @param jpMatch the JoinPointMatch that matched this execution join point
	 * @param returnValue the return value from the method execution (may be null)
	 * @param ex the exception thrown by the method execution (may be null)
	 * @return the invocation result
	 * @throws Throwable in case of invocation failure
	 */
	protected Object invokeAdviceMethod(
			@Nullable JoinPointMatch jpMatch, @Nullable Object returnValue, @Nullable Throwable ex)
			throws Throwable {

		return invokeAdviceMethodWithGivenArgs(argBinding(getJoinPoint(), jpMatch, returnValue, ex));
	}

	// As above, but in this case we are given the join point.
	protected Object invokeAdviceMethod(JoinPoint jp, @Nullable JoinPointMatch jpMatch,
			@Nullable Object returnValue, @Nullable Throwable t) throws Throwable {

		return invokeAdviceMethodWithGivenArgs(argBinding(jp, jpMatch, returnValue, t));
	}

	/**
	 * 执行Advice方法
	 * @param args Advice方法所需的入参
	 * @return
	 * @throws Throwable
	 */
	protected Object invokeAdviceMethodWithGivenArgs(Object[] args) throws Throwable {
		Object[] actualArgs = args;
		// Advice方法无入参
		if (this.aspectJAdviceMethod.getParameterCount() == 0) {
			actualArgs = null;
		}
		try {
			// 暴力反射
			ReflectionUtils.makeAccessible(this.aspectJAdviceMethod);
			// 执行Advice方法
			return this.aspectJAdviceMethod.invoke(this.aspectInstanceFactory.getAspectInstance(), actualArgs);
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("Mismatch on arguments to advice method [" +
					this.aspectJAdviceMethod + "]; pointcut expression [" +
					this.pointcut.getPointcutExpression() + "]", ex);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}

	/**
	 * Overridden in around advice to return proceeding join point.
	 */
	protected JoinPoint getJoinPoint() {
		return currentJoinPoint();
	}

	/**
	 * 获得 {@link MethodInvocation} 中保存的 {@link JoinPointMatch}
	 */
	@Nullable
	protected JoinPointMatch getJoinPointMatch() {
		MethodInvocation mi = ExposeInvocationInterceptor.currentInvocation();
		if (!(mi instanceof ProxyMethodInvocation)) {
			throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + mi);
		}
		return getJoinPointMatch((ProxyMethodInvocation) mi);
	}

	/**
	 * 获得pmi中保存的 {@link JoinPointMatch}
	 * @param pmi
	 * @return
	 */
	// Note: We can't use JoinPointMatch.getClass().getName() as the key, since
	// Spring AOP does all the matching at a join point, and then all the invocations.
	// Under this scenario, if we just use JoinPointMatch as the key, then
	// 'last man wins' which is not what we want at all.
	// Using the expression is guaranteed to be safe, since 2 identical expressions
	// are guaranteed to bind in exactly the same way.
	@Nullable
	protected JoinPointMatch getJoinPointMatch(ProxyMethodInvocation pmi) {
		String expression = this.pointcut.getExpression();
		return (expression != null ? (JoinPointMatch) pmi.getUserAttribute(expression) : null);
	}


	@Override
	public String toString() {
		return getClass().getName() + ": advice method [" + this.aspectJAdviceMethod + "]; " +
				"aspect name '" + this.aspectName + "'";
	}

	private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
		inputStream.defaultReadObject();
		try {
			this.aspectJAdviceMethod = this.declaringClass.getMethod(this.methodName, this.parameterTypes);
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Failed to find advice method on deserialization", ex);
		}
	}


	/**
	 * MethodMatcher that excludes the specified advice method.
	 * @see AbstractAspectJAdvice#buildSafePointcut()
	 */
	private static class AdviceExcludingMethodMatcher extends StaticMethodMatcher {

		private final Method adviceMethod;

		public AdviceExcludingMethodMatcher(Method adviceMethod) {
			this.adviceMethod = adviceMethod;
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return !this.adviceMethod.equals(method);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AdviceExcludingMethodMatcher)) {
				return false;
			}
			AdviceExcludingMethodMatcher otherMm = (AdviceExcludingMethodMatcher) other;
			return this.adviceMethod.equals(otherMm.adviceMethod);
		}

		@Override
		public int hashCode() {
			return this.adviceMethod.hashCode();
		}

		@Override
		public String toString() {
			return getClass().getName() + ": " + this.adviceMethod;
		}
	}

}
