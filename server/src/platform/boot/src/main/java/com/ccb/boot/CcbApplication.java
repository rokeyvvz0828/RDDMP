package com.ccb.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ccb")
public class CcbApplication {
    public static void main(String[] args) {
        SpringApplication.run(CcbApplication.class, args);
    }
}
