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

package org.springframework.web.context.request.async;

import java.util.concurrent.Callable;

import org.springframework.web.context.request.NativeWebRequest;

/**
 * 如果响应尚未提交，则在超时情况下发送503
 * 在4.2.8中，这是通过抛出 AsyncRequestTimeoutException 来间接完成的，然后由Spring MVC的默认异常处理作为503错误处理
 */
public class TimeoutCallableProcessingInterceptor implements CallableProcessingInterceptor {

	@Override
	public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) throws Exception {
		return new AsyncRequestTimeoutException();
	}

}
