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

package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.StringJoiner;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 将传入的文本(class的全路径)用逗号切分，然后转为一个Class数组
 */
public class ClassArrayEditor extends PropertyEditorSupport {

	@Nullable
	private final ClassLoader classLoader;


	/**
	 * Create a default {@code ClassEditor}, using the thread
	 * context {@code ClassLoader}.
	 */
	public ClassArrayEditor() {
		this(null);
	}

	/**
	 * Create a default {@code ClassArrayEditor}, using the given
	 * {@code ClassLoader}.
	 * @param classLoader the {@code ClassLoader} to use
	 * (or pass {@code null} for the thread context {@code ClassLoader})
	 */
	public ClassArrayEditor(@Nullable ClassLoader classLoader) {
		this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
	}


	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		if (StringUtils.hasText(text)) {
			//用逗号分隔传入的text，得到Class名称数组
			String[] classNames = StringUtils.commaDelimitedListToStringArray(text);
			Class<?>[] classes = new Class<?>[classNames.length];
			for (int i = 0; i < classNames.length; i++) {
				String className = classNames[i].trim();
				classes[i] = ClassUtils.resolveClassName(className, this.classLoader);
			}
			setValue(classes);
		}
		else {
			setValue(null);
		}
	}

	@Override
	public String getAsText() {
		Class<?>[] classes = (Class[]) getValue();
		if (ObjectUtils.isEmpty(classes)) {
			return "";
		}
		StringJoiner sj = new StringJoiner(",");
		for (Class<?> klass : classes) {
			sj.add(ClassUtils.getQualifiedName(klass));
		}
		return sj.toString();
	}

}
