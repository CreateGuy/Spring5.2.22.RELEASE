/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.core.convert.support;

import java.util.*;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.lang.Nullable;

/**
 * 负责将一个集合转为另外一个集合
 */
final class CollectionToCollectionConverter implements ConditionalGenericConverter {

	private final ConversionService conversionService;


	public CollectionToCollectionConverter(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * 表明可以转换集合到集合
	 * @return
	 */
	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		return Collections.singleton(new ConvertiblePair(Collection.class, Collection.class));
	}

	/**
	 * 判断是否可以转换
	 * @param sourceType
	 * @param targetType
	 * @return
	 */
	@Override
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		return ConversionUtils.canConvertElements(
				sourceType.getElementTypeDescriptor(), targetType.getElementTypeDescriptor(), this.conversionService);
	}

	@Override
	@Nullable
	public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (source == null) {
			return null;
		}
		Collection<?> sourceCollection = (Collection<?>) source;

		// Shortcut if possible...
		boolean copyRequired = !targetType.getType().isInstance(source);
		if (!copyRequired && sourceCollection.isEmpty()) {
			return source;
		}
		TypeDescriptor elementDesc = targetType.getElementTypeDescriptor();
		if (elementDesc == null && !copyRequired) {
			return source;
		}

		// At this point, we need a collection copy in any case, even if just for finding out about element copies...
		Collection<Object> target = CollectionFactory.createCollection(targetType.getType(),
				(elementDesc != null ? elementDesc.getType() : null), sourceCollection.size());

		if (elementDesc == null) {
			target.addAll(sourceCollection);
		}
		else {
			for (Object sourceElement : sourceCollection) {
				Object targetElement = this.conversionService.convert(sourceElement,
						sourceType.elementTypeDescriptor(sourceElement), elementDesc);
				target.add(targetElement);
				if (sourceElement != targetElement) {
					copyRequired = true;
				}
			}
		}

		return (copyRequired ? target : source);
	}

}
