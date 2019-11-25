/*
 * Copyright 2002-2012 the original author or authors.
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

import org.springframework.beans.BeansException;

/**
 * 可以知道它们所属的{@link BeanFactory}的接口
 * <p>
 * 比如，bean可以通过工厂查找协作的bean（依赖查找）
 * 需要注意的是，绝大多数bean都会选择接收引用来协作bean，以及相应的bean属性或者构造方法参数（依赖注入）
 * <p>
 * 对于所有bean的声明周期方法列表，请看{@link BeanFactory BeanFactory}
 * @author Rod Johnson
 * @author Chris Beams
 * @see BeanNameAware
 * @see BeanClassLoaderAware
 * @see InitializingBean
 * @see org.springframework.context.ApplicationContextAware
 * @since 11.03.2003
 */
public interface BeanFactoryAware extends Aware {

	/**
	 * 将拥有的bean工厂提供给bean实例的回调任务
	 * 在设置普通bean属性之后，在调用实例化回调任务之前调用
	 * 实例化回调任务包括：
	 * 1. {@link InitializingBean#afterPropertiesSet()}
	 * 2. 自定义的初始化方法
	 * @param beanFactory 已拥有的BeanFactory，永不为null
	 *                    bean可以立即调用工厂中的方法
	 * @throws BeansException 假如实例化发生错误
	 * @see BeanInitializationException
	 */
	void setBeanFactory(BeanFactory beanFactory) throws BeansException;

}
