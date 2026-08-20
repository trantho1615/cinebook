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
     * Worker doc cinebook-worker.yml, KHONG doc application.yml trong jar cua api.
     *
     * Ban dau worker cung dung ten application.yml va dua vao viec file cua no dung truoc
     * tren classpath. Thu tu classpath khong phai thu duoc bao dam: mot lan chay day du
     * cua reactor da nap trung file cua api va test nay do voi
     * "expected: null but was: <gia tri mac dinh cua api>". Doi ten file cau
     * hinh khien tinh huong do khong con la kha nang nua.
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
