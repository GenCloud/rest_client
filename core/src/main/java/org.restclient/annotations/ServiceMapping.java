package org.restclient.annotations;

import org.springframework.http.HttpMethod;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface ServiceMapping {
	/**
	 * Registered service application route
	 */
	String path();

	/**
	 * Registered service application route http-method
	 */
	HttpMethod method();

	Header[] defaultHeaders() default {};

	Class<?> fallbackClass() default Object.class;

	String fallbackMethod() default "";
}
