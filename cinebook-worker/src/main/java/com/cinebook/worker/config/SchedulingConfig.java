package com.cinebook.worker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bat lich chay job — TRU khi dang chay test.
 *
 * Vi sao tach ra khoi WorkerApplication: de nguyen @EnableScheduling thi trong moi test
 * cua worker, scheduler khoi dong cung context va chay SweeperJob ngay lap tuc. Job do
 * chiem khoa ShedLock va giu toi thieu 10 giay (lockAtLeastFor), nen SchedulerLockTest
 * khong bao gio giu duoc khoa de kiem tra dieu no muon kiem tra. Da gap dung tinh huong
 * do: ca hai test do voi "Expecting Optional to contain a value but it was empty".
 *
 * Aspect cua @EnableSchedulerLock KHONG phu thuoc annotation nay, nen test van goi truc
 * tiep vao job qua proxy va khoa van co hieu luc.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
