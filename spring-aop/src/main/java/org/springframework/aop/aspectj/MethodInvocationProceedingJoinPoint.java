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

package org.springframework.aop.aspectj;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;

import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * An implementation of the AspectJ {@link ProceedingJoinPoint} interface
 * wrapping an AOP Alliance {@link org.aopalliance.intercept.MethodInvocation}.
 *
 * <p><b>Note</b>: The {@code getThis()} method returns the current Spring AOP proxy.
 * The {@code getTarget()} method returns the current Spring AOP target (which may be
 * {@code null} if there is no target instance) as a plain POJO without any advice.
 * <b>If you want to call the object and have the advice take effect, use {@code getThis()}.</b>
 * A common example is casting the object to an introduced interface in the implementation of
 * an introduction. There is no such distinction between target and proxy in AspectJ itself.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Ramnivas Laddad
 * @since 2.0
 */
public class MethodInvocationProceedingJoinPoint implements ProceedingJoinPoint, JoinPoint.StaticPart {

	private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	private final ProxyMethodInvocation methodInvocation;

	/**
	 * 真实处理方法的参数值列表
	 * <li>采用懒加载</li>
	 */
	@Nullable
	private Object[] args;

	/**
	 * 是获取真实的处理方法的信息的工具类
	 * <li>采用懒加载</li>
	 */
	@Nullable
	private Signature signature;

	/** Lazily initialized source location object. */
	@Nullable
	private SourceLocation sourceLocation;


	/**
	 * Create a new MethodInvocationProceedingJoinPoint, wrapping the given
	 * Spring ProxyMethodInvocation object.
	 * @param methodInvocation the Spring ProxyMethodInvocation object
	 */
	public MethodInvocationProceedingJoinPoint(ProxyMethodInvocation methodInvocation) {
		Assert.notNull(methodInvocation, "MethodInvocation must not be null");
		this.methodInvocation = methodInvocation;
	}


	@Override
	public void set$AroundClosure(AroundClosure aroundClosure) {
		throw new UnsupportedOperationException();
	}

	/**
	 * 执行真实的处理方法
	 * @return
	 * @throws Throwable
	 */
	@Override
	public Object proceed() throws Throwable {
		// 这个是带有参数的
		return this.methodInvocation.invocableClone().proceed();
	}

	/**
	 * 使用传入的参数列表，执行真实的处理方法
	 * @return
	 * @throws Throwable
	 */
	@Override
	public Object proceed(Object[] arguments) throws Throwable {
		Assert.notNull(arguments, "Argument array passed to proceed cannot be null");
		// 参数个数不匹配，直接抛出异常
		if (arguments.length != this.methodInvocation.getArguments().length) {
			throw new IllegalArgumentException("Expecting " +
					this.methodInvocation.getArguments().length + " arguments to proceed, " +
					"but was passed " + arguments.length + " arguments");
		}
		this.methodInvocation.setArguments(arguments);
		return this.methodInvocation.invocableClone(arguments).proceed();
	}

	/**
	 * 返回代理对象
	 */
	@Override
	public Object getThis() {
		return this.methodInvocation.getProxy();
	}

	/**
	 * 返回被代理对象
	 */
	@Override
	@Nullable
	public Object getTarget() {
		return this.methodInvocation.getThis();
	}

	/**
	 * 通过 {@link MethodInvocation} 获得真实处理方法参数列表值
	 * @return
	 */
	@Override
	public Object[] getArgs() {
		if (this.args == null) {
			this.args = this.methodInvocation.getArguments().clone();
		}
		return this.args;
	}

	@Override
	public Signature getSignature() {
		if (this.signature == null) {
			this.signature = new MethodSignatureImpl();
		}
		return this.signature;
	}

	@Override
	public SourceLocation getSourceLocation() {
		if (this.sourceLocation == null) {
			this.sourceLocation = new SourceLocationImpl();
		}
		return this.sourceLocation;
	}

	@Override
	public String getKind() {
		return ProceedingJoinPoint.METHOD_EXECUTION;
	}

	@Override
	public int getId() {
		// TODO: It's just an adapter but returning 0 might still have side effects...
		return 0;
	}

	@Override
	public JoinPoint.StaticPart getStaticPart() {
		return this;
	}

