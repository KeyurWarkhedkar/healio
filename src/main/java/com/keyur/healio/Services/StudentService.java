package com.keyur.healio.Services;

import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.User;

public interface StudentService {
    public User registerStudent(User newStudent);
    public String loginStudent(StudentDto studentDto);
    public Appointment bookAppointment(int slotId);
}
