package com.cinebook.worker;

import com.cinebook.worker.job.OutboxRelayJob;
import com.cinebook.worker.job.ReconcileJob;
import com.cinebook.worker.job.SweeperJob;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moi job dinh ky deu phai co khoa — TRU relay.
 *
 * Relay dung FOR UPDATE SKIP LOCKED: nhieu instance chay song song la TINH NANG, moi
 * instance nhan mot tap dong roi nhau (da kiem chung bang hai phien psql o Milestone 5).
 * Boc ShedLock len relay la tu tay bien mot thiet ke scale ngang thanh mot-instance.
 *
 * Test khong can Spring context: no doc annotation, khong chay job.
 */
class ScheduledJobPolicyTest {

    private static final List<Class<?>> JOBS =
            List.of(SweeperJob.class, OutboxRelayJob.class, ReconcileJob.class);

    /** Job co y KHONG khoa, kem ly do phai viet ra thanh loi. */
    private static final List<String> MIEN_TRU = List.of("relay");

    @Test
    void job_dinh_ky_phai_co_khoa_tru_relay() {
        int soJobDaXet = 0;

        for (Class<?> jobClass : JOBS) {
            for (Method method : jobClass.getDeclaredMethods()) {
                if (method.getAnnotation(Scheduled.class) == null) {
                    continue;
                }
                soJobDaXet++;
                boolean coKhoa = method.getAnnotation(SchedulerLock.class) != null;

                if (MIEN_TRU.contains(method.getName())) {
                    assertThat(coKhoa)
                            .as("%s.%s co y KHONG khoa vi da co FOR UPDATE SKIP LOCKED",
                                    jobClass.getSimpleName(), method.getName())
                            .isFalse();
                } else {
                    assertThat(coKhoa)
                            .as("%s.%s thieu @SchedulerLock — hai instance se cung chay",
                                    jobClass.getSimpleName(), method.getName())
                            .isTrue();
                }
            }
        }

        // Neu mot ngay nao do co nguoi doi ten method hay go @Scheduled, con so nay tut
        // xuong va test do — thay vi im lang khong xet gi ca.
        assertThat(soJobDaXet).isEqualTo(JOBS.size());
    }
}
