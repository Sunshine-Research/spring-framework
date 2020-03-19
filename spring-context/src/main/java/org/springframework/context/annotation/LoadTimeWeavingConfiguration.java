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

package org.springframework.context.annotation;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.context.weaving.DefaultContextLoadTimeWeaver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@code @Configuration} class that registers a {@link LoadTimeWeaver} bean.
 *
 * <p>This configuration class is automatically imported when using the
 * {@link EnableLoadTimeWeaving} annotation. See {@code @EnableLoadTimeWeaving}
 * javadoc for complete usage details.
 *
 * @author Chris Beams
 * @since 3.1
 * @see LoadTimeWeavingConfigurer
 * @see ConfigurableApplicationContext#LOAD_TIME_WEAVER_BEAN_NAME
 */
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class LoadTimeWeavingConfiguration implements ImportAware, BeanClassLoaderAware {

	@Nullable
	private AnnotationAttributes enableLTW;

	@Nullable
	private LoadTimeWeavingConfigurer ltwConfigurer;

	@Nullable
	private ClassLoader beanClassLoader;


	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		this.enableLTW = AnnotationConfigUtils.attributesFor(importMetadata, EnableLoadTimeWeaving.class);
		if (this.enableLTW == null) {
			throw new IllegalArgumentException(
					"@EnableLoadTimeWeaving is not present on importing class " + importMetadata.getClassName());
		}
	}

	@Autowired(required = false)
	public void setLoadTimeWeavingConfigurer(LoadTimeWeavingConfigurer ltwConfigurer) {
		this.ltwConfigurer = ltwConfigurer;
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}


	/**
	 * 默认的bean名称是loadTimeWeaver
	 * 内部工作bean
	 * @return load-time织入
	 */
	@Bean(name = ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public LoadTimeWeaver loadTimeWeaver() {
		Assert.state(this.beanClassLoader != null, "No ClassLoader set");
		LoadTimeWeaver loadTimeWeaver = null;

		if (this.ltwConfigurer != null) {
			// 用户提供了自定义的load-time织入实例
			loadTimeWeaver = this.ltwConfigurer.getLoadTimeWeaver();
		}

		if (loadTimeWeaver == null) {
			// No custom LoadTimeWeaver provided -> fall back to the default
			// 如果没有提供默认的load-time织入实例，降级到使用默认的load-time实例
			loadTimeWeaver = new DefaultContextLoadTimeWeaver(this.beanClassLoader);
		}
		// 如果开启使用load-time织入
		if (this.enableLTW != null) {
			// 获取注解配置的织入方式，并根据不同的类型进行不同的逻辑
			AspectJWeaving aspectJWeaving = this.enableLTW.getEnum("aspectjWeaving");
			switch (aspectJWeaving) {
				case DISABLED:
					// 永不开启的情况下，什么都不做
					break;
				case AUTODETECT:
					// 如果是自动识别开启
					// 首先尝试获取"META-INF/aop.xml"下的资源
					if (this.beanClassLoader.getResource(AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE) == null) {
						// 没有对应的资源，什么都不做
						break;
					}
					// 找到对应的资源，直接开启
					AspectJWeavingEnabler.enableAspectJWeaving(loadTimeWeaver, this.beanClassLoader);
					break;
				case ENABLED:
					// 永远开启，设置开启
					AspectJWeavingEnabler.enableAspectJWeaving(loadTimeWeaver, this.beanClassLoader);
					break;
			}
		}

		return loadTimeWeaver;
	}

}
