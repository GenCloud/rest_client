package org.restclient.model;

import lombok.Data;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Method;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Data
public class MappingMetadata {
	private final Method method;
	private final HttpMethod httpMethod;
	private final String url;
	private final HttpHeaders httpHeaders;
	private final String fallbackMethod;

	public String getMethodName() {
		return method.getName();
	}

	public Class<?> getReturnType() {
		return method.getReturnType();
	}
}
