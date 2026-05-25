package com.example.featurestore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FeatureStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatureStoreApplication.class, args);
    }
}
