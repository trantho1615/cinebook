package com.cinebook.worker.config;

import com.cinebook.booking.infra.BookingQueryJdbc;
import com.cinebook.booking.infra.ConfirmBookingUseCase;
import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import com.cinebook.identity.infra.UserQueryJdbc;
import com.cinebook.notification.infra.LoggingEmailSender;
import com.cinebook.notification.infra.SendBookingConfirmedUseCase;
import com.cinebook.payment.infra.MockPaymentGateway;
import com.cinebook.payment.infra.ProcessPaymentUseCase;
import com.cinebook.payment.infra.ReconcilePaymentsUseCase;
import com.cinebook.payment.infra.RefundUseCase;
import com.cinebook.shared.audit.AuditLogger;
import com.cinebook.shared.outbox.OutboxRelay;
import com.cinebook.shared.outbox.OutboxWriter;
import com.cinebook.shared.realtime.SeatMapChannel;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Danh sach nhung use-case cua cinebook-api ma worker kich hoat. Doc file nay la biet
 * worker chay nhung gi.
 *
 * Vi sao khong @ComponentScan("com.cinebook"): quet ca cay se keo theo controller,
 * SecurityConfig, filter JWT va toan bo chuoi phu thuoc cua chung vao mot tien trinh
 * khong phuc vu mot HTTP request nghiep vu nao — va lam rong nghia luat ArchUnit
 * "worker khong khai bao endpoint". Liet ke ton chin dong va mua lai quyen kiem soat.
 *
 * Chuoi phu thuoc: ReconcilePaymentsUseCase -> ProcessPaymentUseCase -> {
 * ConfirmBookingUseCase -> {AuditLogger, OutboxWriter}, RefundUseCase }, cong
 * MockPaymentGateway cho ca hai. Bo mot dong la context chet ngay luc khoi dong.
 *
 * KHONG @Import LoggingEventPublisher: worker dung KafkaEventPublisher (mot @Component
 * trong chinh package cua worker nen duoc quet binh thuong). De ca hai la co hai bean
 * cung kieu EventPublisher va context khong biet chon cai nao.
 */
@Configuration
@Import({
        MockPaymentGateway.class,
        AuditLogger.class,
        OutboxWriter.class,
        OutboxRelay.class,
        ConfirmBookingUseCase.class,
        SweepExpiredHoldsUseCase.class,
        // Sweeper bao so do ghe doi qua Redis pub/sub; cinebook-api dang giu WebSocket
        // se day xuong client. Worker KHONG @Import RedisSeatMapSubscriber: no khong
        // phuc vu WebSocket cho ai ca.
        SeatMapChannel.class,
        RefundUseCase.class,
        ProcessPaymentUseCase.class,
        ReconcilePaymentsUseCase.class,
        // Chuoi cho BookingEventListener
        BookingQueryJdbc.class,
        UserQueryJdbc.class,
        LoggingEmailSender.class,
        SendBookingConfirmedUseCase.class
})
public class WorkerBeans {
}
