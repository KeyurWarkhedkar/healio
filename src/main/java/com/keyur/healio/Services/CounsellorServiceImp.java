package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.DuplicateEmailException;
import com.keyur.healio.CustomExceptions.UserNotFoundException;
import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.UserRoles;
import com.keyur.healio.Repositories.UserRepository;
import com.keyur.healio.Security.CustomUserDetailsService;
import com.keyur.healio.Security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CounsellorServiceImp implements CounsellorService {
    //fields
    UserRepository userRepository;
    BCryptPasswordEncoder bCryptPasswordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;

    //injecting using dependency injection
    public CounsellorServiceImp(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, CustomUserDetailsService customUserDetailsService) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    //method to add a new user to the database
    @Transactional
    public User registerCounsellor(User newCounsellor) {
        //check if the user with the same email already exists in the database
        Optional<User> optionalUser = userRepository.findByEmail(newCounsellor.getEmail());
        if(optionalUser.isPresent()) {
            throw new DuplicateEmailException("Try again with different email id. A user with this email id already exists!");
        }

        //encrypt the password and set the hashed password as user's password before saving in db
        newCounsellor.setPassword(bCryptPasswordEncoder.encode(newCounsellor.getPassword()));

        //set the role of the student before saving in db
        newCounsellor.setRole(UserRoles.COUNSELLOR);

        //making use of db unique constraint as the final safety net
        try {
            userRepository.save(newCounsellor);
        } catch(DataIntegrityViolationException exception) {
            throw new DuplicateEmailException("Try again with different email id. A user with this email id already exists!");
        }

        return newCounsellor;
    }

    //method to login student
    public String loginCounsellor(CounsellorDto counsellorDto) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            counsellorDto.getEmail(),
                            counsellorDto.getPassword()
                    )
            );

            String username = authentication.getName();
            return jwtService.generateToken(username);
        } catch(AuthenticationException exception) {
            throw new BadCredentialsException("Invalid Credentials");
        }
    }
}
