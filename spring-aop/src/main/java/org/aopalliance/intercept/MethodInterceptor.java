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

package org.aopalliance.intercept;

/**
 * 在到达目标方法的途中拦截接口上的调用，
 * 这些interceptor会在目标上进行嵌套
 *
 * 开发者需要实现{@link #invoke(MethodInvocation)}来修改原有的表现
 * 比如，下面的示例就提供了追踪功能的interceptor
 * <pre class=code>
 * class TracingInterceptor implements MethodInterceptor {
 *   Object invoke(MethodInvocation i) throws Throwable {
 *     System.out.println("method "+i.getMethod()+" is called on "+
 *                        i.getThis()+" with args "+i.getArguments());
 *     Object ret=i.proceed();
 *     System.out.println("method "+i.getMethod()+" returns "+ret);
 *     return ret;
 *   }
 * }
 * </pre>
 * @author Rod Johnson
 */
@FunctionalInterface
public interface MethodInterceptor extends Interceptor {

	/**
	 * 实现这个方法来执行额外的方法前后的电泳，
	 * @param invocation 方法调用的连接点
	 * @return 调用{@link Joinpoint#proceed()}的结果，可能会被interceptor拦截
	 * @throws Throwable interceptor链或者目标对象抛出了异常
	 */
	Object invoke(MethodInvocation invocation) throws Throwable;

}
