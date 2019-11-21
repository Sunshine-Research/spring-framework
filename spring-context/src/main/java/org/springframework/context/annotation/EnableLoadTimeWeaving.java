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

package org.springframework.context.annotation;

import org.springframework.context.weaving.DefaultContextLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在ApplicationContext中开启Spring的{@link LoadTimeWeaver}，bean的名称是"loadTimeWeaver"
 * 和在Spring XML中使用{@code <context:load-time-weaver>}用法相似
 *
 * <h2>{@code LoadTimeWeaverAware}接口</h2>
 * 任何实现了{@link org.springframework.context.weaving.LoadTimeWeaverAware LoadTimeWeaverAware}接口的bean
 * 将会自动收到{@code LoadTimeWeaver}的引用，比如Spring的JPA启动支持
 *
 * <h2>自定义{@code LoadTimeWeaver}</h2>
 * 默认的织入是自动的，详情请看{@link DefaultContextLoadTimeWeaver}
 *
 * 为了自定义织入，配置了{@code @Configuration}、{@code @EnableLoadTimeWeaving}的类可能也需要实现{@link LoadTimeWeavingConfigurer}
 * 并通过{@code #getLoadTimeWeaver}方法，返回一个自定义的{@code LoadTimeWeaver}实例
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableLoadTimeWeaving
 * public class AppConfig implements LoadTimeWeavingConfigurer {
 *
 *     &#064;Override
 *     public LoadTimeWeaver getLoadTimeWeaver() {
 *         MyLoadTimeWeaver ltw = new MyLoadTimeWeaver();
 *         ltw.addClassTransformer(myClassFileTransformer);
 *         // ...
 *         return ltw;
 *     }
 * }</pre>
 *
 * 上面了的例子可以和下面的Spring XML配置进行比较
 * <pre class="code">
 * &lt;beans&gt;
 *
 *     &lt;context:load-time-weaver weaverClass="com.acme.MyLoadTimeWeaver"/&gt;
 *
 * &lt;/beans&gt;
 * </pre>
 *
 * 代码示例和XML示例不同的地方在于，它真正的实现了{@code MyLoadTimeWeaver}类型，意味着它可以配置这个实例
 * 比如：调用{@code #addClassTransformer}方法，这说明基于代码的配置方式直接编程更加的灵活
 *
 * 开启基于AspectJ的织入
 * AspectJ load-time weaving可以通过{@link #aspectjWeaving()}开启
 * 它会触发{@linkplain org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter AspectJ class transformer}
 * 通过{@link LoadTimeWeaver#addTransformer}添加注册
 * 如果"META-INF/aop.xml"存在于classpath中，AspectJ织入将会默认开启
 * 代码示例：
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableLoadTimeWeaving(aspectjWeaving=ENABLED)
 * public class AppConfig {
 * }</pre>
 *
 * <p>下面是Spring XML配置
 *
 * <pre class="code">
 * &lt;beans&gt;
 *
 *     &lt;context:load-time-weaver aspectj-weaving="on"/&gt;
 *
 * &lt;/beans&gt;
 * </pre>
 *
 * 两种示例均等价于一个中的异常，在XML示例中，{@code <context:spring-configured>}函数行为在以下的情况下隐式启用：
 * {@code aspectj-weaving}=on，但是不会在使用{@code @EnableLoadTimeWeaving(aspectjWeaving=ENABLED)}时发生
 * 必须明确地添加{@code @EnableSpringConfigured}（包含在{@code spring-aspects}模块中）来代替这种方式
 * @author Chris Beams
 * @since 3.1
 * @see LoadTimeWeaver
 * @see DefaultContextLoadTimeWeaver
 * @see org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LoadTimeWeavingConfiguration.class)
public @interface EnableLoadTimeWeaving {

	/**
	 * 是否启用AspectJ织入
	 */
	AspectJWeaving aspectjWeaving() default AspectJWeaving.AUTODETECT;


	/**
	 * AspectJ织入启用操作
	 */
	enum AspectJWeaving {

		/**
		 * 切换至基于Spring的AspectJ load-time weaving
		 */
		ENABLED,

		/**
		 * 关闭基于Spring的AspectJ load-time 织入
		 * 无论"META-INF/aop.xml"是否存在于classpath中
		 */
		DISABLED,

		/**
		 * 如果classpath中存在"META-INF/aop.xml"，开启AspectJ load-time织入
		 * 如果没有此文件，关闭AspectJ load-time织入
		 */
		AUTODETECT;
	}

}
