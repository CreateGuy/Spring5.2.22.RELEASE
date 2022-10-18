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

package org.springframework.context.annotation;

import java.lang.reflect.Constructor;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 貌似只是为了实例化前的准备
 */
abstract class ParserStrategyUtils {

	/**
	 * 使用适当的构造函数实例化
	 */
	@SuppressWarnings("unchecked")
	static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo, Environment environment,
			ResourceLoader resourceLoader, BeanDefinitionRegistry registry) {

		Assert.notNull(clazz, "Class must not be null");
		Assert.isAssignable(assignableTo, clazz);
		//接口不能实例化
		if (clazz.isInterface()) {
			throw new BeanInstantiationException(clazz, "Specified class is an interface");
		}
		ClassLoader classLoader = (registry instanceof ConfigurableBeanFactory ?
				((ConfigurableBeanFactory) registry).getBeanClassLoader() : resourceLoader.getClassLoader());
		//通过合适的构造器创建实例对象
		T instance = (T) createInstance(clazz, environment, resourceLoader, registry, classLoader);
		//看是否实现了Aware方法，如果实现了就执行对应的方法
		ParserStrategyUtils.invokeAwareMethods(instance, environment, resourceLoader, registry, classLoader);
		return instance;
	}

	/**
	 * 创建一个实例
	 * @param clazz
	 * @param environment
	 * @param resourceLoader
	 * @param registry
	 * @param classLoader
	 * @return
	 */
	private static Object createInstance(Class<?> clazz, Environment environment,
			ResourceLoader resourceLoader, BeanDefinitionRegistry registry,
			@Nullable ClassLoader classLoader) {

		//获得当前class有几个构造方法
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		//只有 有参构造器
		if (constructors.length == 1 && constructors[0].getParameterCount() > 0) {
			try {
				Constructor<?> constructor = constructors[0];
				//看能否拿到入参
				Object[] args = resolveArgs(constructor.getParameterTypes(),
						environment, resourceLoader, registry, classLoader);
				//调用具体的构造方法，进行实例化
				return BeanUtils.instantiateClass(constructor, args);
			}
			catch (Exception ex) {
				throw new BeanInstantiationException(clazz, "No suitable constructor found", ex);
			}
		}
		//会调用无参构造方法进行实例化
		return BeanUtils.instantiateClass(clazz);
	}

	/**
	 * 通过环境，资源加载器，bean工厂，类加载器获得参数列表
	 * @param parameterTypes 参数类型
	 * @param environment 环境
	 * @param resourceLoader 资源加载器
	 * @param registry bean工厂
	 * @param classLoader 类加载器
	 * @return 解析完成的参数列表
	 */
	private static Object[] resolveArgs(Class<?>[] parameterTypes,
			Environment environment, ResourceLoader resourceLoader,
			BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

			Object[] parameters = new Object[parameterTypes.length];
			for (int i = 0; i < parameterTypes.length; i++) {
				parameters[i] = resolveParameter(parameterTypes[i], environment,
						resourceLoader, registry, classLoader);
			}
			return parameters;
	}

	/**
	 * 只能获得几种参数，否则就会抛出异常
	 * @param parameterType
	 * @param environment
	 * @param resourceLoader
	 * @param registry
	 * @param classLoader
	 * @return
	 */
	@Nullable
	private static Object resolveParameter(Class<?> parameterType,
			Environment environment, ResourceLoader resourceLoader,
			BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

		if (parameterType == Environment.class) {
			return environment;
		}
		if (parameterType == ResourceLoader.class) {
			return resourceLoader;
		}
		if (parameterType == BeanFactory.class) {
			return (registry instanceof BeanFactory ? registry : null);
		}
		if (parameterType == ClassLoader.class) {
			return classLoader;
		}
		throw new IllegalStateException("Illegal method parameter type: " + parameterType.getName());
	}

	/**
	 * 看bean是否实现了某些Aware接口，以进行后置处理
	 * @param parserStrategyBean bean
	 * @param environment 当前环境
	 * @param resourceLoader 资源加载器
	 * @param registry bean工厂
	 * @param classLoader 类加载器
	 */
	private static void invokeAwareMethods(Object parserStrategyBean, Environment environment,
			ResourceLoader resourceLoader, BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

		if (parserStrategyBean instanceof Aware) {
			if (parserStrategyBean instanceof BeanClassLoaderAware && classLoader != null) {
				((BeanClassLoaderAware) parserStrategyBean).setBeanClassLoader(classLoader);
			}
			if (parserStrategyBean instanceof BeanFactoryAware && registry instanceof BeanFactory) {
				((BeanFactoryAware) parserStrategyBean).setBeanFactory((BeanFactory) registry);
			}
			if (parserStrategyBean instanceof EnvironmentAware) {
				((EnvironmentAware) parserStrategyBean).setEnvironment(environment);
			}
			if (parserStrategyBean instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) parserStrategyBean).setResourceLoader(resourceLoader);
			}
		}
	}

}
