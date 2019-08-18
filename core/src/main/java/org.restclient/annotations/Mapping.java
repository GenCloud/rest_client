package org.restclient.annotations;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Mapping {
	/**
	 * Registered service application name, need for config
	 */
	String alias();
}
