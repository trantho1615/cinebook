package com.cinebook.worker.job;

import com.cinebook.payment.infra.ReconcilePaymentsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Xu ly tinh huong "webhook khong bao gio den".
 *
 * Co khoa: hai instance cung doi soat se cung goi cong thanh toan va cung xu ly ket qua
 * cho mot giao dich.
 */
@Component
public class ReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileJob.class);

    private final ReconcilePaymentsUseCase useCase;

    public ReconcileJob(ReconcilePaymentsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${cinebook.worker.reconcile-interval}")
    @SchedulerLock(name = "reconcilePayments", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void reconcile() {
        int soDoiSoat = useCase.reconcile();
        if (soDoiSoat > 0) {
            log.info("Doi soat {} giao dich treo", soDoiSoat);
        }
    }
}
