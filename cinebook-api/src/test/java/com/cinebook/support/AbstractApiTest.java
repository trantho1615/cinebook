package com.cinebook.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Lop cha cho test goi HTTP that. RestClient duoc cau hinh KHONG nem exception
 * khi gap 4xx/5xx, de test tu khang dinh ma trang thai thay vi phai bat exception.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Cong quan tri ngau nhien: application.yml ghim 8090, ma test khong duoc gianh cong
        // voi ung dung that dang chay tren may lap trinh vien.
        properties = {
                "management.server.port=0",
                // Bang gia duoc cache co TTL (xem PriceQueryJpa). Mac dinh 10 giay la hop ly
                // cho ban chay that nhung se lam PriceTableTest phai cho 10 giay.
                "cinebook.catalog.price-cache-ttl=PT2S",
                // Test khong co Jaeger: khong tat thi moi lan chay lai co mot chuoi loi
                // xuat trace that bai, cham va lam nhieu log.
                "management.tracing.export.enabled=false"
        })
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    /** Tu Milestone 8, actuator nam tren cong rieng chu khong tren cong nghiep vu. */
    @LocalManagementPort
    protected int congQuanTri;

    public RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                    // Khong lam gi: de response 4xx/5xx di tiep toi test.
                })
                .build();
    }
}
