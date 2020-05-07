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
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用于Spring容器启动时，处理{@link Configuration @Configuration}类型的类的{@link BeanFactoryPostProcessor}
 * <p>
 * 如果使用了{@code <context:annotation-config/>}或{@code <context:component-scan/>}配置，将会默认添加
 * 否则，可能是由于其他BeanFactoryPostProcessor手动声明
 * <p>
 * 此post-processor是priority-ordered类型，这很重要
 * 在{@code @Configuration}类中声明的{@link Bean}方法，需要在注册其他{@link BeanFactoryPostProcessor}之前，先注册这些bean方法的BeanDefinition
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
		PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {

	/**
	 * A {@code BeanNameGenerator} using fully qualified class names as default bean names.
	 * <p>This default for configuration-level import purposes may be overridden through
	 * {@link #setBeanNameGenerator}. Note that the default for component scanning purposes
	 * is a plain {@link AnnotationBeanNameGenerator#INSTANCE}, unless overridden through
	 * {@link #setBeanNameGenerator} with a unified user-level bean name generator.
	 * @since 5.2
	 * @see #setBeanNameGenerator
	 */
	public static final AnnotationBeanNameGenerator IMPORT_BEAN_NAME_GENERATOR =
			new FullyQualifiedAnnotationBeanNameGenerator();

	private static final String IMPORT_REGISTRY_BEAN_NAME =
			ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	private boolean localBeanNameGeneratorSet = false;

	/**
	 * 使用类名作为默认的beanName
	 */
	private BeanNameGenerator componentScanBeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	/**
	 * 使用类的全限定名作为默认的beanName
	 */
	private BeanNameGenerator importBeanNameGenerator = IMPORT_BEAN_NAME_GENERATOR;


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * 设置在{@link Configuration}类中触发component扫描时需要使用的{@link BeanNameGenerator}
	 * 以及在注册{@link Import}等configuration类时
	 * 默认是标准的{@link AnnotationBeanNameGenerator}，用于扫描components（与默认兼容的{@link ClassPathBeanDefinitionScanner}）
	 * 及其用于导入的configuration类的变体（使用唯一的类的全限定名来代替标准的component重写）
	 * <p>
	 * 注意：策略不会覆盖到内部的{@link Bean}方法
	 * <p>
	 * 此setter方法通常仅在配置post-processor时作为一个独立的BeanDefinition
	 * 比如，不要使用专用的{@code AnnotationConfig*}的ApplicationContext语义或者{@code <context:annotation-config>}元素
	 * 针对context指定的任何beanName生成器将优先于此处设置的任何名称
	 * @since 3.1.1
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}


	/**
	 * 从BeanDefinition注册器的配置类派生出更多的BeanDefinition定义
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		int registryId = System.identityHashCode(registry);
		// BeanDefinitionRegistry已经调用
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		// beanFactory已经执行
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		this.registriesPostProcessed.add(registryId);

		processConfigBeanDefinitions(registry);
	}

	/**
	 * 在运行时为服务bean请求准备好配置类，使用CGLIB代理进行替代
	 * @param beanFactory 加载使用的BeanFactory
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		int factoryId = System.identityHashCode(beanFactory);
		// BeanFactory已经处理，则抛出异常
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		// 注册器已经处理此BeanFactory，则直接处理配配置的BeanDefinition
		// 如果没有处理，则需要添加配置类的BeanDefinition
		if (!this.registriesPostProcessed.contains(factoryId)) {
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}
		enhanceConfigurationClasses(beanFactory);
		// 添加ImportAwareBeanPostProcessor
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
	}

	/**
	 * 构建和校验基于{@link Configuration}类注册的configuration模型
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {

		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		// 获取此时所有已注册的BeanDefinition
		// 举个例子：laboratoryAOPLaboratory internalConfigurationAnnotationProcessor internalAutowiredAnnotationProcessor等
		String[] candidateNames = registry.getBeanDefinitionNames();

		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			if (beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE) != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			// 此时一般是laboratoryAOPLaboratory会放入到configCandidates中
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}

		// 如果没有找到Configuration类型的候选，则直接返回
		if (configCandidates.isEmpty()) {
			return;
		}

		// 根据使用的@Order注解进行排序
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});

		// 检测通过封闭的的context提供的自定义beanName的生成策略
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(
						AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}

		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// 解析每一个@Configuration类
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
			// 解析当前的@Configuration类
			parser.parse(candidates);
			// 校验所有进行了处理的配置类，可能包括我们自己声明的@Component @Service这些类
			parser.validate();

			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			// 移除已经解析的配置类
			configClasses.removeAll(alreadyParsed);

			// 创建BeanDefinition读取器
			if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
			// 加载所有的BeanDefinition，可以理解为：
			// 为除了扫描之外的例如@Import @ImportSource @Bean注解注册BeanDefinition
			this.reader.loadBeanDefinitions(configClasses);
			// 加载完成之后的BeanDefinition，视为已解析
			alreadyParsed.addAll(configClasses);
			// 清除所有候选处理的配置类
			candidates.clear();
			// 如果已注册的BeanDefinition数量＞候选处理的数量
			// 比如开始扫描的时候，只是扫描了一些BeanFactoryPostProcessors，但是现在可能UserService也已经注册了
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				// 获取所有注册的BeanDefinitionName
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				// 获取当前候选的配置类名称
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				Set<String> alreadyParsedClasses = new HashSet<>();
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				for (String candidateName : newCandidateNames) {
					if (!oldCandidateNames.contains(candidateName)) {
						// 如果是新加入的，则获取已经注册的BeanDefinition
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						// 对已注册的BeanDefinition进行@Configuration类型校验，但是却没有解析通过，则仍然放入候选中继续进行解析
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty());

		// 注册ImportRegistry，用于支持ImportAware类型的@Configuration类
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}

		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// 清除内部的缓存
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * 对BeanFactory进行后置处理，以搜索配置类的BeanDefinition
	 * 通过{@link ConfigurationClassEnhancer}对候选配置进行增强
	 * BeanDefinition的属性元数据用于确认候选状态
	 * 也就是处理@Configuration类，将其进行增强，用于正确处理其中的@Bean方法
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		// 刚在在扫描中，所有已经加载的BeanName
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			// 获取给定BeanName的BeanDefinition
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			// 获取configurationClass属性
			Object configClassAttr = beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE);
			MethodMetadata methodMetadata = null;
			// 如果是注解类型的BeanDefinition，获取方法的元数据
			if (beanDef instanceof AnnotatedBeanDefinition) {
				methodMetadata = ((AnnotatedBeanDefinition) beanDef).getFactoryMethodMetadata();
			}
			if ((configClassAttr != null || methodMetadata != null) && beanDef instanceof AbstractBeanDefinition) {
				// 解决BeanDefinition的BeanClass
				AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDef;
				if (!abd.hasBeanClass()) {
					try {
						abd.resolveBeanClass(this.beanClassLoader);
					} catch (Throwable ex) {
						throw new IllegalStateException(
								"Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
					}
				}
			}
			if (ConfigurationClassUtils.CONFIGURATION_CLASS_FULL.equals(configClassAttr)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				} else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				// 比如laboratoryAOPBootstrap就是一个配置bean
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		if (configBeanDefs.isEmpty()) {
			// 无需增强，直接返回
			return;
		}

		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// 如果@Configuration类进行了代理，则代理所有的目标类
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			Class<?> configClass = beanDef.getBeanClass();
			// 使用CGLIB进行代理
			Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
			if (configClass != enhancedClass) {
				if (logger.isTraceEnabled()) {
					logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
							"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
				}
				// 设置当前BeanDefinition为代理类
				beanDef.setBeanClass(enhancedClass);
			}
		}
	}


	private static class ImportAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessProperties method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			if (bean instanceof ImportAware) {
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				AnnotationMetadata importingClass = ir.getImportingClassFor(ClassUtils.getUserClass(bean).getName());
				if (importingClass != null) {
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
