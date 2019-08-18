# Rest Client module

### Functional
- initializing a rest request "on one line" using an interface in the style of spring repositories;
- interceptors functionality before and after the request;
- support reactive HTTP client.

### Intro
Add Rest Client to your project. for maven projects just add this dependency:
```xml
    <repositories>
        <repository>
            <id>restclient</id>
            <url>https://raw.github.com/GenCloud/rest_client/channel/</url>
            <snapshots>
                <enabled>true</enabled>
                <updatePolicy>always</updatePolicy>
            </snapshots>
        </repository>
    </repositories>
    
    <dependencies>
        <dependency>
            <groupId>org.restclient</groupId>
            <artifactId>restclient</artifactId>
            <version>0.0.1</version>
        </dependency>
    </dependencies>
```

A typical use of IoC would be:
```java
@SpringBootApplication
public class MainTest {
    public static void main(String... args) {
        SpringApplication.run(MainTest.class, args);
    }
}
```

A component usage would be:
```java
@Mapping(alias = "github-service") //annotation of component marked is a rest client service
public interface RestGateway {
	/**
	 * Get all repos met-information by user name.
	 *
	 * @param userName - github user name
	 * @return json string
	 */
	@ServiceMapping(path = "/users/${userName}/repos", method = GET) //annotation marked is a method calling rest resource
	@Type(type = ArrayList.class)
	Mono<ArrayList> getRepos(@PathVariable("userName") String userName);
}
```

A component dependency usage would be:
```java
    @Autowired // marked field for scanner found dependency
    private RestGateway restGateway;
```

Create custom interceptor.
```java
@RestInterceptor(aliases = "github-service") // marked as intercept request for github-service
@Slf4j
public class TestInterceptor implements Interceptor {
	@Override
	public void preHandle(Object[] args, Object body, HttpHeaders headers) {
		if (log.isDebugEnabled()) {
			log.debug("Pre handling request of github-service, args: [{}], body: [{}], headers: [{}]", args, body, headers);
		}
	}

	@Override
	public void postHandle(ResponseEntity<?> responseEntity) {
		if (log.isDebugEnabled()) {
			log.debug("Post handling request of github-service, entity: [{}]", responseEntity);
		}
	}
}
```

Usage:
```java
@ScanPackage(packages = {"org.ioc.test"})
public class MainTest {
    public static void main(String... args) {
        final ConfigurableApplicationContext context = SpringApplication.run(MainTest.class, args);
        log.info("Getting RestGateway from context");
        final RestGateway restGateway = channel.getType(RestGateway.class);
        assertNotNull(restGateway);
        final Mono<ArrayList> response = restGateway.getRepos("gencloud");
        response.doOnSuccess(list -> 
                log.info("Received response: {}", list))
            .subscribe();
    }
}
```

### Contribute
Pull requests are welcomed!!

The license is [GNU GPL V3](https://www.gnu.org/licenses/gpl-3.0.html/).

This library is published as an act of giving and generosity, from developers to developers. 

_GenCloud_
