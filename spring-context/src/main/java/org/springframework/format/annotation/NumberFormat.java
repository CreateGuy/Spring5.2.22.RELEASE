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

package org.springframework.format.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明字段或方法参数应格式化为数字
 *
 * <p>Supports formatting by style or custom pattern string. Can be applied
 * to any JDK {@code Number} type such as {@code Double} and {@code Long}.
 *
 * <p>For style-based formatting, set the {@link #style} attribute to be the
 * desired {@link Style}. For custom formatting, set the {@link #pattern}
 * attribute to be the number pattern, such as {@code #, ###.##}.
 *
 * <p>Each attribute is mutually exclusive, so only set one attribute per
 * annotation instance (the one most convenient one for your formatting needs).
 * When the {@link #pattern} attribute is specified, it takes precedence over
 * the {@link #style} attribute. When no annotation attributes are specified,
 * the default format applied is style-based for either number of currency,
 * depending on the annotated field or method parameter type.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see java.text.NumberFormat
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
public @interface NumberFormat {

	/**
	 * 用于格式化字段的样式模式
	 * <p>Defaults to {@link Style#DEFAULT} for general-purpose number formatting
	 * for most annotated types, except for money types which default to currency
	 * formatting. Set this attribute when you wish to format your field in
	 * accordance with a common style other than the default style.
	 */
	Style style() default Style.DEFAULT;

	/**
	 * 用于格式化字段的自定义模式
	 * <ul>
	 *     <li>
	 *         比如说 ‘#,###’，那么传入的 5000 就会变成 5,000
	 *     </li>
	 * </ul>
	 */
	String pattern() default "";


	/**
	 * 常见的数字格式样式。
	 */
	enum Style {

		/**
		 * 带注释类型的默认格式, 不懂
		 */
		DEFAULT,

		/**
		 * 当前Local设置的通用数字格式
		 */
		NUMBER,

		/**
		 * 当前Local设置的百分比格式
		 */
		PERCENT,

		/**
		 * 当前Local设置的货币格式
		 */
		CURRENCY
	}

}
