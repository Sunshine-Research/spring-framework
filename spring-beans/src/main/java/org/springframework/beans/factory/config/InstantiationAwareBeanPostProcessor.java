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
import org.springframework.beans.PropertyValues;
import org.springframework.lang.Nullable;

import java.beans.PropertyDescriptor;

/**
 * {@link BeanPostProcessor}的子接口，可以添加实例化前回调任务，以及实例化之后，但在设置显示属性或发生自动装配之前的回调
 * <p>
 * 通常用于禁止特定目标bean的默认实例化
 * 比如，使用特殊TargetSources创建代理（池化目标，懒实例化目标等等），或者实现额外的注入策略比如字段注入
 * <p>
 * 需要注意的是：这个接口是一个用于特殊目的的接口，主要用于框架的内部使用
 * 建议尽可能实现普通的{@link BeanPostProcessor}接口，或者获得{@link InstantiationAwareBeanPostProcessorAdapter}来屏蔽此接口的扩展
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#setCustomTargetSourceCreators
 * @see org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator
 * @since 1.2
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

	/**
	 * 在目标bean实例化之前应用此BeanPostProcessor
	 * 返回的bean对象可能会作为一个代替目标bean来使用的代理，有效的抑制目标bean的默认实例化
	 * <p>
	 * 如果返回一个非空的对象，bean的创建过程将会被短路，仅仅能做的进一步的处理来自于自己配置的{@link BeanPostProcessor BeanPostProcessors}中的{@link #postProcessAfterInitialization}回调任务
	 * {@link #postProcessAfterInitialization}回调任务会通过其定义的类，应用到bean definition上，以及工厂方法的定义
	 * 在这种情况下，返回的bean类型将在此处传递
	 * post-processors可能会实现扩展的{@link SmartInstantiationAwareBeanPostProcessor}接口来预测它们将在此处返回的bean对象的类型
	 * 默认实现返回null
	 * @param beanClass 需要实例化的bean的类
	 * @param beanName  bean名称
	 * @return 代替默认目标bean实例的暴露的bean对象，或者是null来进行默认的实例化
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see #postProcessAfterInstantiation
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getBeanClass()
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getFactoryMethodName()
	 */
	@Nullable
	default Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * 通过构造方法或者工厂方法，在bean实例化之后执行的操作，但是需要发生在Spring属性注入之前（显示属性或者自动注入）
	 * 这是在Spring的自动装配之前，在给定bean实例上执行自定义字段注入的理想回调
	 * 默认的实现是返回{@code true}
	 * @param bean     创建的bean实例，此时还没有设置bean属性
	 * @param beanName bean名称
	 * @return 如果属性应该设置在bean上，返回{@code true}
	 * 如果需要跳过属性设置，则返回{@code false}
	 * 正常的实现应该返回{@code true}，返回{@code false}也可以防止后续的InstantiationAwareBeanPostProcessor实例在bean实力上调用
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see #postProcessBeforeInstantiation
	 */
	default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		return true;
	}

	/**
	 * 在工厂将它们应用于给定bean之前，对给定的属性值进行post-process处理，而无需使用属性描述符
	 * 如果提供了自定义的{@link #postProcessPropertyValues}的实现，需要返回{@code null}（默认），否则返回{@code pvs}
	 * 默认返回给定的{@code pvs}
	 * @param pvs      工厂即将应用的属性值
	 * @param bean     已经创建，但是还没有设置属性值的bean实例
	 * @param beanName bean名称
	 * @return 返回应用到给定的bean的真实属性值（可以是传入的PropertyValues实例），或者返回{@code null}，则继续使用现有属性，
	 * 但具体来事是继续调用{@link #postProcessPropertyValues}（需要对当前的bean类实现 {@code PropertyDescriptor}）
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see #postProcessPropertyValues
	 * @since 5.1
	 */
	@Nullable
	default PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
			throws BeansException {

		return null;
	}

	/**
	 * 在工厂将它们应用于给定bean之前，对给定的属性值进行post-process处理
	 * 允许检查是否已经满足所有的依赖，比如基于Required注解的bean setter属性
	 * 同时也允许替代要应用的属性，通常是基于原始的PropertyValues创建新的MutablePropertyValues实例，添加或移除特定值来实现
	 * 默认返回给定的{@code pvs}
	 * @param pvs      工厂即将应用的属性值
	 * @param pds      目标bean的相关属性描述符（具有忽略的依赖类型-工厂专门处理的依赖类型-已经过滤掉）
	 * @param bean     已经创建，但是还没有设置属性值的bean实例
	 * @param beanName bean名称
	 * @return 返回应用到给定的bean的真实属性值（可以是传入的PropertyValues实例），返回{@code null}则跳过属性注入
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 * @see #postProcessProperties
	 * @see org.springframework.beans.MutablePropertyValues
	 * @deprecated 作为5.1, 支持{@link #postProcessProperties(PropertyValues, Object, String)}方法
	 */
	@Deprecated
	@Nullable
	default PropertyValues postProcessPropertyValues(
			PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {

		return pvs;
	}

}
