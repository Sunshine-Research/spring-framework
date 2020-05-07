/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.lang.Nullable;

import java.util.Iterator;

/**
 * 除了{@link ConfigurableBeanFactory}它为分析和修改Bean Definition提供了遍历，还可以预先实例化单例
 * <p>
 * 这个{@link org.springframework.beans.factory.BeanFactory}子接口并不打算用于普通的应用程序代码
 * 通常情况下请使用{@link org.springframework.beans.factory.BeanFactory}或{@link org.springframework.beans.factory.ListableBeanFactory}
 * 此接口一般用于框架内部使用，比如在需要访问BeanFactory的配置方法时
 * @author Juergen Hoeller
 * @see org.springframework.context.support.AbstractApplicationContext#getBeanFactory()
 * @since 03.11.2003
 */
public interface ConfigurableListableBeanFactory
		extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 * @param type the dependency type to ignore
	 */
	void ignoreDependencyType(Class<?> type);

	/**
	 * 忽略给定依赖接口的自动注入
	 * <p>
	 * 通常由context使用，用于已经解决注册问题的其他依赖
	 * 比如通过BeanFactoryAware的BeanFactory，ApplicationContextAware的ApplicationContext
	 * <p>
	 * 默认的，只有BeanFactoryAware接口会被忽略，如果想要添加其他忽略类型，需要依次调用此接口
	 * @param ifc 需要beanFactory忽略的依赖
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	void ignoreDependencyInterface(Class<?> ifc);

	/**
	 * 为特殊的依赖注册相关联的自动注入值
	 * <p>
	 * 用于factory/context的引用，想要成为自动注入的，但却还没有声明的工厂中的bean
	 * 比如：类型为ApplicationContext的依赖关系已解析为该bean所在的ApplicationContext实例
	 * <p>
	 * 注意：在普通的beanFactory中没有注册这样的默认类型，beanFactory接口本身也没有
	 * @param dependencyType 需要注册的依赖类型，通常用于基类接口，比如BeanFactory，如果声明为自动装配依赖项
	 *                       则其扩展名也可以解析，比如ListableBeanFactory，只要给定依赖实际实现了此接口
	 * @param autowiredValue 相关联的自动注入值，可能是{@link org.springframework.beans.factory.ObjectFactory}的实现
	 *                       {@link org.springframework.beans.factory.ObjectFactory}实现允许懒加载注入
	 */
	void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

	/**
	 * Determine whether the specified bean qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * <p>This method checks ancestor factories as well.
	 * @param beanName the name of the bean to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return whether the bean should be considered as autowire candidate
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 */
	boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException;

	/**
	 * Return the registered BeanDefinition for the specified bean, allowing access
	 * to its property values and constructor argument value (which can be
	 * modified during bean factory post-processing).
	 * <p>A returned BeanDefinition object should not be a copy but the original
	 * definition object as registered in the factory. This means that it should
	 * be castable to a more specific implementation type, if necessary.
	 * <p><b>NOTE:</b> This method does <i>not</i> consider ancestor factories.
	 * It is only meant for accessing local bean definitions of this factory.
	 * @param beanName the name of the bean
	 * @return the registered BeanDefinition
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * defined in this factory
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Return a unified view over all bean names managed by this factory.
	 * <p>Includes bean definition names as well as names of manually registered
	 * singleton instances, with bean definition names consistently coming first,
	 * analogous to how type/annotation specific retrieval of bean names works.
	 * @return the composite iterator for the bean names view
	 * @since 4.1.2
	 * @see #containsBeanDefinition
	 * @see #registerSingleton
	 * @see #getBeanNamesForType
	 * @see #getBeanNamesForAnnotation
	 */
	Iterator<String> getBeanNamesIterator();

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@link BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 * @see #getBeanDefinition
	 * @see #getMergedBeanDefinition
	 */
	void clearMetadataCache();

	/**
	 * 冻结所有的BeanDefinition，此时意味着不允许修改或post-processed beanDefinition
	 * <p>此方法也就是允许beanFactory可以积极地缓存BeanDefinition的元数据
	 */
	void freezeConfiguration();

	/**
	 * Return whether this factory's bean definitions are frozen,
	 * i.e. are not supposed to be modified or post-processed any further.
	 * @return {@code true} if the factory's configuration is considered frozen
	 */
	boolean isConfigurationFrozen();

	/**
	 * 确认所有的非懒加载单例bean需要实例化，也需要同时考虑{@link org.springframework.beans.factory.FactoryBean FactoryBeans}
	 * 通常在beanFactory启动流程的末尾调用
	 * @throws BeansException 如果其中一个单例bean无法创建
	 * 						  注意：这可能会导致一些bean已经实例化并离开当前的beanFactory
	 * 						  这种情况下，需要调用{@link #destroySingletons()}来进行清理
	 * @see #destroySingletons()
	 */
	void preInstantiateSingletons() throws BeansException;

}
