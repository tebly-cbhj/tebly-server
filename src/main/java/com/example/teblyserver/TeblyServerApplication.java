package com.example.teblyserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TeblyServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeblyServerApplication.class, args);
    }

}
