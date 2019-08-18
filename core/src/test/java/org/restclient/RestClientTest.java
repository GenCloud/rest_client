package org.restclient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.restclient.gateway.RestGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = SpringApplicationTest.class)
@AutoConfigureMockMvc
@AutoConfigureJsonTesters
public class RestClientTest {
	@Autowired
	@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
	private RestGateway restGateway;

	@Test
	public void test_getRepoInfo() {
		final Mono<ArrayList> response = restGateway.getRepos("gencloud");

		StepVerifier.create(response)
				.expectSubscription()
				.expectNextMatches(r -> !r.isEmpty())
				.expectComplete()
				.verify();
	}
}
