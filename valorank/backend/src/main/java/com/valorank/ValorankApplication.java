package com.valorank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ValorankApplication {
    public static void main(String[] args) {
        SpringApplication.run(ValorankApplication.class, args);
    }
}
