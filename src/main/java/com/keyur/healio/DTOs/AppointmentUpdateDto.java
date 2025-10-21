package com.keyur.healio.DTOs;

import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentUpdateDto {
    private LocalDateTime appointmentTime;
    private AppointmentStatus appointmentStatus;
}
