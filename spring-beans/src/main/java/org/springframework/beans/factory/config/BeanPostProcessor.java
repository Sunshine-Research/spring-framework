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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * 用于自定义修改新的bean实例的工厂钩子
 * 比如检查标记的接口，或者使用代理包装bean实例
 * <p>
 * 通常，使用{@link #postProcessBeforeInitialization}方法来支持类似于属性注入、标记接口的事情
 * 使用{@link #postProcessAfterInitialization}方法来进行使用代理包装bean实例的事情
 * <p>
 * 注册
 * {@code ApplicationContext}可以自动检测{@code BeanPostProcessor}bean实例，在它的bean definition，并把这些post-processors用于随后创建的任何bean
 * 一个简单的{@code BeanFactory}允许使用程序注册post-processors，将它们应用于通过bean工厂创建的所有bean
 * <p>
 * 排序
 * 在{@code ApplicationContext}中自动检测到的{@code BeanPostProcessor}bean会根据
 * {@link org.springframework.core.PriorityOrdered}和{@link org.springframework.core.Ordered}语义进行排序
 * 与此相反，使用{@code BeanFactory}通过程序注册的bean，将按照注册的顺序应用
 * 任何于使用的排序语义，会在程序注入post-processors时忽略掉
 * 而且{@code BeanPostProcessor}不会考虑{@link org.springframework.core.annotation.Order}注解
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see InstantiationAwareBeanPostProcessor
 * @see DestructionAwareBeanPostProcessor
 * @see ConfigurableBeanFactory#addBeanPostProcessor
 * @see BeanFactoryPostProcessor
 * @since 10.10.2003
 */
public interface BeanPostProcessor {

	/**
	 * 在任何bean实例化回调以前（比如InitializingBean#afterPropertiesSet，或者自定义的初始化方法），将此{@code BeanPostProcessor}应用于给定的新的bean实例
	 * bean已经填充了属性，返回的bean实例可能是原始bean的包装类
	 * <p>
	 * 默认的实现是返回给定的bean原样
	 * @param bean     新的bean实例
	 * @param beanName bean的名称
	 * @return 可以使用的bean实例，可以是原始的或者是包装的
	 * 如果为null，没有后续的BeanPostProcessors会被调用
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 */
	@Nullable
	default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * 在任何bean实例化回调以后（比如InitializingBean#afterPropertiesSet，或者自定义的初始化方法），将此{@code BeanPostProcessor}应用于给定的新的bean实例
	 * bean已经填充了属性，返回的bean实例可能是原始bean的包装类
	 * 假如是FactoryBean，此回调任务既会被FactoryBean调用，也会被FactoryBean创建的对象的调用（Spring 2.0）
	 * post-processor可以通过{@code bean instanceof FactoryBean}方法，决定是应用到FactoryBean还是FactoryBean创建的对象，或者是两者都应用
	 *
	 * 与其他{@code BeanPostProcessor}的回调任务相反，在{@link InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation}方法触发短路后，也会调用此回调方法
	 *
	 * 默认的实现是返回给定的bean原样
	 * @param bean     新的bean实例
	 * @param beanName bean的名称
	 * @return 可以使用的bean实例，可以是原始的或者是包装的
	 *         如果为null，没有后续的BeanPostProcessors会被调用
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.FactoryBean
	 */
	@Nullable
	default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
