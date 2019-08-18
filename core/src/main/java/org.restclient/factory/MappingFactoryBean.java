package org.restclient.factory;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.restclient.annotations.RestInterceptor;
import org.restclient.annotations.ServiceMapping;
import org.restclient.annotations.Type;
import org.restclient.config.ServicesConfiguration;
import org.restclient.config.ServicesConfiguration.RouteSettings;
import org.restclient.interceptor.Interceptor;
import org.restclient.model.MappingMetadata;
import org.restclient.model.Pair;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jmx.access.InvocationFailureException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.naming.ConfigurationException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Slf4j
@ToString
public class MappingFactoryBean implements BeanFactoryAware, FactoryBean<Object>, ApplicationContextAware {
	private static final Collection<String> ignoredMethods = Arrays.asList("equals", "hashCode", "toString");

	private Class<?> type;
	private List<Object> fallbackInstances;
	private List<MappingMetadata> metadatas;
	private String alias;
	private ApplicationContext applicationContext;
	private BeanFactory beanFactory;

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public Class<?> getObjectType() {
		return type;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public Object getObject() {
		return Enhancer.create(type, (MethodInterceptor) (instance, method, args, methodProxy) -> {
			final boolean skip = ignoredMethods.stream().anyMatch(ignore -> method.getName().equals(ignore));

			final ServiceMapping annotation = method.getAnnotation(ServiceMapping.class);
			if (!skip && annotation != null) {
				return invokeMethod(annotation, method, args);
			}
			return null;
		});
	}

	/**
	 * It determines the meta-information of the executing method, calling an HTTP request based on the
	 * meta-information found; interceptors are also called.
	 *
	 * @param annotation - main annotation that defines the path, type, standard request parameters.
	 * @param method     - callable method
	 * @param args       - method arguments
	 * @return if the request is executed without errors, returns a clean server response in wrappers Mono/Flux.
	 * @throws Throwable
	 */
	private Object invokeMethod(ServiceMapping annotation, Method method, Object[] args) throws Throwable {
		final MappingMetadata metadata = findMetadataByMethodName(method.getName());
		if (metadata == null) {
			throw new NoSuchMethodException(String.format("Cant find metadata for method %s. Check your mapping configuration!", method.getName()));
		}

		final RouteSettings routeSettings = findSettingsByAlias(alias);
		final String host = routeSettings.getHost();

		String url = metadata.getUrl().replace(String.format("${%s}", alias), host);

		final HttpMethod httpMethod = metadata.getHttpMethod();
		final HttpHeaders httpHeaders = metadata.getHttpHeaders();

		final List<Pair<String, Object>> foundVars = new ArrayList<>();
		final List<Pair<String, Object>> foundParams = new ArrayList<>();
		final List<Pair<String, Object>> foundHeaders = new ArrayList<>();

		final Parameter[] parameters = method.getParameters();

		final Object body = initHttpVariables(args, parameters, foundVars, foundParams, foundHeaders);

		url = replaceHttpVariables(url, foundVars, foundParams, foundHeaders, httpHeaders);

		preHandle(args, body, httpHeaders);

		if (log.isDebugEnabled()) {
			log.debug("Execute Service Mapping request");
			log.debug("Url: {}", url);
			log.debug("Headers: {}", httpHeaders);
			if (body != null) {
				log.debug("Body: {}", body);
			}
		}

		final Object call = handleHttpCall(annotation, args, url, httpMethod, body, httpHeaders, metadata);
		postHandle(ResponseEntity.ok(call));
		return call;
	}

	private Object handleHttpCall(ServiceMapping annotation, Object[] args, String url, HttpMethod httpMethod, Object body, HttpHeaders httpHeaders, MappingMetadata metadata) throws Throwable {
		final WebClient webClient = WebClient.create(url);

		ResponseSpec responseSpec;
		final Class<?> returnType = metadata.getReturnType();
		try {
			if (body != null) {
				responseSpec = webClient
						.method(httpMethod)
						.headers(c -> c.addAll(httpHeaders))
						.body(BodyInserters.fromPublisher(Mono.just(body), Object.class))
						.retrieve();
			} else {
				responseSpec = webClient
						.method(httpMethod)
						.headers(c -> c.addAll(httpHeaders))
						.retrieve();
			}
		} catch (RestClientResponseException ex) {
			if (log.isDebugEnabled()) {
				log.debug("Error on execute route request - Code: {}, Error: {}, Route: {}", ex.getRawStatusCode(), ex.getResponseBodyAsString(), url);
			}

			final String fallbackMethod = metadata.getFallbackMethod();

			final Object target = fallbackInstances.stream()
					.filter(o ->
							o.getClass().getSimpleName().equals(annotation.fallbackClass().getSimpleName()))
					.findFirst().orElse(null);

			Method fallback = null;
			if (target != null) {
				fallback = Arrays.stream(target.getClass().getMethods())
						.filter(m -> m.getName().equals(fallbackMethod))
						.findFirst()
						.orElse(null);
			}

			if (fallback != null) {
				args = Arrays.copyOf(args, args.length + 1);
				args[args.length - 1] = ex;
				final Object result = fallback.invoke(target, args);
				return Mono.just(result);
			} else if (returnType == Mono.class) {
				return Mono.just(ResponseEntity.status(ex.getRawStatusCode()).body(ex.getResponseBodyAsString()));
			} else if (returnType == Flux.class) {
				return Flux.just(ResponseEntity.status(ex.getRawStatusCode()).body(ex.getResponseBodyAsString()));
			} else {
				return Mono.empty();
			}
		}

		final Method method = metadata.getMethod();
		final Type classType = method.getDeclaredAnnotation(Type.class);
		final Class<?> type = classType == null ? Object.class : classType.type();

		if (returnType == Mono.class) {
			return responseSpec.bodyToMono(type);
		} else if (returnType == Flux.class) {
			return responseSpec.bodyToFlux(type);
		}

		return null;
	}

	private String replaceHttpVariables(String url, final List<Pair<String, Object>> foundVars, final List<Pair<String, Object>> foundParams,
										final List<Pair<String, Object>> foundHeaders, final HttpHeaders httpHeaders) {
		for (Pair<String, Object> pair : foundVars) {
			url = url.replace(String.format("${%s}", pair.getKey()), String.valueOf(pair.getValue()));
		}

		for (Pair<String, Object> pair : foundParams) {
			url = url.replace(String.format("${%s}", pair.getKey()), String.valueOf(pair.getValue()));
		}

		foundHeaders.forEach(pair -> {
			final String headerName = pair.getKey();
			if (httpHeaders.getFirst(headerName) != null) {
				httpHeaders.set(headerName, String.valueOf(pair.getValue()));
			} else {
				log.warn("Undefined request header name '{}'! Check mapping configuration!", headerName);
			}
		});

		return url;
	}

	private Object initHttpVariables(final Object[] args, final Parameter[] parameters, final List<Pair<String, Object>> foundVars,
									 final List<Pair<String, Object>> foundParams, final List<Pair<String, Object>> foundHeaders) {
		Object body = null;
		for (int i = 0; i < parameters.length; i++) {
			final Object value = args[i];
			final Parameter parameter = parameters[i];

			final PathVariable pv = parameter.getDeclaredAnnotation(PathVariable.class);
			final RequestParam rp = parameter.getDeclaredAnnotation(RequestParam.class);
			final RequestHeader rh = parameter.getDeclaredAnnotation(RequestHeader.class);
			final RequestBody rb = parameter.getDeclaredAnnotation(RequestBody.class);

			if (rb != null) {
				body = value;
			}

			if (rh != null) {
				foundHeaders.add(new Pair<>(rh.value(), value));
			}

			if (pv != null) {
				final String name = pv.value();
				foundVars.add(new Pair<>(name, value));
			}

			if (rp != null) {
				final String name = rp.value();
				foundParams.add(new Pair<>(name, value));
			}
		}

		return body;
	}

	private void preHandle(Object[] args, Object body, HttpHeaders httpHeaders) {
		final Map<String, Interceptor> beansOfType = applicationContext.getBeansOfType(Interceptor.class);
		beansOfType.values()
				.stream()
				.filter(i ->
						i.getClass().isAnnotationPresent(RestInterceptor.class)
								&& ArrayUtils.contains(i.getClass().getDeclaredAnnotation(RestInterceptor.class).aliases(), alias))
				.forEach(i -> i.preHandle(args, body, httpHeaders));
	}

	private void postHandle(ResponseEntity<?> responseEntity) {
		final Map<String, Interceptor> beansOfType = applicationContext.getBeansOfType(Interceptor.class);
		beansOfType.values()
				.stream()
				.filter(i ->
						i.getClass().isAnnotationPresent(RestInterceptor.class)
								&& ArrayUtils.contains(i.getClass().getDeclaredAnnotation(RestInterceptor.class).aliases(), alias))
				.forEach(i -> i.postHandle(responseEntity));
	}

	private MappingMetadata findMetadataByMethodName(String methodName) {
		return metadatas
				.stream()
				.filter(m -> m.getMethodName().equals(methodName)).findFirst()
				.orElseThrow(() -> new InvocationFailureException(""));
	}

	private RouteSettings findSettingsByAlias(String alias) throws ConfigurationException {
		final ServicesConfiguration servicesConfiguration = applicationContext.getAutowireCapableBeanFactory().getBean(ServicesConfiguration.class);
		return servicesConfiguration.getRoutes()
				.stream()
				.filter(r ->
						r.getAlias().equals(alias))
				.findFirst()
				.orElseThrow(() -> new ConfigurationException("Cant find service host! Check configuration. Alias: " + alias));
	}

	@SuppressWarnings("unused")
	public Class<?> getType() {
		return type;
	}

	@SuppressWarnings("unused")
	public void setType(Class<?> type) {
		this.type = type;
	}

	@SuppressWarnings("unused")
	public List<MappingMetadata> getMetadatas() {
		return metadatas;
	}

	@SuppressWarnings("unused")
	public void setMetadatas(List<MappingMetadata> metadatas) {
		this.metadatas = metadatas;
	}

	@SuppressWarnings("unused")
	public String getAlias() {
		return alias;
	}

	@SuppressWarnings("unused")
	public void setAlias(String alias) {
		this.alias = alias;
	}

	@SuppressWarnings("unused")
	public List<Object> getFallbackInstances() {
		return fallbackInstances;
	}

	@SuppressWarnings("unused")
	public void setFallbackInstances(List<Object> fallbackInstances) {
		this.fallbackInstances = fallbackInstances;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MappingFactoryBean that = (MappingFactoryBean) o;
		return Objects.equals(type, that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type);
	}
}
