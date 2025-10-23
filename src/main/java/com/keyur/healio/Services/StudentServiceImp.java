package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.DuplicateEmailException;
import com.keyur.healio.CustomExceptions.InvalidOperationException;
import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.CustomExceptions.SlotAlreadyBookedException;
import com.keyur.healio.DTOs.AppointmentEventDto;
import com.keyur.healio.DTOs.AppointmentUpdateDto;
import com.keyur.healio.DTOs.StudentDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.UserRoles;
import com.keyur.healio.Repositories.AppointmentRepository;
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
public class StudentServiceImp implements StudentService {
    //fields
    UserRepository userRepository;
    BCryptPasswordEncoder bCryptPasswordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;
    SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    AppointmentEventPublisher publisher;

    //injecting using dependency injection
    public StudentServiceImp(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, CustomUserDetailsService customUserDetailsService, SlotRepository slotRepository,
                             AppointmentRepository appointmentRepository, AppointmentEventPublisher publisher) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.publisher = publisher;
    }

    //method to add a new user to the database
    @Transactional
    public User registerStudent(User newStudent) {
        //check if the user with the same email already exists in the database
        Optional<User> optionalUser = userRepository.findByEmail(newStudent.getEmail());
        if(optionalUser.isPresent()) {
            throw new DuplicateEmailException("Try again with different email id. A user with this email id already exists!");
        }

        //encrypt the password and set the hashed password as user's password before saving in db
        newStudent.setPassword(bCryptPasswordEncoder.encode(newStudent.getPassword()));

        //set the role of the student before saving in db
        newStudent.setRole(UserRoles.STUDENT);

        //making use of db unique constraint as the final safety net
        try {
            userRepository.save(newStudent);
        } catch(DataIntegrityViolationException exception) {
            throw new DuplicateEmailException("Try again with different email id. A user with this email id already exists!");
        }

        return newStudent;
    }

    //method to login student
    public String loginStudent(StudentDto studentDto) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            studentDto.getEmail(),
                            studentDto.getPassword()
                    )
            );

            String username = authentication.getName();
            return jwtService.generateToken(username);
        } catch(AuthenticationException exception) {
            throw new BadCredentialsException("Invalid Credentials");
        }
    }

    //method for getting the user from Security Context
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("No counsellor found with the given email!"));
    }

    //method to book counselling appointment for student
    @Override
    @Transactional
    public Appointment bookAppointment(int slotId) {
        //get the slot from db with a lock to avoid race conditions in a concurrent environment
        Slot slot = slotRepository.findByIdWithLock(slotId).orElseThrow(() -> new ResourceNotFoundException("The slot you are trying to book is invalid!"));

        //check if the slot is cancelled by the counsellor
        if(slot.isCancelled()) {
            throw new InvalidOperationException("The slot you are trying to book is cancelled by the counsellor");
        }

        //check if the slot is already booked by another student
        if(slot.isBooked()) {
            throw new SlotAlreadyBookedException("The slot you chose is already booked. Try again with a different slot");
        }

        //check if the user is trying to book a slot in the past
        if(slot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("Cannot book a slot in the past");
        }

        //fetch the current user from security context
        User student = getCurrentUser();

        //if all these checks pass, go ahead with creating an appointment
        Appointment newAppointment = new Appointment();
        newAppointment.setStudent(student);
        newAppointment.setCounsellor(slot.getCounsellor());
        newAppointment.setAppointmentTime(slot.getStartTime());
        newAppointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        newAppointment.setSlot(slot);
        appointmentRepository.save(newAppointment);

        //update the slot to 'BOOKED'
        slot.setBooked(true);
        slot.setStudent(student);
        slotRepository.save(slot);

        //publish the appointment booked event to the notification queue for sending the
        //email notification to the counsellor
        AppointmentEventDto event = new AppointmentEventDto();
        event.setAppointmentId(newAppointment.getId());
        event.setAppointmentTime(newAppointment.getAppointmentTime());
        event.setAppointmentStatus(AppointmentStatus.CONFIRMED.toString());
        event.setCounsellorEmail(newAppointment.getCounsellor().getEmail());
        event.setStudentEmail(newAppointment.getStudent().getEmail());

        publisher.publishBooked(event);

        return newAppointment;
    }

    //method to cancel an appointment
    @Override
    @Transactional
    public Appointment cancelAppointment(int appointmentId) {
        //fetch the current user from Security Context
        User student = getCurrentUser();

        //check if the appointment exists in the db or not
        Appointment appointmentToBeCancelled = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No appointment found"));

        //make the slot of the appointment available for further bookings
        Slot slot = slotRepository.findByCounsellorAndStudentAndStartTimeWithLock(appointmentToBeCancelled.getCounsellor(),
                appointmentToBeCancelled.getStudent(), appointmentToBeCancelled.getAppointmentTime())
                .orElseThrow(() -> new ResourceNotFoundException("No slot found for the current appointment"));

        slot.setStudent(null);
        slot.setBooked(false);
        slotRepository.save(slot);

        //check if the appointment belongs to the student trying to cancel it
        if(!(appointmentToBeCancelled.getStudent().getId() == student.getId())) {
            throw new InvalidOperationException("You cannot cancel appointment of some other student");
        }

        //check if the appointment to be cancelled is valid or not
        if(appointmentToBeCancelled.getAppointmentTime().isBefore(LocalDateTime.now())
                || appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.COMPLETED)) {
            throw new InvalidOperationException("Cannot cancel a past appointment");
        }

        //check if the appointment is already cancelled
        if(appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.CANCELLED)) {
            throw new InvalidOperationException("The appointment is already cancelled");
        }


        //if all the checks pass, then go ahead with cancelling the appointment
        appointmentToBeCancelled.setAppointmentStatus(AppointmentStatus.CANCELLED);
        appointmentToBeCancelled.setSlot(null);
        return appointmentRepository.save(appointmentToBeCancelled);
    }

    //method to get all appointments of a user
    @Override
    public List<Appointment> getAllAppointments() {
        //fetch the current student from Security Context
        User student = getCurrentUser();

        //fetch the appointments of the student from db
        return appointmentRepository.findAllByStudentOrderByAppointmentTimeAsc(student);
    }

    //method to update the appointment from student's side
    @Override
    @Transactional
    public Appointment updateAppointment(AppointmentUpdateDto appointmentUpdateDto, int appointmentId) {
        //get the current user from Security Context
        User student = getCurrentUser();

        //fetch the appointment to be updated from the db
        Appointment appointmentToBeUpdated = appointmentRepository.findById(appointmentId).orElseThrow(() -> new ResourceNotFoundException("No appointment with the given id found"));

        //check if the counsellor is trying to modify his own appointment or not
        if(appointmentToBeUpdated.getStudent().getId() != student.getId()) {
            throw new InvalidOperationException("You cannot update other student's appointment");
        }

        //if all the checks pass, proceed with the update
        if(appointmentUpdateDto.getAppointmentTime() != null) {
            appointmentToBeUpdated.setAppointmentTime(appointmentUpdateDto.getAppointmentTime());
        }

        //this save can cause an optimistic lock exception due to concurrent modifications. handled in GlobalExceptionHandler.
        return appointmentRepository.save(appointmentToBeUpdated);
    }
}
