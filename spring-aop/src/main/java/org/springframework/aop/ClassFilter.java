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

package org.springframework.aop;

/**
 * 过滤限制切入点的匹配，或者对给定目标类的引介
 * 可以用于切点，或者引介切面的整个定位
 * <p>
 * 接口的代表性完全实现应该提供合适的{@link Object#equals(Object)}和{@link Object#hashCode()}实现
 * 为了允许过滤器可以在缓存方案中使用，比如使用CGLIB生成代理
 * @author Rod Johnson
 * @see Pointcut
 * @see MethodMatcher
 */
@FunctionalInterface
public interface ClassFilter {

	/**
	 * 可以匹配所有类的ClassFilter规定实例
	 */
	ClassFilter TRUE = TrueClassFilter.INSTANCE;

	/**
	 * 判断切点是否可以应用到给定的接口或者目标类
	 * @param clazz 目标类或接口
	 * @return 增强是否可以使用在给定的类上
	 */
	boolean matches(Class<?> clazz);

}
