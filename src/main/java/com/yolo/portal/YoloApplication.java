package com.yolo.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class YoloApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoloApplication.class, args);
        System.out.println("==================================================");
        System.out.println(" YOLO Portal Running on: http://localhost:8080");
        System.out.println("==================================================");
    }
}