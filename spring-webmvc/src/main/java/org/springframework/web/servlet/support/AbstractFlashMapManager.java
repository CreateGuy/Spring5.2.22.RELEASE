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

package org.springframework.web.servlet.support;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.util.UrlPathHelper;

/**
 * A base class for {@link FlashMapManager} implementations.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1.1
 */
public abstract class AbstractFlashMapManager implements FlashMapManager {

	private static final Object DEFAULT_FLASH_MAPS_MUTEX = new Object();


	protected final Log logger = LogFactory.getLog(getClass());

	private int flashMapTimeout = 180;

	private UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance;


	/**
	 * Set the amount of time in seconds after a {@link FlashMap} is saved
	 * (at request completion) and before it expires.
	 * <p>The default value is 180 seconds.
	 */
	public void setFlashMapTimeout(int flashMapTimeout) {
		this.flashMapTimeout = flashMapTimeout;
	}

	/**
	 * Return the amount of time in seconds before a FlashMap expires.
	 */
	public int getFlashMapTimeout() {
		return this.flashMapTimeout;
	}

	/**
	 * Set the UrlPathHelper to use to match FlashMap instances to requests.
	 */
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
	}

	/**
	 * Return the UrlPathHelper implementation to use.
	 */
	public UrlPathHelper getUrlPathHelper() {
		return this.urlPathHelper;
	}


	/**
	 * 查找先前请求保存的与当前请求匹配的FLashMap，将其从底层存储中删除，并删除其他过期的FLashMap实例
	 * @param request the current request
	 * @param response the current response
	 * @return
	 */
	@Override
	@Nullable
	public final FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
		// 读取已保存的FlashMap实例
		List<FlashMap> allFlashMaps = retrieveFlashMaps(request);
		if (CollectionUtils.isEmpty(allFlashMaps)) {
			return null;
		}

		// 返回过期的FlashMap
		List<FlashMap> mapsToRemove = getExpiredFlashMaps(allFlashMaps);
		// 返回与请求匹配的给定列表中包含的FlashMap
		FlashMap match = getMatchingFlashMap(allFlashMaps, request);

		// 无论的过期还是匹配成功的都要删除，所以放在一起了
		if (match != null) {
			mapsToRemove.add(match);
		}

		if (!mapsToRemove.isEmpty()) {
			Object mutex = getFlashMapsMutex(request);
			if (mutex != null) {
				synchronized (mutex) {
					// 拿到保存的所有FlashMap
					allFlashMaps = retrieveFlashMaps(request);
					if (allFlashMaps != null) {
						// 删除
						allFlashMaps.removeAll(mapsToRemove);
						// 更新
						updateFlashMaps(allFlashMaps, request, response);
					}
				}
			}
			else {
				// 上同
				allFlashMaps.removeAll(mapsToRemove);
				updateFlashMaps(allFlashMaps, request, response);
			}
		}

		return match;
	}

	/**
	 * 返回过期的FlashMap
	 */
	private List<FlashMap> getExpiredFlashMaps(List<FlashMap> allMaps) {
		List<FlashMap> result = new LinkedList<>();
		for (FlashMap map : allMaps) {
			if (map.isExpired()) {
				result.add(map);
			}
		}
		return result;
	}

	/**
	 * 返回与请求匹配的给定列表中包含的FlashMap
	 * @return a matching FlashMap or {@code null}
	 */
	@Nullable
	private FlashMap getMatchingFlashMap(List<FlashMap> allMaps, HttpServletRequest request) {
		List<FlashMap> result = new LinkedList<>();
		for (FlashMap flashMap : allMaps) {
			// 给定的FlashMap是否与当前请求匹配
			if (isFlashMapForRequest(flashMap, request)) {
				result.add(flashMap);
			}
		}
		if (!result.isEmpty()) {
			Collections.sort(result);
			if (logger.isTraceEnabled()) {
				logger.trace("Found " + result.get(0));
			}
			// 正常来说，只能有一个FlashMap
			return result.get(0);
		}
		return null;
	}

	/**
	 * 给定的FlashMap是否与当前请求匹配
	 */
	protected boolean isFlashMapForRequest(FlashMap flashMap, HttpServletRequest request) {
		// 拿到此FlashMap要给哪个请求使用
		String expectedPath = flashMap.getTargetRequestPath();
		if (expectedPath != null) {
			// 判断请求路径是否匹配
			String requestUri = getUrlPathHelper().getOriginatingRequestUri(request);
			if (!requestUri.equals(expectedPath) && !requestUri.equals(expectedPath + "/")) {
				return false;
			}
		}
		// 返回给定请求URL后的参数，如果这是一个转发请求，则正确解析为原始请求URL后的参数
		// 换言之只能用于转发这种不会改变Request的
		MultiValueMap<String, String> actualParams = getOriginatingRequestParams(request);
		// 拿到FlashMap中的参数
		MultiValueMap<String, String> expectedParams = flashMap.getTargetRequestParams();

		// 进行严格的比较
		// 毕竟如果flashMap的目标Url和从请求域读取的源Url不一样，那就不匹配
		for (Map.Entry<String, List<String>> entry : expectedParams.entrySet()) {
			List<String> actualValues = actualParams.get(entry.getKey());
			// 必须要包含参数
			if (actualValues == null) {
				return false;
			}
			// 参数值必须一样
			for (String expectedValue : entry.getValue()) {
				if (!actualValues.contains(expectedValue)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 返回给定请求URL后的参数。如果这是一个转发请求，则正确解析为原始请求URL后的参数
	 * @param request
	 * @return
	 */
	private MultiValueMap<String, String> getOriginatingRequestParams(HttpServletRequest request) {
		String query = getUrlPathHelper().getOriginatingQueryString(request);
		return ServletUriComponentsBuilder.fromPath("/").query(query).build().getQueryParams();
	}

	@Override
	public final void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request, HttpServletResponse response) {
		if (CollectionUtils.isEmpty(flashMap)) {
			return;
		}

		// 解码和规范化路径
		String path = decodeAndNormalizePath(flashMap.getTargetRequestPath(), request);
		flashMap.setTargetRequestPath(path);

		// 设置过期时间的开始时间
		flashMap.startExpirationPeriod(getFlashMapTimeout());

		// 获得此会话的互斥锁
		Object mutex = getFlashMapsMutex(request);
		if (mutex != null) {
			synchronized (mutex) {
				// 读取已保存的FlashMap实例
				List<FlashMap> allFlashMaps = retrieveFlashMaps(request);
				allFlashMaps = (allFlashMaps != null ? allFlashMaps : new CopyOnWriteArrayList<>());
				// 新增才传入的
				allFlashMaps.add(flashMap);
				// 在会话域中保存传入的flashMaps
				updateFlashMaps(allFlashMaps, request, response);
			}
		}
		else {
			// 和上面的一样，只是没了锁的限制
			List<FlashMap> allFlashMaps = retrieveFlashMaps(request);
			allFlashMaps = (allFlashMaps != null ? allFlashMaps : new LinkedList<>());
			allFlashMaps.add(flashMap);
			updateFlashMaps(allFlashMaps, request, response);
		}
	}

	/**
	 * 解码和规范化路径
	 * @param path
	 * @param request
	 * @return
	 */
	@Nullable
	private String decodeAndNormalizePath(@Nullable String path, HttpServletRequest request) {
		if (path != null && !path.isEmpty()) {
			// 解码路径，是以请求的编码格式进行编码
			path = getUrlPathHelper().decodeRequestString(request, path);
			// 检测路径是否合法，必须以'/'开头
			if (path.charAt(0) != '/') {
				// 拿到本次本次请求的Url
				String requestUri = getUrlPathHelper().getRequestUri(request);
				// 取Url的第一级路径 + path
				path = requestUri.substring(0, requestUri.lastIndexOf('/') + 1) + path;
				// 也是规范化路径
				path = StringUtils.cleanPath(path);
			}
		}
		return path;
	}

	/**
	 * 读取已保存的FlashMap实例
	 * @param request the current request
	 * @return a List with FlashMap instances, or {@code null} if none found
	 */
	@Nullable
	protected abstract List<FlashMap> retrieveFlashMaps(HttpServletRequest request);

	/**
	 * 更新底层存储中的FlashMaps实例
	 * @param flashMaps a (potentially empty) list of FlashMap instances to save
	 * @param request the current request
	 * @param response the current response
	 */
	protected abstract void updateFlashMaps(
			List<FlashMap> flashMaps, HttpServletRequest request, HttpServletResponse response);

	/**
	 * Obtain a mutex for modifying the FlashMap List as handled by
	 * {@link #retrieveFlashMaps} and {@link #updateFlashMaps},
	 * <p>The default implementation returns a shared static mutex.
	 * Subclasses are encouraged to return a more specific mutex, or
	 * {@code null} to indicate that no synchronization is necessary.
	 * @param request the current request
	 * @return the mutex to use (may be {@code null} if none applicable)
	 * @since 4.0.3
	 */
	@Nullable
	protected Object getFlashMapsMutex(HttpServletRequest request) {
		return DEFAULT_FLASH_MAPS_MUTEX;
	}

}
