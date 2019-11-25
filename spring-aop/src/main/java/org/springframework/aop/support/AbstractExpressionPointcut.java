/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.aop.support;

import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 切点表达式
 * 也就是我们使用的@PointCut(expression="")
 * 提供了位置和表达式属性
 * @author Rod Johnson
 * @author Rob Harrop
 * @see #setLocation
 * @see #setExpression
 * @since 2.0
 */
@SuppressWarnings("serial")
public abstract class AbstractExpressionPointcut implements ExpressionPointcut, Serializable {

	/**
	 * 切点关联的位置
	 */
	@Nullable
	private String location;

	/**
	 * 切点适配的表达式
	 */
	@Nullable
	private String expression;

	/**
	 * 提供切点表达式的位置信息
	 * 这在debug过程中非常好用
	 * @return 可读的位置信息，不可用的情况下返回null
	 */
	@Nullable
	public String getLocation() {
		return this.location;
	}

	/**
	 * 为debug提供位置信息
	 */
	public void setLocation(@Nullable String location) {
		this.location = location;
	}

	/**
	 * 如果一个新的切点表达式设置时，调用此方法
	 * 默认为空实现
	 * @param expression 需要设置的表达式
	 * @throws IllegalArgumentException 如果表达式不可用，抛出异常
	 * @see #setExpression
	 */
	protected void onSetExpression(@Nullable String expression) throws IllegalArgumentException {
	}

	/**
	 * 提供切点的表达式
	 */
	@Override
	@Nullable
	public String getExpression() {
		return this.expression;
	}

	/**
	 * 设置表达式
	 * @param expression 切点表达式
	 */
	public void setExpression(@Nullable String expression) {
		this.expression = expression;
		try {
			onSetExpression(expression);
		} catch (IllegalArgumentException ex) {
			// 如果可以，将位置信息放入到异常中
			if (this.location != null) {
				throw new IllegalArgumentException("Invalid expression at location [" + this.location + "]: " + ex);
			} else {
				throw ex;
			}
		}
	}

}
