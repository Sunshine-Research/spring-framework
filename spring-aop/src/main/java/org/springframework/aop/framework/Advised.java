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

package org.springframework.aop.framework;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetClassAware;
import org.springframework.aop.TargetSource;

/**
 * AOP代理的工厂配置
 * 配置包含了interceptors和其他增强，切面，和代理接口
 * <p>
 * 任何Spring包含的AOP代理可以转换为这个接口，来允许操作其他AOP增强
 * 简单来说就是包含了所有的Advisor和Advice
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.framework.AdvisedSupport
 * @since 13.03.2003
 */
public interface Advised extends TargetClassAware {

	/**
	 * 返回Advised的配置是否是冰冻的，在这种冰冻的情况下，无法对增强发生变化
	 */
	boolean isFrozen();

	/**
	 * 判断是否是代理完整的目标类而不是指定的接口
	 */
	boolean isProxyTargetClass();

	/**
	 * 返回AOP代理代理的接口
	 * 不会包含代理的目标类
	 */
	Class<?>[] getProxiedInterfaces();

	/**
	 * 判断给定的接口是否已经被代理
	 * @param intf 需要进行检查的接口
	 */
	boolean isInterfaceProxied(Class<?> intf);

	/**
	 * 返回当前{@code Advised}对象的目标资源
	 */
	TargetSource getTargetSource();

	/**
	 * {@code Advised}对象新的{@code TargetSource}
	 * 只有在非冰冻状态下才可以进行
	 * @param targetSource 新的目标资源
	 */
	void setTargetSource(TargetSource targetSource);

	/**
	 * 判断工厂是否需要将代理暴露为一个{@link ThreadLocal}
	 * 如果Advised对象需要调用其本身的方法，那么就需要暴露这个proxy
	 * 否则，如果一个Advised对象需要使用{@code this}来调用方法，不会应用任何增强
	 * 获取代理类似于EJB中的{@code getEJBObject()}调用
	 * @see AopContext
	 */
	boolean isExposeProxy();

	/**
	 * 代理是否需要通过AOP框架暴露，作为{@link ThreadLocal}以便通过{@link AopContext}进行检索
	 * 如果Advised对象需要调用其本身的方法，那么就需要暴露这个proxy
	 * 否则，如果一个Advised对象需要使用{@code this}来调用方法，不会应用任何增强
	 * 默认是{@code false}，以获得最佳性能
	 */
	void setExposeProxy(boolean exposeProxy);

	/**
	 * 判断代理配置是否是预过滤的
	 * 因此可以只包含合适的Advisors（匹配代理的目标类）
	 */
	boolean isPreFiltered();

	/**
	 * 判断代理配置是否需要预先过滤，因此可以只包含合适的Advisors（匹配代理的目标类）
	 * 默认是false，如果Advisors已经进行预过滤，设置为true，意味着可以在为代理调用构建真实Advisor时，跳过ClassFilter的步骤
	 * @see org.springframework.aop.ClassFilter
	 */
	void setPreFiltered(boolean preFiltered);

	/**
	 * @return 应用到当前代理的所有Advisors
	 */
	Advisor[] getAdvisors();

	/**
	 * 向Advisor链的尾部添加一个Advisor
	 * 可能会是一个{@link org.springframework.aop.IntroductionAdvisor}引介切面
	 * 下次从工厂获取代理时，其中的新接口将可用
	 * @param advisor 需要添加到Advisor链尾部的Advisor
	 * @throws AopConfigException 非法Advice，抛出异常
	 */
	void addAdvisor(Advisor advisor) throws AopConfigException;

	/**
	 * 添加一个Advisor到链中的指定为止
	 * @param advisor 需要添加到Advisor链指定位置的Advisor
	 * @param pos     指定的位置
	 * @throws AopConfigException 非法Advice，抛出异常
	 */
	void addAdvisor(int pos, Advisor advisor) throws AopConfigException;

	/**
	 * 从链中移除给定的Advisor
	 * @param advisor 需要移除的Advisor
	 * @return 移除成功，返回{@code true};
	 * 没有找到相应的Advisor，或者无法移除。返回{@code false}
	 */
	boolean removeAdvisor(Advisor advisor);

	/**
	 * 移除链中指定位置的Advisor
	 * @param index 需要移除Advisor的索引
	 * @throws AopConfigException 非法索引，抛出异常
	 */
	void removeAdvisor(int index) throws AopConfigException;

	/**
	 * 获取给定Advisor在链中的索引位置
	 * @param advisor 进行查找的Advisor
	 * @return 给定Advisor的索引位置，-1意味着没有找到这个Advisor
	 */
	int indexOf(Advisor advisor);

	/**
	 * 用Advisor替换Advisor
	 * 需要注意的是，如果Advisor是{@link org.springframework.aop.IntroductionAdvisor}类型，并且代替的不是这个类型或者实现了不同的接口
	 * 代理需要重新进行获取，否则将不支持旧接口，也不会实现新接口
	 * @param a 需要代替的Advisor
	 * @param b 替换的新的Advisor
	 * @return 是否替换成功，如果没有找到需要进行替换的Advisor，返回{@code false}并什么都不做
	 * @throws AopConfigException 非法Advice，抛出异常
	 */
	boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException;

	/**
	 * 添加给定的AOP联盟的增强方法到增强链的尾部
	 * 会使用带有切点的DefaultPointcutAdvisor来进行包装，并通过{@code getAdvisors()}返回其包装后的对象
	 * 需要注意的是，给定的Advice可以应用到代理的所有调用，甚至是{@code toString()}方法，
	 * 使用适当的Advice实现或指定的、合适的切点来应用到范围更窄的方法
	 * @param advice 需要添加到链尾部的Advice
	 * @throws AopConfigException 非法Advice，抛出异常
	 * @see #addAdvice(int, Advice)
	 * @see org.springframework.aop.support.DefaultPointcutAdvisor
	 */
	void addAdvice(Advice advice) throws AopConfigException;

	/**
	 * 将给定的AOP联盟的Advice添加到Advice链的指定位置
	 * 会使用带有切点的{@link org.springframework.aop.support.DefaultPointcutAdvisor}类进行包装并通过{@link #getAdvisors()}返回其包装后的对象
	 * 需要注意的是，给定的Advice可以应用到代理的所有调用，甚至是{@code toString()}方法，
	 * 使用适当的Advice实现或指定的、合适的切点来应用到范围更窄的方法
	 * @param pos    指定插入的索引位置
	 * @param advice 需要添加到指定位置的Advice
	 * @throws AopConfigException 非法Advice，抛出异常
	 */
	void addAdvice(int pos, Advice advice) throws AopConfigException;

	/**
	 * 移除给定的Advice
	 * @param advice 需要移除的Advice
	 * @return 找到并成功移除，返回{@code true}
	 * 没有找到，返回{@code false}
	 */
	boolean removeAdvice(Advice advice);

	/**
	 * 获取给定AOP联盟类型的Advice在链中的索引位置
	 * @param advice 需要进行查找位置的Advice
	 * @return 如果返回-1，证明链中没有此Advice
	 */
	int indexOf(Advice advice);

	/**
	 * 由于{@code toString()}方法通常会代理给目标
	 * 这个和AOP代理是等效的
	 * @return 代理配置的String类型的描述信息
	 */
	String toProxyConfigString();

}
