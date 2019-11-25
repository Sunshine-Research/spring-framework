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

package org.springframework.aop.framework;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.lang.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring的AOP实现
 * <p>
 * 使用反射调用了目标对象，子类可以重写{@link #invokeJoinpoint()}方法来改变行为，用于更专业的MethodInvocation实现
 * <p>
 * 可以通过{@link #invocableClone()}方法进行克隆调用，来重复调用{@link #proceed()}方法
 * 也可以将自定义属性附加在调用中
 * <p>
 * 主要注意的是，这个类用作内部使用的，不应该被直接请求
 * 作用域是public的唯一原因是适合和已存在的框架进行结合（比如Pitchfork）
 * 如果有其他的任何目的，使用{@link ProxyMethodInvocation}来代替
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @see #invokeJoinpoint
 * @see #proceed
 * @see #invocableClone
 * @see #setUserAttribute
 * @see #getUserAttribute
 */
public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {

	protected final Object proxy;

	@Nullable
	protected final Object target;

	protected final Method method;

	protected Object[] arguments;

	@Nullable
	private final Class<?> targetClass;

	/**
	 * 需要进行动态检查的MethodInterceptor和InterceptorAndDynamicMethodMatcher列表
	 */
	protected final List<?> interceptorsAndDynamicMethodMatchers;
	/**
	 * 开发者指定的调用属性的懒加载字典表
	 */
	@Nullable
	private Map<String, Object> userAttributes;
	/**
	 * 需要调用的当前的Interceptor
	 * 索引从0开始
	 */
	private int currentInterceptorIndex = -1;


	/**
	 * 使用给定的入参构建新的ReflectiveMethodInvocation
	 * @param proxy 调用作用的代理对象
	 * @param target 需要调用的目标对象
	 * @param method 需要调用的目标方法
	 * @param arguments 需要调用方法的参数
	 * @param targetClass 目标类，用于MethodMatcher的调用
	 * @param interceptorsAndDynamicMethodMatchers 需要使用的Interceptors，与任何InterceptorAndDynamicMethodMatchers一起，需要在运行时判断
	 *                                             在这个结构中包含的MethodMatchers必须已经尽可能静态的完成匹配
	 *                                             通过数组传递可以快10%，但是会使代码更复杂
	 *                                             仅会在静态节点执行
	 */
	protected ReflectiveMethodInvocation(
			Object proxy, @Nullable Object target, Method method, @Nullable Object[] arguments,
			@Nullable Class<?> targetClass, List<Object> interceptorsAndDynamicMethodMatchers) {

		this.proxy = proxy;
		this.target = target;
		this.targetClass = targetClass;
		this.method = BridgeMethodResolver.findBridgedMethod(method);
		this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
		this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
	}


	/**
	 * @return
	 */
	@Override
	public final Object getProxy() {
		return this.proxy;
	}

	@Override
	@Nullable
	public final Object getThis() {
		return this.target;
	}

	@Override
	public final AccessibleObject getStaticPart() {
		return this.method;
	}

	/**
	 * 返回在代理接口上调用的方法
	 * 可能对应或者不可能对应在该接口的基础实现上调用的方法
	 */
	@Override
	public final Method getMethod() {
		return this.method;
	}

	@Override
	public final Object[] getArguments() {
		return this.arguments;
	}

	@Override
	public void setArguments(Object... arguments) {
		this.arguments = arguments;
	}


	@Override
	@Nullable
	public Object proceed() throws Throwable {
		// 从索引-1开始，并且是提早增加
		if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
			return invokeJoinpoint();
		}
		// 从0开始，获取Interceptor或者拦截型增强
		Object interceptorOrInterceptionAdvice =
				this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
		// 如果是InterceptorAndDynamicMethodMatcher类型
		if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
			// 在这里判断动态方法匹配，静态的部分已经通过计算获取和匹配
			InterceptorAndDynamicMethodMatcher dm =
					(InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
			Class<?> targetClass = (this.targetClass != null ? this.targetClass : this.method.getDeclaringClass());
			// 如果匹配
			if (dm.methodMatcher.matches(this.method, targetClass, this.arguments)) {
				// 执行Interceptor
				return dm.interceptor.invoke(this);
			} else {
				// 动态匹配失败，跳过这个Interceptor，调用Interceptors链中的下一个Interceptor
				return proceed();
			}
		} else {
			// 此时类型是一个Interceptor，直接调用即可，切点将会在对象构造之前通过静态校验的方式完成判断
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}

	/**
	 * 使用反射调用连接点
	 * 子类可以重写此方法来实现自定义调用
	 * @return 连接点的返回值
	 * @throws Throwable 调用连接点返回的异常
	 */
	@Nullable
	protected Object invokeJoinpoint() throws Throwable {
		return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
	}


	/**
	 * 调用对象的影子拷贝
	 * 包含原始参数数组的独立拷贝
	 * 在这种情况下需要拷贝，比如想要在不同的对象引用中获取相同的Interceptor链，但是还需要当前Interceptor独立索引
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone() {
		Object[] cloneArguments = this.arguments;
		if (this.arguments.length > 0) {
			// 构建参数数组的独立拷贝
			cloneArguments = this.arguments.clone();
		}
		// 生成调用拷贝
		return invocableClone(cloneArguments);
	}

	/**
	 * 调用对象的影子拷贝，使用给定的参数进行拷贝
	 * 在这种情况下需要拷贝，比如想要在不同的对象引用中获取相同的Interceptor链，但是还需要当前Interceptor独立索引
	 * @see java.lang.Object#clone()
	 */
	@Override
	public MethodInvocation invocableClone(Object... arguments) {
		// 强制实现开发者自定义属性字典表
		// 这样可以在克隆中共享同一个字典表引用
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}

		// 创建MethodInvocation克隆对象
		try {
			ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation) clone();
			clone.arguments = arguments;
			return clone;
		} catch (CloneNotSupportedException ex) {
			throw new IllegalStateException(
					"Should be able to clone object of type [" + getClass() + "]: " + ex);
		}
	}


	@Override
	public void setUserAttribute(String key, @Nullable Object value) {
		if (value != null) {
			if (this.userAttributes == null) {
				this.userAttributes = new HashMap<>();
			}
			this.userAttributes.put(key, value);
		}
		else {
			if (this.userAttributes != null) {
				this.userAttributes.remove(key);
			}
		}
	}

	@Override
	@Nullable
	public Object getUserAttribute(String key) {
		return (this.userAttributes != null ? this.userAttributes.get(key) : null);
	}

	/**
	 * 返回和当前调用相关联的开发者属性，此方法提供了和ThreadLocal相关联的调用绑定
	 * 字典表是懒加载的，并且不会用于AOP框架中
	 * @return 任何和调用关联的开发者定义的属性
	 */
	public Map<String, Object> getUserAttributes() {
		if (this.userAttributes == null) {
			this.userAttributes = new HashMap<>();
		}
		return this.userAttributes;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
		sb.append(this.method).append("; ");
		if (this.target == null) {
			sb.append("target is null");
		}
		else {
			sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
		}
		return sb.toString();
	}

}
