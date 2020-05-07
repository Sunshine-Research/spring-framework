/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.convert;

import org.springframework.lang.Nullable;

/**
 * 类型转换的Service接口
 * 是转换系统的入口，调用{@link #convert(Object, Class)}来在系统中执行一个线程安全的类型转换
 * @author Keith Donald
 * @author Phillip Webb
 * @since 3.0
 */
public interface ConversionService {

	/**
	 * 如果类型{@code sourceType}可以转换为{@code targetType}类型，返回{@code true}
	 * <p>
	 * 如果方法返回{@code true}，意味着{@link #convert(Object, Class)}可以完成{@code sourceType}到{@code targetType}实例的转换
	 * <p>
	 * 注意：在集合，数组和字典表类型，方法会返回{@code true}，即使convert可能会产生一个{@link ConversionException}异常
	 * 这可能是由于集合中的某些元素是不可转换的，希望调用者在使用集合和字典表时来处理这些异常情况
	 * @param sourceType the source type to convert from (may be {@code null} if source is {@code null})
	 * @param targetType the target type to convert to (required)
	 * @return {@code true} if a conversion can be performed, {@code false} if not
	 * @throws IllegalArgumentException if {@code targetType} is {@code null}
	 */
	boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType);

	/**
	 * Return {@code true} if objects of {@code sourceType} can be converted to the {@code targetType}.
	 * The TypeDescriptors provide additional context about the source and target locations
	 * where conversion would occur, often object fields or property locations.
	 * <p>If this method returns {@code true}, it means {@link #convert(Object, TypeDescriptor, TypeDescriptor)}
	 * is capable of converting an instance of {@code sourceType} to {@code targetType}.
	 * <p>Special note on collections, arrays, and maps types:
	 * For conversion between collection, array, and map types, this method will return {@code true}
	 * even though a convert invocation may still generate a {@link ConversionException} if the
	 * underlying elements are not convertible. Callers are expected to handle this exceptional case
	 * when working with collections and maps.
	 * @param sourceType context about the source type to convert from
	 * (may be {@code null} if source is {@code null})
	 * @param targetType context about the target type to convert to (required)
	 * @return {@code true} if a conversion can be performed between the source and target types,
	 * {@code false} if not
	 * @throws IllegalArgumentException if {@code targetType} is {@code null}
	 */
	boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType);

	/**
	 * Convert the given {@code source} to the specified {@code targetType}.
	 * @param source the source object to convert (may be {@code null})
	 * @param targetType the target type to convert to (required)
	 * @return the converted object, an instance of targetType
	 * @throws ConversionException if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null}
	 */
	@Nullable
	<T> T convert(@Nullable Object source, Class<T> targetType);

	/**
	 * Convert the given {@code source} to the specified {@code targetType}.
	 * The TypeDescriptors provide additional context about the source and target locations
	 * where conversion will occur, often object fields or property locations.
	 * @param source the source object to convert (may be {@code null})
	 * @param sourceType context about the source type to convert from
	 * (may be {@code null} if source is {@code null})
	 * @param targetType context about the target type to convert to (required)
	 * @return the converted object, an instance of {@link TypeDescriptor#getObjectType() targetType}
	 * @throws ConversionException if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null},
	 * or {@code sourceType} is {@code null} but source is not {@code null}
	 */
	@Nullable
	Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType);

}
