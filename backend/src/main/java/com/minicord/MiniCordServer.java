package com.minicord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniCordServer {

    public static void main(String[] args) {
        SpringApplication.run(MiniCordServer.class, args);
    }
}
