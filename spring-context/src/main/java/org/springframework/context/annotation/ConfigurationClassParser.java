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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
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

/**
 * 解析{@link Configuration}类的BeanDefinition，选择{@link ConfigurationClass}类的集合
 * 解析单个Configuration类可能会对导致任意数量的ConfigurationClass类对象，因为一个Configuration类可能会使用@Import注解导入另一个ConfigurationCla）
 * <p>
 * 此类有助于区分解析配置结构的关注与基于此模型的内容注册BeanDefinition对象的关注
 * （需要立即注册的{@code @ComponentScan}注解）
 *
 * <p>
 * 基于ASM的实现避免了反射和预先类加载，以此来内部高效操作Spring容器的懒加载
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @see ConfigurationClassBeanDefinitionReader
 * @since 3.0
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

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

	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

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

	/**
	 * 解析Configuration类的BeanDefinition
	 * @param configCandidates Configuration类的BeanDefinition
	 */
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		// 迭代进行解析
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				// 现在一般都是使用@Configuration注解进行配置
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				} else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				} else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			} catch (BeanDefinitionStoreException ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}

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

	/**
	 * 解析Configuration类的BeanDefinition
	 * @param metadata BeanDefinition声明的注解的元数据
	 * @param beanName beanName
	 * @throws IOException
	 */
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
	 * 解析Configuration类的BeanDefinition
	 * @param configClass @Configuration配置类
	 * @param filter      过滤器
	 * @throws IOException
	 */
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		// 判断当前配置类是否使用了@Condition注解，并且满足@Condition条件
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}
		// 检查配置类是否已经加载
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		// 如果配置类已经加载
		if (existingClass != null) {
			if (configClass.isImported()) {
				// 配置类是否是Import类型，已存在配置类也是Import类型，则对两次加载进行合并
				if (existingClass.isImported()) {
					existingClass.mergeImportedBy(configClass);
				}
				// 否则，忽略新Import的配置类的加载，因为存在非Import配置类来对其进行重写
				return;
			} else {
				// 此时配置类为非Import类型
				// 需要找到明确的bean定义，可能需要取代已Import的配置，也就是已存在的配置
				// 我们需要移除旧的，加载新的
				this.configurationClasses.remove(configClass);
				this.knownSuperclasses.values().removeIf(configClass::equals);
			}
		}

		// 递归执行配置类，以及它的超类结构
		// 举例：此时是LaboratoryAOPBootstrap
		SourceClass sourceClass = asSourceClass(configClass, filter);
		do {
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);

		this.configurationClasses.put(configClass, configClass);
	}

	/**
	 * 通过从配置类中读取注解，成员变量和方法，处理和构建一个完整的{@link ConfigurationClass}
	 * 随着相关资源的发现，此方法可以被调用多次
	 * @param configClass 需要构建的配置类
	 * @param sourceClass 需要解析的配置类
	 * @return 超类，或者{@code null}，{@code null}意味着没有需要进行处理的关联配置类
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {
		// 如果使用了@Component注解，还需要处理成员的类
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
			// 递归处理当前配置类的内部类
			processMemberClasses(configClass, sourceClass, filter);
		}

		// 处理@PropertySource注解，用于填充Environment的值
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			// 如果是可配置的Environment，则对使用@PropertySource的属性值进行填充
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			} else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// 处理@ComponentScan注解，比如@SpringBootApplication就使用了这个注解
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			// 迭代所有使用的@ComponentScan注解
			for (AnnotationAttributes componentScan : componentScans) {
				// 使用了@ComponentScan注解，我们就会立即执行扫描，这些BeanDefinition在扫描过程中均已注册
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// 由于只是对这些BeanDefinition进行解析，并未对立面的属性进行解析，接下来需要对属性进行递归
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					// 并对配置类进行解析
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// 处理@Import注解
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// 处理@ImportSource注解，一般用仍然使用的xml配置
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			// 获取资源所在的位置
			String[] resources = importResource.getStringArray("locations");
			// 使用读取器进行解析，
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			// 迭代每个资源
			for (String resource : resources) {
				// 检验资源是否需要注入到Environment中
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				// 将已解析的资源放入到importedResource缓存中
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// 处理@Bean方法
		// 获取所有使用@Bean方法的元数据
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			// 添加为ConfigurationMethod
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		// 处理所有接口的default方法
		processInterfaces(configClass, sourceClass);

		// 处理超类
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// 如果当前解析的配置类含有非java或未解析的过的超类，则需要继续返回解析超类的元数据
				return sourceClass.getSuperClass();
			}
		}

		// 无需要解析的超类，解析完成
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	/**
	 * @param configClass 需要处理的配置类的信息
	 * @param sourceClass 需要处理的类
	 * @param filter      配置类的过滤条件，一般是以"java.lang.annotation."或"org.springframework.stereotype."开头的注解
	 * @throws IOException
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
									  Predicate<String> filter) throws IOException {
		// 获取当前配置类的所有内部类
		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		// 如果有需要处理的内部类
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			// 筛选出内部类中使用@Configuration注解的类
			for (SourceClass memberClass : memberClasses) {
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			// 如果这些类使用@Order进行排序，将会遵循设定的规则
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				// 当前解析链路中，引用栈中已经包含了需要解析的配置类
				if (this.importStack.contains(configClass)) {
					// 出现了循环Import的问题
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				} else {
					// 否则进行引用
					this.importStack.push(configClass);
					try {
						// 先处理内部类
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					} finally {
						// 处理完成之后，取消当前Import处理的标志
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * 处理由配置类实现的接口的所有默认方法
	 * @param configClass 配置类
	 * @param sourceClass 当前处理的类
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		// 获取当前处理类的所有接口
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			// 递归处理实现接口的所有@Bean方法
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			// 处理所有接口实现的@Bean方法的元数据
			for (MethodMetadata methodMetadata : beanMethods) {
				// 如果方法并不是抽象的，即为默认实现的方法
				if (!methodMetadata.isAbstract()) {
					// 需要注册为ConfigurationMethod
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			// 递归处理当前接口，是否也实现了的默认方法
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * 递归所有@Bean方法的元数据
	 * @param sourceClass 当前配置的类
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		// 获取配置类的元数据
		AnnotationMetadata original = sourceClass.getMetadata();
		// 获取所有使用了@Bean注解的方法
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// 尝试使用ASM读取类，以确定性声明顺序
			// 使用ASM的原因是因为JVM标准的反射返回的方法是以主观的顺序
			// 在相同的JVM，相同的服务，可能还会返回不同的顺序
			try {
				// 使用ASM读取的信息
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				// 获取当前配置类中所有使用@Bean的方法
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				// 如果ASM解析出的方法数量≥从元数据中解析出的数量
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					// 取两个集合中相交的部分
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// ASM扫描的和配置类元数据中获取的是相同的了
						beanMethods = selectedMethods;
					}
				}
			} catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
			}
		}
		return beanMethods;
	}


	/**
	 * 处理使用@PropertySource注解的元数据
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		// 获取@PropertySource的来源位置，可以是application.yml，至少需要一个
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		for (String location : locations) {
			try {
				// 使Environment填充来自@PropertySource的资源值
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			} catch (IllegalArgumentException | FileNotFoundException | UnknownHostException ex) {
				// 无法解析PlaceHolders或无法找到资源的情况下
				if (ignoreResourceNotFound) {
					// 如果可以忽略注入，则记录日志后返回
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				} else {
					// 其他情况下抛出异常
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				} else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		} else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * 返回所有的带有{@code @Import}类
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * 递归获取所有声明为{@code @Import}的值
	 * 和大多数元注解不同，可以使用多个{@code @Import}来声明多个不同的值
	 * <p>
	 * 比如，除了来自{@code @Enable}的meta-imports之外，{@code @Configuration}类还通常直接声明{@code @Import}
	 * @param sourceClass 需要进行搜寻的类
	 * @param imports     需要搜集的import集合
	 * @param visited     记录追踪的类，比年无限递归
	 * @throws IOException 在读取类的元数据过程中出现异常
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {
		// 记录遍历的类
		if (visited.add(sourceClass)) {
			// 获取当前类的所有注解
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				// 判断注解类型中是否包含Import注解类型
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					// 递归获取所有注解的Import值
					collectImports(annotation, imports, visited);
				}
			}
			// 添加当前类使用的Import注解时赋的值
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
								Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
								boolean checkForCircularImports) {
		if (importCandidates.isEmpty()) {
			return;
		}
		// 如果需要检测循环Import，并且Import栈是链式的
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			// 出现循环且是链式的，则会抛出异常
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		} else {
			// 向扫描栈中加入当前配置类
			this.importStack.push(configClass);
			try {
				// 比如@EnableAutoConfiguration中引入的@AutoConfigurationPackage注解就是引入了@Import(AutoConfigurationPackages.Registrar.class)
				for (SourceClass candidate : importCandidates) {
					if (candidate.isAssignable(ImportSelector.class)) {
						// 如果是ImportSelect类型，比如AutoConfigurationImportSelector
						// 加载Import进来的类
						Class<?> candidateClass = candidate.loadClass();
						// 实例化出一个ImportSelector的具体类
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						// 获取ImportSelector的排除条件
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						// 如果存在不包含条件，给定的断言中将加入新的断言条件
						// 之前的断言条件是配置类的过滤条件，一般是以"java.lang.annotation."或"org.springframework.stereotype."开头的注解
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter);
						}
						// 如果是延迟进行的ImportSelector，则会延迟进行处理
						if (selector instanceof DeferredImportSelector) {
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						} else {
							// 否则立即执行Import
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							// 回到方法调用起始，递归处理@Import
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					} else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Import的类是ImportBeanDefinitionRegistrar类型，用于代理注册额外的BeanDefinition
						Class<?> candidateClass = candidate.loadClass();
						// 真是情况是使用实例化了一个ImportBeanDefinitionRegistrar实例
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						// 构建额外引入的BeanDefinition
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					} else {
						// Import进的类既不是ImportSelector也不是ImportBeanDefinitionRegistrar，将它按照@Configuration类型的类进行处理
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						// 解析当前类的BeanDefinition，并注册
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			} catch (BeanDefinitionStoreException ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
								configClass.getMetadata().getClassName() + "]", ex);
			} finally {
				// 遍历完成之后，弹出当前处理的配置类
				this.importStack.pop();
			}
		}
	}

	/**
	 * 判断当前扫描的Import是否是链式的
	 * @param configClass 扫描的配置类
	 * @return 当前扫描的Import是否是链式的
	 */
	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		} catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain {@link SourceClass SourceClasss} from class names.
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
			} catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext(); ) {
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

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass    the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			} else {
				this.deferredImportSelectors.add(holder);
			}
		}

		public void process() {
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					deferredImports.forEach(handler::register);
					handler.processGroupImports();
				}
			} finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {

		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}

		public void processGroupImports() {
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					} catch (BeanDefinitionStoreException ex) {
						throw ex;
					} catch (Throwable ex) {
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


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

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

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
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

		private final List<Entry> imports = new ArrayList<>();

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
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			} else {
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
				} catch (NoClassDefFoundError err) {
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
				} catch (IOException ex) {
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
			} else {
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
						} catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			} else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						} catch (Throwable ex) {
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
				} catch (ClassNotFoundException ex) {
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
