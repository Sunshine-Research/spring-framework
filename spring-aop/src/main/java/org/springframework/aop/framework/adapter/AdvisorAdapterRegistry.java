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

package org.springframework.aop.framework.adapter;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;

/**
 * Advisor适配器的注册器接口
 * 虽然是一个SPI接口，但是不会通过Spring的使用者实现
 * @author Rod Johnson
 * @author Rob Harrop
 */
public interface AdvisorAdapterRegistry {

	/**
	 * 包装给定的增强为{@link Advisor}
	 * 默认至少支持：
	 * {@link org.aopalliance.intercept.MethodInterceptor},
	 * {@link org.springframework.aop.MethodBeforeAdvice},
	 * {@link org.springframework.aop.AfterReturningAdvice},
	 * {@link org.springframework.aop.ThrowsAdvice}.
	 * @param advice 需要包装的增强
	 * @return 包装了给定增强的Advisor，永不为null，如果给定的增强类型就是Advisor，那么直接返回这个Advisor
	 * @throws UnknownAdviceTypeException 如果没有已注册的Advisor适配器可以包装给定的增强，抛出异常
	 */
	Advisor wrap(Object advice) throws UnknownAdviceTypeException;

	/**
	 * 返回MethodInterceptors的AOP协议数组，来允许基于interceptor框架的切面的使用
	 * 不用关心和切面关联的切点，如果是{@link org.springframework.aop.PointcutAdvisor}类型的切面，只是会返回一个interceptor
	 * @param advisor 需要获取interceptor的切面
	 * @return 暴露切面行为的MethodInterceptors数组
	 * @throws UnknownAdviceTypeException 无法识别的切面类型
	 */
	MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException;

	/**
	 * 注册给定的{@link AdvisorAdapter}切面适配器
	 * 需要注意的是，其实不必要去注册AOP协议的interceptor或者Spring增强，它们必须通过{@code AdvisorAdapterRegistry}的实现来识别
	 * @param adapter 可以理解为是特定的切面，或者增强类型
	 */
	void registerAdvisorAdapter(AdvisorAdapter adapter);

}
