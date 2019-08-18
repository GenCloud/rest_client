package org.restclient.gateway;

import org.restclient.annotations.Mapping;
import org.restclient.annotations.ServiceMapping;
import org.restclient.annotations.Type;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

import static org.springframework.http.HttpMethod.GET;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Mapping(alias = "github-service")
public interface RestGateway {
	/**
	 * Get all repos met-information by user name.
	 *
	 * @param userName - github user name
	 * @return json string
	 */
	@ServiceMapping(path = "/users/${userName}/repos", method = GET)
	@Type(type = ArrayList.class)
	Mono<ArrayList> getRepos(@PathVariable("userName") String userName);
}
