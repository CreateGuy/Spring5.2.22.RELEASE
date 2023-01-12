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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 专门解析矩阵值的
 * <ul><li>/request;username=admin;password=123456;age=20</li></ul>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MatrixVariable {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the matrix variable.
	 * @since 4.2
	 * @see #value
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * 当多级目录的时候，指定具体位置
	 */
	String pathVar() default ValueConstants.DEFAULT_NONE;

	/**
	 * Whether the matrix variable is required.
	 * <p>Default is {@code true}, leading to an exception being thrown in
	 * case the variable is missing in the request. Switch this to {@code false}
	 * if you prefer a {@code null} if the variable is missing.
	 * <p>Alternatively, provide a {@link #defaultValue}, which implicitly sets
	 * this flag to {@code false}.
	 */
	boolean required() default true;

	/**
	 * 如果设置为必穿，但是没有找到值，就是默认值
	 */
	String defaultValue() default ValueConstants.DEFAULT_NONE;

}
