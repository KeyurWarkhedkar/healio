package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Services.CounsellorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/counsellor")
public class CounsellorController {
    //fields
    CounsellorService counsellorService;

    //dependency injection
    public CounsellorController(CounsellorService counsellorService) {
        this.counsellorService = counsellorService;
    }

    //method to receive request to register user
    @PostMapping(value = "/register")
    public ResponseEntity<User> registerCounsellor(@Valid @RequestBody User newCounsellor) {
        return new ResponseEntity<>(counsellorService.registerCounsellor(newCounsellor), HttpStatus.CREATED);
    }

    //method to login student
    @PostMapping(value = "/login")
    public ResponseEntity<String> loginCounsellor(@Valid @RequestBody CounsellorDto counsellorDto) {
        return new ResponseEntity<>(counsellorService.loginCounsellor(counsellorDto), HttpStatus.ACCEPTED);
    }
}