	@Override
	public String toShortString() {
		return "execution(" + getSignature().toShortString() + ")";
	}

	@Override
	public String toLongString() {
		return "execution(" + getSignature().toLongString() + ")";
	}

	@Override
	public String toString() {
		return "execution(" + getSignature().toString() + ")";
	}


	/**
	 * 是方法签名的工具类
	 * <p>获取方法的信息，主要就是利用 {@link MethodInvocation}</p>
	 */
	private class MethodSignatureImpl implements MethodSignature {

		@Nullable
		private volatile String[] parameterNames;

		@Override
		public String getName() {
			return methodInvocation.getMethod().getName();
		}

		@Override
		public int getModifiers() {
			return methodInvocation.getMethod().getModifiers();
		}

		@Override
		public Class<?> getDeclaringType() {
			return methodInvocation.getMethod().getDeclaringClass();
		}

		@Override
		public String getDeclaringTypeName() {
			return methodInvocation.getMethod().getDeclaringClass().getName();
		}

		@Override
		public Class<?> getReturnType() {
			return methodInvocation.getMethod().getReturnType();
		}

		@Override
		public Method getMethod() {
			return methodInvocation.getMethod();
		}

		@Override
		public Class<?>[] getParameterTypes() {
			return methodInvocation.getMethod().getParameterTypes();
		}

		@Override
		@Nullable
		public String[] getParameterNames() {
			String[] parameterNames = this.parameterNames;
			if (parameterNames == null) {
				parameterNames = parameterNameDiscoverer.getParameterNames(getMethod());
				this.parameterNames = parameterNames;
			}
			return parameterNames;
		}

		@Override
		public Class<?>[] getExceptionTypes() {
			return methodInvocation.getMethod().getExceptionTypes();
		}

		@Override
		public String toShortString() {
			return toString(false, false, false, false);
		}

		@Override
		public String toLongString() {
			return toString(true, true, true, true);
		}

		@Override
		public String toString() {
			return toString(false, true, false, true);
		}

		private String toString(boolean includeModifier, boolean includeReturnTypeAndArgs,
				boolean useLongReturnAndArgumentTypeName, boolean useLongTypeName) {

			StringBuilder sb = new StringBuilder();
			if (includeModifier) {
				sb.append(Modifier.toString(getModifiers()));
				sb.append(" ");
			}
			if (includeReturnTypeAndArgs) {
				appendType(sb, getReturnType(), useLongReturnAndArgumentTypeName);
				sb.append(" ");
			}
			appendType(sb, getDeclaringType(), useLongTypeName);
			sb.append(".");
			sb.append(getMethod().getName());
			sb.append("(");
			Class<?>[] parametersTypes = getParameterTypes();
			appendTypes(sb, parametersTypes, includeReturnTypeAndArgs, useLongReturnAndArgumentTypeName);
			sb.append(")");
			return sb.toString();
		}

		private void appendTypes(StringBuilder sb, Class<?>[] types, boolean includeArgs,
				boolean useLongReturnAndArgumentTypeName) {

			if (includeArgs) {
				for (int size = types.length, i = 0; i < size; i++) {
					appendType(sb, types[i], useLongReturnAndArgumentTypeName);
					if (i < size - 1) {
						sb.append(",");
					}
				}
			}
			else {
				if (types.length != 0) {
					sb.append("..");
				}
			}
		}

		private void appendType(StringBuilder sb, Class<?> type, boolean useLongTypeName) {
			if (type.isArray()) {
				appendType(sb, type.getComponentType(), useLongTypeName);
				sb.append("[]");
			}
			else {
				sb.append(useLongTypeName ? type.getName() : type.getSimpleName());
			}
		}
	}


	/**
	 * Lazily initialized SourceLocation.
	 */
	private class SourceLocationImpl implements SourceLocation {

		@Override
		public Class<?> getWithinType() {
			if (methodInvocation.getThis() == null) {
				throw new UnsupportedOperationException("No source location joinpoint available: target is null");
			}
			return methodInvocation.getThis().getClass();
		}

		@Override
		public String getFileName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getLine() {
			throw new UnsupportedOperationException();
		}

		@Override
		@Deprecated
		public int getColumn() {
			throw new UnsupportedOperationException();
		}
	}

}
