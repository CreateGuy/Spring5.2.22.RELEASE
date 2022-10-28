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

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 将传入的值转为Character
 * 如果是长度大于1的字符串就会直接抛出异常，因为Character是char的包装类，char是一个字符
 *
 * 还支持unicode的转换，比如下面的字符A对应的unicode是u0041
 * {@code u0041} ('A').
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Rick Evans
 * @since 1.2
 * @see Character
 * @see org.springframework.beans.BeanWrapperImpl
 */
public class CharacterEditor extends PropertyEditorSupport {

	/**
	 * Unicode的前缀
	 */
	private static final String UNICODE_PREFIX = "\\u";

	/**
	 * Unicode的长度
	 */
	private static final int UNICODE_LENGTH = 6;

	/**
	 * 是否创建空值
	 */
	private final boolean allowEmpty;


	/**
	 * Create a new CharacterEditor instance.
	 * <p>The "allowEmpty" parameter controls whether an empty String is to be
	 * allowed in parsing, i.e. be interpreted as the {@code null} value when
	 * {@link #setAsText(String) text is being converted}. If {@code false},
	 * an {@link IllegalArgumentException} will be thrown at that time.
	 * @param allowEmpty if empty strings are to be allowed
	 */
	public CharacterEditor(boolean allowEmpty) {
		this.allowEmpty = allowEmpty;
	}


	@Override
	public void setAsText(@Nullable String text) throws IllegalArgumentException {
		//是否为其创建空值
		if (this.allowEmpty && !StringUtils.hasLength(text)) {
			// Treat empty String as null value.
			setValue(null);
		}
		else if (text == null) {
			throw new IllegalArgumentException("null String cannot be converted to char type");
		}
		//是Unicode
		else if (isUnicodeCharacterSequence(text)) {
			setAsUnicode(text);
		}
		else if (text.length() == 1) {
			setValue(Character.valueOf(text.charAt(0)));
		}
		//最后当长度大于1的时候就会抛出异常
		else {
			throw new IllegalArgumentException("String [" + text + "] with length " +
					text.length() + " cannot be converted to char type: neither Unicode nor single character");
		}
	}

	@Override
	public String getAsText() {
		Object value = getValue();
		return (value != null ? value.toString() : "");
	}

	/**
	 * 判断是否是Unicode
	 * @param sequence
	 * @return
	 */
	private boolean isUnicodeCharacterSequence(String sequence) {
		return (sequence.startsWith(UNICODE_PREFIX) && sequence.length() == UNICODE_LENGTH);
	}

	/**
	 * 将Unicode转为Character
	 * @param text
	 */
	private void setAsUnicode(String text) {
		//拿到的对应的ASCII码
		int code = Integer.parseInt(text.substring(UNICODE_PREFIX.length()), 16);
		setValue(Character.valueOf((char) code));
	}

}
