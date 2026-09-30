package com.roboleague;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Spring wires the adapters and the use cases; the domain and application modules never see it.
 */
@SpringBootApplication
public class RoboLeagueApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoboLeagueApplication.class, args);
    }
}
