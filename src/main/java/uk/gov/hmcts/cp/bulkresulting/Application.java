package uk.gov.hmcts.cp.bulkresulting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entry point for the bulk resulting for enforcement service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    /**
     * Starts the application.
     *
     * @param args startup arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
