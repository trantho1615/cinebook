package com.cinebook.worker;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

// @EnableScheduling nam o SchedulingConfig chu khong o day — xem ly do trong file do.
//
// Loai UserDetailsServiceAutoConfiguration: worker khong co nguoi dung nao de xac thuc,
// nhung spring-security tren classpath khien Spring Boot tao mot user in-memory va in
// "Using generated security password: ..." ra log moi lan khoi dong. Mat khau do khong
// dung duoc vao viec gi (WorkerSecurityConfig khong bat httpBasic hay formLogin), nen no
// chi la mot dong log gay hieu nham rang dich vu nay co cho de dang nhap.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
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
