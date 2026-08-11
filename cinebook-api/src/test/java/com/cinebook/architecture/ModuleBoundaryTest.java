package com.cinebook.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleBoundaryTest {

    private static final String[] MODULES = {
            "identity", "catalog", "booking", "payment", "notification"
    };

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.cinebook");

    @Test
    void module_chi_duoc_phu_thuoc_vao_package_api_cua_module_khac() {
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.cinebook." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.cinebook." + module + ".domain..",
                            "com.cinebook." + module + ".infra..",
                            "com.cinebook." + module + ".web..")
                    .because("chi package com.cinebook." + module + ".api duoc phep lo ra ngoai");

            rule.allowEmptyShould(true).check(productionClasses);
        }
    }

    @Test
    void luat_ranh_gioi_that_su_bat_loi_khi_co_vi_pham() {
        JavaClasses fixture = new ClassFileImporter()
                .importPackages("com.cinebook.archfixture");

        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.cinebook.archfixture.booking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.cinebook.archfixture.booking.domain..");

        assertThatThrownBy(() -> rule.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PaymentReachesIntoBookingDomain");
    }
}
