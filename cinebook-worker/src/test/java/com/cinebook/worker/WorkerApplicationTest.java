package com.cinebook.worker;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@SpringBootTest
class WorkerApplicationTest {

    @Test
    void spring_context_nap_duoc() {
        // Test pass khi @SpringBootTest nap context thanh cong.
        // Cac milestone sau se thay bang assertion tren scheduled job cu the.
    }

    @Test
    void worker_khong_khai_bao_endpoint_http_nghiep_vu() {
        JavaClasses workerClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cinebook.worker");

        ArchRule rule = noClasses()
                .should().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(Controller.class)
                .because("cinebook-worker chi tieu thu Kafka va chay scheduled job (spec muc 2.1)");

        rule.allowEmptyShould(true).check(workerClasses);
    }
}
