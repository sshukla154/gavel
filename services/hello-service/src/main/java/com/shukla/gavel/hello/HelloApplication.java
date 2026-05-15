package com.shukla.gavel.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for hello-service.
 *
 * <p>This is the walking-skeleton service that proves the build, deploy, and
 * observability pipeline end-to-end before any business logic is added.
 */
@SpringBootApplication
public class HelloApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments forwarded to Spring's environment
     */
    public static void main(String[] args) {
        SpringApplication.run(HelloApplication.class, args);
    }
}
