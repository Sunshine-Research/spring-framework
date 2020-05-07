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

package org.springframework.beans.factory;

/**
 * 在{@link BeanFactory}启动时，单例Bean实例化结束阶段触发的回调接口
 * 单例bean可以实现此接口来执行一些在常规单例bean实例化之后的实现
 * 避免一些因为过早实例化而带来的副作用（比如{@link ListableBeanFactory#getBeansOfType}的调用）
 * 从这个意义上来讲，它是替代{@link InitializingBean}的
 * {@link InitializingBean}用于触发在bean本地构建语义之后的动作
 * <p>
 * 这个回调任务可以认为是{@link org.springframework.context.event.ContextRefreshedEvent}
 * 但是不需要一个{@link org.springframework.context.ApplicationListener}listener
 * 无需筛选整个context层次结构中的上下文引用，这也意味着对于{@code beans}包的依赖性最小
 * 并且受到独立{@link ListableBeanFactory}实现的尊重，并不仅仅在{@link org.springframework.context.ApplicationContext}环境下
 * <p>
 * 注意：如果打算启动或管理异步任务，更好地实现{@link org.springframework.context.Lifecycle}接口
 * 来代替用于运行时管理的更丰富的而模型，并允许分阶段启动和关闭
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#preInstantiateSingletons()
 * @since 4.1
 */
public interface SmartInitializingSingleton {

	/**
	 * 在单例预实例化结束阶段进行调用
	 * 用于保证所有常规的单例bean已经被创建
	 * {@link ListableBeanFactory#getBeansOfType}使用此方法的调用将不会触发beanFactory启动时的副作用
	 * <p>
	 * 注意：此回调任务不会在{@link BeanFactory}启动之后，懒加载单例bean实例化后触发
	 * 也不支持其他scope类型
	 * 需要在仅有的预期语义之下谨慎使用
	 */
	void afterSingletonsInstantiated();

}
