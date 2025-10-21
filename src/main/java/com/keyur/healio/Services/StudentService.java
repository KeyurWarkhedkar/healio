package com.keyur.healio.Services;

import com.keyur.healio.DTOs.AppointmentUpdateDto;
import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.User;

import java.util.List;

public interface StudentService {
    public User registerStudent(User newStudent);
    public String loginStudent(StudentDto studentDto);
    public Appointment bookAppointment(int slotId);
    public Appointment cancelAppointment(int appointmentId);
    public List<Appointment> getAllAppointments();
    public Appointment updateAppointment(AppointmentUpdateDto appointmentUpdateDto, int appointmentId);
}
