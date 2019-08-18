package org.restclient.annotations;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
@Documented
public @interface Header {
	String name();

	String value();
}
