package com.keyur.healio.Controllers;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.SlotDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Services.AuthService;
import com.keyur.healio.Services.CounsellorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/counsellor")
public class CounsellorController {
    //fields
    CounsellorService counsellorService;
    AuthService authService;

    //dependency injection
    public CounsellorController(CounsellorService counsellorService) {
        this.counsellorService = counsellorService;
    }

    //method to add slot for counsellor
    @PostMapping(value = "/publishSlot")
    public ResponseEntity<Slot> publishSlot(@Valid @RequestBody SlotDto slotDto) {
        return new ResponseEntity<>(counsellorService.publishSlots(slotDto), HttpStatus.CREATED);
    }

    //method to add slot for counsellor
    @DeleteMapping(value = "/removeSlot/{slotId}")
    public ResponseEntity<Slot> removeSlot(@PathVariable int slotId) {
        return new ResponseEntity<>(counsellorService.removeSlot(slotId), HttpStatus.NO_CONTENT);
    }

    //method to add slot for counsellor
    @GetMapping(value = "/getAllAppointments")
    public ResponseEntity<List<Appointment>> publishSlot() {
        return new ResponseEntity<>(counsellorService.getAllAppointments(), HttpStatus.OK);
    }
}
