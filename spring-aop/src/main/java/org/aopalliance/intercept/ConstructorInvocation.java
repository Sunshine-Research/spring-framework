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

package org.aopalliance.intercept;

import java.lang.reflect.Constructor;

/**
 * 构造方法的调用描述，在构造方法调用是进行拦截
 *
 * 构造方法调用是一个连接点，可以通过interceptor进行拦截
 * @author Rod Johnson
 * @see ConstructorInterceptor
 */
public interface ConstructorInvocation extends Invocation {

	/**
	 * 获取进行调用的构造方法
	 * 是{@link Joinpoint#getStaticPart()}方法的友好实现
	 * @return 进行调用的构造方法
	 */
	Constructor<?> getConstructor();

}
