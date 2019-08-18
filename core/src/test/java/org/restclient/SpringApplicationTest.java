package org.restclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@SpringBootApplication(scanBasePackages = "org.restclient")
public class SpringApplicationTest {
	public static void main(String[] args) {
		SpringApplication.run(SpringApplicationTest.class);
	}
}
