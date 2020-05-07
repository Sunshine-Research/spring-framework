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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * BeanDefinition描述了一个Bean实例，Bean实例拥有属性，构造参数，以及具体实现提供的更多信息
 * <p>
 * 这仅仅是一个最小的接口，主要的意图是允许{@link BeanFactoryPostProcessor}内省和修改属性值，以及bean的元数据
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see ConfigurableListableBeanFactory#getBeanDefinition
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 * @since 19.03.2004
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	/**
	 * Scope标识符——标准单例——"singleton"
	 * 需要注意的是，扩展BeanFactory可以支持更多的scope类型
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON
	 */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	/**
	 * Scope标识符——标准多例——"prototype"
	 * 需要注意的是，扩展BeanFactory可以支持更多的scope类型
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE
	 */
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;


	/**
	 * BeanDefinition的角色信息
	 * {@code BeanDefinition}是应用程序的主要部分
	 * 通常和用户自定义的bean关联
	 */
	int ROLE_APPLICATION = 0;

	/**
	 * BeanDefinition的角色信息
	 * {@code BeanDefinition}是一些大型配置的支撑部分
	 * 通常是外层的{@link org.springframework.beans.factory.parsing.ComponentDefinition}
	 * {@code SUPPORT}类型的bean会被认为足够重要，以便更仔细的查看特定{@link org.springframework.beans.factory.parsing.ComponentDefinition}对象时意识到
	 * 但是在查看应用程序的整体配置是却没有
	 */
	int ROLE_SUPPORT = 1;

	/**
	 * {@code BeanDefinition}提供一个完整的后台角色，与最终用户无关
	 * 在注册内部工作bean时使用
	 */
	int ROLE_INFRASTRUCTURE = 2;


	// 可修改的属性

	/**
	 * 返回当前BeanDefinition的父Definition的名称
	 */
	@Nullable
	String getParentName();

	/**
	 * 设置当前BeanDefinition的父Definition
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * BeanDefinition的类名称
	 * 需要注意的是，如果是子级定义从起父类重写/继承类名，可以不是在运行时真是的类名称
	 * 当然，可以仅仅是调用工厂方法的类，或者可以是空的，由于工厂Bean引用是由调用方法得到的
	 * 因此，不用考虑在运行时将其视为确定的bean类型，但也不是仅仅在单个bean定义级别时将其用于解析目的
	 * @see #getParentName()
	 * @see #getFactoryBeanName()
	 * @see #getFactoryMethodName()
	 */
	@Nullable
	String getBeanClassName();

	/**
	 * 设置BeanDefinition的Bean类名称
	 * 在BeanFactory的post-process过程中，类名称可以随时修改
	 * 通常用于将原始类名称为其解析的变体
	 * @see #setParentName
	 * @see #setFactoryBeanName
	 * @see #setFactoryMethodName
	 */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	 * 返回Bean当前scope
	 */
	@Nullable
	String getScope();

	/**
	 * 重写Bean的scope，指定新的scope名称
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	void setScope(@Nullable String scope);

	/**
	 * Bean是否是懒加载的
	 * 仅适用于单例Bean
	 */
	boolean isLazyInit();

	/**
	 * 设置Bean是否需要懒加载
	 * 如果返回{@code false}，bean会在Spring容器启动时由BeanFactory进行初始化，
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * 获取当前Bean依赖的Bean数组
	 */
	@Nullable
	String[] getDependsOn();

	/**
	 * Bean需要依赖的哪个Bean的实例化
	 * BeanFactory会保证给定的Bean先进行实例化
	 */
	void setDependsOn(@Nullable String... dependsOn);

	/**
	 * Set whether this bean is a candidate for getting autowired into some other bean.
	 * <p>Note that this flag is designed to only affect type-based autowiring.
	 * It does not affect explicit references by name, which will get resolved even
	 * if the specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 *
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * Return whether this bean is a candidate for getting autowired into some other bean.
	 */
	boolean isAutowireCandidate();

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>If this value is {@code true} for exactly one bean among multiple
	 * matching candidates, it will serve as a tie-breaker.
	 */
	void setPrimary(boolean primary);

	/**
	 * Return whether this bean is a primary autowire candidate.
	 */
	boolean isPrimary();

	/**
	 * Specify the factory bean to use, if any.
	 * This the name of the bean to call the specified factory method on.
	 * @see #setFactoryMethodName
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * Return the factory bean name, if any.
	 */
	@Nullable
	String getFactoryBeanName();

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified.
	 * The method will be invoked on the specified factory bean, if any,
	 * or otherwise as a static method on the local bean class.
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * Return a factory method, if any.
	 */
	@Nullable
	String getFactoryMethodName();

	/**
	 * Return the constructor argument values for this bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the ConstructorArgumentValues object (never {@code null})
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	 * Return if there are constructor argument values defined for this bean.
	 * @since 5.0.2
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * Return the property values to be applied to a new instance of the bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the MutablePropertyValues object (never {@code null})
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * Return if there are property values values defined for this bean.
	 * @since 5.0.2
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	/**
	 * Set the name of the initializer method.
	 * @since 5.1
	 */
	void setInitMethodName(@Nullable String initMethodName);

	/**
	 * Return the name of the initializer method.
	 * @since 5.1
	 */
	@Nullable
	String getInitMethodName();

	/**
	 * Set the name of the destroy method.
	 * @since 5.1
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);

	/**
	 * Return the name of the destroy method.
	 * @since 5.1
	 */
	@Nullable
	String getDestroyMethodName();

	/**
	 * Set the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools with an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * @since 5.1
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	void setRole(int role);

	/**
	 * Get the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools with an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * 获取{@code BeanDefinition}的角色信息
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	int getRole();

	/**
	 * Set a human-readable description of this bean definition.
	 * @since 5.1
	 */
	void setDescription(@Nullable String description);

	/**
	 * Return a human-readable description of this bean definition.
	 */
	@Nullable
	String getDescription();


	// Read-only attributes

	/**
	 * Return a resolvable type for this bean definition,
	 * based on the bean class or other specific metadata.
	 * <p>This is typically fully resolved on a runtime-merged bean definition
	 * but not necessarily on a configuration-time definition instance.
	 * @return the resolvable type (potentially {@link ResolvableType#NONE})
	 * @since 5.2
	 * @see ConfigurableBeanFactory#getMergedBeanDefinition
	 */
	ResolvableType getResolvableType();

	/**
	 * Return whether this a <b>Singleton</b>, with a single, shared instance
	 * returned on all calls.
	 * @see #SCOPE_SINGLETON
	 */
	boolean isSingleton();

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance
	 * returned for each call.
	 * @since 3.0
	 * @see #SCOPE_PROTOTYPE
	 */
	boolean isPrototype();

	/**
	 * Return whether this bean is "abstract", that is, not meant to be instantiated.
	 */
	boolean isAbstract();

	/**
	 * Return a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 */
	@Nullable
	String getResourceDescription();

	/**
	 * Return the originating BeanDefinition, or {@code null} if none.
	 * Allows for retrieving the decorated bean definition, if any.
	 * <p>Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 */
	@Nullable
	BeanDefinition getOriginatingBeanDefinition();

}
