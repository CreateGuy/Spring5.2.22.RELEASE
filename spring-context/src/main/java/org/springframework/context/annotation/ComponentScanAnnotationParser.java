/*
 * Copyright 2002-2021 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Parser for the @{@link ComponentScan} annotation.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @see ClassPathBeanDefinitionScanner#scan(String...)
 * @see ComponentScanBeanDefinitionParser
 */
class ComponentScanAnnotationParser {

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanNameGenerator beanNameGenerator;

	private final BeanDefinitionRegistry registry;


	public ComponentScanAnnotationParser(Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator beanNameGenerator, BeanDefinitionRegistry registry) {

		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.beanNameGenerator = beanNameGenerator;
		this.registry = registry;
	}


	public Set<BeanDefinitionHolder> parse(AnnotationAttributes componentScan, String declaringClass) {
		//创建一个类扫描器
		//useDefaultFilters是是否使用默认的filter进行包扫描，而默认的是可以扫描@Service,@Controller和@Repository的注解
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.registry,
				componentScan.getBoolean("useDefaultFilters"), this.environment, this.resourceLoader);

		//获取名称生产器
		Class<? extends BeanNameGenerator> generatorClass = componentScan.getClass("nameGenerator");
		//判断用户是否设置过名称生产器的
		boolean useInheritedGenerator = (BeanNameGenerator.class == generatorClass);
		//如果没有设置过就用自己默认的
		//而这个默认的是当时创建ConfigurationClassParser的时候，也会创建ComponentScanAnnotationParser而是在的
		scanner.setBeanNameGenerator(useInheritedGenerator ? this.beanNameGenerator :
				//设置了就使用空构造器实例化一个
				//正常实例化后应该有对象引用保存，所以我认为这里是看能否创建这个对象
				BeanUtils.instantiateClass(generatorClass));

		//看是否是代理
		ScopedProxyMode scopedProxyMode = componentScan.getEnum("scopedProxy");
		if (scopedProxyMode != ScopedProxyMode.DEFAULT) {
			scanner.setScopedProxyMode(scopedProxyMode);
		}
		else {
			//设置范围解析器
			Class<? extends ScopeMetadataResolver> resolverClass = componentScan.getClass("scopeResolver");
			scanner.setScopeMetadataResolver(BeanUtils.instantiateClass(resolverClass));
		}

		//设置资源文件的类型范围
		scanner.setResourcePattern(componentScan.getString("resourcePattern"));

		//添加包含过滤器：匹配这些过滤器的就可以加入到容器中
		for (AnnotationAttributes filter : componentScan.getAnnotationArray("includeFilters")) {
			//includeFilters属性是可以设置多个 typeFilter进行过滤的
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addIncludeFilter(typeFilter);
			}
		}
		//添加排除过滤器：匹配这些过滤器的就不可以加入到容器中
		for (AnnotationAttributes filter : componentScan.getAnnotationArray("excludeFilters")) {
			for (TypeFilter typeFilter : typeFiltersFor(filter)) {
				scanner.addExcludeFilter(typeFilter);
			}
		}

		//是否进行懒加载
		boolean lazyInit = componentScan.getBoolean("lazyInit");
		if (lazyInit) {
			scanner.getBeanDefinitionDefaults().setLazyInit(true);
		}

		//获得扫描包路径
		Set<String> basePackages = new LinkedHashSet<>();
		String[] basePackagesArray = componentScan.getStringArray("basePackages");
		for (String pkg : basePackagesArray) {
			//貌似是去除包路径中一些不合法的字符
			String[] tokenized = StringUtils.tokenizeToStringArray(this.environment.resolvePlaceholders(pkg),
					ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
			Collections.addAll(basePackages, tokenized);
		}
		//获得某个类的
		for (Class<?> clazz : componentScan.getClassArray("basePackageClasses")) {
			//从某个类的包路径开始扫描
			basePackages.add(ClassUtils.getPackageName(clazz));
		}
		//如果basePackages和basePackageClasses都没有设置，就用当前类的包路径进行扫描
		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(declaringClass));
		}

		//添加一个默认的排除过滤器，貌似是当前类就不用放入容器中了
		scanner.addExcludeFilter(new AbstractTypeHierarchyTraversingFilter(false, false) {
			@Override
			protected boolean matchClassName(String className) {
				return declaringClass.equals(className);
			}
		});
		//准备工作做完，开始扫描
		return scanner.doScan(StringUtils.toStringArray(basePackages));
	}

	/**
	 * 返回一个候选的TypeFilter集合
	 * @param filterAttributes
	 * @return
	 */
	private List<TypeFilter> typeFiltersFor(AnnotationAttributes filterAttributes) {
		List<TypeFilter> typeFilters = new ArrayList<>();
		//获取这个filter以什么类型进行筛选
		FilterType filterType = filterAttributes.getEnum("type");

		//获取当前这个filter设置的所有 TypeFilter
		for (Class<?> filterClass : filterAttributes.getClassArray("classes")) {
			switch (filterType) {
				//如果是按照注解过滤
				case ANNOTATION:
					Assert.isAssignable(Annotation.class, filterClass,
							"@ComponentScan ANNOTATION type filter requires an annotation type");
					@SuppressWarnings("unchecked")
					Class<Annotation> annotationType = (Class<Annotation>) filterClass;
					typeFilters.add(new AnnotationTypeFilter(annotationType));
					break;
				//如果是按照类型过滤
				case ASSIGNABLE_TYPE:
					typeFilters.add(new AssignableTypeFilter(filterClass));
					break;
				//如果是用户自定义的
				case CUSTOM:
					Assert.isAssignable(TypeFilter.class, filterClass,
							"@ComponentScan CUSTOM type filter requires a TypeFilter implementation");

					TypeFilter filter = ParserStrategyUtils.instantiateClass(filterClass, TypeFilter.class,
							this.environment, this.resourceLoader, this.registry);
					typeFilters.add(filter);
					break;
				default:
					throw new IllegalArgumentException("Filter type not supported with Class value: " + filterType);
			}
		}

		//获取表达式
		//要按照表达式过滤，就不能设置classes属性，不然上面那个for循环就会抛出异常
		for (String expression : filterAttributes.getStringArray("pattern")) {
			switch (filterType) {
				//按照ASPECTJ表达式过滤
				case ASPECTJ:
					typeFilters.add(new AspectJTypeFilter(expression, this.resourceLoader.getClassLoader()));
					break;
				//按照正则表达式过滤
				case REGEX:
					typeFilters.add(new RegexPatternTypeFilter(Pattern.compile(expression)));
					break;
				default:
					throw new IllegalArgumentException("Filter type not supported with String pattern: " + filterType);
			}
		}

		return typeFilters;
	}

}
