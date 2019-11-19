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

package org.springframework.transaction.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.StringUtils;

/**
 * 公有的事务属性实现
 * 运行时会进行回滚，但并不会进行检查
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16.03.2003
 */
@SuppressWarnings("serial")
public class DefaultTransactionAttribute extends DefaultTransactionDefinition implements TransactionAttribute {

	@Nullable
	private String qualifier;

	@Nullable
	private String descriptor;


	/**
	 * 创建默认的DefaultTransactionAttribute，使用默认的配置
	 * 可以通过bean属性setter方法修改
	 * @see #setPropagationBehavior
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 * @see #setName
	 */
	public DefaultTransactionAttribute() {
		super();
	}

	/**
	 * 拷贝构造方法
	 * 可以通过属性的setter的方法修改
	 * @see #setPropagationBehavior
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 * @see #setName
	 */
	public DefaultTransactionAttribute(TransactionAttribute other) {
		super(other);
	}

	/**
	 * 使用给定的事务传播方式创建一个新的DefaultTransactionAttribute
	 * 可以通过属性的setter的方法修改
	 * @param propagationBehavior 传播方式，TransactionAttribute中的常量值
	 * @see #setIsolationLevel
	 * @see #setTimeout
	 * @see #setReadOnly
	 */
	public DefaultTransactionAttribute(int propagationBehavior) {
		super(propagationBehavior);
	}

	/**
	 * 返回事务属性关联的修饰符
	 * @since 3.0
	 */
	@Override
	@Nullable
	public String getQualifier() {
		return this.qualifier;
	}

	/**
	 * 事务属性关联的修饰符
	 * 用于选择一个对应的TransactionManager来执行特定的事务
	 * @since 3.0
	 */
	public void setQualifier(@Nullable String qualifier) {
		this.qualifier = qualifier;
	}

	/**
	 * 返回属性的描述符，如果没有，返回null
	 * @since 4.3.4
	 */
	@Nullable
	public String getDescriptor() {
		return this.descriptor;
	}

	/**
	 * 为事务属性设置描述符
	 * 比如，标识这个属性用于何处
	 * @since 4.3.4
	 */
	public void setDescriptor(@Nullable String descriptor) {
		this.descriptor = descriptor;
	}

	/**
	 * EJB的默认实现，在未检查的异常回滚，假设超出了任何业务规则的意外结果
	 * 除此之外，也可以尝试回滚错误，这个错误是未期待的
	 * 相反，一个检查的异常会被认为一个业务异常，会被视为一个常规期待的返回结果，比如，一种替代的返回值，他仍然允许资源操作的正常完成
	 * 这很大程度上和TransactionManager的默认行为一致，除了TransactionTemplate还会在未声明的检查异常上回滚
	 * 对于声明式事务，希望检查异常是内部声明的业务异常，默认会引导提交
	 * @see org.springframework.transaction.support.TransactionTemplate#execute
	 */
	@Override
	public boolean rollbackOn(Throwable ex) {
		return (ex instanceof RuntimeException || ex instanceof Error);
	}

	/**
	 * 为事务属性返回一个验证的描述
	 * 可用于子类，包含在他们的{@code toString()}结果中
	 */
	protected final StringBuilder getAttributeDescription() {
		StringBuilder result = getDefinitionDescription();
		if (StringUtils.hasText(this.qualifier)) {
			result.append("; '").append(this.qualifier).append("'");
		}
		return result;
	}

}
