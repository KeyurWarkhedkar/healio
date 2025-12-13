package com.keyur.healio.Services;

import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentCleanupScheduler {
    //fields
    private final AppointmentRepository appointmentRepository;

    //method to clear appointments whose payments are not completed within 10 minutes
    @Scheduled(cron = "0 0/10 * * * ?")
    public void deleteUnpaidAppointments() {
        // Calculate time threshold: 10 minutes ago
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        List<Appointment> oldUnpaidAppointments =
                appointmentRepository.findAllByAppointmentStatusAndCreatedAtBefore(
                        AppointmentStatus.PENDING_PAYMENT, threshold);

        if (!oldUnpaidAppointments.isEmpty()) {
            for(Appointment appointment : oldUnpaidAppointments) {
                appointment.setAppointmentStatus(AppointmentStatus.CANCELLED_EXTERNAL);
                appointmentRepository.save(appointment);
            }
        }
    }
}
