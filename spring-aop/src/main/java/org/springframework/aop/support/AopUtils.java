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

package org.springframework.aop.support;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for AOP support code.
 *
 * <p>Mainly for internal use within Spring's AOP support.
 *
 * <p>See {@link org.springframework.aop.framework.AopProxyUtils} for a
 * collection of framework-specific AOP utility methods which depend
 * on internals of Spring's AOP framework implementation.
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public abstract class AopUtils {

	/**
	 * Check whether the given object is a JDK dynamic proxy or a CGLIB proxy.
	 * <p>This method additionally checks if the given object is an instance
	 * of {@link SpringProxy}.
	 * @param object the object to check
	 * @see #isJdkDynamicProxy
	 * @see #isCglibProxy
	 */
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
	}

	/**
	 * Check whether the given object is a JDK dynamic proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link Proxy#isProxyClass(Class)} by additionally checking if the
	 * given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see java.lang.reflect.Proxy#isProxyClass
	 */
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * Check whether the given object is a CGLIB proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link ClassUtils#isCglibProxy(Object)} by additionally checking if
	 * the given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see ClassUtils#isCglibProxy(Object)
	 */
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
	}

	/**
	 * Determine the target class of the given bean instance which might be an AOP proxy.
	 * <p>Returns the target class for an AOP proxy or the plain class otherwise.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the target class (or the plain class of the given object as fallback;
	 * never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass(Object)
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		if (candidate instanceof TargetClassAware) {
			result = ((TargetClassAware) candidate).getTargetClass();
		}
		if (result == null) {
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 * @param method     the method to check
	 * @param targetType the target type to search methods on (typically an AOP proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 *                               target type (typically due to a proxy mismatch)
	 * @see MethodIntrospector#selectInvocableMethod(Method, Class)
	 * @since 4.3
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
							"be delegated to target bean. Switch its visibility to package or protected.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
		return methodToUse;
	}

	/**
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * Determine whether the given method is a "finalize" method.
	 * @see java.lang.Object#finalize()
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * Given a method, which may come from an interface, and a target class used
	 * in the current AOP invocation, find the corresponding target method if there
	 * is one. E.g. the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {@link org.springframework.util.ClassUtils#getMostSpecificMethod},
	 * this method resolves Java 5 bridge methods in order to retrieve attributes
	 * from the <i>original</i> method definition.
	 * @param method      the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation.
	 *                    May be {@code null} or may not even implement the method.
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} doesn't implement it or is {@code null}
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		Method resolvedMethod = ClassUtils.getMostSpecificMethod(method, specificTargetClass);
		// If we are dealing with method with generic parameters, find the original method.
		return BridgeMethodResolver.findBridgedMethod(resolvedMethod);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc          the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * 判断切面是否可以应用于给定的类型
	 * 这是一项重要的测试，因为它可以优化类型的切面
	 * @param pc               需要进行检查的静态或者动态的切点
	 * @param targetClass      需要测试的类型
	 * @param hasIntroductions 给定的Bean实例的切面链是否包含任何的引介
	 * @return 切点是否可以应用于方法
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");
		// 如果切点类型不匹配，则判定不能应用
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}
		// 接下来对方法进行匹配
		MethodMatcher methodMatcher = pc.getMethodMatcher();
		if (methodMatcher == MethodMatcher.TRUE) {
			// 如何设置的是匹配所有的方法，则不进行校验，直接判定为应用
			return true;
		}
		// 引介的方法匹配适配
		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}

		Set<Class<?>> classes = new LinkedHashSet<>();
		// 非代理类型的情况下，缓存给定类型
		if (!Proxy.isProxyClass(targetClass)) {
			classes.add(ClassUtils.getUserClass(targetClass));
		}
		// 缓存给定类型的接口类型
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));
		// 遍历所有缓存的接口类型
		for (Class<?> clazz : classes) {
			// 获取这些类型的方法
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			// 遍历每个类型的方法
			for (Method method : methods) {
				// 引介类型使用引介方法匹配进行判断
				// 通用类型使用通用方法匹配进行判断
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						methodMatcher.matches(method, targetClass)) {
					// 匹配成功，判定可以应用
					return true;
				}
			}
		}
		// 其他情况，判定不可应用
		return false;
	}

	/**
	 * 判断切面是否可以应用于给定的类型
	 * 这是一项重要的测试，因为它可以优化类型的切面
	 * @param advisor     需要进行检查的切面
	 * @param targetClass 需要测试的类型
	 * @return 切点是否可以应用于方法
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		// 默认不带有引介类型
		return canApply(advisor, targetClass, false);
	}

	/**
	 * 判断切面是否可以应用于给定的类型
	 * 这是一项重要的测试，因为它可以优化类型的切面
	 * 这个版本还考虑了引介（比如IntroductionAwareMethodMatchers）
	 * @param advisor          需要进行检查的切面
	 * @param targetClass      需要测试的类型
	 * @param hasIntroductions 给定的Bean实例的切面链是否包含任何的引介
	 * @return 切点是否可以应用于方法
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		// 首先进行引介切面的判断
		if (advisor instanceof IntroductionAdvisor) {
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		} else if (advisor instanceof PointcutAdvisor) {
			// 接下来进行切点切面类型的判断
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		} else {
			// 此时没有切点，默认认为它适用
			return true;
		}
	}

	/**
	 * 确认符合给定Bean类型的可用切面
	 * @param candidateAdvisors 需要进行判断的候选切面
	 * @param clazz             目标Bean实例类型
	 * @return 符合Bean实例类型的前面集合
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		// 候选切面为空，不进行过滤
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		// 符合条件的切面
		List<Advisor> eligibleAdvisors = new ArrayList<>();
		// 遍历所有的候选切面
		for (Advisor candidate : candidateAdvisors) {
			// 如果是引介增强，并且增强可以用于给定类型
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				// 此增强符合条件
				eligibleAdvisors.add(candidate);
			}
		}
		// 刚才仅有引介类型的切面可以放入符合条件的列表中，此时用于筛选当前类型是否有引介切面
		// 在canApply()中，使用的没有引介切面
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();
		// 接下来遍历除引介切面之外的切面
		for (Advisor candidate : candidateAdvisors) {
			if (candidate instanceof IntroductionAdvisor) {
				// 已经处理，直接跳过
				continue;
			}
			// 有引介切面的情况下，判断当前切面是否可应用
			if (canApply(candidate, clazz, hasIntroductions)) {
				// 符合条件
				eligibleAdvisors.add(candidate);
			}
		}
		// 返回可以作用在此类型上的切面
		return eligibleAdvisors;
	}

	/**
	 * 通过反射调用目标，是AOP方法调用的一部分
	 * @param target 进行调用的目标对象
	 * @param method 进行调用的目标方法
	 * @param args   调用方法的参数列表
	 * @return 调用结果
	 * @throws Throwable                                      目标方法抛出的异常
	 * @throws org.springframework.aop.AopInvocationException 抛出反射异常
	 */
	@Nullable
	public static Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, Object[] args)
			throws Throwable {

		// 使用反射来调用方法
		try {
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		} catch (InvocationTargetException ex) {
			// 调用方法抛出了检查异常，需要再次抛出，客户端将看不到interceptor
			throw ex.getTargetException();
		} catch (IllegalArgumentException ex) {
			throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" +
					method + "] on target [" + target + "]", ex);
		} catch (IllegalAccessException ex) {
			throw new AopInvocationException("Could not access method [" + method + "]", ex);
		}
	}

}
