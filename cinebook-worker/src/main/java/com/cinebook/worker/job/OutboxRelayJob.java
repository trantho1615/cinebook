package com.cinebook.worker.job;

import com.cinebook.shared.outbox.OutboxRelay;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KHONG co @SchedulerLock, va do la chu y.
 *
 * OutboxRelay dung FOR UPDATE SKIP LOCKED: nhieu instance chay song song thi moi instance
 * nhan mot tap dong roi nhau — da kiem chung bang hai phien psql o Milestone 5. Boc
 * ShedLock len day la tu tay bien mot thiet ke scale ngang thanh mot-instance.
 * ScheduledJobPolicyTest ep dieu nay o tang build.
 */
@Component
public class OutboxRelayJob {

    private final OutboxRelay outboxRelay;

    public OutboxRelayJob(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Scheduled(fixedDelayString = "${cinebook.worker.relay-interval}")
    public void relay() {
        outboxRelay.relayBatch(100);
    }
}
