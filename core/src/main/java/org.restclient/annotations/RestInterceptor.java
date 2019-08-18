package org.restclient.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface RestInterceptor {
	/**
	 * Registered service application name, need for config
	 */
	String[] aliases();
}
