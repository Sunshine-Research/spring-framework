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

package org.springframework.beans.factory;

/**
 * 一个标记超级接口，表明bean有资格通过回调类型方法由Spring容器通知特定框架对象
 * 实际的方法签名由子接口确定，但是通常仅包含一个返回值类型是void的方法，并仅有一个请求参数
 * <p>
 * 需要注意的是，仅实现{@link Aware}并不能提供默认的功能
 * 而是必须明确的进行处理，比如：在{@link org.springframework.beans.factory.config.BeanPostProcessor}中
 * 参考{@link org.springframework.context.support.ApplicationContextAwareProcessor}
 * 就是一个处理特定{@code *Aware}接口回调任务的例子
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
public interface Aware {

}
