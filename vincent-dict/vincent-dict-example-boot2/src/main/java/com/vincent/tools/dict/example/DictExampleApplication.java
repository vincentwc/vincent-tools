package com.vincent.tools.dict.example;

import com.vincent.tools.host.TenantProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@SpringBootApplication
public class DictExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(DictExampleApplication.class, args);
    }

    @Bean
    TenantProvider tenantProvider() {
        return () -> Optional.of("tenant-a");
    }
}
