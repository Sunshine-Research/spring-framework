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

/**
 * 拦截一个新对象的构造方法
 *
 * 开发者需要实现{@link #construct(ConstructorInvocation)}方法来修改原有的表现形式
 * 比如：接下来的例子实现了一个单例interceptor（仅允许唯一一个拦截类的实例）
 * <pre class=code>
 * class DebuggingInterceptor implements ConstructorInterceptor {
 *   Object instance=null;
 *
 *   Object construct(ConstructorInvocation i) throws Throwable {
 *     if(instance==null) {
 *       return instance=i.proceed();
 *     } else {
 *       throw new Exception("singleton does not allow multiple instance");
 *     }
 *   }
 * }
 * </pre>
 * @author Rod Johnson
 */
public interface ConstructorInterceptor extends Interceptor  {

	/**
	 * 实现这个方法来执行额外的方法前后的部分
	 * @param invocation 建造的连接点
	 * @return 新创建的对象，同时也是{@link Joinpoint#proceed()}调用的结果，可能被interceptor替换
	 * @throws Throwable 如果interceptors或者目标对象抛出了一个异常
	 */
	Object construct(ConstructorInvocation invocation) throws Throwable;

}
