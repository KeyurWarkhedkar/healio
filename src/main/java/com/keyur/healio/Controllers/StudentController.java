package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Services.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/student")
public class StudentController {
    //fields
    StudentService studentService;

    //dependency injection
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    //method to receive request to register user
    @PostMapping(value = "/register")
    public ResponseEntity<User> registerStudent(@Valid @RequestBody User newStudent) {
        return new ResponseEntity<>(studentService.registerStudent(newStudent), HttpStatus.CREATED);
    }

    //method to login student
    @PostMapping(value = "/login")
    public ResponseEntity<String> loginStudent(@Valid @RequestBody StudentDto studentDto) {
        return new ResponseEntity<>(studentService.loginStudent(studentDto), HttpStatus.ACCEPTED);
    }
}
