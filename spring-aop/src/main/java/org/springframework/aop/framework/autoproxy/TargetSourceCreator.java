/*
 * Copyright 2002-2016 the original author or authors.
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

import org.springframework.aop.TargetSource;
import org.springframework.lang.Nullable;

/**
 * 可以创建特殊目标资源的接口，比如对于特定bean的汇集目标资源
 * 比如，它们可能基于属性选择，比如目标类的汇集属性
 * <p>
 * AbstractAutoProxyCreator可以按照顺序支持一定数量的TargetSourceCreators
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
@FunctionalInterface
public interface TargetSourceCreator {

	/**
	 * 为给定的bean创建一个特殊的目标资源
	 * @param beanClass 给定需要创建目标资源的bean
	 * @param beanName bean名称
	 * @return 一个特殊的目标资源
	 * 		   如果TargetSourceCreator对特定的bean不感兴趣，返回{@code null}
	 */
	@Nullable
	TargetSource getTargetSource(Class<?> beanClass, String beanName);

}
