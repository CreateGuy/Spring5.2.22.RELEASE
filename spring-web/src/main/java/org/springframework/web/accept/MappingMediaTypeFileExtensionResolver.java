/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.accept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

/**
 * 提供文件扩展名和媒体类型(MediaTypes)之间双向查找
 */
public class MappingMediaTypeFileExtensionResolver implements MediaTypeFileExtensionResolver {

	/**
	 * 文件扩展名和媒体类型的映射关系
	 */
	private final ConcurrentMap<String, MediaType> mediaTypes = new ConcurrentHashMap<>(64);

	/**
	 * 媒体类型和文件扩展名的映射关系
	 */
	private final ConcurrentMap<MediaType, List<String>> fileExtensions = new ConcurrentHashMap<>(64);

	/**
	 * 此对象维护的所有的文件扩展名
	 */
	private final List<String> allFileExtensions = new CopyOnWriteArrayList<>();


	/**
	 * 用给定的文件扩展名和媒体类型映射创建一个实例
	 */
	public MappingMediaTypeFileExtensionResolver(@Nullable Map<String, MediaType> mediaTypes) {
		if (mediaTypes != null) {
			// 文件扩展名
			Set<String> allFileExtensions = new HashSet<>(mediaTypes.size());
			mediaTypes.forEach((extension, mediaType) -> {
				// 文件扩展名都取小写
				String lowerCaseExtension = extension.toLowerCase(Locale.ENGLISH);
				this.mediaTypes.put(lowerCaseExtension, mediaType);
				addFileExtension(mediaType, lowerCaseExtension);
				allFileExtensions.add(lowerCaseExtension);
			});
			// 将文件扩展名加入 allFileExtensions 中
			this.allFileExtensions.addAll(allFileExtensions);
		}
	}


	public Map<String, MediaType> getMediaTypes() {
		return this.mediaTypes;
	}

	protected List<MediaType> getAllMediaTypes() {
		return new ArrayList<>(this.mediaTypes.values());
	}

	/**
	 * 将文件扩展名映射到媒体类型，忽略已映射的扩展名
	 */
	protected void addMapping(String extension, MediaType mediaType) {
		MediaType previous = this.mediaTypes.putIfAbsent(extension, mediaType);
		if (previous == null) {
			addFileExtension(mediaType, extension);
			this.allFileExtensions.add(extension);
		}
	}

	private void addFileExtension(MediaType mediaType, String extension) {
		// 不存在的才加入
		this.fileExtensions.computeIfAbsent(mediaType, key -> new CopyOnWriteArrayList<>())
				.add(extension);
	}


	/**
	 * 将给定的媒体类型解析为文件扩展名列表
	 * @param mediaType the media type to resolve
	 * @return
	 */
	@Override
	public List<String> resolveFileExtensions(MediaType mediaType) {
		List<String> fileExtensions = this.fileExtensions.get(mediaType);
		return (fileExtensions != null ? fileExtensions : Collections.emptyList());
	}

	/**
	 * 获取所有已注册的文件扩展名
	 * @return
	 */
	@Override
	public List<String> getAllFileExtensions() {
		return Collections.unmodifiableList(this.allFileExtensions);
	}

	/**
	 * 使用此方法进行从文件扩展名到媒体类型的反向查找
	 */
	@Nullable
	protected MediaType lookupMediaType(String extension) {
		return this.mediaTypes.get(extension.toLowerCase(Locale.ENGLISH));
	}

}
