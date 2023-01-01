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

package org.springframework.web.method;

import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * 封装关于 {@link ControllerAdvice } 这种spring管理bean的信息，而不需要实例化它。
 *
 * <p>The {@link #findAnnotatedBeans(ApplicationContext)} method can be used to
 * discover such beans. However, a {@code ControllerAdviceBean} may be created
 * from any object, including ones without an {@code @ControllerAdvice} annotation.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.2
 */
public class ControllerAdviceBean implements Ordered {

	/**
	 * 标注了{@link ControllerAdvice }注解的bean的名称
	 */
	private final Object beanOrName;

	/**
	 * 是否单例
	 */
	private final boolean isSingleton;

	/**
	 * 标注了@controllerAdvice注解的bean的实例
	 */
	@Nullable
	private Object resolvedBean;

	/**
	 * 标注了@controllerAdvice注解的bean的Class
	 */
	@Nullable
	private final Class<?> beanType;

	/**
	 * 是有关@ControllerAdvice的切入规则的
	 */
	private final HandlerTypePredicate beanTypePredicate;

	/**
	 * bean工厂，一般是{@link org.springframework.beans.factory.support.DefaultListableBeanFactory}
	 */
	@Nullable
	private final BeanFactory beanFactory;

	/**
	 * 排序值
	 */
	@Nullable
	private Integer order;


	/**
	 * Create a {@code ControllerAdviceBean} using the given bean instance.
	 * @param bean the bean instance
	 */
	public ControllerAdviceBean(Object bean) {
		Assert.notNull(bean, "Bean must not be null");
		this.beanOrName = bean;
		this.isSingleton = true;
		this.resolvedBean = bean;
		this.beanType = ClassUtils.getUserClass(bean.getClass());
		this.beanTypePredicate = createBeanTypePredicate(this.beanType);
		this.beanFactory = null;
	}

	/**
	 * Create a {@code ControllerAdviceBean} using the given bean name and
	 * {@code BeanFactory}.
	 * @param beanName the name of the bean
	 * @param beanFactory a {@code BeanFactory} to retrieve the bean type initially
	 * and later to resolve the actual bean
	 */
	public ControllerAdviceBean(String beanName, BeanFactory beanFactory) {
		this(beanName, beanFactory, null);
	}

	/**
	 * Create a {@code ControllerAdviceBean} using the given bean name,
	 * {@code BeanFactory}, and {@link ControllerAdvice @ControllerAdvice}
	 * annotation.
	 * @param beanName the name of the bean
	 * @param beanFactory a {@code BeanFactory} to retrieve the bean type initially
	 * and later to resolve the actual bean
	 * @param controllerAdvice the {@code @ControllerAdvice} annotation for the
	 * bean, or {@code null} if not yet retrieved
	 * @since 5.2
	 */
	public ControllerAdviceBean(String beanName, BeanFactory beanFactory, @Nullable ControllerAdvice controllerAdvice) {
		Assert.hasText(beanName, "Bean name must contain text");
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		Assert.isTrue(beanFactory.containsBean(beanName), () -> "BeanFactory [" + beanFactory +
				"] does not contain specified controller advice bean '" + beanName + "'");

		this.beanOrName = beanName;
		this.isSingleton = beanFactory.isSingleton(beanName);
		this.beanType = getBeanType(beanName, beanFactory);
		this.beanTypePredicate = (controllerAdvice != null ? createBeanTypePredicate(controllerAdvice) :
				createBeanTypePredicate(this.beanType));
		this.beanFactory = beanFactory;
	}


	/**
	 * Get the order value for the contained bean.
	 * <p>As of Spring Framework 5.2, the order value is lazily retrieved using
	 * the following algorithm and cached. Note, however, that a
	 * {@link ControllerAdvice @ControllerAdvice} bean that is configured as a
	 * scoped bean &mdash; for example, as a request-scoped or session-scoped
	 * bean &mdash; will not be eagerly resolved. Consequently, {@link Ordered} is
	 * not honored for scoped {@code @ControllerAdvice} beans.
	 * <ul>
	 * <li>If the {@linkplain #resolveBean resolved bean} implements {@link Ordered},
	 * use the value returned by {@link Ordered#getOrder()}.</li>
	 * <li>If the {@linkplain #getBeanType() bean type} is known, use the value returned
	 * by {@link OrderUtils#getOrder(Class, int)} with {@link Ordered#LOWEST_PRECEDENCE}
	 * used as the default order value.</li>
	 * <li>Otherwise use {@link Ordered#LOWEST_PRECEDENCE} as the default, fallback
	 * order value.</li>
	 * </ul>
	 * @see #resolveBean()
	 */
	@Override
	public int getOrder() {
		if (this.order == null) {
			Object resolvedBean = null;
			if (this.beanFactory != null && this.beanOrName instanceof String) {
				String beanName = (String) this.beanOrName;
				String targetBeanName = ScopedProxyUtils.getTargetBeanName(beanName);
				boolean isScopedProxy = this.beanFactory.containsBean(targetBeanName);
				// Avoid eager @ControllerAdvice bean resolution for scoped proxies,
				// since attempting to do so during context initialization would result
				// in an exception due to the current absence of the scope. For example,
				// an HTTP request or session scope is not active during initialization.
				if (!isScopedProxy && !ScopedProxyUtils.isScopedTarget(beanName)) {
					resolvedBean = resolveBean();
				}
			}
			else {
				resolvedBean = resolveBean();
			}

			if (resolvedBean instanceof Ordered) {
				this.order = ((Ordered) resolvedBean).getOrder();
			}
			else if (this.beanType != null) {
				this.order = OrderUtils.getOrder(this.beanType, Ordered.LOWEST_PRECEDENCE);
			}
			else {
				this.order = Ordered.LOWEST_PRECEDENCE;
			}
		}
		return this.order;
	}

	/**
	 * Return the type of the contained bean.
	 * <p>If the bean type is a CGLIB-generated class, the original user-defined
	 * class is returned.
	 */
	@Nullable
	public Class<?> getBeanType() {
		return this.beanType;
	}

	/**
	 * 获取这个ControllerAdviseBean的bean实例，如果需要，可以通过BeanFactory解析bean名称。
	 */
	public Object resolveBean() {
		if (this.resolvedBean == null) {
			// 从容器中获取ControllerAdvise的实例对象
			Object resolvedBean = obtainBeanFactory().getBean((String) this.beanOrName);
			// 不要缓存非单例对象(例如，原型)
			if (!this.isSingleton) {
				return resolvedBean;
			}
			this.resolvedBean = resolvedBean;
		}
		return this.resolvedBean;
	}

	private BeanFactory obtainBeanFactory() {
		Assert.state(this.beanFactory != null, "No BeanFactory set");
		return this.beanFactory;
	}

	/**
	 * 检查给定的类是否满足切入规则
	 * {@code ControllerAdviceBean}.
	 * @param beanType the type of the bean to check
	 * @since 4.0
	 * @see ControllerAdvice
	 */
	public boolean isApplicableToBeanType(@Nullable Class<?> beanType) {
		return this.beanTypePredicate.test(beanType);
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ControllerAdviceBean)) {
			return false;
		}
		ControllerAdviceBean otherAdvice = (ControllerAdviceBean) other;
		return (this.beanOrName.equals(otherAdvice.beanOrName) && this.beanFactory == otherAdvice.beanFactory);
	}

	@Override
	public int hashCode() {
		return this.beanOrName.hashCode();
	}

	@Override
	public String toString() {
		return this.beanOrName.toString();
	}


	/**
	 * 通过给定的 ApplicationContext，找到带有 {@code @ControllerAdvice} 注解的bean，并将它们包装为 ControllerAdviceBean 实例
	 * @param context
	 * @return
	 */
	public static List<ControllerAdviceBean> findAnnotatedBeans(ApplicationContext context) {
		List<ControllerAdviceBean> adviceBeans = new ArrayList<>();
		// 遍历所有给定类型的所有bean名称
		for (String name : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, Object.class)) {
			// 确定不是代理bean
			if (!ScopedProxyUtils.isScopedTarget(name)) {
				// 拿到类上关于 @ControllerAdvice 的属性
				ControllerAdvice controllerAdvice = context.findAnnotationOnBean(name, ControllerAdvice.class);
				if (controllerAdvice != null) {
					// Use the @ControllerAdvice annotation found by findAnnotationOnBean()
					// in order to avoid a subsequent lookup of the same annotation.
					adviceBeans.add(new ControllerAdviceBean(name, context, controllerAdvice));
				}
			}
		}
		OrderComparator.sort(adviceBeans);
		return adviceBeans;
	}

	@Nullable
	private static Class<?> getBeanType(String beanName, BeanFactory beanFactory) {
		Class<?> beanType = beanFactory.getType(beanName);
		return (beanType != null ? ClassUtils.getUserClass(beanType) : null);
	}

	private static HandlerTypePredicate createBeanTypePredicate(@Nullable Class<?> beanType) {
		ControllerAdvice controllerAdvice = (beanType != null ?
				AnnotatedElementUtils.findMergedAnnotation(beanType, ControllerAdvice.class) : null);
		return createBeanTypePredicate(controllerAdvice);
	}

	private static HandlerTypePredicate createBeanTypePredicate(@Nullable ControllerAdvice controllerAdvice) {
		if (controllerAdvice != null) {
			return HandlerTypePredicate.builder()
					.basePackage(controllerAdvice.basePackages())
					.basePackageClass(controllerAdvice.basePackageClasses())
					.assignableType(controllerAdvice.assignableTypes())
					.annotation(controllerAdvice.annotations())
					.build();
		}
		return HandlerTypePredicate.forAnyHandlerType();
	}

}
