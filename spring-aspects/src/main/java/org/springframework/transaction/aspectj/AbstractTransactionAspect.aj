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

package org.springframework.transaction.aspectj;

import org.aspectj.lang.annotation.SuppressAjWarnings;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

/**
 * 事务的超级切面，具体的超级切面会实现{@code transactionalMethodExecution()}方法
 *
 * 可以适配Spring IoC容器内外的使用
 * 适当的设置transactionManager属性，允许使用Spring提供的任意事务实现
 *
 * 需要注意的是，如果一个方法实现了一个接口，这个接口被事务注解了，有关的事务属性不会得到解决
 * 这种行为会因为Spring的AOP，因为代理的是这个接口，而不是这个类
 * 建议事务注解需要添加到类中，其次才是业务接口，因为他们是实现细节，而不是规范验证
 * @author Rod Johnson
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.0
 */
public abstract aspect AbstractTransactionAspect extends TransactionAspectSupport implements DisposableBean {

	/**
	 * Construct the aspect using the given transaction metadata retrieval strategy.
	 * @param tas TransactionAttributeSource implementation, retrieving Spring
	 * transaction metadata for each joinpoint. Implement the subclass to pass in
	 * {@code null} if it is intended to be configured through Setter Injection.
	 */
	protected AbstractTransactionAspect(TransactionAttributeSource tas) {
		setTransactionAttributeSource(tas);
	}

	@Override
	public void destroy() {
		clearTransactionManagerCache(); // An aspect is basically a singleton
	}

	@SuppressAjWarnings("adviceDidNotMatch")
	Object around(final Object txObject): transactionalMethodExecution(txObject) {
		MethodSignature methodSignature = (MethodSignature) thisJoinPoint.getSignature();
		// Adapt to TransactionAspectSupport's invokeWithinTransaction...
		try {
			return invokeWithinTransaction(methodSignature.getMethod(), txObject.getClass(), new InvocationCallback() {
				public Object proceedWithInvocation() throws Throwable {
					return proceed(txObject);
				}
			});
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Throwable thr) {
			Rethrower.rethrow(thr);
			throw new IllegalStateException("Should never get here", thr);
		}
	}

	/**
	 * Concrete subaspects must implement this pointcut, to identify
	 * transactional methods. For each selected joinpoint, TransactionMetadata
	 * will be retrieved using Spring's TransactionAttributeSource interface.
	 */
	protected abstract pointcut transactionalMethodExecution(Object txObject);


	/**
	 * Ugly but safe workaround: We need to be able to propagate checked exceptions,
	 * despite AspectJ around advice supporting specifically declared exceptions only.
	 */
	private static class Rethrower {

		public static void rethrow(final Throwable exception) {
			class CheckedExceptionRethrower<T extends Throwable> {
				@SuppressWarnings("unchecked")
				private void rethrow(Throwable exception) throws T {
					throw (T) exception;
				}
			}
			new CheckedExceptionRethrower<RuntimeException>().rethrow(exception);
		}
	}

}
