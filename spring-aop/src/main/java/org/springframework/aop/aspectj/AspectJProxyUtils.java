/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.aop.aspectj;

import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;

import java.util.List;

/**
 * Utility methods for working with AspectJ proxies.
 * @author Rod Johnson
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract class AspectJProxyUtils {

	/**
	 * 必要时添加特殊的切面，以与包含AspectJ切面的代理链一起使用：
	 * 具体来说，{@link ExposeInvocationInterceptor}需要在列表的头部
	 * 这会暴露当前Spring AOP的调用（一些AspectJ切入点匹配所必须的），并提供当前AspectJ JoinPoint
	 * 如果切面链中没有切面，那么本次调用不会造成任何影响
	 * @param advisors 可用的切面
	 * @return 添加{@link ExposeInvocationInterceptor}到增强列表成功或失败
	 */
	public static boolean makeAdvisorChainAspectJCapableIfNecessary(List<Advisor> advisors) {
		// 在没有切面的情况下不进行添加
		// 可以表明此时不需要代理
		if (!advisors.isEmpty()) {
			boolean foundAspectJAdvice = false;
			// 遍历所有的切面
			for (Advisor advisor : advisors) {
				// 再次判断当前的切面类型
				if (isAspectJAdvice(advisor)) {
					foundAspectJAdvice = true;
					break;
				}
			}
			// 在当前切面中没有ExposeInvocationInterceptor类型切面的情况下，添加ExposeInvocationInterceptor切面到首位
			if (foundAspectJAdvice && !advisors.contains(ExposeInvocationInterceptor.ADVISOR)) {
				advisors.add(0, ExposeInvocationInterceptor.ADVISOR);
				return true;
			}
		}
		return false;
	}

	/**
	 * 确认给定的切面包含了AspectJ类型的增强
	 * @param advisor 需要进行检查的切面
	 */
	private static boolean isAspectJAdvice(Advisor advisor) {
		// 切面是InstantiationModelAwarePointcutAdvisor类型
		// 增强方法是AbstractAspectJAdvice
		// 或者PointcutAdvisor类型的切面，切点是AspectJExpressionPointcut，AspectJ类型的切点
		return (advisor instanceof InstantiationModelAwarePointcutAdvisor ||
				advisor.getAdvice() instanceof AbstractAspectJAdvice ||
				(advisor instanceof PointcutAdvisor &&
						((PointcutAdvisor) advisor).getPointcut() instanceof AspectJExpressionPointcut));
	}

}
