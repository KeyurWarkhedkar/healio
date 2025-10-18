package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.DuplicateEmailException;
import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.CustomExceptions.SlotOverlapException;
import com.keyur.healio.DTOs.CounsellorDto;
import com.keyur.healio.DTOs.SlotDto;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.UserRoles;
import com.keyur.healio.Repositories.SlotRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CounsellorServiceImp implements CounsellorService {
    //fields
    UserRepository userRepository;
    BCryptPasswordEncoder bCryptPasswordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;
    private final SlotRepository slotRepository;

    //injecting using dependency injection
    public CounsellorServiceImp(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, CustomUserDetailsService customUserDetailsService,
                                SlotRepository slotRepository) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.slotRepository = slotRepository;
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

    //method for counsellors to publish their slots
    @Override
    @Transactional
    public Slot publishSlots(SlotDto slotDto) {
        //get the current counsellor from security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User counsellor = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("No counsellor found with the given email!"));

        //validate time duration of the slot
        if (!slotDto.getStartTime().isBefore(slotDto.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (slotDto.getStartTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot create a slot in the past");
        }

        //get all the slots of the current counsellor to check for overlapping slots
        List<Slot> slots = slotRepository.findByCounsellorAndStartTimeAfter(counsellor, LocalDateTime.now());

        //iterate and check for overlapping slots
        for(Slot slot : slots) {
            if(slotDto.getStartTime().isBefore(slot.getEndTime()) && slotDto.getEndTime().isAfter(slot.getStartTime())) {

                throw new SlotOverlapException("You already have a slot published in this time duration");
            }
        }

        //if the slot is valid, go ahead and store it in the db
        Slot newSlot = new Slot();
        newSlot.setCounsellor(counsellor);
        newSlot.setStartTime(slotDto.getStartTime());
        newSlot.setEndTime(slotDto.getEndTime());
        newSlot.setStudent(null);
        newSlot.setBooked(false);

        //use db unique constraint as the final safety net, in case we have a case of
        //same login from different machine. also handles the case of counsellor publishing
        //same slot again
        try {
            slotRepository.save(newSlot);
        } catch(DataIntegrityViolationException exception) {
            throw new SlotOverlapException("The slot you are trying to publish already exists!");
        }

        return newSlot;
    }
}
