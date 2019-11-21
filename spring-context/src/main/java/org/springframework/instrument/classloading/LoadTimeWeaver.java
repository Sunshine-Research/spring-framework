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

package org.springframework.instrument.classloading;

import java.lang.instrument.ClassFileTransformer;

/**
 * 定义一个或多个{@link ClassFileTransformer ClassFileTransformers}到{@link ClassLoader}的协议
 * 实现可以操作在当前的{@code ClassLoader}上面，也可以暴露自己的instrument
 * @author Rod Johnson
 * @author Costin Leau
 * @since 2.0
 * @see java.lang.instrument.ClassFileTransformer
 */
public interface LoadTimeWeaver {

	/**
	 * 添加供{@code LoadTimeWeaver}使用的{@code ClassFileTransformer}
	 * @param transformer 需要添加的{@code ClassFileTransformer}
	 */
	void addTransformer(ClassFileTransformer transformer);

	/**
	 * 返回支持instrumentation的{@code ClassLoader}，通过AspectJ-style load-time weaving
	 * 基于用户自定义的{@link ClassFileTransformer ClassFileTransformers}
	 * 可能是一个当前的{@code ClassLoader}，或者一个通过{@link LoadTimeWeaver}创建的{@code ClassLoader}实例
	 * @return 将会暴露根据已注册的transformers的instrumented的接口的类{@code ClassLoader}
	 */
	ClassLoader getInstrumentableClassLoader();

	/**
	 * 返回一个可抛出的{@code ClassLoader}，允许类加载并且在父{@code ClassLoader}的情况下进行了检查
	 * 不应该返回通过调用{@link #getInstrumentableClassLoader()}返回的相同实例的{@link ClassLoader}
	 * @return 可以抛出的{@code ClassLoader}临时对象，应该为每个调用均生成一个不带有存在状态的新实例
	 */
	ClassLoader getThrowawayClassLoader();

}
