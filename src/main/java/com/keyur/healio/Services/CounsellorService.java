package com.keyur.healio.Services;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.Entities.User;

public interface CounsellorService {
    public User registerCounsellor(User newCounsellor);
    public String loginCounsellor(CounsellorDto counsellorDto);
}
