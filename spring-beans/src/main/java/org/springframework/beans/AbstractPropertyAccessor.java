/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * Abstract implementation of the {@link PropertyAccessor} interface.
 * Provides base implementations of all convenience methods, with the
 * implementation of actual property access left to subclasses.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.0
 * @see #getPropertyValue
 * @see #setPropertyValue
 */
public abstract class AbstractPropertyAccessor extends TypeConverterSupport implements ConfigurablePropertyAccessor {

	private boolean extractOldValueForEditor = false;

	private boolean autoGrowNestedPaths = false;

	/**
	 * 是否禁止写入异常
	 */
	boolean suppressNotWritablePropertyException = false;


	@Override
	public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
		this.extractOldValueForEditor = extractOldValueForEditor;
	}

	@Override
	public boolean isExtractOldValueForEditor() {
		return this.extractOldValueForEditor;
	}

	@Override
	public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
		this.autoGrowNestedPaths = autoGrowNestedPaths;
	}

	@Override
	public boolean isAutoGrowNestedPaths() {
		return this.autoGrowNestedPaths;
	}


	@Override
	public void setPropertyValue(PropertyValue pv) throws BeansException {
		setPropertyValue(pv.getName(), pv.getValue());
	}

	@Override
	public void setPropertyValues(Map<?, ?> map) throws BeansException {
		setPropertyValues(new MutablePropertyValues(map));
	}

	@Override
	public void setPropertyValues(PropertyValues pvs) throws BeansException {
		setPropertyValues(pvs, false, false);
	}

	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException {
		setPropertyValues(pvs, ignoreUnknown, false);
	}

	@Override
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException {

		// 参数绑定过程中出现的异常
		List<PropertyAccessException> propertyAccessExceptions = null;
		List<PropertyValue> propertyValues = (pvs instanceof MutablePropertyValues ?
				((MutablePropertyValues) pvs).getPropertyValueList() : Arrays.asList(pvs.getPropertyValues()));

		// 先禁止写入异常
		if (ignoreUnknown) {
			this.suppressNotWritablePropertyException = true;
		}
		try {
			for (PropertyValue pv : propertyValues) {

				try {
					// 可能会抛出任何BeansException，如果出现诸如没有匹配字段之类的严重失败，则不会在这里捕获该异常。我们可以尝试只处理不太严重的例外情况。
					setPropertyValue(pv);
				}
				catch (NotWritablePropertyException ex) {
					// 如果是不可以写入值的异常，还可以不抛出
					if (!ignoreUnknown) {
						throw ex;
					}
					// Otherwise, just ignore it and continue...
				}
				catch (NullValueInNestedPathException ex) {
					// 如果是字段名不存在的，还可以不抛出
					if (!ignoreInvalid) {
						throw ex;
					}
					// Otherwise, just ignore it and continue...
				}
				catch (PropertyAccessException ex) {
					if (propertyAccessExceptions == null) {
						propertyAccessExceptions = new ArrayList<>();
					}
					propertyAccessExceptions.add(ex);
				}
			}
		}
		finally {
			// 最后取消禁止写入异常
			if (ignoreUnknown) {
				this.suppressNotWritablePropertyException = false;
			}
		}

		// 如果抛出多个异常，则抛出复合异常
		if (propertyAccessExceptions != null) {
			PropertyAccessException[] paeArray = propertyAccessExceptions.toArray(new PropertyAccessException[0]);
			throw new PropertyBatchUpdateException(paeArray);
		}
	}


	// Redefined with public visibility.
	@Override
	@Nullable
	public Class<?> getPropertyType(String propertyPath) {
		return null;
	}

	/**
	 * Actually get the value of a property.
	 * @param propertyName name of the property to get the value of
	 * @return the value of the property
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't readable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed
	 */
	@Override
	@Nullable
	public abstract Object getPropertyValue(String propertyName) throws BeansException;

	/**
	 * Actually set a property value.
	 * @param propertyName name of the property to set value of
	 * @param value the new value
	 * @throws InvalidPropertyException if there is no such property or
	 * if the property isn't writable
	 * @throws PropertyAccessException if the property was valid but the
	 * accessor method failed or a type mismatch occurred
	 */
	@Override
	public abstract void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;

}
