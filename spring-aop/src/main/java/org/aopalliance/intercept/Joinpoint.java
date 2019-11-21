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

package org.aopalliance.intercept;

import java.lang.reflect.AccessibleObject;

/**
 * AOP中的连接点，代表了一个通用的运行时的连接点（在AOP中）
 *
 * 一个运行时的连接点是一个事件，发生在一个静态的连接点上（比如程序中的位置）
 * 举个例子，一次调用是在一个方法上的运行时连接点（静态连接点）
 * 可以通过{@link #getStaticPart()}方法对给定的连接点举行遍历
 *
 * 在拦截框架的上下文中，一个运行时的连接点是一个队可访问对象的访问形式化（一个方法，一个构造方法，一个字段）
 * 它用于传递给静态连接点上的拦截器
 *
 * @author Rod Johnson
 * @see Interceptor
 */
public interface Joinpoint {

	/**
	 * 进行链上的下一个interceptor
	 * 此方法的实现和语义取决于实际的连接点类型
	 * @return 请看子类接口进行定义
	 * @throws Throwable 连接点抛出了异常
	 */
	Object proceed() throws Throwable;

	/**
	 * 返回持有当前连接点的静态部分的对象
	 * 举个例子，一次调用的目标对象
	 * @return 对象
	 */
	Object getThis();

	/**
	 * 返回连接点的静态部分
	 * 静态部分是指已安装的interceptor链上可访问的部分
	 */
	AccessibleObject getStaticPart();

}
