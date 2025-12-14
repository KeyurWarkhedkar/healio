package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.PaymentRepository;
import com.keyur.healio.Repositories.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppointmentCleanupScheduler {
    //fields
    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final PaymentRepository paymentRepository;

    // Run every minute (or 10 min) to clean up unpaid appointments older than 10 minutes
    @Scheduled(cron = "0 0/1 * * * ?") // adjust schedule as needed
    @Transactional
    public void deleteUnpaidAppointments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        // Fetch appointments with PENDING_PAYMENT older than threshold, with row-level lock
        List<Appointment> oldUnpaidAppointments =
                appointmentRepository.findAllByAppointmentStatusAndCreatedAtBefore(
                        AppointmentStatus.PENDING_PAYMENT, threshold
                );

        for (Appointment appointment : oldUnpaidAppointments) {

            // double-check status in case payment succeeded just now
            if (appointment.getAppointmentStatus() != AppointmentStatus.PENDING_PAYMENT) {
                continue; // skip, payment completed concurrently
            }

            // mark appointment as cancelled
            appointment.setAppointmentStatus(AppointmentStatus.CANCELLED_EXTERNAL);
            appointmentRepository.save(appointment);

            // free the slot safely
            Slot slot = slotRepository.findById(appointment.getSlot().getId())
                    .orElseThrow(() -> new RuntimeException("Slot not found"));
            slot.setBooked(false);
            slot.setStudent(null);
            slotRepository.save(slot);

            //make the payment as failed
            Optional<Payment> payment = paymentRepository.findByAppointmentId(appointment.getId());
            if(payment.isPresent()) {
                payment.get().setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment.get());
            }
        }
    }
}
