/*
 * Copyright 2002-2016 the original author or authors.
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

import java.lang.reflect.Constructor;

/**
 * {@link InstantiationAwareBeanPostProcessor}的扩展接口，添加了用于预测已处理bean的最终类型的回调方法
 * <p>
 * 需要注意的是：这个接口是一个特殊用途的接口，主要用于框架的内部使用，一般而言，应用提供的post-processor应该简单实现普通的{@link BeanPostProcessor}接口
 * 或者从{@link InstantiationAwareBeanPostProcessorAdapter}类派生
 * 新的方法可能需要添加到这个接口，即使是in point releases
 * @author Juergen Hoeller
 * @see InstantiationAwareBeanPostProcessorAdapter
 * @since 2.0.3
 */
public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

	/**
	 * 预测最终从此processor的{@link #postProcessBeforeInstantiation}回调返回的bean类型
	 * 默认的实现返回{@code null}
	 * @param beanClass bean的原始类
	 * @param beanName  bean的名称
	 * @return bean的类型，如果无法预测，返回{@code null}
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 */
	@Nullable
	default Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * 确认需要在给定bean上使用的候选构造方法
	 * 默认的实现是返回null
	 * @param beanClass bean的原始类
	 * @param beanName  bean的名称
	 * @return 候选的构造方法，如果没有指定，返回值{@code null}
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 */
	@Nullable
	default Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName)
			throws BeansException {

		return null;
	}

	/**
	 * 获取早期访问指定bean的引用，通常用于解决循环依赖
	 * 此回调方法提供了提早暴露包装类的机会，也就是在目标bean实例完全初始化之前暴露
	 * 暴露的对象应该相当于{@link #postProcessBeforeInitialization}/{@link #postProcessAfterInitialization}否则会暴露
	 * 需要注意的是，此方法的返回值将会用于bean引用，除非post-processor返回了从所属的post-processor回调
	 * 换句话说，这些post-process回调任务可能最终暴露相同的引用，或者从这些后续的回调中返回原始的bean实例
	 * （如果已经为该方法的调用构建了受影响的包装，则默认情况下它将作为最终bean引用公开）
	 * 默认实现是返回给定bean
	 * @param bean     bean的原始类
	 * @param beanName bean的名称
	 * @return 暴露bean引用的对象（通常使用形参的bean实例对象作为默认）
	 * @throws org.springframework.beans.BeansException 假如出现错误，抛出异常
	 */
	default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
