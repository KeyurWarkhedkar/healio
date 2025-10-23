package com.keyur.healio.DTOs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEventDto {
    private int appointmentId;
    private String studentEmail;
    private String counsellorEmail;
    private String appointmentStatus;
    private LocalDateTime appointmentTime;
}
