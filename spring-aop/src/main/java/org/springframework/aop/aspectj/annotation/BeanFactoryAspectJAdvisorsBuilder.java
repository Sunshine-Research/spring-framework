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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为BeanFactory检索@AspectJ注解标识的Bean实例，并基于检索结果包装为Spring AOP的切面
 * 用于自动代理
 * @author Juergen Hoeller
 * @see AnnotationAwareAspectJAutoProxyCreator
 * @since 2.0.2
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory    the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * 在当前BeanFactory中查询使用AspectJ注解的切面bean
	 * 然后使用Spring AOP的Advisor类进行包装
	 * @return 使用AspectJ注解的{@link org.springframework.aop.Advisor}包装的列表
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		List<String> aspectNames = this.aspectBeanNames;
		// 首先从缓存中获取使用aspectJ注解的bean名称
		if (aspectNames == null) {
			// 进行同步校验锁
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					// 构建Advisor切面集合
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();
					// 获取当前BeanFactory中的所有Bean名称，包括父BeanFactory
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					for (String beanName : beanNames) {
						// 首先判定Bean是否合法
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// 不要过早实例化Bean，以防Container会对其进行缓存，但是不会织入
						// 获取当前Bean的Bean类型
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}
						// 判断当前Bean类型是否是Aspect
						// 即没有被ajc进行编译的，并且使用了@Aspect注解的
						if (this.advisorFactory.isAspect(beanType)) {
							// 添加符合条件的Aspect的bean名称
							aspectNames.add(beanName);
							// 创建Aspect元数据
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 如果是单例的切面
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								// 创建新的用于此Bean类型的Aspect类型的BeanFactory
								MetadataAwareAspectInstanceFactory factory =
										new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								// 将当前Bean包装为Advisor
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								// 如果Bean是单例的，放入到Advisor全局缓存中
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								} else {
									// 否则放入到aspect切面工程缓存中
									this.aspectFactoryCache.put(beanName, factory);
								}
								// 添加到筛选结果中
								advisors.addAll(classAdvisors);
							} else {
								// 切面非单例的情况
								// 如果当前Bean实例是单例的，抛出异常
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								// 创建多例的Bean实例的Aspect类型的BeanFactory
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								// 放入到切面工厂缓存中
								this.aspectFactoryCache.put(beanName, factory);
								// 添加到筛选结果中
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					// 设置全局Aspect切面Bean的全局缓存
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}
		// 如果没有符合条件的Aspect名称
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Advisor> advisors = new ArrayList<>();
		// 遍历所有的Aspect名称
		for (String aspectName : aspectNames) {
			// 从缓存的Advisor中获取对应Aspect Bean的Advisor
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);

			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			} else {
				// 如果从Advisor缓存中没有获取到，由上面的逻辑可知，可能AspectBean是多例的
				// 从对应的AspectBeanFactory中构建Advisor
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		// 返回筛选结果
		return advisors;
	}

	/**
	 * Aspect类型的Bean是否是合法的
	 * @param beanName Aspect Bean名称
	 * @return Bean是否合法
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
