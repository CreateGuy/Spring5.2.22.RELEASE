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

package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	//默认的排出过滤器
	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	//对延迟ImportSelector的处理器的排序规则，是针对order的
	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	//已经导入了的配置类
	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	//已处理过父类集合：像有多个类继承A类，那A类只需要处理一次即可
	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	//这个只有内部类，或者被@Import导入的类会放入这个双端队列
	private final ImportStack importStack = new ImportStack();

	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				//判断是否标志了注解的
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				//是spring内部引入的
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

		//调用延迟ImportSelector处理器
		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}


	/**
	 * 处理配置类(并不是单指有@Configuration的类，比如@Comonoent也算)
	 * @param configClass class对应的ConfigurationClass
	 * @param filter 排出过滤器
	 * @throws IOException
	 */
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		//判断这个配置类是否需要跳过
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}

		//看这个配置类是否已经被导入过
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		//如果已经被导入过了
		if (existingClass != null) {
			//这个配置类又被其他类导入了
			if (configClass.isImported()) {
				//旧的也是被其他人导入的
				if (existingClass.isImported()) {
					//然后都合并到existingClass的importedBy中
					existingClass.mergeImportedBy(configClass);
				}
				//否则忽略新导入的配置类；现有的非导入类将覆盖它。
				return;
			}
			else {
				//到这就说明新的配置类不是被@Import导入的，那么就移除旧的，换新的
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		//递归处理有父类的情况
		SourceClass sourceClass = asSourceClass(configClass, filter);
		//do while 表示递归处理类，因为一个类可能有父类
		//如果存在父类，则需要将configClass变成sourceClass去解析，然后返回sourceClass的父类
		do {
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		//放入已经处理的配置类集合中
		this.configurationClasses.put(configClass, configClass);
	}

	/**
	 * 处理配置类
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {

		//判断是否标志了@Component注解
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			//首先处理任何成员（嵌套）类
			processMemberClasses(configClass, sourceClass, filter);
		}

		//处理标注了@PropertySource注解的配置类
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				//这个方法只是将当前需要加载的配置类加入到环境上下文的属性源集合中
				//毕竟现在bean还没初始化，还只是一个ConfigurationClass，甚至还不是BeanDefinition
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		//处理标注了@ComponentScans和@ComponentScan的配置类
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		//当设置对应的注解和是否需要跳过
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			//遍历所有的componentScan注解：一个类可以使用多个ComponentScan和@ComponentScans
			for (AnnotationAttributes componentScan : componentScans) {
				//通过componentScan扫描器 获取满足条件的BeanDefinitionHolder
				//BeanDefinitionHolder是beanDefinition + beanName
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				//检查所有候选BeanDefinitionHolder，看是否是标志了@Configuration注解，并根据需要递归解析
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					//获得原始BeanDefinition
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					//有些可能就是原始的BeanDefinition，并没有进行封装，所有直接getBeanDefinition就是原始的BeanDefinition
					//像RootBeanDefinition会在代理中用到，他的getOriginatingBeanDefinition()就不为空
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					//判断当前BeanDefinition是否标志了@Configuration注解
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						//又递归解析
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		//处理标注了@Imports注解
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		//处理标注了@ImportResource注解
		//看是否有关于@ImportResource注解的属性
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			//获得需要导入的xml文件的位置
			String[] resources = importResource.getStringArray("locations");
			//获得怎么将xml中的bean转为BeanDefinition的转换器
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				//通过上下文环境解析xml文件位置，如果找不到会抛出异常
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				//加入到对应的集合中，后面会处理
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		//处理内部标注了@Bean的方法
		//返回内部标注了@Bean的方法元数据
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			//放入configClass中保存起来
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		//看class实现的接口内部是否有标注了@Bean的方法，如果有加入对应的集合中
		processInterfaces(configClass, sourceClass);

		//如果有父类，继续处理
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			//如果有父类，并且父类不是java开头的，并且还没有处理过，就继续递归调用
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {

				//加入已处理过父类集合中
				this.knownSuperclasses.put(superclass, configClass);
				//返回父类
				return sourceClass.getSuperClass();
			}
		}

		//没有父类，处理完成
		return null;
	}

	/**
	 * 注册在配置类本身的成员（嵌套）类
	 * @param configClass
	 * @param sourceClass
	 * @param filter 排出过滤器
	 * @throws IOException
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

		//先获取当前类的内部类
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				//判断内部类是否是一个候选类：
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			//由于传入的是一个list，里面是直接调用list的排序
			OrderComparator.sort(candidates);

			for (SourceClass candidate : candidates) {
				//importStack是用于判断循环导入的,如果包含在这里就说明出现了循环导入
				//importStack的push方法只会在这里和处理@Imports注解的时候会执行
				//也就是说可能会出现有一个A @Imports B ，importStack就有A了，然后 B 里面又有一个 标志了@Compent的 A
				if (this.importStack.contains(configClass)) {
					//抛出了循环导入异常
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					//将内部类推入importStack中
					//importStack是一个双端队列
					this.importStack.push(configClass);
					try {
						//到这就处理这个内部类
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						//推出一个内部类或者导入类(带有@Import)，是双端队列，一边出，一边进
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * 处理实现的接口内部是否有标注了@Bean的方法，如果有加入对应的集合中
	 * @param configClass
	 * @param sourceClass
	 * @throws IOException
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		//遍历所有实现的接口
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			//获得这个class上有@Bean注解的方法信息
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				//如果不是抽象的就加入对应的集合中
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			//继续处理，看接口有没有实现另外一个接口(递归调用)
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * 获得当前class内部标注了@Bean的方法元数据
	 * @param sourceClass 对应的class
	 * @return
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		//获得Class的所有注解元数据
		AnnotationMetadata original = sourceClass.getMetadata();
		//获得有关于@Bean的方法注解元数据
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		//当有方法注解元数据并且类注解元数据是一个标准的
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			//尝试通过ASM读取类文件以确定声明顺序，不懂
			//应该是在为了确保在不同的虚拟机上运行能够获得一样的beanMethods的顺序
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * 处理@PropertySource注解元数据。
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		//获得编码格式
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		//获得需要导入的配置文件
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		//获得找不到配置文件是否忽略的标志位
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		//获取专门用于创建属性源的工程
		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		//如果用户没有配置就用默认的DefaultPropertySourceFactory
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		//如果设置了配置文件，可能有多个
		for (String location : locations) {
			try {
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				//获取当前的配置文件
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				//添加配置文件对应的属性源到环境上线文的属性源集合中
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	/**
	 * 添加配置文件对应的属性源到环境上线文的属性源集合中
	 * @param propertySource
	 */
	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		//获取环境上下文中的所有属性源
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {

			PropertySource<?> existing = propertySources.get(name);
			//可能环境上下文中已经有了这样一个属性源
			if (existing != null) {
				//有可能这个新的属性源有了一些新的属性，是另外一个属性源，和原来的名称相同
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				//如果环境上下文中中原来的属性源就是一个混合的，那么就直接加到原来的里面的去，但是是最前面
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					//构建一个混合的属性源
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					//还替换了原来保存在环境上下文中的这个属性源
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			//将当前的propertySource加入到环境上下文的propertySources中
			propertySources.addLast(propertySource);
		}
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			//这里是放入最后一个的前面： a，b -> a，c，b
			propertySources.addBefore(firstProcessed, propertySource);
		}
		//加入已经加载的属性源的集合中，并不是环境上下文里面
		this.propertySourceNames.add(name);
	}


	/**
	 * 返回当前sourceClass中的@Import导入SourceClass(类)
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * 递归的方法获取SourceClass(类)上的@Import中引入的SourceClass(类)
	 * @param sourceClass 源头类
	 * @param imports 最终包含了导入的SourceClass(类)
	 * @param visited 只是内部有用
	 * @throws IOException
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			//每一次for循环结束都代表一个sourceClass(类)解析完毕
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			//添加有关@Import注解信息的属性到集合中
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	/**
	 * 处理引入的类
	 * @param configClass 源头的配置类
	 * @param currentSourceClass 源头的SourceClass
	 * @param importCandidates 从源头解析出来需要导入的类
	 * @param exclusionFilter 排出过滤器
	 * @param checkForCircularImports 是否检查循环导入
	 */
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		//需要导入的类为空
		if (importCandidates.isEmpty()) {
			return;
		}

		//判断是否存在循环导入
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			this.importStack.push(configClass);
			try {
				//遍历所有需要导入的类
				for (SourceClass candidate : importCandidates) {
					//判断是否是ImportSelector类型
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						//实例化一个ImportSelector
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						//获得这个selector设置的排除过滤器
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							//执行了这行代码后实际上就是 exclusionFilter + selectorFilter的排除过滤器在一起了
							//从内部代码上来看：也就是说会先执行exclusionFilter 再 执行selectorFilter
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						//如果是一个延迟导入选择器
						if (selector instanceof DeferredImportSelector) {
							//加入到延迟导入选择处理器中
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						//立即执行：和延迟导入选择器是执行时机的不同
						else {
							//获得需要导入的类名称
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							//将需要导入的类转为SourceClass
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							//开始处理导入类
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						Class<?> candidateClass = candidate.loadClass();
						//实例化一个ImportBeanDefinitionRegistrar
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					else {
						//候选类不是ImportSelector或ImportBeanDefinitionRegistrar那就当成@Configuration类处理
						//如果是成@Configuration类就有可能出现循环依赖，就放入下面的importStack的imports中
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						//处理此配置类
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	/**
	 * 检查是否会发生循环导入
	 * @param configClass
	 * @return
	 */
	private boolean isChainedImportOnStack(ConfigurationClass configClass) {

		//如果configClass包含在importStack中，就代表这个configClass被谁导入了
		//例如有两个键值对 a ： b(就代表 a 被 b导入了)， b ： a
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			//先通过a 拿到 b
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				//发现b 不是 a，即不是 a：a的情况
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				//又重新通过b拿到a，这样configClass 和 importingClass就一样了，就出现了循环导入
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * 获得对应Class和注解元数据的包装类
	 * @param configurationClass Class对应的ConfigurationClass
	 * @param filter 排除过滤器
	 * @return
	 * @throws IOException
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * 获得对应Class和注解元数据的包装类
	 * @param classType
	 * @param filter
	 * @return
	 * @throws IOException
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		//即使条件不通过，也会返回一个顶级的包装类
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			//注解如果有用Class或者Class数组作为返回值的方法，就要检查是否会出现类找不到异常的检查了
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			//通过类名解析强制ASM
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	/**
	 * 这个类本身就是一个双端队列
	 * 内部花有一个imports是用来保存有导入关系的
	 */
	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		//如果键值对是 a ：b，就代表a被b导入了
		//key是被被导入类的名称
		//value是导入类的注解元数据
		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		/**
		 * 删除有关importingClass的循环导入的信息
		 * @param importingClass
		 */
		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		/**
		 * 延时的ImportSelectors
		 */
		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				//注册进去
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			//获得延时ImportSelectors集合
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					//设置排序规则
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					//将延迟Import注册到分组处理器中
					deferredImports.forEach(handler::register);
					//开始处理
					handler.processGroupImports();
				}
			}
			finally {
				//应该是表明已经没有延迟导入选择器了
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		//key是一个分组器，value是属于这个分组器的 延迟Import
		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		//key是导入类的注解元数据，value是导入类对应的ConfigurationClass
		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		/**
		 * 往分组器集合中注册延迟Import
		 * @param deferredImport
		 */
		public void register(DeferredImportSelectorHolder deferredImport) {
			//获得当前导入选择器的分组器
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			//如果这个分组器没有注册过(有没有在groupings中)，就执行下面第三行的lambda表达式，如果存在就直接返回
			//lambda表达式：包装为DeferredImportSelectorGrouping放入集合中，并返回
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			//给这个分组器添加一个延迟ImportSelector
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		public void processGroupImports() {
			//遍历所有的分组器
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				//获得排除过滤器
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						//处理需要导入的类
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	/**
	 * 延迟ImportSelector的处理器
	 */
	private static class DeferredImportSelectorHolder {

		//导入类
		private final ConfigurationClass configurationClass;

		//导入类上@Import中的延迟类
		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		//当前分组器
		private final DeferredImportSelector.Group group;

		//延迟ImportSelector集合
		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * 处理所有的延迟导入选择器
		 */
		public Iterable<Group.Entry> getImports() {
			//遍历所有的延迟导入选择器，获得他们指定的配置类
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			//获得符合条件的自动配置类的迭代器
			return this.group.selectImports();
		}

		/**
		 * 获得这个分组器中所有延迟Import中的排除过滤器
		 * @return
		 */
		public Predicate<String> getCandidateFilter() {
			//有一个默认的排除过滤器
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		//需要导入的类(不是像启动类的一样的导入类)
		private final List<Entry> imports = new ArrayList<>();

		/**
		 * 将导入选择器中的所有需要导入的类放入imports集合中(springboot中有了新的实现)
		 * @param metadata
		 * @param selector
		 */
		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * 对于Class和对应的注解元数据的一个包装，允许以统一的方式处理带注解的类
	 */
	private class SourceClass implements Ordered {

		//Class
		private final Object source;

		//Class对应的注解元数据
		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		/**
		 * 判断是否是ImportSelector类型
		 * @param clazz
		 * @return
		 * @throws IOException
		 */
		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
