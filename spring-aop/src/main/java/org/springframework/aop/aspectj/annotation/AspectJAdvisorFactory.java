/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Interface for factories that can create Spring AOP Advisors from classes
 * annotated with AspectJ annotation syntax.
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see AspectMetadata
 * @see org.aspectj.lang.reflect.AjTypeSystem
 * @since 2.0
 */
public interface AspectJAdvisorFactory {

	/**
	 * Determine whether or not the given class is an aspect, as reported
	 * by AspectJ's {@link org.aspectj.lang.reflect.AjTypeSystem}.
	 * <p>Will simply return {@code false} if the supposed aspect is
	 * invalid (such as an extension of a concrete aspect class).
	 * Will return true for some aspects that Spring AOP cannot process,
	 * such as those with unsupported instantiation models.
	 * Use the {@link #validate} method to handle these cases if necessary.
	 * @param clazz the supposed annotation-style AspectJ class
	 * @return whether or not this class is recognized by AspectJ as an aspect class
	 */
	boolean isAspect(Class<?> clazz);

	/**
	 * Is the given class a valid AspectJ aspect class?
	 * @param aspectClass the supposed AspectJ annotation-style class to validate
	 * @throws AopConfigException     if the class is an invalid aspect
	 *                                (which can never be legal)
	 * @throws NotAnAtAspectException if the class is not an aspect at all
	 *                                (which may or may not be legal, depending on the context)
	 */
	void validate(Class<?> aspectClass) throws AopConfigException;

	/**
	 * 为所有使用@AspectJ注解的方法的特定Aspect实例构建Spring AOP Advisor
	 * @param aspectInstanceFactory Aspect实例factory，不是Aspect实例本身，避免过早实例化
	 * @return 给定类型的Advisor列表
	 */
	List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory);

	/**
	 * Build a Spring AOP Advisor for the given AspectJ advice method.
	 * @param candidateAdviceMethod the candidate advice method
	 * @param aspectInstanceFactory the aspect instance factory
	 * @param declarationOrder      the declaration order within the aspect
	 * @param aspectName            the name of the aspect
	 * @return {@code null} if the method is not an AspectJ advice method
	 * or if it is a pointcut that will be used by other advice but will not
	 * create a Spring advice in its own right
	 */
	@Nullable
	Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
					   int declarationOrder, String aspectName);

	/**
	 * 用于将AspectJ增强方法构建为一个Spring AOP Advice
	 * @param candidateAdviceMethod 候选的增强方法
	 * @param expressionPointcut    切点表达式
	 * @param aspectInstanceFactory 切面实例BeanFactory
	 * @param declarationOrder      Aspect声明的顺序
	 * @param aspectName            Aspect名称
	 * @return Spring AOP类型的Advisor
	 * @see org.springframework.aop.aspectj.AspectJAroundAdvice
	 * @see org.springframework.aop.aspectj.AspectJMethodBeforeAdvice
	 * @see org.springframework.aop.aspectj.AspectJAfterAdvice
	 * @see org.springframework.aop.aspectj.AspectJAfterReturningAdvice
	 * @see org.springframework.aop.aspectj.AspectJAfterThrowingAdvice
	 */
	@Nullable
	Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
					 MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName);

}
