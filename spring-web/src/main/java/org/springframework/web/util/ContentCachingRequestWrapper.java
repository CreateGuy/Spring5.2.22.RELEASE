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

package org.springframework.web.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;

/**
 *  {@link javax.servlet.http.HttpServletRequest} 的包装类，用于缓存 InputStream 和 reader
 *  <p>这样就可以解决无法多次读取请求体的问题了</p>
 */
public class ContentCachingRequestWrapper extends HttpServletRequestWrapper {

	private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

	/**
	 * 源输入流对应字节的存储位置
	 */
	private final ByteArrayOutputStream cachedContent;

	/**
	 * 输入流对应字节的最大值
	 */
	@Nullable
	private final Integer contentCacheLimit;

	/**
	 * 已经包装的输入流
	 */
	@Nullable
	private ServletInputStream inputStream;

	@Nullable
	private BufferedReader reader;


	/**
	 * Create a new ContentCachingRequestWrapper for the given servlet request.
	 * @param request the original servlet request
	 */
	public ContentCachingRequestWrapper(HttpServletRequest request) {
		super(request);
		int contentLength = request.getContentLength();
		this.cachedContent = new ByteArrayOutputStream(contentLength >= 0 ? contentLength : 1024);
		this.contentCacheLimit = null;
	}

	/**
	 * Create a new ContentCachingRequestWrapper for the given servlet request.
	 * @param request the original servlet request
	 * @param contentCacheLimit the maximum number of bytes to cache per request
	 * @since 4.3.6
	 * @see #handleContentOverflow(int)
	 */
	public ContentCachingRequestWrapper(HttpServletRequest request, int contentCacheLimit) {
		super(request);
		this.cachedContent = new ByteArrayOutputStream(contentCacheLimit);
		this.contentCacheLimit = contentCacheLimit;
	}


	/**
	 * 获得请求的请求体对应的输入流
	 * @return
	 * @throws IOException
	 */
	@Override
	public ServletInputStream getInputStream() throws IOException {
		if (this.inputStream == null) {
			// 将输入流进行包装，重点也是在这个类上
			this.inputStream = new ContentCachingInputStream(getRequest().getInputStream());
		}
		return this.inputStream;

		/*
			1、ContentCachingInputStream会将读取到的字节数组保存到cachedContent中
			2、这个时候一般我们都是用 getContentAsByteArray 方法来获取对应的字节数组的，然后再转输入流
			3、但是框架里面怎么可能用这个方法，都是用 getInputStream 方法直接获取输入流，那么在框架的角度上来说还是无法读取多次输入流
			4、所有要对这个方法进行重写，比如像下面这样
				if (this.inputStream == null) {
					// 将输入流进行包装，重点也是在这个类上
					this.inputStream = new ContentCachingInputStream(getRequest().getInputStream());
				}
				//这里是重新创建一个输入流
				return new ServletInputStreamNew(getContentAsByteArray());
		 */
	}

	/**
	 * 返回此请求的字符编码
	 * @return
	 */
	@Override
	public String getCharacterEncoding() {
		String enc = super.getCharacterEncoding();
		return (enc != null ? enc : WebUtils.DEFAULT_CHARACTER_ENCODING);
	}

	@Override
	public BufferedReader getReader() throws IOException {
		if (this.reader == null) {
			this.reader = new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
		}
		return this.reader;
	}

	@Override
	public String getParameter(String name) {
		if (this.cachedContent.size() == 0 && isFormPost()) {
			writeRequestParametersToCachedContent();
		}
		return super.getParameter(name);
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		if (this.cachedContent.size() == 0 && isFormPost()) {
			writeRequestParametersToCachedContent();
		}
		return super.getParameterMap();
	}

	@Override
	public Enumeration<String> getParameterNames() {
		if (this.cachedContent.size() == 0 && isFormPost()) {
			writeRequestParametersToCachedContent();
		}
		return super.getParameterNames();
	}

	@Override
	public String[] getParameterValues(String name) {
		if (this.cachedContent.size() == 0 && isFormPost()) {
			writeRequestParametersToCachedContent();
		}
		return super.getParameterValues(name);
	}


	private boolean isFormPost() {
		String contentType = getContentType();
		return (contentType != null && contentType.contains(FORM_CONTENT_TYPE) &&
				HttpMethod.POST.matches(getMethod()));
	}

