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
 * {@link Ordered}接口的扩展接口，表达了一种优先级排序：{@code PriorityOrdered}对象总是比普通的{@link Ordered}对象之前应用，无论其顺序值如何
 * <p>
 * 当排序{@code Ordered}对象集合时，{@code PriorityOrdered}对象和普通的{@code Ordered}对象将会被视为两个分离的子集
 * {@code PriorityOrdered}子集在{@code Ordered}对象子集之前，并且在这些子集中应用了相对排序
 * <p>
 * 这也是一个专用接口，用于框架自己内部使用，用于对象需要特别重要来识别为优先级对象，甚至可能没有获取剩余的对象，比如Spring ApplicationContext中的优先级post-processors
 * <p>
 * 需要注意的是：{@code PriorityOrdered}类型的post-processor bean在一个特殊的阶段实例化，在其他post-processors bean之前
 * 这巧妙的影响了它们的自动装配行为：它们将仅针对不需要为类型匹配而紧急初始化的bean自动装配
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see org.springframework.beans.factory.config.PropertyOverrideConfigurer
 * @see org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
 * @since 2.5
 */
public interface PriorityOrdered extends Ordered {
}
