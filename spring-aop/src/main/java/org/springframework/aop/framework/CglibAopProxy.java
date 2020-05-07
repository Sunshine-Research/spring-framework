/*
 * Copyright 2002-2020 the original author or authors.
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

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Dispatcher;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * CGLIB-based {@link AopProxy} implementation for the Spring AOP framework.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} object. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>{@link DefaultAopProxyFactory} will automatically create CGLIB-based
 * proxies if necessary, for example in case of proxying a target class
 * (see the {@link DefaultAopProxyFactory attendant javadoc} for details).
 *
 * <p>Proxies created using this class are thread-safe if the underlying
 * (target) class is thread-safe.
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Chris Beams
 * @author Dave Syer
 * @see org.springframework.cglib.proxy.Enhancer
 * @see AdvisedSupport#setProxyTargetClass
 * @see DefaultAopProxyFactory
 */
@SuppressWarnings("serial")
class CglibAopProxy implements AopProxy, Serializable {

	/**
	 * Logger available to subclasses; static to optimize serialization.
	 */
	protected static final Log logger = LogFactory.getLog(CglibAopProxy.class);
	private static final int INVOKE_TARGET = 1;
	private static final int NO_OVERRIDE = 2;
	private static final int DISPATCH_TARGET = 3;
	private static final int DISPATCH_ADVISED = 4;
	private static final int INVOKE_EQUALS = 5;
	private static final int INVOKE_HASHCODE = 6;
	/**
	 * CGLIB回调任务的常量索引
	 */
	private static final int AOP_PROXY = 0;
	/**
	 * Keeps track of the Classes that we have validated for final methods.
	 */
	private static final Map<Class<?>, Boolean> validatedClasses = new WeakHashMap<>();


	/**
	 * The configuration used to configure this proxy.
	 */
	protected final AdvisedSupport advised;

	@Nullable
	protected Object[] constructorArgs;

	@Nullable
	protected Class<?>[] constructorArgTypes;

	/**
	 * Dispatcher used for methods on Advised.
	 */
	private final transient AdvisedDispatcher advisedDispatcher;
	/**
	 * 比如静态，或者Advised配置已经锁定的情况下
	 * 固定的方法的interceptors集合缓存
	 */
	private transient Map<Method, Integer> fixedInterceptorMap = Collections.emptyMap();

	private transient int fixedInterceptorOffset;


