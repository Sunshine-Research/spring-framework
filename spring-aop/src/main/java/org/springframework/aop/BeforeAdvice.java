/*
 * Copyright 2002-2007 the original author or authors.
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

import org.aopalliance.aop.Advice;

/**
 * 前置增强的普通标志接口，实现如{@link MethodBeforeAdvice}
 * <p>
 * 现在只支持方法级别的前置增强，可能以后也没什么变化
 * 之所以设计这个接口，是为了未来允许字段增强而考虑的
 * @author Rod Johnson
 * @see AfterAdvice
 */
public interface BeforeAdvice extends Advice {

}
