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

package org.springframework.cache.interceptor;

import org.springframework.lang.Nullable;

/**
 * 用于读取和保存缓存值的，对应 @Cacheable
 */
public class CacheableOperation extends CacheOperation {

	/**
	 * 是最终决定是否保存在缓存中的条件
	 */
	@Nullable
	private final String unless;

	/**
	 * 是否开启同步
	 */
	private final boolean sync;


	/**
	 * Create a new {@link CacheableOperation} instance from the given builder.
	 * @since 4.3
	 */
	public CacheableOperation(CacheableOperation.Builder b) {
		super(b);
		this.unless = b.unless;
		this.sync = b.sync;
	}


	@Nullable
	public String getUnless() {
		return this.unless;
	}

	public boolean isSync() {
		return this.sync;
	}


	/**
	 * 创建 {@link CacheableOperation} 的基类
	 * @since 4.3
	 */
	public static class Builder extends CacheOperation.Builder {

		@Nullable
		private String unless;

		private boolean sync;

		public void setUnless(String unless) {
			this.unless = unless;
		}

		public void setSync(boolean sync) {
			this.sync = sync;
		}

		@Override
		protected StringBuilder getOperationDescription() {
			StringBuilder sb = super.getOperationDescription();
			sb.append(" | unless='");
			sb.append(this.unless);
			sb.append("'");
			sb.append(" | sync='");
			sb.append(this.sync);
			sb.append("'");
			return sb;
		}

		@Override
		public CacheableOperation build() {
			return new CacheableOperation(this);
		}
	}

}
