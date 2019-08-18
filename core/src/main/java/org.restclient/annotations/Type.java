package org.restclient.annotations;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface Type {
	Class<?> type();
}
