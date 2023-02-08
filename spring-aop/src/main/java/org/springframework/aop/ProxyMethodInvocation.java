/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.lang.Nullable;

/**
 * Extension of the AOP Alliance {@link org.aopalliance.intercept.MethodInvocation}
 * interface, allowing access to the proxy that the method invocation was made through.
 *
 * <p>Useful to be able to substitute return values with the proxy,
 * if necessary, for example if the invocation target returned itself.
 *
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @since 1.1.3
 * @see org.springframework.aop.framework.ReflectiveMethodInvocation
 * @see org.springframework.aop.support.DelegatingIntroductionInterceptor
 */
public interface ProxyMethodInvocation extends MethodInvocation {

	/**
	 * 返回代理对象
	 * @return the original proxy object
	 */
	Object getProxy();

	/**
	 * 创建当前对象的副本
	 * If cloning is done before {@code proceed()}
	 * is invoked on this object, {@code proceed()} can be invoked once per clone
	 * to invoke the joinpoint (and the rest of the advice chain) more than once.
	 * @return an invocable clone of this invocation.
	 * {@code proceed()} can be called once per clone.
	 */
	MethodInvocation invocableClone();

	/**
	 * 创建当前对象的副本(带参数的) If cloning is done before {@code proceed()}
	 * is invoked on this object, {@code proceed()} can be invoked once per clone
	 * to invoke the joinpoint (and the rest of the advice chain) more than once.
	 * @param arguments the arguments that the cloned invocation is supposed to use,
	 * overriding the original arguments
	 * @return an invocable clone of this invocation.
	 * {@code proceed()} can be called once per clone.
	 */
	MethodInvocation invocableClone(Object... arguments);

	/**
	 * 设置后续要使用的参数
	 * @param arguments the argument array
	 */
	void setArguments(Object... arguments);

	/**
	 * 将指定值的指定用户属性添加到此调用
	 * <p>Such attributes are not used within the AOP framework itself. They are
	 * just kept as part of the invocation object, for use in special interceptors.
	 * @param key the name of the attribute
	 * @param value the value of the attribute, or {@code null} to reset it
	 */
	void setUserAttribute(String key, @Nullable Object value);

	/**
	 * 返回保存的属性
	 * @param key the name of the attribute
	 * @return the value of the attribute, or {@code null} if not set
	 * @see #setUserAttribute
	 */
	@Nullable
	Object getUserAttribute(String key);

}