	/**
	 * Create a new CglibAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 *                            exception in this case, rather than let a mysterious failure happen later.
	 */
	public CglibAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		this.advised = config;
		this.advisedDispatcher = new AdvisedDispatcher(this.advised);
	}

	/**
	 * Set constructor arguments to use for creating the proxy.
	 * @param constructorArgs     the constructor argument values
	 * @param constructorArgTypes the constructor argument types
	 */
	public void setConstructorArguments(@Nullable Object[] constructorArgs, @Nullable Class<?>[] constructorArgTypes) {
		if (constructorArgs == null || constructorArgTypes == null) {
			throw new IllegalArgumentException("Both 'constructorArgs' and 'constructorArgTypes' need to be specified");
		}
		if (constructorArgs.length != constructorArgTypes.length) {
			throw new IllegalArgumentException("Number of 'constructorArgs' (" + constructorArgs.length +
					") must match number of 'constructorArgTypes' (" + constructorArgTypes.length + ")");
		}
		this.constructorArgs = constructorArgs;
		this.constructorArgTypes = constructorArgTypes;
	}


	@Override
	public Object getProxy() {
		return getProxy(null);
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating CGLIB proxy: " + this.advised.getTargetSource());
		}

		try {
			// 获取目标类
			Class<?> rootClass = this.advised.getTargetClass();
			Assert.state(rootClass != null, "Target class must be available for creating a CGLIB proxy");
			// 获取代理的超类类型
			Class<?> proxySuperClass = rootClass;
			// 判断目标类是否是CGLIB产生的代理类
			// 判断目标类的类名称中是否包含"$$"
			if (rootClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
				// 如果说CGLIB代理的类，获取rootClass的超类和实现的接口，添加到全局Advise配置中
				proxySuperClass = rootClass.getSuperclass();
				Class<?>[] additionalInterfaces = rootClass.getInterfaces();
				for (Class<?> additionalInterface : additionalInterfaces) {
					this.advised.addInterface(additionalInterface);
				}
			}

			// 校验代理的超类类型
			validateClassIfNecessary(proxySuperClass, classLoader);

			// 创建CGLIB enhancer
			Enhancer enhancer = createEnhancer();
			if (classLoader != null) {
				// 设置ClassLoader
				enhancer.setClassLoader(classLoader);
				if (classLoader instanceof SmartClassLoader &&
						((SmartClassLoader) classLoader).isClassReloadable(proxySuperClass)) {
					enhancer.setUseCache(false);
				}
			}
			// 设置超类、接口、命名策略，以及
			enhancer.setSuperclass(proxySuperClass);
			enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
			enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
			enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(classLoader));
			// 获取当前类的所有回调方法
			Callback[] callbacks = getCallbacks(rootClass);
			Class<?>[] types = new Class<?>[callbacks.length];
			for (int x = 0; x < types.length; x++) {
				// 获取拦截方法的所有类型
				types[x] = callbacks[x].getClass();
			}
			// fixedInterceptorMap仅会在上述调用完成之后填充
			enhancer.setCallbackFilter(new ProxyCallbackFilter(
					this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset));
			// 设置代理类所有回调任务的类类型
			enhancer.setCallbackTypes(types);

			// 生成代理类，并创建代理实例
			// ObjenesisCglibAopProxy
			return createProxyClassAndInstance(enhancer, callbacks);
		} catch (CodeGenerationException | IllegalArgumentException ex) {
			throw new AopConfigException("Could not generate CGLIB subclass of " + this.advised.getTargetClass() +
					": Common causes of this problem include using a final class or a non-visible class",
					ex);
		} catch (Throwable ex) {
			// 获取目标资源失败，抛出异常
			throw new AopConfigException("Unexpected AOP exception", ex);
		}
	}

	/**
	 * 创建CGLIB代理实例
	 * @param enhancer  代理类信息集合器
	 * @param callbacks 代理的类的所有回调任务
	 * @return 代理实例
	 */
	protected Object createProxyClassAndInstance(Enhancer enhancer, Callback[] callbacks) {
		enhancer.setInterceptDuringConstruction(false);
		enhancer.setCallbacks(callbacks);
		return (this.constructorArgs != null && this.constructorArgTypes != null ?
				enhancer.create(this.constructorArgTypes, this.constructorArgs) :
				enhancer.create());
	}

	/**
	 * 创建CGLIB {@link Enhancer}
	 * 子类可以进行重写来实现自定义的enhancer
	 */
	protected Enhancer createEnhancer() {
		return new Enhancer();
	}

	/**
	 * Checks to see whether the supplied {@code Class} has already been validated and
	 * validates it if not.
	 */
	private void validateClassIfNecessary(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader) {
		if (logger.isWarnEnabled()) {
			synchronized (validatedClasses) {
				if (!validatedClasses.containsKey(proxySuperClass)) {
					doValidateClass(proxySuperClass, proxyClassLoader,
							ClassUtils.getAllInterfacesForClassAsSet(proxySuperClass));
					validatedClasses.put(proxySuperClass, Boolean.TRUE);
				}
			}
		}
	}

	/**
	 * Checks for final methods on the given {@code Class}, as well as package-visible
	 * methods across ClassLoaders, and writes warnings to the log for each one found.
	 */
	private void doValidateClass(Class<?> proxySuperClass, @Nullable ClassLoader proxyClassLoader, Set<Class<?>> ifcs) {
		if (proxySuperClass != Object.class) {
			Method[] methods = proxySuperClass.getDeclaredMethods();
			for (Method method : methods) {
				int mod = method.getModifiers();
				if (!Modifier.isStatic(mod) && !Modifier.isPrivate(mod)) {
					if (Modifier.isFinal(mod)) {
						if (logger.isInfoEnabled() && implementsInterface(method, ifcs)) {
							logger.info("Unable to proxy interface-implementing method [" + method + "] because " +
									"it is marked as final: Consider using interface-based JDK proxies instead!");
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Final method [" + method + "] cannot get proxied via CGLIB: " +
									"Calls to this method will NOT be routed to the target instance and " +
									"might lead to NPEs against uninitialized fields in the proxy instance.");
						}
					} else if (logger.isDebugEnabled() && !Modifier.isPublic(mod) && !Modifier.isProtected(mod) &&
							proxyClassLoader != null && proxySuperClass.getClassLoader() != proxyClassLoader) {
						logger.debug("Method [" + method + "] is package-visible across different ClassLoaders " +
								"and cannot get proxied via CGLIB: Declare this method as public or protected " +
								"if you need to support invocations through the proxy.");
					}
				}
			}
			doValidateClass(proxySuperClass.getSuperclass(), proxyClassLoader, ifcs);
		}
	}

	/**
	 * 获取类的所有回调方法
	 * @param rootClass 给定需要获取的类
	 * @return 给定类的所有回调方法
	 * @throws Exception
	 */
	private Callback[] getCallbacks(Class<?> rootClass) throws Exception {
		// 用于优化选择的参数
		boolean exposeProxy = this.advised.isExposeProxy();
		boolean isFrozen = this.advised.isFrozen();
		boolean isStatic = this.advised.getTargetSource().isStatic();

		// 选择一个aop interceptor
		Callback aopInterceptor = new DynamicAdvisedInterceptor(this.advised);

		// 选择一个直接路由到目标的interceptor，用于非Advised但是可以返回的调用，可能用于暴露proxy
		Callback targetInterceptor;
		if (exposeProxy) {
			targetInterceptor = (isStatic ?
					new StaticUnadvisedExposedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedExposedInterceptor(this.advised.getTargetSource()));
		} else {
			targetInterceptor = (isStatic ?
					new StaticUnadvisedInterceptor(this.advised.getTargetSource().getTarget()) :
					new DynamicUnadvisedInterceptor(this.advised.getTargetSource()));
		}

		// 选择一个直接到目标的分发器，用于非Advised调用到静态目标，但是不能返回的目标任务
		Callback targetDispatcher = (isStatic ?
				new StaticDispatcher(this.advised.getTargetSource().getTarget()) : new SerializableNoOp());
		// 核心回调任务
		Callback[] mainCallbacks = new Callback[]{
				aopInterceptor,  // 用于普通的Advice
				targetInterceptor,  // 再优化的情况下，调用目标，不需要考虑Advice
				new SerializableNoOp(),  // 没有覆盖映射到此的方法
				targetDispatcher, this.advisedDispatcher,
				new EqualsInterceptor(this.advised),
				new HashCodeInterceptor(this.advised)
		};

		Callback[] callbacks;

		// 如果目标是静态的，并且Advice链处于冰冻状态，可以通过一些优化来使用该方法的固定链直接发送AOP调用到目标
		if (isStatic && isFrozen) {
			// 获取目标类的所有方法
			Method[] methods = rootClass.getMethods();
			// 固定的回调任务数组
			Callback[] fixedCallbacks = new Callback[methods.length];
			this.fixedInterceptorMap = new HashMap<>(methods.length);

			// TODO: 一些内存的小优化，可以跳过没有Advice的方法创建
			for (int x = 0; x < methods.length; x++) {
				Method method = methods[x];
				// 获取给定方法的MethodInterceptor列表
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, rootClass);
				// 使用获取的拦截器链构建当前方法的回调任务
				fixedCallbacks[x] = new FixedChainStaticTargetInterceptor(
						chain, this.advised.getTargetSource().getTarget(), this.advised.getTargetClass());
				// 由于是固定的，可以将其放入到缓存中
				this.fixedInterceptorMap.put(method, x);
			}

			// 同时拷贝主要回调任务和固定回调任务
			callbacks = new Callback[mainCallbacks.length + fixedCallbacks.length];
			System.arraycopy(mainCallbacks, 0, callbacks, 0, mainCallbacks.length);
			System.arraycopy(fixedCallbacks, 0, callbacks, mainCallbacks.length, fixedCallbacks.length);
			this.fixedInterceptorOffset = mainCallbacks.length;
		} else {
			// 目标资源不是静待的，或者全局Advised配置没有处于冰冻状态
			// 直接拷贝主回调任务
			callbacks = mainCallbacks;
		}
		// 返回给定类筛选出来的回调任务
		return callbacks;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof CglibAopProxy &&
				AopProxyUtils.equalsInProxy(this.advised, ((CglibAopProxy) other).advised)));
	}

	@Override
	public int hashCode() {
		return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}


	/**
	 * Check whether the given method is declared on any of the given interfaces.
	 */
	private static boolean implementsInterface(Method method, Set<Class<?>> ifcs) {
		for (Class<?> ifc : ifcs) {
			if (ClassUtils.hasMethod(ifc, method)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Process a return value. Wraps a return of {@code this} if necessary to be the
	 * {@code proxy} and also verifies that {@code null} is not returned as a primitive.
	 */
	@Nullable
	private static Object processReturnType(
			Object proxy, @Nullable Object target, Method method, @Nullable Object returnValue) {

		// Massage return value if necessary
		if (returnValue != null && returnValue == target &&
				!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
			// Special case: it returned "this". Note that we can't help
			// if the target sets a reference to itself in another returned object.
			returnValue = proxy;
		}
		Class<?> returnType = method.getReturnType();
		if (returnValue == null && returnType != Void.TYPE && returnType.isPrimitive()) {
			throw new AopInvocationException(
					"Null return value from advice does not match primitive return type for: " + method);
		}
		return returnValue;
	}


	/**
	 * Serializable replacement for CGLIB's NoOp interface.
	 * Public to allow use elsewhere in the framework.
	 */
	public static class SerializableNoOp implements NoOp, Serializable {
	}


	/**
	 * Method interceptor used for static targets with no advice chain. The call
	 * is passed directly back to the target. Used when the proxy needs to be
	 * exposed and it can't be determined that the method won't return
	 * {@code this}.
	 */
	private static class StaticUnadvisedInterceptor implements MethodInterceptor, Serializable {

		@Nullable
		private final Object target;

		public StaticUnadvisedInterceptor(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object retVal = methodProxy.invoke(this.target, args);
			return processReturnType(proxy, this.target, method, retVal);
		}
	}


	/**
	 * Method interceptor used for static targets with no advice chain, when the
	 * proxy is to be exposed.
	 */
	private static class StaticUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {

		@Nullable
		private final Object target;

		public StaticUnadvisedExposedInterceptor(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			try {
				oldProxy = AopContext.setCurrentProxy(proxy);
				Object retVal = methodProxy.invoke(this.target, args);
				return processReturnType(proxy, this.target, method, retVal);
			} finally {
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Interceptor used to invoke a dynamic target without creating a method
	 * invocation or evaluating an advice chain. (We know there was no advice
	 * for this method.)
	 */
	private static class DynamicUnadvisedInterceptor implements MethodInterceptor, Serializable {

		private final TargetSource targetSource;

		public DynamicUnadvisedInterceptor(TargetSource targetSource) {
			this.targetSource = targetSource;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object target = this.targetSource.getTarget();
			try {
				Object retVal = methodProxy.invoke(target, args);
				return processReturnType(proxy, target, method, retVal);
			} finally {
				if (target != null) {
					this.targetSource.releaseTarget(target);
				}
			}
		}
	}


	/**
	 * Interceptor for unadvised dynamic targets when the proxy needs exposing.
	 */
	private static class DynamicUnadvisedExposedInterceptor implements MethodInterceptor, Serializable {

		private final TargetSource targetSource;

		public DynamicUnadvisedExposedInterceptor(TargetSource targetSource) {
			this.targetSource = targetSource;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			Object target = this.targetSource.getTarget();
			try {
				oldProxy = AopContext.setCurrentProxy(proxy);
				Object retVal = methodProxy.invoke(target, args);
				return processReturnType(proxy, target, method, retVal);
			} finally {
				AopContext.setCurrentProxy(oldProxy);
				if (target != null) {
					this.targetSource.releaseTarget(target);
				}
			}
		}
	}


	/**
	 * Dispatcher for a static target. Dispatcher is much faster than
	 * interceptor. This will be used whenever it can be determined that a
	 * method definitely does not return "this"
	 */
	private static class StaticDispatcher implements Dispatcher, Serializable {

		@Nullable
		private final Object target;

		public StaticDispatcher(@Nullable Object target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object loadObject() {
			return this.target;
		}
	}


	/**
	 * Dispatcher for any methods declared on the Advised class.
	 */
	private static class AdvisedDispatcher implements Dispatcher, Serializable {

		private final AdvisedSupport advised;

		public AdvisedDispatcher(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		public Object loadObject() {
			return this.advised;
		}
	}


	/**
	 * Dispatcher for the {@code equals} method.
	 * Ensures that the method call is always handled by this class.
	 */
	private static class EqualsInterceptor implements MethodInterceptor, Serializable {

		private final AdvisedSupport advised;

		public EqualsInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
			Object other = args[0];
			if (proxy == other) {
				return true;
			}
			if (other instanceof Factory) {
				Callback callback = ((Factory) other).getCallback(INVOKE_EQUALS);
				if (!(callback instanceof EqualsInterceptor)) {
					return false;
				}
				AdvisedSupport otherAdvised = ((EqualsInterceptor) callback).advised;
				return AopProxyUtils.equalsInProxy(this.advised, otherAdvised);
			} else {
				return false;
			}
		}
	}


	/**
	 * Dispatcher for the {@code hashCode} method.
	 * Ensures that the method call is always handled by this class.
	 */
	private static class HashCodeInterceptor implements MethodInterceptor, Serializable {

		private final AdvisedSupport advised;

		public HashCodeInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
			return CglibAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
		}
	}


	/**
	 * Interceptor used specifically for advised methods on a frozen, static proxy.
	 */
	private static class FixedChainStaticTargetInterceptor implements MethodInterceptor, Serializable {

		private final List<Object> adviceChain;

		@Nullable
		private final Object target;

		@Nullable
		private final Class<?> targetClass;

		public FixedChainStaticTargetInterceptor(
				List<Object> adviceChain, @Nullable Object target, @Nullable Class<?> targetClass) {

			this.adviceChain = adviceChain;
			this.target = target;
			this.targetClass = targetClass;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			MethodInvocation invocation = new CglibMethodInvocation(
					proxy, this.target, method, args, this.targetClass, this.adviceChain, methodProxy);
			// If we get here, we need to create a MethodInvocation.
			Object retVal = invocation.proceed();
			retVal = processReturnType(proxy, this.target, method, retVal);
			return retVal;
		}
	}


	/**
	 * General purpose AOP callback. Used when the target is dynamic or when the
	 * proxy is not frozen.
	 */
	private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {

		private final AdvisedSupport advised;

		public DynamicAdvisedInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
				if (this.advised.exposeProxy) {
					// Make invocation available if necessary.
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}
				// Get as late as possible to minimize the time we "own" the target, in case it comes from a pool...
				target = targetSource.getTarget();
				Class<?> targetClass = (target != null ? target.getClass() : null);
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;
				// Check whether we only have one InvokerInterceptor: that is,
				// no real advice, but just reflective invocation of the target.
				if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
					// We can skip creating a MethodInvocation: just invoke the target directly.
					// Note that the final invoker must be an InvokerInterceptor, so we know
					// it does nothing but a reflective operation on the target, and no hot
					// swapping or fancy proxying.
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					retVal = methodProxy.invoke(target, argsToUse);
				} else {
					// We need to create a method invocation...
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				retVal = processReturnType(proxy, target, method, retVal);
				return retVal;
			} finally {
				if (target != null && !targetSource.isStatic()) {
					targetSource.releaseTarget(target);
				}
				if (setProxyContext) {
					// Restore old proxy.
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other ||
					(other instanceof DynamicAdvisedInterceptor &&
							this.advised.equals(((DynamicAdvisedInterceptor) other).advised)));
		}

		/**
		 * CGLIB uses this to drive proxy creation.
		 */
		@Override
		public int hashCode() {
			return this.advised.hashCode();
		}
	}


	/**
	 * AOP联盟的MethodInvocation实现
	 * 用于AOP代理
	 */
	private static class CglibMethodInvocation extends ReflectiveMethodInvocation {

		@Nullable
		private final MethodProxy methodProxy;

		public CglibMethodInvocation(Object proxy, @Nullable Object target, Method method,
									 Object[] arguments, @Nullable Class<?> targetClass,
									 List<Object> interceptorsAndDynamicMethodMatchers, MethodProxy methodProxy) {

			super(proxy, target, method, arguments, targetClass, interceptorsAndDynamicMethodMatchers);

			// 仅用于修饰符为public并且方法类型不是Object类型
			this.methodProxy = (Modifier.isPublic(method.getModifiers()) &&
					method.getDeclaringClass() != Object.class && !AopUtils.isEqualsMethod(method) &&
					!AopUtils.isHashCodeMethod(method) && !AopUtils.isToStringMethod(method) ?
					methodProxy : null);
		}

		@Override
		@Nullable
		public Object proceed() throws Throwable {
			try {
				// 使用ReflectiveMethodInvocation那一套递归的interceptors和JoinPoint链的调用方式
				return super.proceed();
			} catch (RuntimeException ex) {
				throw ex;
			} catch (Exception ex) {
				if (ReflectionUtils.declaresException(getMethod(), ex.getClass())) {
					throw ex;
				} else {
					throw new UndeclaredThrowableException(ex);
				}
			}
		}

		/**
		 * 重写了ReflectiveMethodInvocation中调用JoinPoint的逻辑
		 * 在调用public方法时，对调用目标方法使用反射可以获得一定程度的性能提高
		 */
		@Override
		protected Object invokeJoinpoint() throws Throwable {
			if (this.methodProxy != null) {
				// 存在MethodProxy的情况下，使用反射进行调用
				return this.methodProxy.invoke(this.target, this.arguments);
			} else {
				// 否则，交由ReflectiveMethodInvocation的执行逻辑
				return super.invokeJoinpoint();
			}
		}
	}


	/**
	 * 回调任务过滤器
	 * 用于分配方法的回调任务
	 */
	private static class ProxyCallbackFilter implements CallbackFilter {
		/**
		 * 全局的Advised配置
		 */
		private final AdvisedSupport advised;
		/**
		 * 固定的interceptor缓存集合
		 */
		private final Map<Method, Integer> fixedInterceptorMap;
		/**
		 * 固定的interceptors的偏移量
		 */
		private final int fixedInterceptorOffset;

		public ProxyCallbackFilter(
				AdvisedSupport advised, Map<Method, Integer> fixedInterceptorMap, int fixedInterceptorOffset) {

			this.advised = advised;
			this.fixedInterceptorMap = fixedInterceptorMap;
			this.fixedInterceptorOffset = fixedInterceptorOffset;
		}

		/**
		 * {@link CallbackFilter#accept(Method)}方法的实现，并返回需要的回调任务索引
		 * <p>The callback used is determined thus:
		 * 每个代理的回调任务都是用于一般使用的固定回调任务集合构建的，然后是一组特定于目标链上用于静态目标方法的回调
		 * 因此确认使用的回调任务是：
		 * 对于暴露的代理:
		 * 暴露的代理需要执行方法/链的之前、之后的调用代码，这意味着我们必须使用DynamicAdvisedInterceptor，因此所有其他interceptors可以避免对try/catch代码块的需求
		 *
		 * 对于{@link Object#finalize()}:
		 * 没有使用此方法的替代
		 * 对于{@link Object#equals(Object)}}
		 * {@link EqualsInterceptor}用于重定向到代理特殊处理逻辑的equals()调用
		 *
		 * 对于Advised上的方法：
		 * AdvisedDispatcher用于分派调用直接到目标上
		 *
		 * 对于Advised方法：
		 * 如果目标对象是静态的，并且Advice链处于冷冻状态，将会为方法指定一个FixedChainStaticTargetInterceptor来调用Advice链
		 * 否则使用DynamicAdvisedInterceptor
		 *
		 * 对于非Advised方法
		 * 可以确定该方法不返回{@code this}，或者当{@code ProxyFactory.getExposeProxy()}返回{@code false}时
		 * 会使用一个分配器，对于静态的目标，使用StaticDispatcher，对于动态的目标，使用DynamicUnadvisedInterceptor
		 * 如果该方法可能返回{@code this}，则将StaticUnadvisedInterceptor用于静态目标，DynamicUnadvisedInterceptor已经考虑到了这一点
		 */
		@Override
		public int accept(Method method) {
			if (AopUtils.isFinalizeMethod(method)) {
				logger.trace("Found finalize() method - using NO_OVERRIDE");
				return NO_OVERRIDE;
			}
			if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Method is declared on Advised interface: " + method);
				}
				return DISPATCH_ADVISED;
			}
			// 必须代理equals()方法来引导调用到这里
			if (AopUtils.isEqualsMethod(method)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Found 'equals' method: " + method);
				}
				return INVOKE_EQUALS;
			}
			// 必须使用基于proxy计算的hashCode()方法
			if (AopUtils.isHashCodeMethod(method)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Found 'hashCode' method: " + method);
				}
				return INVOKE_HASHCODE;
			}
			Class<?> targetClass = this.advised.getTargetClass();
			// 代理目前还不可用，但是不会影响什么
			// 获取目标方法的拦截器链
			List<?> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
			// 是否包含Advice
			boolean haveAdvice = !chain.isEmpty();
			// 是否需要暴露代理
			boolean exposeProxy = this.advised.isExposeProxy();
			// 目标资源是否是静态的
			boolean isStatic = this.advised.getTargetSource().isStatic();
			// 全局Advised配置管理是否处于冷冻状态
			boolean isFrozen = this.advised.isFrozen();
			// 如果有Advice链或者Advised没有处于冷冻状态
			if (haveAdvice || !isFrozen) {
				// 如果需要暴露proxy，则必须使用AOP_PROXY
				if (exposeProxy) {
					if (logger.isTraceEnabled()) {
						logger.trace("Must expose proxy on advised method: " + method);
					}
					return AOP_PROXY;
				}
				// 我们需要检查在固定的interceptors中是否包含给定的方法
				if (isStatic && isFrozen && this.fixedInterceptorMap.containsKey(method)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Method has advice and optimizations are enabled: " + method);
					}
					// 我们知道我们正在优化，因此我们可以使用FixedStaticChainInterceptors
					int index = this.fixedInterceptorMap.get(method);
					return (index + this.fixedInterceptorOffset);
				} else {
					// 如果在固定的interceptors中仍然没有给定的方法，则返回AOP_PROXY
					if (logger.isTraceEnabled()) {
						logger.trace("Unable to apply any optimizations to advised method: " + method);
					}
					return AOP_PROXY;
				}
			} else {
				// 此时状态为没有Advice链或者Advised处于冷冻状态
				// 检查方法的返回值类型是否在目标类型的类层次结构之外
				// 如果在目标类型的层次之外，知道它永不需要返回值类型信息和可以使用的分配器
				// 如果一个proxy正在暴露，那么就必须使用正确配置的interceptor
				// 如果目标资源不是静态的，我们不能使用分配器因为目标资源需要在调用后显示释放

				// 如果需要暴露代理，或者目标资源不是静态的，则返回INVOKE_TARGET
				if (exposeProxy || !isStatic) {
					return INVOKE_TARGET;
				}
				// 否则，获取方法的返回值类型
				Class<?> returnType = method.getReturnType();
				if (targetClass != null && returnType.isAssignableFrom(targetClass)) {
					// 如果方法的返回值类型是目标类的自累成，则使用INVOKE_TARGET
					if (logger.isTraceEnabled()) {
						logger.trace("Method return type is assignable from target type and " +
								"may therefore return 'this' - using INVOKE_TARGET: " + method);
					}
					return INVOKE_TARGET;
				} else {
					// 否则，使用DISPATCH_TARGET
					if (logger.isTraceEnabled()) {
						logger.trace("Method return type ensures 'this' cannot be returned - " +
								"using DISPATCH_TARGET: " + method);
					}
					return DISPATCH_TARGET;
				}
			}
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ProxyCallbackFilter)) {
				return false;
			}
			ProxyCallbackFilter otherCallbackFilter = (ProxyCallbackFilter) other;
			AdvisedSupport otherAdvised = otherCallbackFilter.advised;
			if (this.advised.isFrozen() != otherAdvised.isFrozen()) {
				return false;
			}
			if (this.advised.isExposeProxy() != otherAdvised.isExposeProxy()) {
				return false;
			}
			if (this.advised.getTargetSource().isStatic() != otherAdvised.getTargetSource().isStatic()) {
				return false;
			}
			if (!AopProxyUtils.equalsProxiedInterfaces(this.advised, otherAdvised)) {
				return false;
			}
			// Advice instance identity is unimportant to the proxy class:
			// All that matters is type and ordering.
			Advisor[] thisAdvisors = this.advised.getAdvisors();
			Advisor[] thatAdvisors = otherAdvised.getAdvisors();
			if (thisAdvisors.length != thatAdvisors.length) {
				return false;
			}
			for (int i = 0; i < thisAdvisors.length; i++) {
				Advisor thisAdvisor = thisAdvisors[i];
				Advisor thatAdvisor = thatAdvisors[i];
				if (!equalsAdviceClasses(thisAdvisor, thatAdvisor)) {
					return false;
				}
				if (!equalsPointcuts(thisAdvisor, thatAdvisor)) {
					return false;
				}
			}
			return true;
		}

		private static boolean equalsAdviceClasses(Advisor a, Advisor b) {
			return (a.getAdvice().getClass() == b.getAdvice().getClass());
		}

		private static boolean equalsPointcuts(Advisor a, Advisor b) {
			// If only one of the advisor (but not both) is PointcutAdvisor, then it is a mismatch.
			// Takes care of the situations where an IntroductionAdvisor is used (see SPR-3959).
			return (!(a instanceof PointcutAdvisor) ||
					(b instanceof PointcutAdvisor &&
							ObjectUtils.nullSafeEquals(((PointcutAdvisor) a).getPointcut(), ((PointcutAdvisor) b).getPointcut())));
		}

		@Override
		public int hashCode() {
			int hashCode = 0;
			Advisor[] advisors = this.advised.getAdvisors();
			for (Advisor advisor : advisors) {
				Advice advice = advisor.getAdvice();
				hashCode = 13 * hashCode + advice.getClass().hashCode();
			}
			hashCode = 13 * hashCode + (this.advised.isFrozen() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isExposeProxy() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isOptimize() ? 1 : 0);
			hashCode = 13 * hashCode + (this.advised.isOpaque() ? 1 : 0);
			return hashCode;
		}
	}

}
