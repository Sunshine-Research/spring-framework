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

package org.aopalliance.aop;

/**
 * 所有AOP结构异常的超类
 * 未经检查的，因为这样的代码是很严重的，因此不能强迫用户去捕获
 * @author Rod Johnson
 * @author Bob Lee
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class AspectException extends RuntimeException {

	/**
	 * Constructor for AspectException.
	 * @param message the exception message
	 */
	public AspectException(String message) {
		super(message);
	}

	/**
	 * Constructor for AspectException.
	 * @param message the exception message
	 * @param cause the root cause, if any
	 */
	public AspectException(String message, Throwable cause) {
		super(message, cause);
	}

}
