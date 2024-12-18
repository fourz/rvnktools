package org.fourz.rvnktools.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.fourz.rvnktools.announceManager.AnnounceManager;

@SpringBootApplication(scanBasePackages = "org.fourz.rvnktools")
public class SpringBootServer extends SpringBootServletInitializer {
    private ConfigurableApplicationContext context;
    private final AnnounceManager announceManager;

    public SpringBootServer(AnnounceManager announceManager) {
        this.announceManager = announceManager;
    }

    public void start() {
        try {
            SpringApplication app = new SpringApplication(SpringBootServer.class);
            app.setDefaultProperties(java.util.Collections.singletonMap("server.port", "8080"));
            context = app.run();
            context.getBeanFactory().registerResolvableDependency(AnnounceManager.class, announceManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (context != null) {
            SpringApplication.exit(context, () -> 0);
        }
    }
}
