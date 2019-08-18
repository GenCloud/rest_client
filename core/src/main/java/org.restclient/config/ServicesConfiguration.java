package org.restclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Configuration
@ConfigurationProperties(prefix = "services")
@Primary
@Order
public class ServicesConfiguration {
	private List<RouteSettings> routes;

	public List<RouteSettings> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteSettings> routes) {
		this.routes = routes;
	}

	public static class RouteSettings {
		private String alias;
		private String host;

		public String getAlias() {
			return alias;
		}

		public void setAlias(String alias) {
			this.alias = alias;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}
	}
}
