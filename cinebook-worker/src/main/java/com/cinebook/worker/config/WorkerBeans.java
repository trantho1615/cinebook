package com.cinebook.worker.config;

import com.cinebook.booking.infra.ConfirmBookingUseCase;
import com.cinebook.booking.infra.SweepExpiredHoldsUseCase;
import com.cinebook.payment.infra.MockPaymentGateway;
import com.cinebook.payment.infra.ProcessPaymentUseCase;
import com.cinebook.payment.infra.ReconcilePaymentsUseCase;
import com.cinebook.payment.infra.RefundUseCase;
import com.cinebook.shared.audit.AuditLogger;
import com.cinebook.shared.outbox.LoggingEventPublisher;
import com.cinebook.shared.outbox.OutboxRelay;
import com.cinebook.shared.outbox.OutboxWriter;
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
 * LoggingEventPublisher chi la cho tam: milestone nay se thay bang KafkaEventPublisher
 * o task relay, va dong do phai bi go ra — de lai ca hai se thanh hai bean cung kieu.
 */
@Configuration
@Import({
        MockPaymentGateway.class,
        AuditLogger.class,
        OutboxWriter.class,
        LoggingEventPublisher.class,
        OutboxRelay.class,
        ConfirmBookingUseCase.class,
        SweepExpiredHoldsUseCase.class,
        RefundUseCase.class,
        ProcessPaymentUseCase.class,
        ReconcilePaymentsUseCase.class
})
public class WorkerBeans {
}
