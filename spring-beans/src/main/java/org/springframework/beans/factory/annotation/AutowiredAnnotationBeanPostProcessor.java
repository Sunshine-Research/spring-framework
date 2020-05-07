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

package org.springframework.beans.factory.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessor}实现
 * 自动注入注解的字段，setter方法和用户的配置方法
 * 默认通过注解检测需要注入的成员，比如Spring的{@link Autowired @Autowired}和{@link Value @Value}注解
 *
 * <p>
 * 同时也支持JSR-330的{@link javax.inject.Inject @Inject}注解
 * 如果可以，可以作为{@code @Autowired}的直接替代
 * <h3>自动注入的构造方法</h3>
 * <p>
 * 任何给定bean类的只有一个构造函数可以使用"required"设置为{@code true}的来使用此注解
 * 用于证明此构造方法作为一个Spring bean时，需要自动注入
 * 如果多个使用@Autowired(require = false)的构造方法，则均会被视为自动注入的候选
 * 入参最多的构造函数将会被选定为满足Spring容器条件的bean
 * 如果没有满足条件的候选人，则会使用主/默认构造函数
 * 如果类仅声明了一个构造函数，则使用此构造函数，即使没有使用@Autowired注解
 * 如果构造方法使用了@Autowired注解，则它不必是public的
 * <h3>自动注入的字段</h3>
 * <p>
 * 在bean的构造函数之后注入，在配置方法的调用之前，也是我们平时经常使用的方式
 * 配置的需要注入的字段无需是public的
 *
 * <h3>自动注入的方法</h3>
 * <p>
 * 配置的方法可以拥有任意多的名称和任意数量的参数
 * 每个参数都会被Spring容器自动注入为匹配的bean
 * Bean属性中的setter方法就是自动注入方法中，最有效方法的一种特殊case
 * 配置的方法也无需是public的
 *
 * <h3>注解配置和XML配置的比较</h3>
 * <p>A default {@code AutowiredAnnotationBeanPostProcessor} will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom {@code AutowiredAnnotationBeanPostProcessor} bean definition.
 * 使用"context:annotation-config"和"context:component-scan"XML标签配置时，其实也就相当于注册了{@code AutowiredAnnotationBeanPostProcessor}
 * 如果你需要自定义{@code AutowiredAnnotationBeanPostProcessor}的BeanDefinition，则需要移除或关闭默认的注解配置
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection;
 * thus the latter configuration will override the former for properties wired through
 * both approaches.
 * 注意：注解注入要比XML注入更早，因此使用两种配置方式的应用程序
 * XML配置将会覆盖注解配置
 *
 * <h3>{@literal @Lookup Methods}</h3>
 * <p>
 * 除了上面讨论的常规注入点，此post-processor也会处理Spring的{@link Lookup @Lookup}注解
 * {@link Lookup @Lookup}主要用于在运行时有容器替换的lookup方法
 * 相当于线程安全的{@code getBean(Class, args)}和{@code getBean(String, args)}
 * 查看{@link Lookup @Lookup's javadoc}获取更多信息
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @see #setAutowiredAnnotationType
 * @see Autowired
 * @see Value
 * @since 2.5
 */
public class AutowiredAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
		implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {

	protected final Log logger = LogFactory.getLog(getClass());
	/**
	 * 自动注入的注解类型
	 */
	private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

	private String requiredParameterName = "required";

	private boolean requiredParameterValue = true;

	private int order = Ordered.LOWEST_PRECEDENCE - 2;

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	private final Set<String> lookupMethodsChecked = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);

	private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * 创建用于解析{@link Autowired @Autowired}，{@link Value @Value}和{@link javax.inject.Inject @Inject}注解的Spring BeanPostProcessor
	 */
	@SuppressWarnings("unchecked")
	public AutowiredAnnotationBeanPostProcessor() {
		this.autowiredAnnotationTypes.add(Autowired.class);
		this.autowiredAnnotationTypes.add(Value.class);
		try {
			this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
					ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
			logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
		} catch (ClassNotFoundException ex) {
			// 没有找到javax.inject.Inject类，不用处理此时的异常，直接跳过
		}
	}


	/**
	 * Set the 'autowired' annotation type, to be used on constructors, fields,
	 * setter methods, and arbitrary config methods.
	 * <p>The default autowired annotation types are the Spring-provided
	 * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
	 * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate that a member is supposed
	 * to be autowired.
	 */
	public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
		Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.add(autowiredAnnotationType);
	}

	/**
	 * Set the 'autowired' annotation types, to be used on constructors, fields,
	 * setter methods, and arbitrary config methods.
	 * <p>The default autowired annotation types are the Spring-provided
	 * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
	 * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation types to indicate that a member is supposed
	 * to be autowired.
	 */
	public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
		Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
		this.autowiredAnnotationTypes.clear();
		this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
	}

	/**
	 * Set the name of an attribute of the annotation that specifies whether it is required.
	 * @see #setRequiredParameterValue(boolean)
	 */
	public void setRequiredParameterName(String requiredParameterName) {
		this.requiredParameterName = requiredParameterName;
	}

	/**
	 * Set the boolean value that marks a dependency as required.
	 * <p>For example if using 'required=true' (the default), this value should be
	 * {@code true}; but if using 'optional=false', this value should be {@code false}.
	 * @see #setRequiredParameterName(String)
	 */
	public void setRequiredParameterValue(boolean requiredParameterValue) {
		this.requiredParameterValue = requiredParameterValue;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
		metadata.checkConfigMembers(beanDefinition);
	}

	@Override
	public void resetBeanDefinition(String beanName) {
		this.lookupMethodsChecked.remove(beanName);
		this.injectionMetadataCache.remove(beanName);
	}

	@Override
	@Nullable
	public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName)
			throws BeanCreationException {

		// 首先判断当前的beanName是否为需要替换的bean
		if (!this.lookupMethodsChecked.contains(beanName)) {
			if (AnnotationUtils.isCandidateClass(beanClass, Lookup.class)) {
				try {
					Class<?> targetClass = beanClass;
					do {
						ReflectionUtils.doWithLocalMethods(targetClass, method -> {
							Lookup lookup = method.getAnnotation(Lookup.class);
							if (lookup != null) {
								Assert.state(this.beanFactory != null, "No BeanFactory available");
								LookupOverride override = new LookupOverride(method, lookup.value());
								try {
									RootBeanDefinition mbd = (RootBeanDefinition)
											this.beanFactory.getMergedBeanDefinition(beanName);
									mbd.getMethodOverrides().addOverride(override);
								} catch (NoSuchBeanDefinitionException ex) {
									throw new BeanCreationException(beanName,
											"Cannot apply @Lookup to beans without corresponding bean definition");
								}
							}
						});
						targetClass = targetClass.getSuperclass();
					}
					while (targetClass != null && targetClass != Object.class);

				} catch (IllegalStateException ex) {
					throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
				}
			}
			this.lookupMethodsChecked.add(beanName);
		}

		// Quick check on the concurrent map first, with minimal locking.
		Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
		if (candidateConstructors == null) {
			// Fully synchronized resolution now...
			synchronized (this.candidateConstructorsCache) {
				candidateConstructors = this.candidateConstructorsCache.get(beanClass);
				if (candidateConstructors == null) {
					Constructor<?>[] rawCandidates;
					try {
						rawCandidates = beanClass.getDeclaredConstructors();
					} catch (Throwable ex) {
						throw new BeanCreationException(beanName,
								"Resolution of declared constructors on bean Class [" + beanClass.getName() +
										"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
					}
					List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
					Constructor<?> requiredConstructor = null;
					Constructor<?> defaultConstructor = null;
					Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
					int nonSyntheticConstructors = 0;
					for (Constructor<?> candidate : rawCandidates) {
						if (!candidate.isSynthetic()) {
							nonSyntheticConstructors++;
						} else if (primaryConstructor != null) {
							continue;
						}
						MergedAnnotation<?> ann = findAutowiredAnnotation(candidate);
						if (ann == null) {
							Class<?> userClass = ClassUtils.getUserClass(beanClass);
							if (userClass != beanClass) {
								try {
									Constructor<?> superCtor =
											userClass.getDeclaredConstructor(candidate.getParameterTypes());
									ann = findAutowiredAnnotation(superCtor);
								} catch (NoSuchMethodException ex) {
									// Simply proceed, no equivalent superclass constructor found...
								}
							}
						}
						if (ann != null) {
							if (requiredConstructor != null) {
								throw new BeanCreationException(beanName,
										"Invalid autowire-marked constructor: " + candidate +
												". Found constructor with 'required' Autowired annotation already: " +
												requiredConstructor);
							}
							boolean required = determineRequiredStatus(ann);
							if (required) {
								if (!candidates.isEmpty()) {
									throw new BeanCreationException(beanName,
											"Invalid autowire-marked constructors: " + candidates +
													". Found constructor with 'required' Autowired annotation: " +
													candidate);
								}
								requiredConstructor = candidate;
							}
							candidates.add(candidate);
						} else if (candidate.getParameterCount() == 0) {
							defaultConstructor = candidate;
						}
					}
					if (!candidates.isEmpty()) {
						// Add default constructor to list of optional constructors, as fallback.
						if (requiredConstructor == null) {
							if (defaultConstructor != null) {
								candidates.add(defaultConstructor);
							} else if (candidates.size() == 1 && logger.isInfoEnabled()) {
								logger.info("Inconsistent constructor declaration on bean with name '" + beanName +
										"': single autowire-marked constructor flagged as optional - " +
										"this constructor is effectively required since there is no " +
										"default constructor to fall back to: " + candidates.get(0));
							}
						}
						candidateConstructors = candidates.toArray(new Constructor<?>[0]);
					} else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
						candidateConstructors = new Constructor<?>[]{rawCandidates[0]};
					} else if (nonSyntheticConstructors == 2 && primaryConstructor != null &&
							defaultConstructor != null && !primaryConstructor.equals(defaultConstructor)) {
						candidateConstructors = new Constructor<?>[]{primaryConstructor, defaultConstructor};
					} else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
						candidateConstructors = new Constructor<?>[]{primaryConstructor};
					} else {
						candidateConstructors = new Constructor<?>[0];
					}
					this.candidateConstructorsCache.put(beanClass, candidateConstructors);
				}
			}
		}
		return (candidateConstructors.length > 0 ? candidateConstructors : null);
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
		// 解析出bean中带有@Autowired，@Value和@Inject注解的属性和方法
		// 比如kafkaProduceService bean中的kafkaTemplate
		InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
		try {
			// 自动装配，实现依赖注入
			metadata.inject(bean, beanName, pvs);
		} catch (BeanCreationException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
		}
		return pvs;
	}

	@Deprecated
	@Override
	public PropertyValues postProcessPropertyValues(
			PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) {

		return postProcessProperties(pvs, bean, beanName);
	}

	/**
	 * 'Native' processing method for direct calls with an arbitrary target instance,
	 * resolving all of its fields and methods which are annotated with one of the
	 * configured 'autowired' annotation types.
	 * @param bean the target instance to process
	 * @throws BeanCreationException if autowiring failed
	 * @see #setAutowiredAnnotationTypes(Set)
	 */
	public void processInjection(Object bean) throws BeanCreationException {
		Class<?> clazz = bean.getClass();
		InjectionMetadata metadata = findAutowiringMetadata(clazz.getName(), clazz, null);
		try {
			metadata.inject(bean, null, null);
		} catch (BeanCreationException ex) {
			throw ex;
		} catch (Throwable ex) {
			throw new BeanCreationException(
					"Injection of autowired dependencies failed for class [" + clazz + "]", ex);
		}
	}

	/**
	 * 获取所有需要依赖注入的元数据
	 * @param beanName beanName
	 * @param clazz    进行依赖注入的bean class
	 * @param pvs      注入的属性值
	 * @return 给定bean的所有依赖注入属性
	 */
	private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
		// 退回到类名作为缓存键，以向后兼容自定义调用者，一般是beanName，比如kafkaProduceService
		String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());

		// 首先从缓存中获取需要注入的字段
		// 是在applyMergedBeanDefinitionPostProcessors是通过AutowiredAnnotationBeanPostProcessor时注入的
		InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
		// 如果自动注入元数据需要进行刷新
		// 需要进行刷新的条件：缓存中不存在对应的beanName，或者两次进行字段解析的bean class不相同
		if (InjectionMetadata.needsRefresh(metadata, clazz)) {
			synchronized (this.injectionMetadataCache) {
				metadata = this.injectionMetadataCache.get(cacheKey);
				if (InjectionMetadata.needsRefresh(metadata, clazz)) {
					if (metadata != null) {
						// 清除属性值
						metadata.clear(pvs);
					}
					// 重构当前bean class的自动装配元数据
					metadata = buildAutowiringMetadata(clazz);
					this.injectionMetadataCache.put(cacheKey, metadata);
				}
			}
		}
		return metadata;
	}

	/**
	 * 构建自动装配元数据
	 * @param clazz 需要构建自动装配的bean class
	 * @return 给定bean class的自动装配元数据
	 */
	private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
		if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
			return InjectionMetadata.EMPTY;
		}

		List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
		Class<?> targetClass = clazz;

		do {
			final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();
			// 使用反射获取bean class的本地字段
			ReflectionUtils.doWithLocalFields(targetClass, field -> {
				// 查找使用自动装配注解的字段
				MergedAnnotation<?> ann = findAutowiredAnnotation(field);
				if (ann != null) {
					// 不支持在static字段上使用@Autowird @Value @Inject
					// 只要使用了static关键字，那么此类将不会继续进行自动装配
					if (Modifier.isStatic(field.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static fields: " + field);
						}
						return;
					}
					// 注解属性中是否指定了required属性
					boolean required = determineRequiredStatus(ann);
					// 添加到需要自动装配的结果集中
					currElements.add(new AutowiredFieldElement(field, required));
				}
			});

			// 桥接方法：本来应该使用泛型的方法，没有使用泛型，那么JVM会为泛型方法多生成一个非泛型方法，这个非泛型的方法就是桥接方法
			// 下面对桥接方法进行操作
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				// 获取给定方法的桥接方法
				Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
				// 如果入参和返回参数完全相同，则视为桥接方法是可见的
				if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
					return;
				}
				// 查找桥接方法的所有需要自动装配的注解
				MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
				if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
					if (Modifier.isStatic(method.getModifiers())) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation is not supported on static methods: " + method);
						}
						return;
					}
					if (method.getParameterCount() == 0) {
						if (logger.isInfoEnabled()) {
							logger.info("Autowired annotation should only be used on methods with parameters: " +
									method);
						}
					}
					// 注解属性中是否指定了required属性
					boolean required = determineRequiredStatus(ann);
					PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
					// 添加到需要自动装配的结果集中
					currElements.add(new AutowiredMethodElement(method, required, pd));
				}
			});
			// 添加所有搜索出来的需要自动装配的属性
			elements.addAll(0, currElements);
			targetClass = targetClass.getSuperclass();
		}
		while (targetClass != null && targetClass != Object.class);
		// 用InjectionMetadata包装需要自动装配的属性
		return InjectionMetadata.forElements(elements, clazz);
	}

	@Nullable
	private MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
		MergedAnnotations annotations = MergedAnnotations.from(ao);
		for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
			MergedAnnotation<?> annotation = annotations.get(type);
			if (annotation.isPresent()) {
				return annotation;
			}
		}
		return null;
	}

	/**
	 * 确认如果在注解的字段或方法需要其依赖的bean
	 * <p>
	 * "required"意味着自动注入应该失败，在没有bean找到时
	 * 否则，自动注入的进程会绕过字段或方法，在没有bean找到时
	 * @param ann 自动注入注解的属性
	 * @return 注解施法指示需要依赖项
	 */
	@SuppressWarnings({"deprecation", "cast"})
	protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
		// The following (AnnotationAttributes) cast is required on JDK 9+.
		// 以下的AnnotationAttributes，注解属性转换需要JDK 9
		return determineRequiredStatus((AnnotationAttributes)
				ann.asMap(mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType())));
	}

	/**
	 * Determine if the annotated field or method requires its dependency.
	 * <p>A 'required' dependency means that autowiring should fail when no beans
	 * are found. Otherwise, the autowiring process will simply bypass the field
	 * or method when no beans are found.
	 * @param ann the Autowired annotation
	 * @return whether the annotation indicates that a dependency is required
	 * @deprecated since 5.2, in favor of {@link #determineRequiredStatus(MergedAnnotation)}
	 */
	@Deprecated
	protected boolean determineRequiredStatus(AnnotationAttributes ann) {
		return (!ann.containsKey(this.requiredParameterName) ||
				this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
	}

	/**
	 * Obtain all beans of the given type as autowire candidates.
	 * @param type the type of the bean
	 * @return the target beans, or an empty Collection if no bean of this type is found
	 * @throws BeansException if bean retrieval failed
	 */
	protected <T> Map<String, T> findAutowireCandidates(Class<T> type) throws BeansException {
		if (this.beanFactory == null) {
			throw new IllegalStateException("No BeanFactory configured - " +
					"override the getBeanOfType method or specify the 'beanFactory' property");
		}
		return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
	}

	/**
	 * Register the specified bean as dependent on the autowired beans.
	 */
	private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
		if (beanName != null) {
			for (String autowiredBeanName : autowiredBeanNames) {
				if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
					this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Autowiring by type from bean name '" + beanName +
							"' to bean named '" + autowiredBeanName + "'");
				}
			}
		}
	}

	/**
	 * 解析指定缓存的方法参数或字段值
	 * @param beanName       beanName
	 * @param cachedArgument 缓存的参数
	 */
	@Nullable
	private Object resolvedCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
		if (cachedArgument instanceof DependencyDescriptor) {
			DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
			Assert.state(this.beanFactory != null, "No BeanFactory available");
			// 通过解析依赖的方式，获取指定beanName的bean实例，会调用getBean()方法
			return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
		} else {
			return cachedArgument;
		}
	}


	/**
	 * Class representing injection information about an annotated field.
	 */
	private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object cachedFieldValue;

		public AutowiredFieldElement(Field field, boolean required) {
			super(field, null);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			// 举例，此时字段为kafkaProduceService
			Field field = (Field) this.member;
			Object value;
			// 如果已经缓存，则直接使用解析的缓存参数，解析依赖，调用getBean()，返回指定bean的实例
			if (this.cached) {
				value = resolvedCachedArgument(beanName, this.cachedFieldValue);
			} else {
				// 声明依赖描述符，用于存储依赖注入情况
				DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
				// 设置依赖注入所包含的类
				desc.setContainingClass(bean.getClass());
				Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
				Assert.state(beanFactory != null, "No BeanFactory available");
				// 获取当前beanFactory的类型转换器
				TypeConverter typeConverter = beanFactory.getTypeConverter();
				try {
					// 根据依赖注入情况，获取需要注入的bean实例
					value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
				} catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
				}
				synchronized (this) {
					// 如果是第一次进行注入，没有进行缓存，则进行结果缓存
					if (!this.cached) {
						if (value != null || this.required) {
							this.cachedFieldValue = desc;
							// 注册依赖关系，依赖关系是相互的
							registerDependentBeans(beanName, autowiredBeanNames);
							if (autowiredBeanNames.size() == 1) {
								// 获取唯一的bean实例
								String autowiredBeanName = autowiredBeanNames.iterator().next();
								if (beanFactory.containsBean(autowiredBeanName) &&
										beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
									// 构建一个方便读取的依赖注入描述符
									this.cachedFieldValue = new ShortcutDependencyDescriptor(
											desc, autowiredBeanName, field.getType());
								}
							}
						} else {
							this.cachedFieldValue = null;
						}
						this.cached = true;
					}
				}
			}
			if (value != null) {
				// 如果获取到了bean实例，那么通过反射的方式，设置此值
				ReflectionUtils.makeAccessible(field);
				field.set(bean, value);
			}
		}
	}


	/**
	 * Class representing injection information about an annotated method.
	 */
	private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {

		private final boolean required;

		private volatile boolean cached = false;

		@Nullable
		private volatile Object[] cachedMethodArguments;

		public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
			super(method, pd);
			this.required = required;
		}

		@Override
		protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
			if (checkPropertySkipping(pvs)) {
				return;
			}
			Method method = (Method) this.member;
			Object[] arguments;
			if (this.cached) {
				// Shortcut for avoiding synchronization...
				arguments = resolveCachedArguments(beanName);
			} else {
				int argumentCount = method.getParameterCount();
				arguments = new Object[argumentCount];
				DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
				Set<String> autowiredBeans = new LinkedHashSet<>(argumentCount);
				Assert.state(beanFactory != null, "No BeanFactory available");
				TypeConverter typeConverter = beanFactory.getTypeConverter();
				for (int i = 0; i < arguments.length; i++) {
					MethodParameter methodParam = new MethodParameter(method, i);
					DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
					currDesc.setContainingClass(bean.getClass());
					descriptors[i] = currDesc;
					try {
						Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
						if (arg == null && !this.required) {
							arguments = null;
							break;
						}
						arguments[i] = arg;
					} catch (BeansException ex) {
						throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
					}
				}
				synchronized (this) {
					if (!this.cached) {
						if (arguments != null) {
							DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, arguments.length);
							registerDependentBeans(beanName, autowiredBeans);
							if (autowiredBeans.size() == argumentCount) {
								Iterator<String> it = autowiredBeans.iterator();
								Class<?>[] paramTypes = method.getParameterTypes();
								for (int i = 0; i < paramTypes.length; i++) {
									String autowiredBeanName = it.next();
									if (beanFactory.containsBean(autowiredBeanName) &&
											beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
										cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
												descriptors[i], autowiredBeanName, paramTypes[i]);
									}
								}
							}
							this.cachedMethodArguments = cachedMethodArguments;
						} else {
							this.cachedMethodArguments = null;
						}
						this.cached = true;
					}
				}
			}
			if (arguments != null) {
				try {
					ReflectionUtils.makeAccessible(method);
					method.invoke(bean, arguments);
				} catch (InvocationTargetException ex) {
					throw ex.getTargetException();
				}
			}
		}

		@Nullable
		private Object[] resolveCachedArguments(@Nullable String beanName) {
			Object[] cachedMethodArguments = this.cachedMethodArguments;
			if (cachedMethodArguments == null) {
				return null;
			}
			Object[] arguments = new Object[cachedMethodArguments.length];
			for (int i = 0; i < arguments.length; i++) {
				arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i]);
			}
			return arguments;
		}
	}


	/**
	 * DependencyDescriptor variant with a pre-resolved target bean name.
	 */
	@SuppressWarnings("serial")
	private static class ShortcutDependencyDescriptor extends DependencyDescriptor {

		private final String shortcut;

		private final Class<?> requiredType;

		public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut, Class<?> requiredType) {
			super(original);
			this.shortcut = shortcut;
			this.requiredType = requiredType;
		}

		@Override
		public Object resolveShortcut(BeanFactory beanFactory) {
			return beanFactory.getBean(this.shortcut, this.requiredType);
		}
	}

}
