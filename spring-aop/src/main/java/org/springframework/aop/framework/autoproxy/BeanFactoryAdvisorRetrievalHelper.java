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

package org.springframework.aop.framework.autoproxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for retrieving standard Spring Advisors from a BeanFactory,
 * for use with auto-proxying.
 * @author Juergen Hoeller
 * @see AbstractAdvisorAutoProxyCreator
 * @since 2.0.2
 */
public class BeanFactoryAdvisorRetrievalHelper {

	private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);

	private final ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private volatile String[] cachedAdvisorBeanNames;


	/**
	 * Create a new BeanFactoryAdvisorRetrievalHelper for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		this.beanFactory = beanFactory;
	}


	/**
	 * 获取当前BeanFactory中的所有符合条件的切面
	 * 忽略FactoryBean和正在创建的Bean
	 * 换句话说，是从Container中获取Advisor类型的Bean
	 * @return 符合条件的切面列表
	 * @see #isEligibleBean
	 */
	public List<Advisor> findAdvisorBeans() {
		// 获取所有的缓存的切面名称
		String[] advisorNames = this.cachedAdvisorBeanNames;
		if (advisorNames == null) {
			// 如果没有缓存可用的切面名称，进而去Container中进行查找
			advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
					this.beanFactory, Advisor.class, true, false);
			this.cachedAdvisorBeanNames = advisorNames;
		}
		// 如果没有的话，直接返回空的可用切面列表
		if (advisorNames.length == 0) {
			return new ArrayList<>();
		}

		List<Advisor> advisors = new ArrayList<>();
		// 遍历所有的切面名称
		for (String name : advisorNames) {
			// 切面名称符合条件
			if (isEligibleBean(name)) {
				// 不添加正在创建的切面
				if (this.beanFactory.isCurrentlyInCreation(name)) {
					if (logger.isTraceEnabled()) {
						logger.trace("Skipping currently created advisor '" + name + "'");
					}
				} else {
					try {
						// 添加到符合条件的切面名称列表中
						advisors.add(this.beanFactory.getBean(name, Advisor.class));
					} catch (BeanCreationException ex) {
						Throwable rootCause = ex.getMostSpecificCause();
						// 如果异常类型是Bean正在创建异常
						if (rootCause instanceof BeanCurrentlyInCreationException) {
							BeanCreationException bce = (BeanCreationException) rootCause;
							String bceBeanName = bce.getBeanName();
							// 需要判断Bean是否真的正在创建
							// 如果真的正在创建，可以忽略异常，进行下一个切面的遍历
							if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
								if (logger.isTraceEnabled()) {
									logger.trace("Skipping advisor '" + name +
											"' with dependency on currently created bean: " + ex.getMessage());
								}
								continue;
							}
						}
						throw ex;
					}
				}
			}
		}
		// 返回符合条件的切面名称列表符合条件的切面名称列表
		return advisors;
	}

	/**
	 * 确认切面Bean名称是否符合条件
	 * 默认实现判定符合条件，返回{@code true}
	 * @param beanName 切面Bean名称
	 * @return Bean是否符合条件
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
