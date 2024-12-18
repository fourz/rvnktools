package org.fourz.rvnktools.announceManager.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.AnnounceRESTDebug;
import java.util.Properties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
@SpringBootApplication(scanBasePackages = "org.fourz.rvnktools.announceManager.api")
public class SpringBootServer {
    
    private ConfigurableApplicationContext applicationContext;
    private final AnnounceManager announceManager;
    private final AnnounceRESTDebug debug;

    public SpringBootServer(AnnounceManager announceManager, AnnounceRESTDebug debug) {
        this.announceManager = announceManager;
        this.debug = debug;
    }

    public void start(AnnotationConfigApplicationContext parentContext) {
        try {
            debug.debug("Initializing Spring Boot server with parent context");
            debug.debug("Parent context beans: " + String.join(", ", parentContext.getBeanDefinitionNames()));
            
            SpringApplicationBuilder builder = new SpringApplicationBuilder()
                .sources(SpringBootServer.class)
                .parent(parentContext)
                .properties(getDefaultProperties())
                .web(org.springframework.boot.WebApplicationType.SERVLET);

            debug.debug("Starting Spring Boot application context");
            applicationContext = builder.run();
            debug.debug("Active profiles: " + String.join(", ", applicationContext.getEnvironment().getActiveProfiles()));
            debug.log("Spring Boot server started successfully");
            
        } catch (Exception e) {
            debug.error("Failed to start Spring Boot server", e);
            debug.debug("Exception details: " + e.getMessage());
            if (e.getCause() != null) {
                debug.debug("Root cause: " + e.getCause().getMessage());
            }
            throw new RuntimeException("Failed to start Spring Boot server", e);
        }
    }

    private Properties getDefaultProperties() {
        Properties props = new Properties();
        props.put("server.port", "8080");
        props.put("spring.main.allow-bean-definition-overriding", "true");
        props.put("spring.main.web-application-type", "SERVLET");
        props.put("spring.main.allow-circular-references", "true");
        props.put("spring.autoconfigure.exclude", "");
        props.put("spring.boot.admin.client.enabled", "false");
        return props;
    }

    public void stop() {
        if (applicationContext != null) {
            SpringApplication.exit(applicationContext, () -> 0);
            debug.log("Spring Boot server stopped successfully");
        }
    }
}
