package com.cinebook.config;

import com.cinebook.ApiApplication;
import com.cinebook.catalog.infra.DemoDataSeeder;
import com.cinebook.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hai bat bien cua ban chay that.
 *
 * Ca hai deu thuoc loai "im lang thi nguy hiem": neu hong, he thong van len binh thuong va
 * khong ai biet gi cho toi khi qua muon.
 */
class ProdConfigTest extends AbstractIntegrationTest {

    /**
     * Thieu bi mat thi PHAI chet ngay luc khoi dong.
     *
     * application.yml co gia tri mac dinh CHAY DUOC:
     *   secret: ${CINEBOOK_JWT_SECRET:doi-chuoi-nay-truoc-khi-deploy-that-va-dai-toi-thieu-32-byte}
     * Nghia la mot lan deploy quen dat bien moi truong se len hoan toan binh thuong — va bat
     * ky ai doc repo tren GitHub cung ky duoc token gia cho bat ky tai khoan nao.
     *
     * Profile prod bo gia tri mac dinh di, nen thieu bien la Spring nem loi placeholder.
     */
    @Test
    void prod_thieu_jwt_secret_thi_khong_khoi_dong() {
        // hasStackTraceContaining chu khong hasMessageContaining: Spring boc loi placeholder
        // vao trong UnsatisfiedDependencyException, nen ten bien nam o chuoi nguyen nhan chu
        // khong o thong bao ngoai cung.
        assertThatThrownBy(() -> chayVoiProfileProd(false).close())
                .hasStackTraceContaining("CINEBOOK_JWT_SECRET");
    }

    /**
     * Prod KHONG duoc nap du lieu mau.
     *
     * Mot he thong ban ve that tu tao 10 phim gia luc khoi dong la chuyen khong duoc phep
     * xay ra lan nao. DemoDataSeeder mang @Profile("demo") nen da an toan — test nay giu cho
     * ai do sau nay khong go annotation do ra.
     */
    @Test
    void prod_khong_nap_du_lieu_mau() {
        try (ConfigurableApplicationContext context = chayVoiProfileProd(true)) {
            assertThat(context.getBeanNamesForType(DemoDataSeeder.class))
                    .as("prod khong duoc co DemoDataSeeder")
                    .isEmpty();
        }
    }

    private ConfigurableApplicationContext chayVoiProfileProd(boolean coBiMat) {
        // Truyen bang THAM SO DONG LENH chu khong phai builder.properties(...).
        //
        // properties(...) di vao "default properties" — muc uu tien THAP NHAT, nen
        // application.yml de len va context van tro ve localhost:5432. Da gap: test do voi
        // "Connection to localhost:5432 refused" thay vi loi thieu bi mat.
        List<String> args = new ArrayList<>(List.of(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                // Test khong co Jaeger.
                "--management.tracing.export.enabled=false"));

        if (coBiMat) {
            args.add("--CINEBOOK_JWT_SECRET=chuoi-bi-mat-du-dai-cho-hs256-toi-thieu-32-byte");
            args.add("--CINEBOOK_WEBHOOK_SECRET=bi-mat-webhook-cho-test");
        }

        return new SpringApplicationBuilder(ApiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .run(args.toArray(String[]::new));
    }
}
