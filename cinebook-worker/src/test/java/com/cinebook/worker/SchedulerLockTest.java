package com.cinebook.worker;

import com.cinebook.worker.job.SweeperJob;
import com.cinebook.worker.support.AbstractWorkerTest;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLockTest extends AbstractWorkerTest {

    private static final String TEN_KHOA = "sweepExpiredHolds";

    @Autowired
    LockProvider lockProvider;

    @Autowired
    SweeperJob sweeperJob;

    @Autowired
    JdbcTemplate db;

    /**
     * Moi test bat dau voi khoa tu do.
     *
     * "now() AT TIME ZONE 'UTC'" chu khong phai "now()", va do la mot cai bay da lam hai
     * test nay xanh vi ly do sai: cot lock_until kieu timestamp KHONG mang mui gio, ShedLock
     * voi usingDbTime() ghi vao do theo UTC, con now() cua Postgres tra ve gio phien (+07).
     * Ghi bang now() la day lock_until len bay tieng trong tuong lai, khoa trong nhu luon bi
     * giu, va moi khang dinh phia sau deu vo nghia. Quan sat duoc bang cach in ca hai cot:
     * lock_until = 00:51:21 trong khi locked_at = 17:52:21 cua cung mot lan chay.
     *
     * Day lock_until ve qua khu chu khong DELETE dong khoa: README cua ShedLock canh bao
     * provider co cache trong bo nho ve cac dong da ton tai, nen dong bi xoa se khong duoc
     * tao lai cho toi khi khoi dong lai ung dung.
     */
    @BeforeEach
    void nhaKhoa() {
        db.update("""
                UPDATE shedlock SET lock_until = (now() AT TIME ZONE 'UTC') - interval '1 minute'
                 WHERE name = ?
                """, TEN_KHOA);
    }

    /**
     * Hai nua cua cung mot su that, va phai co ca hai thi khang dinh moi co rang:
     *
     * (a) khoa tu do  -> job chay VA chiem khoa, nen dong khoa doi;
     * (b) khoa bi giu -> job bi bo qua, nen dong khoa khong doi mot chut nao.
     *
     * Chi khang dinh (b) thi test van xanh ca khi @SchedulerLock bi go han ra — luc do job
     * chay tu do nhung cung khong dong vao bang shedlock. Da kiem chung dung tinh huong do.
     */
    @Test
    void job_chay_duoi_khoa_va_bi_bo_qua_khi_instance_khac_dang_giu() {
        // (a)
        sweeperJob.sweep();
        List<Map<String, Object>> sauLanChay = docDongKhoa();
        assertThat(sauLanChay).hasSize(1);

        nhaKhoa();
        Optional<SimpleLock> khoa = giuKhoa();
        assertThat(khoa).isPresent();

        try {
            // (b)
            List<Map<String, Object>> truoc = docDongKhoa();
            sweeperJob.sweep();
            assertThat(docDongKhoa()).isEqualTo(truoc);
        } finally {
            khoa.get().unlock();
        }
    }

    /**
     * lockAtLeastFor giu khoa them mot khoang sau khi job chay xong. Neu job xong trong
     * 20 ms va khoa nha ngay, mot instance khac voi dong ho nhanh hon co the chay lai gan
     * nhu tuc thi — dung thu ma sweeper khong duoc phep lam.
     */
    @Test
    void khoa_van_duoc_giu_them_mot_khoang_sau_khi_job_chay_xong() {
        sweeperJob.sweep();

        assertThat(giuKhoa()).isEmpty();
    }

    private Optional<SimpleLock> giuKhoa() {
        return lockProvider.lock(new LockConfiguration(
                Instant.now(), TEN_KHOA, Duration.ofMinutes(5), Duration.ZERO));
    }

    private List<Map<String, Object>> docDongKhoa() {
        return db.queryForList(
                "SELECT locked_at, locked_by, lock_until FROM shedlock WHERE name = ?", TEN_KHOA);
    }
}
