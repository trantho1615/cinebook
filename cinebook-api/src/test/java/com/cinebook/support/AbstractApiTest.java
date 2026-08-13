package com.cinebook.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Lop cha cho test goi HTTP that. RestClient duoc cau hinh KHONG nem exception
 * khi gap 4xx/5xx, de test tu khang dinh ma trang thai thay vi phai bat exception.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    protected RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                    // Khong lam gi: de response 4xx/5xx di tiep toi test.
                })
                .build();
    }
}
