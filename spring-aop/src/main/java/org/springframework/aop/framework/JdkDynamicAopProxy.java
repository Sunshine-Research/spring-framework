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

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Spring AOP基于JDK的{@link AopProxy}实现
 * <p>
 * 创建一个动态代理，实现了AOP暴露的接口，动态代理不能用于代理类（而不是接口）中定义的方法
 * <p>
 * 这种类型的对象需要通过代理工厂获得，由{@link AdvisedSupport}类提供配置
 * 这个类是Spring AOP的内部类，不需要直接由客户端直接进行编码
 * <p>
 * 如果目标类是线程安全的，使用此类创建的代理也是线程安全的
 * <p>
 * 如果所有的切面以及目标资源都是可序列化的，那么创建的代理类也是线程安全的
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	/*
	 * 需要注意的是：我们可以通过将"调用"重构为模板方法来避免此类与CGLIB动态代理之间的代码重复
	 * 但是这种方法可以提高性能
	 * 我们有很好的测试套件，可以确保不同的代理的表现是相同的
	 * 这样，我们可以利用每个类中的次要优化
	 */

	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/**
	 * 代理的配置信息
	 */
	private final AdvisedSupport advised;

	/**
	 * 代理接口是否声明了{@link #equals}方法
	 */
	private boolean equalsDefined;

	/**
	 * 代理接口是否声明了{@link #hashCode}方法
	 */
	private boolean hashCodeDefined;


	/**
	 * 使用给定的AOP配置构建一个JDK动态代理
	 * @param config {@link AdvisedSupport}AOP配置
	 * @throws AopConfigException 如果非法配置，会抛出异常
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		// 没有切面，并且没有目标代理类
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		// 设置增强代理配置
		this.advised = config;
	}


	@Override
	public Object getProxy() {
		// 使用默认的ClassLoader获取代理
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
		// 为给定的接口查询equals()、hashcode()方法
		findDefinedEqualsAndHashCodeMethods(proxiedInterfaces);
		// 使用Proxy创建代理实例
		return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
	}

	/**
	 * Finds any {@link #equals} or {@link #hashCode} method that may be defined
	 * on the supplied set of interfaces.
	 * @param proxiedInterfaces the interfaces to introspect
	 */
	private void findDefinedEqualsAndHashCodeMethods(Class<?>[] proxiedInterfaces) {
		for (Class<?> proxiedInterface : proxiedInterfaces) {
			Method[] methods = proxiedInterface.getDeclaredMethods();
			for (Method method : methods) {
				if (AopUtils.isEqualsMethod(method)) {
					this.equalsDefined = true;
				}
				if (AopUtils.isHashCodeMethod(method)) {
					this.hashCodeDefined = true;
				}
				if (this.equalsDefined && this.hashCodeDefined) {
					return;
				}
			}
		}
	}


	/**
	 * {@link InvocationHandler#invoke(Object, Method, Object[])}的实现
	 * 除非hook方法抛出了异常，调用者将获取目标方法抛出的异常
	 */
	@Override
	@Nullable
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object oldProxy = null;
		boolean setProxyContext = false;

		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			if (!this.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// 目标没有实现自己的equal()方法
				return equals(args[0]);
			}
			else if (!this.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// 目标没有实现自己的hashCode()方法
				return hashCode();
			}
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// There is only getDecoratedClass() declared -> dispatch to proxy config.
				return AopProxyUtils.ultimateTargetClass(this.advised);
			} else if (!this.advised.opaque && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				// Service invocations on ProxyConfig with the proxy config...
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}

			Object retVal;
			// 如果全局配置的exposeProxy属性为true
			if (this.advised.exposeProxy) {
				// 则暴露代理对象，将代理对象放入到AopContext中，也就是ThreadLocal中
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// 尽可能晚些，以最大程度的减少"拥有"目标对象的时间，以防它来自池化目标
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			// 获取当前方法的拦截器链
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// 是否包含了任何的Advice
			// 如果没有，可以直接回调目标的直接方法调用，而避免创建MethodInvocation
			if (chain.isEmpty()) {
				// 可以跳过创建MethodInvocation的步骤，而直接调用目标本身
				// 需要注意的是，最终的invoker必须是是一个InvokerInterceptor，因此可以知道它除了作用域目标的反射操作之外，什么都没有做，也没有任何的热插拔和代理
				// 适配目标方法的参数
				Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				// 使用反射调用连接点，并获取返回结果
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			} else {
				// 由于存在一个拦截器链，需要创建一套方法调用
				MethodInvocation invocation =
						new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
				// 通过拦截器链进入连接点
				// 通过递归调用，完成对interceptors链和连接点的调用，连接点是最后调用
				retVal = invocation.proceed();
			}

			// 推演返回值
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// 返回结果≠null，返回结果=目标资源，返回值类型≠Object，返回值类型是代理类的实例，方法类是RawTargetAccess的父类
				// 这是一种特殊的情况，返回值是"this"，返回值类型是类型兼容的
				// 需要注意的是，如果目标集在另一个返回对象中是一个指向自己的引用，那我们是什么都不了的
				// 传递代理引用
				// 直接将代理赋值给返回结果
				retVal = proxy;
			} else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
				// 在返回结果=null，返回值类型≠Void，返回值类型是基本类型
				// 返回类型不匹配，以为基本类型的返回值类型不应该返回null
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}
			// 其他情况，直接返回调用结果
			return retVal;
		}
		finally {
			if (target != null && !targetSource.isStatic()) {
				// 使用完成之后，释放资源
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// 如果开启了暴露服务，那么缓存已有的代理
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy) {
			otherProxy = (JdkDynamicAopProxy) other;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = (JdkDynamicAopProxy) ih;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}

}
