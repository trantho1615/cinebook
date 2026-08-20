package com.cinebook.worker;

import com.cinebook.worker.support.AbstractWorkerTest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class WorkerApplicationTest extends AbstractWorkerTest {

    @Test
    void spring_context_nap_duoc() {
        // Test pass khi context nap thanh cong. Tu Milestone 6, context nay gom ca
        // cac use-case cua cinebook-api ma WorkerBeans kich hoat.
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
