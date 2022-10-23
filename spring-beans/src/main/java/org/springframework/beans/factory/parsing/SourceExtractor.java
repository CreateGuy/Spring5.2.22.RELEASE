/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.factory.parsing;

import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

/**
 * 控制如何读取到bean定义元数据。
 */
@FunctionalInterface
public interface SourceExtractor {

	/**
	 * 从候选对象中提取元数据。
	 * @param sourceCandidate 候选对象
	 * @param definingResource
	 * @return
	 */
	@Nullable
	Object extractSource(Object sourceCandidate, @Nullable Resource definingResource);

}
