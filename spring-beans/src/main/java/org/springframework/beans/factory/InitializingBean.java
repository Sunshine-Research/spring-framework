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

package org.springframework.beans.factory;

/**
 * bean可以实现的接口，bean需要在{@link BeanFactory}对其属性值设定之后，做出反应
 * 比如：执行自定义初始化，或者只是检查是否已设置所有必填属性
 * <p>
 * 实现{@code InitializingBean}的替代方法是给定一个初始化方法，比如在XML bean声明中
 * 对于所有的bean的生命周期方法，请看{@link BeanFactory BeanFactory javadocs}
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DisposableBean
 * @see org.springframework.beans.factory.config.BeanDefinition#getPropertyValues()
 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getInitMethodName()
 */
public interface InitializingBean {

	/**
	 * 通过包含的{@code BeanFactory}方法调用，在{@code BeanFactory}已经设置了bean的所有属性
	 * 并且需要满足{@link BeanFactoryAware}, {@code ApplicationContextAware}等
	 * <p>
	 * 此方法允许bean实例来执行整体配置的校验，以及在所有bean属性设置后的最后的实例化
	 * @throws Exception 如果配置错误(比如设置基本属性时失败)或者其他原因的实例化失败
	 */
	void afterPropertiesSet() throws Exception;

}
