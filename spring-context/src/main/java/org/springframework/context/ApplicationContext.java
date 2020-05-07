/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.context;

import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;

/**
 * 为应用提供配置的核心接口
 * 在应用运行过程中只可读，如果它的接口实现支持重载，可以进行重载
 * <p>
 * ApplicationContext提供了：
 * <ul>
 * <li>
 * 用于访问应用组件的BeanFactory方法，继承自{@link org.springframework.beans.factory.ListableBeanFactory}
 * <li>
 * 加载文件资源的能力，继承自{@link org.springframework.core.io.ResourceLoader}接口
 * <li>
 * 向注册的listener广播事件的能力，继承自{@link ApplicationEventPublisher}接口
 * <li>
 * 接收消息，支持国际化的能力，继承自{@link MessageSource}接口
 * <li>
 * 用可以继承来自父类的context，但是子context中的定义始终优先
 * 这意味着，举个例子，一个简单的父context可以被一个完整的web应用程序使用，因为每个servlet均有其专属的context
 * 专属context独立与其他servlet的context
 * </ul>
 *
 * <p>
 * 除了标准的{@link org.springframework.beans.factory.BeanFactory}声明周期能力之外
 * ApplicationContext的实现需要发现和调用{@link ApplicationContextAware}、{@link ApplicationEventPublisherAware}、
 * {@link MessageSourceAware}、{@link ResourceLoaderAware}类型的bean
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see ConfigurableApplicationContext
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.core.io.ResourceLoader
 */
public interface ApplicationContext extends EnvironmentCapable, ListableBeanFactory, HierarchicalBeanFactory,
		MessageSource, ApplicationEventPublisher, ResourcePatternResolver {

	/**
	 * @return ApplicationContext的唯一ID，如果没有则返回{@code null}
	 */
	@Nullable
	String getId();

	/**
	 * @return 部署的应用名称，默认为""
	 */
	String getApplicationName();

	/**
	 * @return 当前context的友好名称（永不为{@code null}）
	 */
	String getDisplayName();

	/**
	 * @return context首次加载时的时间戳
	 */
	long getStartupDate();

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * and this is the root of the context hierarchy.
	 * @return the parent context, or {@code null} if there is no parent
	 */
	@Nullable
	ApplicationContext getParent();

	/**
	 * 获取当前context的beanFactory类型
	 * <p>
	 * 应用程序通常不应该使用这段代码，除了需要在context之外再需要实例化bean实例
	 * 并对这些bean实例提供Spring Bean生命周期（全部或者部分）
	 * <p>
	 * 作为选择，{@link ConfigurableApplicationContext}接口提供的内部beanFactory也也可以访问{@link AutowireCapableBeanFactory}接口
	 * 当前方法主要用作ApplicationContext接口上的便捷、特定工具
	 * <p><b>
	 * 注意：在4.2版本，如果在context关闭后还调用此方法获取beanFactory，则会一直抛出IllegalStateException异常
	 * </b>
	 * 在当前的Spring版本，只有可以刷新的context才会抛出IllegalStateException异常
	 * 对于4.2版本，所有context实现需要遵守抛出IllegalStateException异常
	 * @return context的AutowireCapableBeanFactory
	 * @throws IllegalStateException context不支持{@link AutowireCapableBeanFactory}接口
	 *         						 或者是还没有持有可自动装配的beanFactory（比如还没有调用context#refresh()方法）
	 *								 或者context已经关闭
	 * @see ConfigurableApplicationContext#refresh()
	 * @see ConfigurableApplicationContext#getBeanFactory()
	 */
	AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException;

}
