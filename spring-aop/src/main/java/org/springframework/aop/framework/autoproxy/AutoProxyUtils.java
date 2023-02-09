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

package org.springframework.aop.framework.autoproxy;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Conventions;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Utilities for auto-proxy aware components.
 * Mainly for internal use within the framework.
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see AbstractAutoProxyCreator
 */
public abstract class AutoProxyUtils {

	/**
	 * Bean定义属性，该属性可以指示Bean是否应该被其目标类代理(以防它首先被代理)
	 * <li>比如说使用了 {@link org.springframework.beans.factory.config.Scope @Scope} 注解并且设置了Cglib这里就是True</li>
	 * <p>Proxy factories can set this attribute if they built a target class proxy
	 * for a specific bean, and want to enforce that bean can always be cast
	 * to its target class (even if AOP advices get applied through auto-proxying).
	 * @see #shouldProxyTargetClass
	 */
	public static final String PRESERVE_TARGET_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(AutoProxyUtils.class, "preserveTargetClass");

	/**
	 * Bean定义属性，表示代理后的Bean的原始目标类
	 * <li>例如，用于在基于接口的代理后对目标类上的注释进行内省</li>
	 * @since 4.2.3
	 * @see #determineTargetClass
	 */
	public static final String ORIGINAL_TARGET_CLASS_ATTRIBUTE =
			Conventions.getQualifiedAttributeName(AutoProxyUtils.class, "originalTargetClass");


	/**
	 * 检查是否应该使用Cglib
	 * @param beanFactory the containing ConfigurableListableBeanFactory
	 * @param beanName the name of the bean
	 * @return whether the given bean should be proxied with its target class
	 */
	public static boolean shouldProxyTargetClass(
			ConfigurableListableBeanFactory beanFactory, @Nullable String beanName) {

		if (beanName != null && beanFactory.containsBeanDefinition(beanName)) {
			BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
			return Boolean.TRUE.equals(bd.getAttribute(PRESERVE_TARGET_CLASS_ATTRIBUTE));
		}
		return false;
	}

	/**
	 * Determine the original target class for the specified bean, if possible,
	 * otherwise falling back to a regular {@code getType} lookup.
	 * @param beanFactory the containing ConfigurableListableBeanFactory
	 * @param beanName the name of the bean
	 * @return the original target class as stored in the bean definition, if any
	 * @since 4.2.3
	 * @see org.springframework.beans.factory.BeanFactory#getType(String)
	 */
	@Nullable
	public static Class<?> determineTargetClass(
			ConfigurableListableBeanFactory beanFactory, @Nullable String beanName) {

		if (beanName == null) {
			return null;
		}
		if (beanFactory.containsBeanDefinition(beanName)) {
			BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);
			Class<?> targetClass = (Class<?>) bd.getAttribute(ORIGINAL_TARGET_CLASS_ATTRIBUTE);
			if (targetClass != null) {
				return targetClass;
			}
		}
		return beanFactory.getType(beanName);
	}

	/**
	 * 暴露Bean的原始类的Class对象
	 * <li>比如说到这来就说明要创建代理对象了，那么最后容器中存放的也是代理对象，所以我猜测这里就是为了让容器知道这个Bean实际的Class对象是什么</li>
	 * @param beanFactory the containing ConfigurableListableBeanFactory
	 * @param beanName the name of the bean
	 * @param targetClass the corresponding target class
	 * @since 4.2.3
	 */
	static void exposeTargetClass(
			ConfigurableListableBeanFactory beanFactory, @Nullable String beanName, Class<?> targetClass) {

		if (beanName != null && beanFactory.containsBeanDefinition(beanName)) {
			beanFactory.getMergedBeanDefinition(beanName).setAttribute(ORIGINAL_TARGET_CLASS_ATTRIBUTE, targetClass);
		}
	}

	/**
	 * Determine whether the given bean name indicates an "original instance"
	 * according to {@link AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX},
	 * skipping any proxy attempts for it.
	 * @param beanName the name of the bean
	 * @param beanClass the corresponding bean class
	 * @since 5.1
	 * @see AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
	 */
	static boolean isOriginalInstance(String beanName, Class<?> beanClass) {
		if (!StringUtils.hasLength(beanName) || beanName.length() !=
				beanClass.getName().length() + AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX.length()) {
			return false;
		}
		return (beanName.startsWith(beanClass.getName()) &&
				beanName.endsWith(AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX));
	}

}
