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

package org.springframework.aop.framework.autoproxy;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor}实现
 * 包装了每个合格的AOP代理的bean，在调用bean自己之前，代理到特定的interceptors上
 * <p>
 * 这个类用于区别共同的interceptors，共享所有由它创建的代理，以及特定的interceptors：每个bean的唯一实例
 * 将不会有任何共同的interceptors，如果有，它们是使用interceptorNames属性设置的
 * 与{@link org.springframework.aop.framework.ProxyFactoryBean}一样，在当前工厂的使用interceptors names
 * 而不是bean的引用，来允许正确的处理多例的Advisors和interceptors：比如，支持稳定的mixins
 * 任何增强类型都支持{@link #setInterceptorNames "interceptorNames"}元素
 * <p>
 * 如果需要将一大波bean包装为相近的代理，这种自动代理特别的好用。
 * 比如代理到相同的interceptors，对于X个目标bean的X个重复代理定义，你可以通过注册一个简单的这样的post processor的bean工厂来实现相同的效果
 * <p>
 * 子类也可以实现任何策略来决定bean是否需要代理，比如通过类型，名称，Definition的详细信息等
 * 也可以返回额外的，仅用于特定的bean实例的interceptors，一个简单的具体实现是{@link BeanNameAutoProxyCreator}，通给给定的名称来识别要代理的bean
 * <p>
 * 任何数量的{@link TargetSourceCreator}实现可以用于创建自定义的目标资源，比如：
 * 对于池化类型对象，自动注入可以在没有增强的情况下发生，只要一个TargetSourceCreator指定了一个自定义的{@link org.springframework.aop.TargetSource}
 * 如果没有TargetSourceCreators集合，或者没有匹配的，会使用{@link org.springframework.aop.target.SingletonTargetSource}用作默认的资源来包装目标bean实例
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 * @since 13.10.2003
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
		implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

	/**
	 * 返回不进行代理的值
	 * 提供给子类的便捷常量
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Nullable
	protected static final Object[] DO_NOT_PROXY = null;

	/**
	 * 返回"没有其他额外interceptors的代理，只是普通的代理"
	 * 提供给子类的便捷常量
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];

	protected final Log logger = LogFactory.getLog(getClass());
	/**
	 * 目标资源bean
	 */
	private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));
	/**
	 * 提前代理引用
	 */
	private final Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);
	/**
	 * 代理类型
	 */
	private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);
	/**
	 * 增强bean
	 */
	private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);
	/**
	 * 全局的切面适配器注册器
	 */
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();
	/**
	 * 标示proxy是否需要冷冻
	 * 覆盖超类来避免配置更早的进入冷冻状态
	 * 默认false
	 */
	private boolean freezeProxy = false;
	/**
	 * 通用interceptors
	 * 默认没有共同的interceptors（意思默认是空数组）
	 */
	private String[] interceptorNames = new String[0];
	/**
	 * 是否首先执行共同的interceptors
	 * 默认首先执行
	 */
	private boolean applyCommonInterceptorsFirst = true;
	/**
	 * 目标资源创建器集合
	 */
	@Nullable
	private TargetSourceCreator[] customTargetSourceCreators;
	/**
	 * bean工厂
	 */
	@Nullable
	private BeanFactory beanFactory;

	/**
	 * 设置代理是否需要冷冻，防止在通知创建之后就添加到代理中
	 * 重写超类方法来避免代理创建之前，代理配置已经是冷冻的了
	 */
	@Override
	public void setFrozen(boolean frozen) {
		this.freezeProxy = frozen;
	}

	@Override
	public boolean isFrozen() {
		return this.freezeProxy;
	}

	/**
	 * 指定使用的{@link AdvisorAdapterRegistry}
	 * 默认是全局的{@link AdvisorAdapterRegistry}
	 * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
	 */
	public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
		this.advisorAdapterRegistry = advisorAdapterRegistry;
	}

	/**
	 * 设置自定义的{@code TargetSourceCreators}，并以该顺序进行应用
	 * 如果列表为空，或者返回null，则将为每个bean创建一个{@link SingletonTargetSource}
	 * <p>
	 * 需要注意的是，即使在没有找到增强或增强，TargetSourceCreators也会创建
	 * 如果{@code TargetSourceCreator}为特定的bean返回了{@link TargetSource}，任何情况都会代理这个bean
	 * 仅有在当前post-processor在{@link BeanFactory}中，并且{@link BeanFactoryAware}触发的情况下，才可以调用此{@code TargetSourceCreators}
	 * @param targetSourceCreators {@code TargetSourceCreators}列表
	 *                             顺序很重要：将使用第一个匹配的{@code TargetSourceCreator}返回的{@code TargetSource}（即，第一个返回非null的）
	 */
	public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
		this.customTargetSourceCreators = targetSourceCreators;
	}

	/**
	 * 设置共同的interceptors，必须是当前工厂的bean名称
	 * 可以是Spring支持的通知或者切面
	 * 如果没有设置这个属性，意味着没有共同的interceptors
	 * 如果仅需要"特定的"interceptors，这完全是有效的，
	 */
	public void setInterceptorNames(String... interceptorNames) {
		this.interceptorNames = interceptorNames;
	}

	/**
	 * 设置通用interceptors是否需要在bean指定的interceptors之前执行
	 * 默认是true，否则，bean指定的interceptors将会先执行
	 */
	public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
		this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * 返回已拥有的{@link BeanFactory}
	 * 在post-processor没有依赖bean工厂的时候，可能是null
	 */
	@Nullable
	protected BeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	@Nullable
	public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
		if (this.proxyTypes.isEmpty()) {
			return null;
		}
		Object cacheKey = getCacheKey(beanClass, beanName);
		return this.proxyTypes.get(cacheKey);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
		return null;
	}

	@Override
	public Object getEarlyBeanReference(Object bean, String beanName) {
		Object cacheKey = getCacheKey(bean.getClass(), beanName);
		this.earlyProxyReferences.put(cacheKey, bean);
		return wrapIfNecessary(bean, beanName, cacheKey);
	}

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
		// 实例化之前的post-process
		// 获取给定bean的缓存key
		Object cacheKey = getCacheKey(beanClass, beanName);

		if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
			if (this.advisedBeans.containsKey(cacheKey)) {
				return null;
			}
			if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) {
				this.advisedBeans.put(cacheKey, Boolean.FALSE);
				return null;
			}
		}

		// 获取自定义的目标资源
		// 如果有自定义的目标资源，则在此处创建代理
		// 抑制目标bean的不必要的默认实例化
		// 目标资源会以自定义的方式处理目标实例
		TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
		if (targetSource != null) {
			if (StringUtils.hasLength(beanName)) {
				this.targetSourcedBeans.add(beanName);
			}
			// 获取给定bean的增强和切面
			Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
			// 为给定bean使用增强创建代理
			Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) {
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		return pvs;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	/**
	 * 如果bean确认是需要织入的类，则为这个类创建一个带有配置好的interceptors的代理
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		if (bean != null) {
			// 获取当前Bean的对象
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			//
			if (this.earlyProxyReferences.remove(cacheKey) != bean) {
				// 包装为代理对象
				// 在AOP的基础类，或者原始类型Bean对象，以及没有相应interceptors的情况下不会进行包装
				// 其他情况会创建一个代理
				return wrapIfNecessary(bean, beanName, cacheKey);
			}
		}
		// 此时已经包装为代理对象，直接返回Bean的Proxy
		return bean;
	}


	/**
	 * 为给定的bean类型和bean名称创建缓存
	 * <p>
	 * 需要注意的是，从4.2.3版本起，不会返回一个把类型和名称联系起来的String，但是会返回一个更高效的可用缓存key：
	 * 一个简单的bean名称，如果是{@code FactoryBean}，以{@link BeanFactory#FACTORY_BEAN_PREFIX}开头
	 * * 或者没有指定bean名称，返回给定bean的类型
	 * @param beanClass 给定bean的类型
	 * @param beanName  给定bean的名称
	 * @return 给定bean类型、名称的缓存key
	 */
	protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
		if (StringUtils.hasLength(beanName)) {
			// FactoryBean类型："&beanName"
			return (FactoryBean.class.isAssignableFrom(beanClass) ?
					BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
		} else {
			return beanClass;
		}
	}

	/**
	 * 在需要的情况下，包装Bean为Proxy
	 * @param bean     被包装的Bean实例
	 * @param beanName Bean名称
	 * @param cacheKey 访问缓存的Key
	 * @return 包装了Bean实例的Proxy，或者就是原始的Bean实例
	 */
	protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
		// 判断targetSource缓存中是否有当前bean
		// 如果有无需包装，直接返回
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}
		// 如果缓存中中是基础类型的bean，或者是原始类型的Bean，则返回Bean实例本身
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}
		// 如果是诸如Advice、Advisor、PointCut和AopInfrastructureBean的子类
		// 或者是原始类型的Bean
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			// 针对这两大类的Bean统一缓存到advisedBean缓存中
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}

		// 获取当前Bean的interceptors，即bean的Advisors
		Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
		// 如果针对该Bean存在对应的interceptors
		if (specificInterceptors != DO_NOT_PROXY) {
			// 先对这个Bean对象的代理标志进行缓存
			this.advisedBeans.put(cacheKey, Boolean.TRUE);
			// 创建代理
			Object proxy = createProxy(
					bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
			// 缓存代理类型，并返回当前代理
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}
		// 如果当前Bean对象没有对应的interceptors，则在切面缓存的Bean中设置此Bean对象没有代理，并返回Bean实例
		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}

	/**
	 * 给定的bean是否代表了不需要代理的基础类
	 * 默认的实现需要考虑将增强，切面和AopInfrastructureBean类考虑为基础结构的类
	 * @param beanClass bean类型
	 * @return 是否代表了不需要代理的基础类
	 * @see org.aopalliance.aop.Advice
	 * @see org.springframework.aop.Advisor
	 * @see org.springframework.aop.framework.AopInfrastructureBean
	 * @see #shouldSkip
	 */
	protected boolean isInfrastructureClass(Class<?> beanClass) {
		// 是否继承了Advice、PointCut、Advisor、AopInfrastructureBean
		boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
				Pointcut.class.isAssignableFrom(beanClass) ||
				Advisor.class.isAssignableFrom(beanClass) ||
				AopInfrastructureBean.class.isAssignableFrom(beanClass);
		if (retVal && logger.isTraceEnabled()) {
			logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
		}
		return retVal;
	}

	/**
	 * Subclasses should override this method to return {@code true} if the
	 * given bean should not be considered for auto-proxying by this post-processor.
	 * <p>Sometimes we need to be able to avoid this happening, e.g. if it will lead to
	 * a circular reference or if the existing target instance needs to be preserved.
	 * This implementation returns {@code false} unless the bean name indicates an
	 * "original instance" according to {@code AutowireCapableBeanFactory} conventions.
	 * @param beanClass the class of the bean
	 * @param beanName  the name of the bean
	 * @return whether to skip the given bean
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
	 */
	protected boolean shouldSkip(Class<?> beanClass, String beanName) {
		// 判断Bean是否是原始类型的Bean（以".ORIGINAL"）结尾的
		return AutoProxyUtils.isOriginalInstance(beanName, beanClass);
	}

	/**
	 * 为bean实例创建目标资源，使用设置的任何TargetSourceCreators
	 * 如果没有可用的自定义的目标资源，返回{@code null}
	 * 这个实现用到了{@link #customTargetSourceCreators}属性
	 * 子类可以重写这个方法来使用不同的机制
	 * @param beanClass 创建目标资源的bean类型
	 * @param beanName  bean名称
	 * @return bean的目标资源
	 * @see #setCustomTargetSourceCreators
	 */
	@Nullable
	protected TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
		// 不能为直接注册的单例bean创建设想的目标资源
		// 三个条件缺一不可
		if (this.customTargetSourceCreators != null &&
				this.beanFactory != null && this.beanFactory.containsBean(beanName)) {
			// 遍历所有的自定义的目标资源创建器
			for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
				// 根据bean的类型、名称、目标资源创建器，获取对应的资源
				TargetSource ts = tsc.getTargetSource(beanClass, beanName);
				if (ts != null) {
					if (logger.isTraceEnabled()) {
						logger.trace("TargetSourceCreator [" + tsc +
								"] found custom TargetSource for bean with name '" + beanName + "'");
					}
					// 返回找到的目标资源
					return ts;
				}
			}
		}

		// 没有找到自定义的目标资源
		return null;
	}

	/**
	 * 为给定的Bean创建AOP代理
	 * @param beanClass            Bean类型
	 * @param beanName             Bean名称
	 * @param specificInterceptors Bean实例关联的interceptors，可以为空，但不是null
	 * @param targetSource         代理的TargetSource，已经预先配置可以访问Bean实例了
	 * @return Bean实例的AOP代理
	 * @see #buildAdvisors
	 */
	protected Object createProxy(Class<?> beanClass, @Nullable String beanName,
								 @Nullable Object[] specificInterceptors, TargetSource targetSource) {
		// 如果BeanFactory类型是ConfigurableListableBeanFactory，也就是需要对内部
		if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
			AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
		}
		// 创建代理工厂来生成代理
		ProxyFactory proxyFactory = new ProxyFactory();
		// 复制当前类的一些属性，因为AbstractAutoProxyCreator是ProxyConfig类型
		proxyFactory.copyFrom(this);
		//
		if (!proxyFactory.isProxyTargetClass()) {
			if (shouldProxyTargetClass(beanClass, beanName)) {
				proxyFactory.setProxyTargetClass(true);
			} else {
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
		}
		// 构建切面，包括特定interceptors和通用interceptors
		Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
		// 设置切面、TargetSource，并进行客制化
		proxyFactory.addAdvisors(advisors);
		proxyFactory.setTargetSource(targetSource);
		customizeProxyFactory(proxyFactory);
		// 设置代理是否需要冷冻
		proxyFactory.setFrozen(this.freezeProxy);
		if (advisorsPreFiltered()) {
			proxyFactory.setPreFiltered(true);
		}
		// 根据代理处理器配置的ClassLoader构建新的代理
		return proxyFactory.getProxy(getProxyClassLoader());
	}

	/**
	 * 确认给定的Bean是否需要用目标类而不是目标接口来进行代理
	 * 检查相关BeanDefinition的{@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE "preserveTargetClass" attribute}属性
	 * @param beanClass Bean类型
	 * @param beanName  Bean名称
	 * @return 给定Bean是否需要使用目标类类型进行代理
	 * @see AutoProxyUtils#shouldProxyTargetClass
	 */
	protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
		return (this.beanFactory instanceof ConfigurableListableBeanFactory &&
				AutoProxyUtils.shouldProxyTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName));
	}

	/**
	 * 判断子类返回的切面是否已预先过滤以匹配Bean的目标类，在为AOP调用构建切面链时允许跳过ClassFilter的检查
	 * 默认是{@code false}，子类可以根据情况进行重写，可以始终返回经过预过滤的切面
	 * @return 切面是否是预过滤的
	 * @see #getAdvicesAndAdvisorsForBean
	 * @see org.springframework.aop.framework.Advised#setPreFiltered
	 */
	protected boolean advisorsPreFiltered() {
		return false;
	}

	/**
	 * 确认包含特定interceptors在内的，以及通用interceptors，所有给定Bean的增强
	 * @param beanName             Bean名称
	 * @param specificInterceptors Bean实例关联的interceptors，可以为空，但不是null
	 * @return 给定Bean的所有增强
	 */
	protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors) {
		// 将所有通用的interceptors转换为切面
		Advisor[] commonInterceptors = resolveInterceptorNames();

		List<Object> allInterceptors = new ArrayList<>();
		if (specificInterceptors != null) {
			allInterceptors.addAll(Arrays.asList(specificInterceptors));
			if (commonInterceptors.length > 0) {
				if (this.applyCommonInterceptorsFirst) {
					// 在需要先应用通用interceptors时，将通用切面放在前面
					allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
				} else {
					allInterceptors.addAll(Arrays.asList(commonInterceptors));
				}
			}
		}
		if (logger.isTraceEnabled()) {
			int nrOfCommonInterceptors = commonInterceptors.length;
			int nrOfSpecificInterceptors = (specificInterceptors != null ? specificInterceptors.length : 0);
			logger.trace("Creating implicit proxy for bean '" + beanName + "' with " + nrOfCommonInterceptors +
					" common interceptors and " + nrOfSpecificInterceptors + " specific interceptors");
		}
		// 使用数组进行存储
		Advisor[] advisors = new Advisor[allInterceptors.size()];
		for (int i = 0; i < allInterceptors.size(); i++) {
			// 遍历所有的interceptors，使用AdvisorAdapterRegistry将其包装为切面
			advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
		}
		return advisors;
	}

	/**
	 * 将通用的interceptor名称解析为切面对象
	 * @see #setInterceptorNames
	 */
	private Advisor[] resolveInterceptorNames() {
		BeanFactory bf = this.beanFactory;
		//
		ConfigurableBeanFactory cbf = (bf instanceof ConfigurableBeanFactory ? (ConfigurableBeanFactory) bf : null);
		List<Advisor> advisors = new ArrayList<>();
		// 遍历所有的通用interceptors
		for (String beanName : this.interceptorNames) {
			//
			if (cbf == null || !cbf.isCurrentlyInCreation(beanName)) {
				Assert.state(bf != null, "BeanFactory required for resolving interceptor names");
				// 从上下文中获取bean对象
				Object next = bf.getBean(beanName);
				// 通过切面注册器包装后，放入到切面缓存中
				advisors.add(this.advisorAdapterRegistry.wrap(next));
			}
		}
		//
		return advisors.toArray(new Advisor[0]);
	}

	/**
	 * 子类可以选择性实现这个方法
	 * 比如：改变接口的暴露方式
	 * 默认的实现为空
	 * @param proxyFactory 代理工厂，已经配置了目标资源和接口，将用于此方法返回后立即创建代理
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}


	/**
	 * 对给定Bean筛选出指定的增强增强信息
	 * 返回给定的bean是否需要代理，以及应用的额外的增强（比如AOP联盟的interceptors）或者切面
	 * @param beanClass          需要进行增强的bean类型
	 * @param beanName           bean名称
	 * @param customTargetSource 自定义目标资源，可能会被忽略，如果没有自定义的目标资源，返回{@code null}
	 * @return 特定bean额外interceptors数组，如果没有额外的interceptors，仅有通用的interceptors，则是一个空的数据
	 * 如果没有代理，返回{@code null}，甚至不会返回通用的interceptors
	 * 请看常量DO_NOT_PROXY和PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
	 * @throws BeansException 假如出现异常，抛出异常
	 * @see #DO_NOT_PROXY
	 * @see #PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
	 */
	@Nullable
	protected abstract Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
															 @Nullable TargetSource customTargetSource) throws BeansException;

}
