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

package org.springframework.core;

/**
 * {@code Ordered}用于对象实现的排序功能的接口
 * 实际的{@link #getOrder()}方法解释为优先级，优先级最高的第一个对象（顺序值最低）
 * <p>
 * 需要注意的是，接口还有一个priority标记位：{@link PriorityOrdered}
 * 有关{@code PriorityOrdered}对象相对于简单的{@link Ordered}如何排序的详细信息，请查阅{@code PriorityOrdered}的JavaDoc
 * <p>
 * 有关{@link OrderComparator}的信息，请查阅JavaDoc，以获取有关非排序对象的排序语义的详细信息
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see PriorityOrdered
 * @see OrderComparator
 * @see org.springframework.core.annotation.Order
 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
 * @since 07.04.2003
 */
public interface Ordered {

	/**
	 * 最高优先级常量
	 * @see java.lang.Integer#MIN_VALUE
	 */
	int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

	/**
	 * 最低优先级常量
	 * @see java.lang.Integer#MAX_VALUE
	 */
	int LOWEST_PRECEDENCE = Integer.MAX_VALUE;


	/**
	 * 获取当前对象的排序值
	 * 值越大，优先级越低
	 * 相同的排序值将导致任意的排序位置
	 * @return 对象的排序值
	 * @see #HIGHEST_PRECEDENCE
	 * @see #LOWEST_PRECEDENCE
	 */
	int getOrder();

}
