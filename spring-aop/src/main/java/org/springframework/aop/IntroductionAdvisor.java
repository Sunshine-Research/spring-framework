/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.aop;

/**
 * 引介切面，是引介增强的适配器，通过引介切面可以很容易为现有对象添加任何接口实现
 * 它可以为目标类创建新的方法和属性
 * 和{@link PointcutAdvisor}不同，{@link IntroductionAdvisor}仅有{@code ClassFilter}，它是类级别的切面
 * <p>
 * 这个接口没有直接实现，子接口必须提供增强类型来实现引介
 * @author Rod Johnson
 * @see IntroductionInterceptor
 * @since 04.04.2003
 */
public interface IntroductionAdvisor extends Advisor, IntroductionInfo {

	/**
	 * 确认引介需要应用的目标类型
	 * 这代表切点的类部分，需要注意的是，引介没有方法匹配（MethodMatcher）
	 * @return 类型过滤
	 */
	ClassFilter getClassFilter();

	/**
	 * 判断引介增强是否可以实现切面接口
	 * 在添加一个引介切面前调用
	 * @throws IllegalArgumentException 如果切面接口不能由引介增强实现
	 */
	void validateInterfaces() throws IllegalArgumentException;

}
