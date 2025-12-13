package com.keyur.healio.Services;

import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.User;

public interface AuthService {
    public User registerStudent(User newStudent);
    public String loginStudent(StudentDto studentDto);
    public User registerCounsellor(User newCounsellor);
    public String loginCounsellor(CounsellorDto counsellorDto);
}
