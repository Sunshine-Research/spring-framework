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

package org.springframework.aop.framework.autoproxy.target;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.autoproxy.TargetSourceCreator;
import org.springframework.aop.target.AbstractBeanFactoryBasedTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Convenient superclass for
 * {@link org.springframework.aop.framework.autoproxy.TargetSourceCreator}
 * implementations that require creating multiple instances of a prototype bean.
 *
 * <p>Uses an internal BeanFactory to manage the target instances,
 * copying the original bean definition to this internal factory.
 * This is necessary because the original BeanFactory will just
 * contain the proxy instance created through auto-proxying.
 *
 * <p>Requires running in an
 * {@link org.springframework.beans.factory.support.AbstractBeanFactory}.
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.target.AbstractBeanFactoryBasedTargetSource
 * @see org.springframework.beans.factory.support.AbstractBeanFactory
 */
public abstract class AbstractBeanFactoryBasedTargetSourceCreator
		implements TargetSourceCreator, BeanFactoryAware, DisposableBean {

	protected final Log logger = LogFactory.getLog(getClass());

	private ConfigurableBeanFactory beanFactory;

	/**
	 * Internally used DefaultListableBeanFactory instances, keyed by bean name.
	 */
	private final Map<String, DefaultListableBeanFactory> internalBeanFactories =
			new HashMap<>();


	@Override
	public final void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Cannot do auto-TargetSource creation with a BeanFactory " +
					"that doesn't implement ConfigurableBeanFactory: " + beanFactory.getClass());
		}
		this.beanFactory = (ConfigurableBeanFactory) beanFactory;
	}

	/**
	 * Return the BeanFactory that this TargetSourceCreators runs in.
	 */
	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	//---------------------------------------------------------------------
	// TargetSourceCreator接口的实现
	//---------------------------------------------------------------------
	@Override
	@Nullable
	public final TargetSource getTargetSource(Class<?> beanClass, String beanName) {
		AbstractBeanFactoryBasedTargetSource targetSource =
				createBeanFactoryBasedTargetSource(beanClass, beanName);
		if (targetSource == null) {
			return null;
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Configuring AbstractBeanFactoryBasedTargetSource: " + targetSource);
		}

		DefaultListableBeanFactory internalBeanFactory = getInternalBeanFactoryForBean(beanName);

		// We need to override just this bean definition, as it may reference other beans
		// and we're happy to take the parent's definition for those.
		// Always use prototype scope if demanded.
		BeanDefinition bd = this.beanFactory.getMergedBeanDefinition(beanName);
		GenericBeanDefinition bdCopy = new GenericBeanDefinition(bd);
		if (isPrototypeBased()) {
			bdCopy.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		}
		internalBeanFactory.registerBeanDefinition(beanName, bdCopy);

		// Complete configuring the PrototypeTargetSource.
		targetSource.setTargetBeanName(beanName);
		targetSource.setBeanFactory(internalBeanFactory);

		return targetSource;
	}

	/**
	 * Return the internal BeanFactory to be used for the specified bean.
	 * @param beanName the name of the target bean
	 * @return the internal BeanFactory to be used
	 */
	protected DefaultListableBeanFactory getInternalBeanFactoryForBean(String beanName) {
		synchronized (this.internalBeanFactories) {
			DefaultListableBeanFactory internalBeanFactory = this.internalBeanFactories.get(beanName);
			if (internalBeanFactory == null) {
				internalBeanFactory = buildInternalBeanFactory(this.beanFactory);
				this.internalBeanFactories.put(beanName, internalBeanFactory);
			}
			return internalBeanFactory;
		}
	}

	/**
	 * 用于解决目标bean来构建的内部BeanFactory
	 * @param containingFactory 原始声明bean的BeanFactory
	 * @return 内部独立的BeanFactory，持有了一些目标bean的拷贝
	 */
	protected DefaultListableBeanFactory buildInternalBeanFactory(ConfigurableBeanFactory containingFactory) {
		// 设置父BeanFactory，以便正确的解析引用（容器的结构）
		DefaultListableBeanFactory internalBeanFactory = new DefaultListableBeanFactory(containingFactory);

		// 所有的BeanPostProcessors、Scopes等变为可用
		internalBeanFactory.copyConfigurationFrom(containingFactory);

		// 过滤掉AopInfrastructureBean类型的BeanPostProcessors
		// 因为这些仅适用于原始工厂中定义的bean
		internalBeanFactory.getBeanPostProcessors().removeIf(beanPostProcessor ->
				beanPostProcessor instanceof AopInfrastructureBean);

		return internalBeanFactory;
	}

	/**
	 * 在TargetSourceCreator关闭时，销毁内部的beanFactory
	 * @see #getInternalBeanFactoryForBean
	 */
	@Override
	public void destroy() {
		synchronized (this.internalBeanFactories) {
			for (DefaultListableBeanFactory bf : this.internalBeanFactories.values()) {
				bf.destroySingletons();
			}
		}
	}


	//---------------------------------------------------------------------
	// 子类需要实现的模板方法
	//---------------------------------------------------------------------

	/**
	 * 判断当前的TargetSourceCreator是否是多例的
	 * 将相应地设置目标bean定义的范围，默认为true
	 * @see org.springframework.beans.factory.config.BeanDefinition#isSingleton()
	 */
	protected boolean isPrototypeBased() {
		return true;
	}

	/**
	 * 如果需要创建bean的自定义目标资源，子类必须这个方法来返回新的AbstractPrototypeBasedTargetSource，
	 * 或者在不感兴趣的情况下，返回{@code null}
	 * 在这种情况下，没有特殊的目标资源会创建
	 * 子类不能在AbstractPrototypeBasedTargetSource调用{@code setTargetBeanName}或{@code setBeanFactory}
	 * 此类的{@code getTargetSource()}实现会调用这些方法
	 * @param beanClass 创建bean的目标资源的bean类型
	 * @param beanName  bean名称
	 * @return AbstractPrototypeBasedTargetSource，没有匹配的情况下返回null
	 */
	@Nullable
	protected abstract AbstractBeanFactoryBasedTargetSource createBeanFactoryBasedTargetSource(
			Class<?> beanClass, String beanName);

}
