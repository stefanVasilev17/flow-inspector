package com.flowinspector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FlowInspectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowInspectorApplication.class, args);
    }
}
