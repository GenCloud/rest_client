package org.restclient.interceptor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
public interface Interceptor {
	/**
	 * Pre-handle http request.
	 *
	 * @param args    - method args
	 * @param body    - request body
	 * @param headers - request headers
	 */
	void preHandle(Object[] args, Object body, HttpHeaders headers);

	/**
	 * Post-handle http request.
	 *
	 * @param responseEntity - returned response
	 */
	void postHandle(ResponseEntity<?> responseEntity);
}
