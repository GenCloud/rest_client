package org.restclient.factory;

import lombok.extern.slf4j.Slf4j;
import org.restclient.annotations.Header;
import org.restclient.annotations.Mapping;
import org.restclient.annotations.ServiceMapping;
import org.restclient.model.MappingMetadata;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import javax.naming.ConfigurationException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GenCloud
 * @created: 2019/08
 */
@Slf4j
public class ServiceMappingRegistrator implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
	private ResourceLoader resourceLoader;
	private Environment environment;

	@Override
	public void setEnvironment(@NonNull Environment environment) {
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
		registerMappings(metadata, registry);
	}

	private void registerMappings(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
		final ClassPathScanningCandidateComponentProvider scanner = getScanner();
		scanner.setResourceLoader(resourceLoader);

		final Set<String> basePackages = getBasePackages(metadata);

		final AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(Mapping.class);
		scanner.addIncludeFilter(annotationTypeFilter);

		basePackages
				.stream()
				.map(scanner::findCandidateComponents)
				.flatMap(Collection::stream)
				.filter(candidateComponent -> candidateComponent instanceof AnnotatedBeanDefinition)
				.map(candidateComponent -> (AnnotatedBeanDefinition) candidateComponent)
				.map(AnnotatedBeanDefinition::getMetadata)
				.map(ClassMetadata::getClassName)
				.forEach(className -> buildGateway(className, registry));
	}

	private void buildGateway(String className, BeanDefinitionRegistry registry) {
		try {
			final Class<?> type = Class.forName(className);
			final List<Method> methods = Arrays
					.stream(type.getMethods())
					.filter(method ->
							method.isAnnotationPresent(ServiceMapping.class))
					.collect(Collectors.toList());

			final String alias = type.getDeclaredAnnotation(Mapping.class).alias();

			final List<MappingMetadata> metadatas = new ArrayList<>();

			final List<Object> fallbackInstances = new ArrayList<>();

			for (Method method : methods) {
				final ServiceMapping serviceMapping = method.getDeclaredAnnotation(ServiceMapping.class);

				final Class<?>[] args = method.getParameterTypes();

				final Header[] defaultHeaders = serviceMapping.defaultHeaders();

				final String path = serviceMapping.path();
				final HttpMethod httpMethod = serviceMapping.method();
				final HttpHeaders httpHeaders = new HttpHeaders();

				final StringBuilder url = new StringBuilder();
				url.append("${").append(alias).append("}").append(path);

				final Parameter[] parameters = method.getParameters();
				for (int i = 0; i < parameters.length; i++) {
					final Parameter parameter = parameters[i];
					for (Annotation annotation : parameter.getAnnotations()) {
						if (!checkValidParams(annotation, args)) {
							break;
						}

						if (annotation instanceof RequestParam) {
							final String argName = ((RequestParam) annotation).value();
							if (argName.isEmpty()) {
								throw new ConfigurationException("Configuration error: defined RequestParam annotation dont have value! Api method: " + method.getName() + ", Api Class: " + type);
							}

							final String toString = url.toString();
							if (toString.endsWith("&") && i + 1 == args.length) {
								url.append(argName).append("=").append("${").append(argName).append("}");
							} else if (!toString.endsWith("&") && i + 1 == args.length) {
								url.append("?").append(argName).append("=").append("${").append(argName).append("}");
							} else if (!toString.endsWith("&")) {
								url.append("?").append(argName).append("=").append("${").append(argName).append("}").append("&");
							} else {
								url.append(argName).append("=").append("${").append(argName).append("}").append("&");
							}
						} else if (annotation instanceof PathVariable) {
							final String argName = ((PathVariable) annotation).value();
							if (argName.isEmpty()) {
								throw new ConfigurationException("Configuration error: defined PathVariable annotation dont have value! Api method: " + method.getName() + ", Api Class: " + type);
							}

							final String toString = url.toString();
							final String argStr = String.format("${%s}", argName);
							if (!toString.contains(argStr)) {
								if (toString.endsWith("/")) {
									url.append(argStr);
								} else {
									url.append("/").append(argStr);
								}
							}
						} else if (annotation instanceof RequestHeader) {
							final String argName = ((RequestHeader) annotation).value();
							if (argName.isEmpty()) {
								throw new ConfigurationException("Configuration error: defined RequestHeader annotation dont have value! Api method: " + method.getName() + ", Api Class: " + type);
							}

							httpHeaders.add(argName, String.format("${%s}", argName));
						}
					}
				}

				if (defaultHeaders.length > 0) {
					Arrays.stream(defaultHeaders)
							.forEach(header -> httpHeaders.add(header.name(), header.value()));
				}

				final Object instance = serviceMapping.fallbackClass().newInstance();
				fallbackInstances.add(instance);

				final String fallbackName = serviceMapping.fallbackMethod();
				final String buildedUrl = url.toString();
				final MappingMetadata mappingMetadata = new MappingMetadata(method, httpMethod, buildedUrl, httpHeaders, fallbackName);
				metadatas.add(mappingMetadata);

				log.info("Bind api path - alias: {}, url: {}", alias, buildedUrl);
			}

			final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(MappingFactoryBean.class);
			beanDefinitionBuilder.addPropertyValue("type", className);
			beanDefinitionBuilder.addPropertyValue("alias", alias);
			beanDefinitionBuilder.addPropertyValue("metadatas", metadatas);
			beanDefinitionBuilder.addPropertyValue("fallbackInstances", fallbackInstances);

			final AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();

			final BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, new String[]{type.getSimpleName()});
			BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
		} catch (IllegalAccessException | InstantiationException | ClassNotFoundException | ConfigurationException e) {
			e.printStackTrace();
		}
	}

	private boolean checkValidParams(Annotation annotation, Object[] args) {
		Arrays
				.stream(args)
				.map(Object::getClass)
				.forEach(type -> {
					if (annotation instanceof RequestParam) {
						if (type.isAnnotationPresent(PathVariable.class)) {
							throw new IllegalArgumentException("Annotation RequestParam cannot be used with PathVariable");
						}
					} else if (annotation instanceof PathVariable) {
						if (type.isAnnotationPresent(RequestParam.class)) {
							throw new IllegalArgumentException("Annotation PathVariable cannot be used with RequestParam");
						}
					}
				});

		return true;
	}

	private Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(SpringBootApplication.class.getCanonicalName());
		if (attributes == null) {
			attributes = importingClassMetadata.getAnnotationAttributes(ComponentScan.class.getCanonicalName());
		}

		Set<String> basePackages = new HashSet<>();
		if (attributes != null) {
			basePackages = Arrays.stream((String[]) attributes.get("scanBasePackages")).filter(StringUtils::hasText).collect(Collectors.toSet());

			Arrays.stream((Class[]) attributes.get("scanBasePackageClasses")).map(ClassUtils::getPackageName).forEach(basePackages::add);
		}

		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
		}

		return basePackages;
	}

	private ClassPathScanningCandidateComponentProvider getScanner() {
		return new ClassPathScanningCandidateComponentProvider(false, environment) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				boolean isCandidate = false;
				if (beanDefinition.getMetadata().isIndependent()) {
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}
}
