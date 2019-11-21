/*
 * Copyright 2002-2015 the original author or authors.
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

import org.springframework.beans.factory.config.BeanDefinition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * 赋予给定bean角色信息
 *
 * 可以使用在直接或间接使用{@link org.springframework.stereotype.Component}注解的类上
 * 也可以使用在使用{@link Bean}的方法上
 *
 * 如果此注解没有存在于一个Component或Bean Definition上，会默认设置为{@link BeanDefinition#ROLE_APPLICATION}角色
 *
 * 如果为{@link Configuration @Configuration}类设置了一个角色，证明配置类的角色已经定义，并且不会级联到其中定义的所有@{@code Bean}方法上
 * 这种行为和@{@link Lazy}注解是不同的
 * @author Chris Beams
 * @since 3.1
 * @see BeanDefinition#ROLE_APPLICATION
 * @see BeanDefinition#ROLE_INFRASTRUCTURE
 * @see BeanDefinition#ROLE_SUPPORT
 * @see Bean
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Role {

	/**
	 * 为关联的bean设置角色
	 * @see BeanDefinition#ROLE_APPLICATION
	 * @see BeanDefinition#ROLE_INFRASTRUCTURE
	 * @see BeanDefinition#ROLE_SUPPORT
	 */
	int value();

}
