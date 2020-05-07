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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**
	 * 缓存的单例实例
	 * 缓存的是实例
	 * 一级缓存
	 * 进入实例化阶段
	 * key: beanName
	 * value: bean实例
	 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 缓存的是ObjectFactory
	 * 是为了循环依赖而缓存的ObjectFactory
	 * 三级缓存
	 * key: beanName
	 * value: ObjectFactory
	 */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 缓存的是提早暴露的实例
	 * 是为了解决循环依赖而提早暴露的实例
	 * 二级缓存
	 * 完成实例化但尚未初始化
	 * key: beanName
	 * value: Object
	 */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/**
	 * 大而全，需要判断当前bean是否已经注册为单例bean
	 * 即上面三个集合中的任意一个添加了，都会添加到此集合中
	 * 已经注册的单例bean
	 */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * Names of beans that are currently in creation.
	 */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * Names of beans currently excluded from in creation checks.
	 */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * List of suppressed Exceptions, available for associating related causes.
	 */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/**
	 * 目前是否处于bean的销毁状态
	 */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * Disposable bean instances: bean name to disposable instance.
	 */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/**
	 * key: 单例beanName
	 * value: 单例bean包含的bean集合
	 */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/**
	 * key: 单例beanName
	 * value: 依赖此bean的集合
	 */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/**
	 * key: 单例beanName
	 * value: 创建bean时，所需要依赖bean集合
	 */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 向当前BeanFactory的缓存中放入新创建的单例
	 * <p>
	 * 每个急于创建的单例都会调用
	 * @param beanName        beanName
	 * @param singletonObject 创建的单例对象
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			// 放入一级缓存
			this.singletonObjects.put(beanName, singletonObject);
			// 从三级缓存中移除
			this.singletonFactories.remove(beanName);
			// 从二级缓存中移除
			this.earlySingletonObjects.remove(beanName);
			// 添加到已注册的单例缓存中
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName         the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			if (!this.singletonObjects.containsKey(beanName)) {
				this.singletonFactories.put(beanName, singletonFactory);
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 返回给定beanName注册的原生单例对象
	 * <p>
	 * 检查已经实例化的单例
	 * 也允许返回一个正在创建的单例的早期引用（解决循环依赖问题）
	 * @param beanName            the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// 首先从已经实例化的单例缓存中获取单例bean实例
		Object singletonObject = this.singletonObjects.get(beanName);
		// 如果单例对象不存在，则继续判断单例是否处于正在创建状态
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 如果单例处于正在创建状态，则对一级缓存进行同步操作
			synchronized (this.singletonObjects) {
				// 先从二级缓存中获取单例bean实例
				singletonObject = this.earlySingletonObjects.get(beanName);
				// 如果二级缓存中也未创建单例bean，但是仍允许
				if (singletonObject == null && allowEarlyReference) {
					// 从三级缓存中获取正在实例化的单例bean的ObjectFactory
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						// 获取ObjectFactory生产对象，放入到二级缓存中，并从一级缓存中删除
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		// 返回获取到的beanName的实例
		return singletonObject;
	}

	/**
	 * 返回原生的注册的单例对象，如果还没有注册，则会创建或注册一个
	 * @param beanName         beanName
	 * @param singletonFactory 创建的bean实例
	 * @return 注册的单例对象
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		synchronized (this.singletonObjects) {
			// 先从一级缓存中获取当前beanName是否已注册
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				// bean是否处于正在销毁状态
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
									"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 一级缓存中没有，那么大概率处于未创建或创建中，进行一些准备工作
				// 将beanName状态置为创建中
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 从单例工厂中获取创建的bean实例，也就是从是doCreateBean返回的bean实例中获取初始化后的单例对象
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// 同时，单例对象是否隐式出现
					// 如果隐式出现，继续进行处理，因为一场提示该状态
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				} catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				} finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					// 单例创建完成的收尾工作
					// 移除beanName正在创建的标识
					afterSingletonCreation(beanName);
				}
				// 如果是一个新创建的单例，那么还需要从二级缓存、三级缓存中移除
				// 并添加到一级缓存和已注册缓存中
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * Register an Exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * @param ex the Exception to register
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 从所有缓存中移除给定的bean
	 * 从当前beanFactory的单例bean缓存中移除给定的bean
	 * 需要在同步状态下进行
	 * 如果创建失败，可以清理单例bean的饥饿注册
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		} else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 单例创建的回调方法
	 * <p>
	 * 默认实现将单例注册为当前正在创建中
	 * @param beanName beanName
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		// 将beanName状态置为创建中
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 将给定的bean添加到disposable bean列表中
	 * <p>
	 * Disposable通常和注册的单例进行关联，和beanName匹配，但是可能是其他实例
	 * 举例：一个单例的DisposableBean适配器，可能并没有实现Spring的DisposableBean的接口
	 * @param beanName beanName
	 * @param bean     bean实例
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		// 进行同步操作
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName  the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 为给定的bean生成一个依赖bean，这个依赖bean会在给定的bean销毁之前销毁
	 * @param beanName          给定beanName
	 * @param dependentBeanName 依赖beanName
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName          the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * 在移除所有的单例bean缓存
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 销毁给定的bean
	 * 是{@code destroyBean}的代理
	 * 如果找到了相关的disposable bean实例
	 * disposable bean用于销毁一个bean
	 * @param beanName 需要销毁的beanName
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// 移除已经注册的单例bean
		removeSingleton(beanName);

		// 销毁相关的DisposableBean
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		// 销毁给定的bean
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 销毁给定的bean
	 * 必须在销毁bean之前销毁依赖此bean的bean，不应抛出任何异常
	 * @param beanName 需要销毁的bean
	 * @param bean     销毁bean的调用方法bean实例
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// 首先触发依赖bean的销毁过程
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// 进行同步的原因是为了保证获得一个完整的销毁集合
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			// 遍历所有需要销毁的bean
			for (String dependentBeanName : dependencies) {
				// 销毁bean
				destroySingleton(dependentBeanName);
			}
		}

		// 现在开始销毁给定的bean
		if (bean != null) {
			try {
				// 调用给定DisposableBean的销毁方法
				bean.destroy();
			} catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// 触发销毁此bean包含的bean
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// 进行同步的原因是为了保证获得一个完整的销毁集合
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			// 遍历所有此bean包含的bean
			for (String containedBeanName : containedBeans) {
				// 销毁bean
				destroySingleton(containedBeanName);
			}
		}

		// 销毁所有依赖此bean的bean实例
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// 移除此bean创建时的依赖bean的信息
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
