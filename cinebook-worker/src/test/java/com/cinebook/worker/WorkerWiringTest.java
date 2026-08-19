package com.cinebook.worker;

import com.cinebook.payment.infra.ReconcilePaymentsUseCase;
import com.cinebook.shared.outbox.OutboxRelay;
import com.cinebook.worker.support.AbstractWorkerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerWiringTest extends AbstractWorkerTest {

    @Autowired
    OutboxRelay relay;

    @Autowired
    ReconcilePaymentsUseCase reconcile;

    @Autowired
    Environment env;

    @Autowired
    ApplicationContext context;

    @Test
    void worker_chay_duoc_use_case_nghiep_vu_cua_api() {
        // Chua co gi de lam, nhung phai chay den noi tren DB that.
        assertThat(relay.relayBatch(10)).isZero();
        assertThat(reconcile.reconcile()).isZero();
    }

    /**
     * Khoa lai mot cai bay da kiem chung bang thuc nghiem: co HAI application.yml tren
     * classpath (mot cua worker, mot nam trong jar cua api), va Spring Boot chi doc mot.
     * File cua api bi bo qua HOAN TOAN, khong mot dong canh bao.
     *
     * Test nay ton tai de nguoi sau khong mat mot buoi chieu tu hoi vi sao property
     * minh khai o ben api lai khong co tac dung o worker.
     */
    @Test
    void worker_khong_thua_huong_cau_hinh_cua_api() {
        // cinebook.jwt.secret chi co trong application.yml cua api
        assertThat(env.getProperty("cinebook.jwt.secret")).isNull();
        // con cai worker tu khai thi phai co
        assertThat(env.getProperty("cinebook.payment.reconcile-after")).isEqualTo("15m");
    }

    @Test
    void worker_khong_nap_controller_nao_cua_api() {
        assertThat(context.getBeanNamesForAnnotation(RestController.class)).isEmpty();
    }
}
