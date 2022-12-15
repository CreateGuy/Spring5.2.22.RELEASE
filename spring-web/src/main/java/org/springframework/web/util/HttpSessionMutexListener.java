/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.web.util;

import java.io.Serializable;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * 会话互斥锁的应用程序监听器
 * <ul>
 *     <li>
 *         创建HttpSession时自动创建会话互斥锁
 *     </li>
 *     <li>
 *         销毁HttpSession时自动销毁会话互斥锁
 *     </li>
 * </ul>
 */
public class HttpSessionMutexListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent event) {
		event.getSession().setAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE, new Mutex());
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		event.getSession().removeAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE);
	}


	/**
	 * The mutex to be registered.
	 * Doesn't need to be anything but a plain Object to synchronize on.
	 * Should be serializable to allow for HttpSession persistence.
	 */
	@SuppressWarnings("serial")
	private static class Mutex implements Serializable {
	}

}
