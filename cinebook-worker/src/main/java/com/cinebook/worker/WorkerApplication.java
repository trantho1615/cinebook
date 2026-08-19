package com.cinebook.worker;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

// @EnableScheduling nam o SchedulingConfig chu khong o day — xem ly do trong file do.
@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        // spring.config.name doi ten file cau hinh tu "application" thanh "cinebook-worker".
        // Bat buoc: jar cua cinebook-api tren classpath cung mang mot application.yml, va de
        // hai file cung ten thi file nao thang phu thuoc thu tu classpath.
        new SpringApplicationBuilder(WorkerApplication.class)
                .properties("spring.config.name=cinebook-worker")
                .run(args);
    }
}
