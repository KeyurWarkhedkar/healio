package com.keyur.healio.Services;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.SlotDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;

import java.util.List;

public interface CounsellorService {
    public User registerCounsellor(User newCounsellor);
    public String loginCounsellor(CounsellorDto counsellorDto);
    public Slot publishSlots(SlotDto slotDto);
    public List<Appointment> getAllAppointments();
}
