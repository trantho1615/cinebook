package com.cinebook.worker.job;

import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SweeperJob {

    private static final Logger log = LoggerFactory.getLogger(SweeperJob.class);

    private final SweepExpiredHoldsUseCase useCase;

    public SweeperJob(SweepExpiredHoldsUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * lockAtLeastFor khac 0 la co chu y: neu job chay xong trong 20 ms, khoa nha ngay va
     * mot instance khac voi dong ho nhanh hon co the chay lai gan nhu tuc thi. Giu khoa
     * toi thieu 10 giay chan hien tuong do.
     */
    @Scheduled(fixedDelayString = "${cinebook.worker.sweep-interval}")
    @SchedulerLock(name = "sweepExpiredHolds", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void sweep() {
        int soGhe = useCase.sweep();
        if (soGhe > 0) {
            log.info("Sweeper nha {} ghe het han", soGhe);
        }
    }
}
