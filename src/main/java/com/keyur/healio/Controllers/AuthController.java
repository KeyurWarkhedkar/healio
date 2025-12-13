package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Services.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping(value = "/auth")
public class AuthController {
    //fields
    private final AuthService authService;

    //method to receive request to register user
    @PostMapping(value = "/register/student")
    public ResponseEntity<User> registerStudent(@Valid @RequestBody User newStudent) {
        return new ResponseEntity<>(authService.registerStudent(newStudent), HttpStatus.CREATED);
    }

    //method to login student
    @PostMapping(value = "/login/student")
    public ResponseEntity<String> loginStudent(@Valid @RequestBody StudentDto studentDto) {
        return new ResponseEntity<>(authService.loginStudent(studentDto), HttpStatus.OK);
    }

    //method to receive request to register user
    @PostMapping(value = "/register/counsellor")
    public ResponseEntity<User> registerCounsellor(@Valid @RequestBody User newCounsellor) {
        return new ResponseEntity<>(authService.registerCounsellor(newCounsellor), HttpStatus.CREATED);
    }

    //method to login student
    @PostMapping(value = "/login/counsellor")
    public ResponseEntity<String> loginCounsellor(@Valid @RequestBody CounsellorDto counsellorDto) {
        return new ResponseEntity<>(authService.loginCounsellor(counsellorDto), HttpStatus.OK);
    }
}
