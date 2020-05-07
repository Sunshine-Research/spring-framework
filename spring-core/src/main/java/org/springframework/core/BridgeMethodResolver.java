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

package org.springframework.core;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Helper for resolving synthetic {@link Method#isBridge bridge Methods} to the
 * {@link Method} being bridged.
 *
 * <p>Given a synthetic {@link Method#isBridge bridge Method} returns the {@link Method}
 * being bridged. A bridge method may be created by the compiler when extending a
 * parameterized type whose methods have parameterized arguments. During runtime
 * invocation the bridge {@link Method} may be invoked and/or used via reflection.
 * When attempting to locate annotations on {@link Method Methods}, it is wise to check
 * for bridge {@link Method Methods} as appropriate and find the bridged {@link Method}.
 *
 * <p>See <a href="https://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.12.4.5">
 * The Java Language Specification</a> for more details on the use of bridge methods.
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 2.0
 */
public final class BridgeMethodResolver {

	private static final Map<Method, Method> cache = new ConcurrentReferenceHashMap<>();

	private BridgeMethodResolver() {
	}


	/**
	 * 查找原生的方法，也就是获取桥接方法
	 * <p>
	 * 通过非桥接方法调用此方法是线程安全的
	 * 在此种情况下，提供方法实例，将会直接返回给调用者
	 * 调用者不必在调用此方法之前检查桥接
	 * @param bridgeMethod 需要进行内省的方法
	 * @return 原生的方法，即不带泛型的方法，即桥接方法，或者传入的方法
	 */
	public static Method findBridgedMethod(Method bridgeMethod) {
		// 如果给定的方法不是桥接方法，直接返回
		if (!bridgeMethod.isBridge()) {
			return bridgeMethod;
		}
		// 此时给定方法是桥接方法
		// 从缓存中获取桥接方法
		Method bridgedMethod = cache.get(bridgeMethod);
		if (bridgedMethod == null) {
			// 收集所有匹配的方法名称和参数大小
			List<Method> candidateMethods = new ArrayList<>();
			MethodFilter filter = candidateMethod ->
					isBridgedCandidateFor(candidateMethod, bridgeMethod);
			// 查找所有符合条件的桥接方法
			ReflectionUtils.doWithMethods(bridgeMethod.getDeclaringClass(), candidateMethods::add, filter);
			if (!candidateMethods.isEmpty()) {
				// 如果仅有一个匹配，则返回此桥接方法
				// 如果有多个匹配，则进行搜索
				bridgedMethod = candidateMethods.size() == 1 ?
						candidateMethods.get(0) :
						searchCandidates(candidateMethods, bridgeMethod);
			}
			// 如果没有桥接方法，则返回给定的方法
			if (bridgedMethod == null) {
				bridgedMethod = bridgeMethod;
			}
			// 找到桥接方法的情况下，将桥接方法放入到缓存中
			cache.put(bridgeMethod, bridgedMethod);
		}
		return bridgedMethod;
	}

	/**
	 * Returns {@code true} if the supplied '{@code candidateMethod}' can be
	 * consider a validate candidate for the {@link Method} that is {@link Method#isBridge() bridged}
	 * by the supplied {@link Method bridge Method}. This method performs inexpensive
	 * checks and can be used quickly filter for a set of possible matches.
	 */
	private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
		return (!candidateMethod.isBridge() && !candidateMethod.equals(bridgeMethod) &&
				candidateMethod.getName().equals(bridgeMethod.getName()) &&
				candidateMethod.getParameterCount() == bridgeMethod.getParameterCount());
	}

	/**
	 * 在多个候选桥接方法中，寻找合适的桥接方法
	 * @param candidateMethods 候选桥接方法列表
	 * @param bridgeMethod     给定的桥接方法
	 * @return 匹配的候选桥接方法，或者{@code null}
	 */
	@Nullable
	private static Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod) {
		if (candidateMethods.isEmpty()) {
			return null;
		}
		Method previousMethod = null;
		boolean sameSig = true;
		// 迭代候选桥接方法
		for (Method candidateMethod : candidateMethods) {
			// 筛选桥接方法，通过比较方法的参数类型
			if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.getDeclaringClass())) {
				return candidateMethod;
			} else if (previousMethod != null) {
				sameSig = sameSig &&
						Arrays.equals(candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
			}
			previousMethod = candidateMethod;
		}
		return (sameSig ? candidateMethods.get(0) : null);
	}

	/**
	 * 确认方法是否是给定方法的桥接方法
	 * 通过比较参数类型
	 * @param bridgeMethod    给定方法
	 * @param candidateMethod 候选桥接方法
	 * @param declaringClass  桥接方法所属类
	 */
	static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> declaringClass) {
		// 比较两个方法的参数类型是否匹配
		if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
			return true;
		}
		Method method = findGenericDeclaration(bridgeMethod);
		return (method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass));
	}

	/**
	 * 两个方法的{@link Type}标志和具体实现是相同的，在解析所有的声明类型之后，返回{@code true}
	 * 否则返回{@code false}.
	 * @param genericMethod   给定的方法
	 * @param candidateMethod 候选方法
	 * @param declaringClass  候选方法所属的类
	 * @return
	 */
	private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> declaringClass) {
		// 获取给定方法的参数类型，对参数类型个数进行比较
		Type[] genericParameters = genericMethod.getGenericParameterTypes();
		if (genericParameters.length != candidateMethod.getParameterCount()) {
			return false;
		}
		// 获取候选方法的参数类
		Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
		for (int i = 0; i < candidateParameters.length; i++) {
			ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, declaringClass);
			Class<?> candidateParameter = candidateParameters[i];
			if (candidateParameter.isArray()) {
				// 数组类型，那么继续比较其中的元素类型
				if (!candidateParameter.getComponentType().equals(genericParameter.getComponentType().toClass())) {
					return false;
				}
			}
			// 非数组类型，那么直接比较两个的参数类型
			if (!candidateParameter.equals(genericParameter.toClass())) {
				return false;
			}
		}
		// 匹配成功
		return true;
	}

	/**
	 * Searches for the generic {@link Method} declaration whose erased signature
	 * matches that of the supplied bridge method.
	 * @throws IllegalStateException if the generic declaration cannot be found
	 */
	@Nullable
	private static Method findGenericDeclaration(Method bridgeMethod) {
		// Search parent types for method that has same signature as bridge.
		Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
		while (superclass != null && Object.class != superclass) {
			Method method = searchForMatch(superclass, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			superclass = superclass.getSuperclass();
		}

		Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(bridgeMethod.getDeclaringClass());
		return searchInterfaces(interfaces, bridgeMethod);
	}

	@Nullable
	private static Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
		for (Class<?> ifc : interfaces) {
			Method method = searchForMatch(ifc, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			} else {
				method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
				if (method != null) {
					return method;
				}
			}
		}
		return null;
	}

	/**
	 * If the supplied {@link Class} has a declared {@link Method} whose signature matches
	 * that of the supplied {@link Method}, then this matching {@link Method} is returned,
	 * otherwise {@code null} is returned.
	 */
	@Nullable
	private static Method searchForMatch(Class<?> type, Method bridgeMethod) {
		try {
			return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
		} catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Compare the signatures of the bridge method and the method which it bridges. If
	 * the parameter and return types are the same, it is a 'visibility' bridge method
	 * introduced in Java 6 to fix https://bugs.java.com/view_bug.do?bug_id=6342411.
	 * See also https://stas-blogspot.blogspot.com/2010/03/java-bridge-methods-explained.html
	 * @return whether signatures match as described
	 */
	public static boolean isVisibilityBridgeMethodPair(Method bridgeMethod, Method bridgedMethod) {
		if (bridgeMethod == bridgedMethod) {
			return true;
		}
		return (bridgeMethod.getReturnType().equals(bridgedMethod.getReturnType()) &&
				bridgeMethod.getParameterCount() == bridgedMethod.getParameterCount() &&
				Arrays.equals(bridgeMethod.getParameterTypes(), bridgedMethod.getParameterTypes()));
	}

}
