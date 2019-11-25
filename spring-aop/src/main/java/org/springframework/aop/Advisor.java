/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop;

import org.aopalliance.aop.Advice;

/**
 * 如果想要使用Advisor切面，那么实现类必须实现Advisor接口，也就是需要适配器
 * 切面
 * 持有AOP增强的基础接口（在拦截点执行的动作）
 * 和一个确认增强的适用性的过滤器
 * 这个接口不是为Spring的开发者使用的，但是为了不同的增强提供了通用性
 * <p>
 * Spring AOP通过方法拦截基于围绕增强，符合AOP联盟的拦截的API
 * Advisor接口允许支持不同类型的增强，比如前置、后置增强
 * 这些增强不需要使用拦截来实现
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface Advisor {

	/**
	 * 空增强的默认实现
	 * 在调用{@link #getAdvice()}方法时，如果增强没有配置，访问此空增强
	 * @since 5.0
	 */
	Advice EMPTY_ADVICE = new Advice() {};


	/**
	 * 返回aspect的增强部分，一个增强可能是一个interceptor，一个前置增强，一个异常增强
	 * @return 如果连接点匹配，应该返回的增强
	 * @see org.aopalliance.intercept.MethodInterceptor
	 * @see BeforeAdvice
	 * @see ThrowsAdvice
	 * @see AfterReturningAdvice
	 */
	Advice getAdvice();

	/**
	 * 当前增强是否和一个特定的实例关联，或者与同一Spring Bean工厂获得的增强的所有实例共享
	 * 需要注意的是，当前方法没有被当前框架所使用
	 * 典型的Advisor实现应该总是返回true
	 * 使用单例或者多例的bean definition，或者适当的程序化代理创建，来确认Advisors拥有正确的生命周期模型
	 * @return 增强是否和一个特定的实例关联
	 */
	boolean isPerInstance();

}