	private void writeRequestParametersToCachedContent() {
		try {
			if (this.cachedContent.size() == 0) {
				String requestEncoding = getCharacterEncoding();
				Map<String, String[]> form = super.getParameterMap();
				for (Iterator<String> nameIterator = form.keySet().iterator(); nameIterator.hasNext(); ) {
					String name = nameIterator.next();
					List<String> values = Arrays.asList(form.get(name));
					for (Iterator<String> valueIterator = values.iterator(); valueIterator.hasNext(); ) {
						String value = valueIterator.next();
						this.cachedContent.write(URLEncoder.encode(name, requestEncoding).getBytes());
						if (value != null) {
							this.cachedContent.write('=');
							this.cachedContent.write(URLEncoder.encode(value, requestEncoding).getBytes());
							if (valueIterator.hasNext()) {
								this.cachedContent.write('&');
							}
						}
					}
					if (nameIterator.hasNext()) {
						this.cachedContent.write('&');
					}
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write request parameters to cached content", ex);
		}
	}

	/**
	 * 将缓存的请求体作为字节数组返回
	 * <p>The returned array will never be larger than the content cache limit.
	 * @see #ContentCachingRequestWrapper(HttpServletRequest, int)
	 */
	public byte[] getContentAsByteArray() {
		return this.cachedContent.toByteArray();
	}

	/**
	 * 处理内容溢出的模板方法:具体来说，读取的请求体超过了指定的内容缓存限制。
	 * <p>The default implementation is empty. Subclasses may override this to
	 * throw a payload-too-large exception or the like.
	 * @param contentCacheLimit the maximum number of bytes to cache per request
	 * which has just been exceeded
	 * @since 4.3.6
	 * @see #ContentCachingRequestWrapper(HttpServletRequest, int)
	 */
	protected void handleContentOverflow(int contentCacheLimit) {
	}


	/**
	 * 保存了源输入流对应字节数组的
	 */
	private class ContentCachingInputStream extends ServletInputStream {

		/**
		 * 外部输入流，也就是原生的输入流
		 */
		private final ServletInputStream is;

		/**
		 * 是否溢出
		 */
		private boolean overflow = false;

		public ContentCachingInputStream(ServletInputStream is) {
			this.is = is;
		}

		/**
		 * 读取一个字节，当为-1的时候表示已读到最后了
		 * @return
		 * @throws IOException
		 */
		@Override
		public int read() throws IOException {
			// 读取原始输入流的一个字节
			int ch = this.is.read();
			if (ch != -1 && !this.overflow) {
				// 处理溢出的情况
				if (contentCacheLimit != null && cachedContent.size() == contentCacheLimit) {
					this.overflow = true;
					handleContentOverflow(contentCacheLimit);
				}
				else {
					// 重点：将此输入流的数据写入缓存中
					cachedContent.write(ch);
				}
			}
			return ch;
		}

		@Override
		public int read(byte[] b) throws IOException {
			int count = this.is.read(b);
			writeToCache(b, 0, count);
			return count;
		}

		/**
		 * 将读取出来的字节数组保存在缓存中
		 * @param b
		 * @param off
		 * @param count
		 */
		private void writeToCache(final byte[] b, final int off, int count) {
			if (!this.overflow && count > 0) {
				if (contentCacheLimit != null &&
						count + cachedContent.size() > contentCacheLimit) {
					this.overflow = true;
					cachedContent.write(b, off, contentCacheLimit - cachedContent.size());
					handleContentOverflow(contentCacheLimit);
					return;
				}
				cachedContent.write(b, off, count);
			}
		}

		/**
		 * 直接从指定偏移量读取指定长度的数据放入 b 中，然后返回读取数据的大小
		 * <ul>
		 *     <li>正常走完这个方法，输入流对应的字节数组就已经完全保存在缓存中了</li>
		 *     <li>后面就可以直接通过缓存重新转为输入流，就可以重新读取了</li>
		 * </ul>
		 * @param b
		 * @param off
		 * @param len
		 * @return
		 * @throws IOException
		 */
		@Override
		public int read(final byte[] b, final int off, final int len) throws IOException {
			// 读取原输入流
			int count = this.is.read(b, off, len);
			// 将读取出来的字节数组保存在缓存中
			writeToCache(b, off, count);
			return count;
		}

		@Override
		public int readLine(final byte[] b, final int off, final int len) throws IOException {
			int count = this.is.readLine(b, off, len);
			writeToCache(b, off, count);
			return count;
		}

		@Override
		public boolean isFinished() {
			return this.is.isFinished();
		}

		@Override
		public boolean isReady() {
			return this.is.isReady();
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			this.is.setReadListener(readListener);
		}
	}

}
