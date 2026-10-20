package aisafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Main entry point for the Aircraft & Maintenance Service microservice.
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class AircraftMaintenanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AircraftMaintenanceServiceApplication.class, args);
    }
}
