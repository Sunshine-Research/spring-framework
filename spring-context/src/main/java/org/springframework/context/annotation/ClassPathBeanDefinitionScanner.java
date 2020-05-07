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

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A bean definition scanner that detects bean candidates on the classpath,
 * registering corresponding bean definitions with a given registry ({@code BeanFactory}
 * or {@code ApplicationContext}).
 *
 * <p>Candidate classes are detected through configurable type filters. The
 * default filters include classes that are annotated with Spring's
 * {@link org.springframework.stereotype.Component @Component},
 * {@link org.springframework.stereotype.Repository @Repository},
 * {@link org.springframework.stereotype.Service @Service}, or
 * {@link org.springframework.stereotype.Controller @Controller} stereotype.
 *
 * <p>Also supports Java EE 6's {@link javax.annotation.ManagedBean} and
 * JSR-330's {@link javax.inject.Named} annotations, if available.
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see AnnotationConfigApplicationContext#scan
 * @see org.springframework.stereotype.Component
 * @see org.springframework.stereotype.Repository
 * @see org.springframework.stereotype.Service
 * @see org.springframework.stereotype.Controller
 * @since 2.5
 */
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

	private final BeanDefinitionRegistry registry;

	private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

	@Nullable
	private String[] autowireCandidatePatterns;

	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private boolean includeAnnotationConfig = true;


	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory.
	 * @param registry the {@code BeanFactory} to load bean definitions into, in the form
	 *                 of a {@code BeanDefinitionRegistry}
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
		this(registry, true);
	}

	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory.
	 * <p>If the passed-in bean factory does not only implement the
	 * {@code BeanDefinitionRegistry} interface but also the {@code ResourceLoader}
	 * interface, it will be used as default {@code ResourceLoader} as well. This will
	 * usually be the case for {@link org.springframework.context.ApplicationContext}
	 * implementations.
	 * <p>If given a plain {@code BeanDefinitionRegistry}, the default {@code ResourceLoader}
	 * will be a {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}.
	 * <p>If the passed-in bean factory also implements {@link EnvironmentCapable} its
	 * environment will be used by this reader.  Otherwise, the reader will initialize and
	 * use a {@link org.springframework.core.env.StandardEnvironment}. All
	 * {@code ApplicationContext} implementations are {@code EnvironmentCapable}, while
	 * normal {@code BeanFactory} implementations are not.
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @see #setResourceLoader
	 * @see #setEnvironment
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
		this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
	}

	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory and
	 * using the given {@link Environment} when evaluating bean definition profile metadata.
	 * <p>If the passed-in bean factory does not only implement the {@code
	 * BeanDefinitionRegistry} interface but also the {@link ResourceLoader} interface, it
	 * will be used as default {@code ResourceLoader} as well. This will usually be the
	 * case for {@link org.springframework.context.ApplicationContext} implementations.
	 * <p>If given a plain {@code BeanDefinitionRegistry}, the default {@code ResourceLoader}
	 * will be a {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}.
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @param environment       the Spring {@link Environment} to use when evaluating bean
	 *                          definition profile metadata
	 * @see #setResourceLoader
	 * @since 3.1
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment) {

		this(registry, useDefaultFilters, environment,
				(registry instanceof ResourceLoader ? (ResourceLoader) registry : null));
	}

	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory and
	 * using the given {@link Environment} when evaluating bean definition profile metadata.
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @param environment       the Spring {@link Environment} to use when evaluating bean
	 *                          definition profile metadata
	 * @param resourceLoader    the {@link ResourceLoader} to use
	 * @since 4.3.6
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment, @Nullable ResourceLoader resourceLoader) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		if (useDefaultFilters) {
			registerDefaultFilters();
		}
		setEnvironment(environment);
		setResourceLoader(resourceLoader);
	}


	/**
	 * Return the BeanDefinitionRegistry that this scanner operates on.
	 */
	@Override
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	/**
	 * Set the defaults to use for detected beans.
	 * @see BeanDefinitionDefaults
	 */
	public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
		this.beanDefinitionDefaults =
				(beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults());
	}

	/**
	 * Return the defaults to use for detected beans (never {@code null}).
	 * @since 4.1
	 */
	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		return this.beanDefinitionDefaults;
	}

	/**
	 * Set the name-matching patterns for determining autowire candidates.
	 * @param autowireCandidatePatterns the patterns to match against
	 */
	public void setAutowireCandidatePatterns(@Nullable String... autowireCandidatePatterns) {
		this.autowireCandidatePatterns = autowireCandidatePatterns;
	}

	/**
	 * Set the BeanNameGenerator to use for detected bean classes.
	 * <p>Default is a {@link AnnotationBeanNameGenerator}.
	 */
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	/**
	 * Set the ScopeMetadataResolver to use for detected bean classes.
	 * Note that this will override any custom "scopedProxyMode" setting.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 * @see #setScopedProxyMode
	 */
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}

	/**
	 * Specify the proxy behavior for non-singleton scoped beans.
	 * Note that this will override any custom "scopeMetadataResolver" setting.
	 * <p>The default is {@link ScopedProxyMode#NO}.
	 * @see #setScopeMetadataResolver
	 */
	public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
		this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
	}

	/**
	 * Specify whether to register annotation config post-processors.
	 * <p>The default is to register the post-processors. Turn this off
	 * to be able to ignore the annotations or to process them differently.
	 */
	public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
		this.includeAnnotationConfig = includeAnnotationConfig;
	}


	/**
	 * Perform a scan within the specified base packages.
	 * @param basePackages the packages to check for annotated classes
	 * @return number of beans registered
	 */
	public int scan(String... basePackages) {
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();

		doScan(basePackages);

		// Register annotation config processors, if necessary.
		if (this.includeAnnotationConfig) {
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}

		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}

	/**
	 * 对给定的扫描目录，开始递归扫描
	 * 返回所有注册的BeanDefinition
	 * <p>
	 * 此方法不会注册注解配置的processor，留给调用者进行处理
	 * @param basePackages 需要扫描的最顶层的包，比如com.sunshine.spring.boot.laboratory.aop
	 * @return 注册的BeanDefinition
	 */
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		for (String basePackage : basePackages) {
			// 获取当前目录下所有使用注解的，需要Spring管理的BeanDefinition，比如userService, UserController
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
				// 获取BeanDefinition的scope类型，即单例还是多例
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
				// 生成BeanName
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				if (candidate instanceof AbstractBeanDefinition) {
					// post-process处理BeanDefinition，主要是设置BeanDefinition的默认值
					postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
				}
				if (candidate instanceof AnnotatedBeanDefinition) {
					// 注解类型生成的BeanDefinition，主要是针对诸如@Primary @DependsOn等注解的是否使用，进行属性值设置
					AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
				}
				// 如上的两个BeanDefinition处理可能都会执行
				// 检查一下生成的BeanDefinition，主要是检测beanName是否可以使用
				if (checkCandidate(beanName, candidate)) {
					// 创建BeanDefinitionHolder
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
					// 需要注册BeanDefinition
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}

	/**
	 * 为给定的BeanDefinition提供更多的配置
	 * 超出了通过扫描组件类获取的内容
	 * @param beanDefinition 扫描出的BeanDefinition
	 * @param beanName       生成的BeanName
	 */
	protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
		// 设置当前BeanDefinition的默认值
		beanDefinition.applyDefaults(this.beanDefinitionDefaults);
		if (this.autowireCandidatePatterns != null) {
			beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
		}
	}

	/**
	 * 使用给定的注册器注册BeanDefinition
	 * <p>
	 * 可以由子类实现，比如适配了注册器的进程或者为每个扫描的Bean注册更多的BeanDefinition
	 * @param definitionHolder the bean definition plus bean name for the bean
	 * @param registry         the BeanDefinitionRegistry to register the bean with
	 */
	protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
	}


	/**
	 * 检查给定的BeanDefinition的beanName，确认相关联的BeanDefinition是需要注册还是和一个已存在的BeanDefinition冲突
	 * @param beanName       建议的BeanName
	 * @param beanDefinition 相关联的BeanDefinition
	 * @return 如果Bean可以被注册，则返回{@code true}
	 * 如果出现了已存在的，可并存的BeanDefinition，则返回{@code false}
	 * @throws ConflictingBeanDefinitionException 已存在的，无法兼容的BeanName
	 */
	protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
		// 如果BeanName还没有被使用，直接使用此BeanName
		if (!this.registry.containsBeanDefinition(beanName)) {
			return true;
		}
		// 如果BeanName已经被使用
		BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
		BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
		if (originatingDef != null) {
			existingDef = originatingDef;
		}
		// 两个BeanDefinition是否可以共存
		if (isCompatible(beanDefinition, existingDef)) {
			return false;
		}
		throw new ConflictingBeanDefinitionException("Annotation-specified bean name '" + beanName +
				"' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
				"non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]");
	}

	/**
	 * Determine whether the given new bean definition is compatible with
	 * the given existing bean definition.
	 * <p>The default implementation considers them as compatible when the existing
	 * bean definition comes from the same source or from a non-scanning source.
	 * @param newDefinition      the new bean definition, originated from scanning
	 * @param existingDefinition the existing bean definition, potentially an
	 *                           explicitly defined one or a previously generated one from scanning
	 * @return whether the definitions are considered as compatible, with the
	 * new definition to be skipped in favor of the existing definition
	 */
	protected boolean isCompatible(BeanDefinition newDefinition, BeanDefinition existingDefinition) {
		return (!(existingDefinition instanceof ScannedGenericBeanDefinition) ||  // explicitly registered overriding bean
				(newDefinition.getSource() != null && newDefinition.getSource().equals(existingDefinition.getSource())) ||  // scanned same file twice
				newDefinition.equals(existingDefinition));  // scanned equivalent class twice
	}


	/**
	 * Get the Environment from the given registry if possible, otherwise return a new
	 * StandardEnvironment.
	 */
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		return new StandardEnvironment();
	}

}
