/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.context;

/**
 * 对象的接口，该对象可能参与一个阶段过程，例如生命周期管理
 *
 * @author Mark Fisher
 * @since 3.0
 * @see SmartLifecycle
 */
public interface Phased {

	/**
	 * 返回该对象的相位值
	 * <li>为了控制bean的初始化顺序和销毁的顺序</li>
	 */
	int getPhase();

}
