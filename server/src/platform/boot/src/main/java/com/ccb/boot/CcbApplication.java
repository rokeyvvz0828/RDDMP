package com.ccb.boot;

import com.ccb.boot.persistence.BootWorkflowMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan(basePackageClasses = BootWorkflowMapper.class)
@SpringBootApplication(scanBasePackages = "com.ccb")
public class CcbApplication {
    public static void main(String[] args) {
        SpringApplication.run(CcbApplication.class, args);
    }
}
