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

package org.springframework.aop;

import org.springframework.lang.Nullable;

import java.lang.reflect.Method;

/**
 * 前置增强调用
 * 前置增强不能阻止方法的调用，除非抛出异常
 * @author Rod Johnson
 * @see AfterReturningAdvice
 * @see ThrowsAdvice
 */
public interface MethodBeforeAdvice extends BeforeAdvice {

	/**
	 * 在调用真实方法之前需要调用的方法
	 * @param method 目标方法的反射对象
	 * @param args   调用目标方法的参数
	 * @param target 方法调用的目标，可能为null
	 * @throws Throwable 如果这个对象可以中断调用，如果方法签名允许，任何异常都可以抛出给调用者
	 *                   否则异常就会包装成一个运行时异常
	 */
	void before(Method method, Object[] args, @Nullable Object target) throws Throwable;

}
