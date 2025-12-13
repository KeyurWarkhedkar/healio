package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Services.AuthService;
import com.keyur.healio.Services.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/student")
public class StudentController {
    //fields
    StudentService studentService;
    AuthService authService;

    //dependency injection
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    //method to book appointment for a student
    @PostMapping(value = "/bookAppointment/{slotId}")
    public ResponseEntity<Appointment> bookAppointment(@PathVariable int slotId) {
        return new ResponseEntity<>(studentService.bookAppointment(slotId), HttpStatus.CREATED);
    }

    //method to cancel an appointment for student
    @DeleteMapping(value = "/cancelAppointment/{appointmentId}")
    public ResponseEntity<Appointment> cancelAppointment(@PathVariable int appointmentId) {
        return new ResponseEntity<>(studentService.cancelAppointment(appointmentId), HttpStatus.NO_CONTENT);
    }

    //method to get all appointments of a student
    @GetMapping(value = "/getAllAppointments")
    public ResponseEntity<List<Appointment>> getAllAppointments() {
        return new ResponseEntity<>(studentService.getAllAppointments(), HttpStatus.OK);
    }
}
