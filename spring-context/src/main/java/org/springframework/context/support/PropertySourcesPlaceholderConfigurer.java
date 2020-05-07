/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringValueResolver;

import java.io.IOException;
import java.util.Properties;

/**
 * {@link PlaceholderConfigurerSupport}特别用于解决${...}类型赋值的处理器，使用了{@link Environment}和{@link PropertySources}
 * <p>
 * 此类用于设计来代替{@code PropertyPlaceholderConfigurer}
 * 默认支持{@code property-placeholder}元素，在spring-context 3.1或更高的版本之上
 * 但是，spring-context＜3.0默认使用{@code PropertyPlaceholderConfigurer}来确保向后兼容
 * <p>
 * 任何本地的属性，比如：{@link #setProperties}，{@link #setLocations}将会被添加为{@code PropertySource}
 * 本地属性的优先级基于{@link #setLocalOverride localOverride}属性值
 * 默认是{@code false}意味着本地属性值最后才会被搜索，在所有Environment属性值之后
 * @author Chris Beams
 * @author Juergen Hoeller
 * @see org.springframework.core.env.ConfigurableEnvironment
 * @see org.springframework.beans.factory.config.PlaceholderConfigurerSupport
 * @see org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
 * @since 3.1
 */
public class PropertySourcesPlaceholderConfigurer extends PlaceholderConfigurerSupport implements EnvironmentAware {

	/**
	 * {@value} is the name given to the {@link PropertySource} for the set of
	 * {@linkplain #mergeProperties() merged properties} supplied to this configurer.
	 */
	public static final String LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME = "localProperties";

	/**
	 * {@value} is the name given to the {@link PropertySource} that wraps the
	 * {@linkplain #setEnvironment environment} supplied to this configurer.
	 */
	public static final String ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME = "environmentProperties";


	@Nullable
	private MutablePropertySources propertySources;

	@Nullable
	private PropertySources appliedPropertySources;

	@Nullable
	private Environment environment;


	/**
	 * Customize the set of {@link PropertySources} to be used by this configurer.
	 * <p>Setting this property indicates that environment property sources and
	 * local properties should be ignored.
	 * @see #postProcessBeanFactory
	 */
	public void setPropertySources(PropertySources propertySources) {
		this.propertySources = new MutablePropertySources(propertySources);
	}

	/**
	 * {@code PropertySources} from the given {@link Environment}
	 * will be searched when replacing ${...} placeholders.
	 * @see #setPropertySources
	 * @see #postProcessBeanFactory
	 */
	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}


	/**
	 * Processing occurs by replacing ${...} placeholders in bean definitions by resolving each
	 * against this configurer's set of {@link PropertySources}, which includes:
	 * 处理所有BeanDefinition中的${...}占位符，通过针对此配置程序的{@link PropertySources}集解析
	 * <ul>
	 * <li>
	 * 所有{@linkplain org.springframework.core.env.ConfigurableEnvironment#getPropertySources environment property sources}
	 * 如果存在{@code Environment}
	 * <li>
	 * {@linkplain #mergeProperties merged local properties}合并本地属性
	 * 如果{@linkplain #setLocation}和{@linkplain #setLocations}和{@linkplain #setPropertiesArray}和{@linkplain #setProperties}存在
	 * <li>调用{@link #setPropertySources}设置的属性
	 * </ul>
	 * <p>
	 * 此方法用于给开发者更细粒度的属性控制，一旦设置，处理器不会假设添加起额外的属性源
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (this.propertySources == null) {
			// 创建可变的属性集
			this.propertySources = new MutablePropertySources();
			if (this.environment != null) {
				// 首先添加Environment
				this.propertySources.addLast(
						new PropertySource<Environment>(ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME, this.environment) {
							@Override
							@Nullable
							public String getProperty(String key) {
								return this.source.getProperty(key);
							}
						}
				);
			}
			try {
				// 其次添加合并的属性，这里会处理@ImportSource属性值
				PropertySource<?> localPropertySource =
						new PropertiesPropertySource(LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME, mergeProperties());
				// 如果允许本地覆写，则添加到属性的最前面，即为优先级最高
				if (this.localOverride) {
					this.propertySources.addFirst(localPropertySource);
				}
				// 不允许覆写，则放在最后面，优先级最低
				else {
					this.propertySources.addLast(localPropertySource);
				}
			} catch (IOException ex) {
				throw new BeanInitializationException("Could not load properties", ex);
			}
		}
		// 创建一个新的属性占位符处理器，一般的属性值注入包括environment和localProperties
		// 接下来就是使用装配好的所有属性注入值，替换占位符
		processProperties(beanFactory, new PropertySourcesPropertyResolver(this.propertySources));
		this.appliedPropertySources = this.propertySources;
	}

	/**
	 * 访问所有已注册的BeanDefinition，解决其中的每一个${...}占位符
	 * @param beanFactoryToProcess 遍历BeanDefinition所属的BeanFactory
	 * @param propertyResolver 占位符解决器
	 */
	protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
			final ConfigurablePropertyResolver propertyResolver) throws BeansException {
		// 设置前缀${
		propertyResolver.setPlaceholderPrefix(this.placeholderPrefix);
		// 设置后缀}
		propertyResolver.setPlaceholderSuffix(this.placeholderSuffix);
		// 设置分割符:，:代表后半部分为默认值
		propertyResolver.setValueSeparator(this.valueSeparator);
		// 构建字符串值处理器
		StringValueResolver valueResolver = strVal -> {
			String resolved = (this.ignoreUnresolvablePlaceholders ?
					propertyResolver.resolvePlaceholders(strVal) :
					propertyResolver.resolveRequiredPlaceholders(strVal));
			// 是否需要去掉字符串中的空格
			if (this.trimValues) {
				resolved = resolved.trim();
			}
			return (resolved.equals(this.nullValue) ? null : resolved);
		};
		// 使用字符串值处理器处理当前BeanFactory中的所有占位符
		doProcessProperties(beanFactoryToProcess, valueResolver);
	}

	/**
	 * Implemented for compatibility with
	 * {@link org.springframework.beans.factory.config.PlaceholderConfigurerSupport}.
	 * @deprecated in favor of
	 * {@link #processProperties(ConfigurableListableBeanFactory, ConfigurablePropertyResolver)}
	 * @throws UnsupportedOperationException in this implementation
	 */
	@Override
	@Deprecated
	protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) {
		throw new UnsupportedOperationException(
				"Call processProperties(ConfigurableListableBeanFactory, ConfigurablePropertyResolver) instead");
	}

	/**
	 * Return the property sources that were actually applied during
	 * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory) post-processing}.
	 * @return the property sources that were applied
	 * @throws IllegalStateException if the property sources have not yet been applied
	 * @since 4.0
	 */
	public PropertySources getAppliedPropertySources() throws IllegalStateException {
		Assert.state(this.appliedPropertySources != null, "PropertySources have not yet been applied");
		return this.appliedPropertySources;
	}

}
