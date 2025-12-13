package com.keyur.healio.Services;

import com.keyur.healio.Configuration.RabbitConfig;
import com.keyur.healio.DTOs.AppointmentEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationConsumer {
    //fields
    private final EmailService dummyEmailService;

    //dependency injection
    public NotificationConsumer(EmailService dummyEmailService) {
        this.dummyEmailService = dummyEmailService;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFY_QUEUE)
    public void receiveNotification(AppointmentEventDto event) {
        switch (event.getEventType()) {
            case BOOKED -> handleBooking(event);
            case CANCELLED_STUDENT -> handleStudentCancellation(event);
            case CANCELLED_COUNSELLOR_REFUND_SUCCESS -> handleCounsellorCancellationWithRefundSuccess(event);
            case CANCELLED_COUNSELLOR_REFUND_FAILED -> handleCounsellorCancellationWithRefundFailed(event);
            case CANCELLED_COUNSELLOR_NO_REFUND -> handleCounsellorCancellationWithNoRefund(event);
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }
    }

    private void handleBooking(AppointmentEventDto event) {
        // Send email to counsellor notifying about new booking
        String subject = "New Appointment Booked";
        String body = String.format("Student %s booked an appointment at %s",
                event.getStudentEmail(), event.getAppointmentTime());
        dummyEmailService.sendEmail(event.getCounsellorEmail(), subject, body);
    }

    private void handleStudentCancellation(AppointmentEventDto event) {
        // Send email to counsellor notifying that student cancelled
        String subject = "Appointment Cancelled by Student";
        String body = String.format("Student %s cancelled the appointment at %s",
                event.getStudentEmail(), event.getAppointmentTime());
        dummyEmailService.sendEmail(event.getCounsellorEmail(), subject, body);
    }

    private void handleCounsellorCancellationWithRefundSuccess(AppointmentEventDto event) {
        // Send email to student notifying that counsellor cancelled and your refund is successful
        String subject = "Appointment Cancelled by Counsellor";
        String body = String.format("Counsellor cancelled your appointment at %s and your refund is successfully processed!",
                event.getAppointmentTime());
        dummyEmailService.sendEmail(event.getStudentEmail(), subject, body);
    }

    private void handleCounsellorCancellationWithRefundFailed(AppointmentEventDto event) {
        //send email to the student notifying that the counsellor cancelled and your refund is pending
        String subject = "Appointment Cancelled by Counsellor";
        String body = String.format("Counsellor cancelled your appointment at %s and your refund is pending. Our team will contact you shortly.",
                event.getAppointmentTime());
        dummyEmailService.sendEmail(event.getStudentEmail(), subject, body);
    }

    private void handleCounsellorCancellationWithNoRefund(AppointmentEventDto event) {
        //handle cancellation by counsellor when the payment was not made
        String subject = "Appointment Cancelled by Counsellor";
        String body = String.format("Counsellor cancelled your appointment at %s",
                event.getAppointmentTime());
        dummyEmailService.sendEmail(event.getStudentEmail(), subject, body);
    }
}

