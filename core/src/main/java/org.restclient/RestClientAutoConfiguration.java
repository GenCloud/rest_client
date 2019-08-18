package org.restclient;

import org.restclient.config.ServicesConfiguration;
import org.restclient.factory.ServiceMappingRegistrator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Configuration
@Import(ServiceMappingRegistrator.class)
@EnableConfigurationProperties(ServicesConfiguration.class)
public class RestClientAutoConfiguration {
}
