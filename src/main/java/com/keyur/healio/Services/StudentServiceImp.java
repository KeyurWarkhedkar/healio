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
import com.keyur.healio.Enums.AppointmentEventType;
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
    private final UserRepository userRepository;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher publisher;

    //injecting using dependency injection
    public StudentServiceImp(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, CustomUserDetailsService customUserDetailsService, SlotRepository slotRepository,
                             AppointmentRepository appointmentRepository, AppointmentEventPublisher publisher) {
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.publisher = publisher;
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
        newAppointment.setAppointmentStatus(AppointmentStatus.PENDING_PAYMENT);
        newAppointment.setSlot(slot);
        newAppointment.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        appointmentRepository.save(newAppointment);

        //update the slot to 'BOOKED'
        slot.setBooked(true);
        slot.setStudent(student);
        slotRepository.save(slot);

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

        //acquire slot lock first to ensure that concurrent cancellation attempts
        //are serialized. Validation after locking ensures only the first transaction passes.
        Slot slot = slotRepository.findById(appointmentToBeCancelled.getSlot().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No slot found for the current appointment"));

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
        if(appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.CANCELLED_STUDENT)
        || appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.CANCELLED_COUNSELLOR)) {
            throw new InvalidOperationException("The appointment is already cancelled");
        }

        //make the slot of the appointment available for further bookings
        slot.setStudent(null);
        slot.setBooked(false);
        slotRepository.save(slot);

        //if all the checks pass, then go ahead with cancelling the appointment
        appointmentToBeCancelled.setAppointmentStatus(AppointmentStatus.CANCELLED_STUDENT);
        appointmentToBeCancelled.setSlot(null);
        appointmentRepository.save(appointmentToBeCancelled);

        //publish the appointment booked event to the notification queue for sending the
        //email notification to the counsellor
        AppointmentEventDto event = new AppointmentEventDto();
        event.setAppointmentId(appointmentToBeCancelled.getId());
        event.setAppointmentTime(appointmentToBeCancelled.getAppointmentTime());
        event.setCounsellorEmail(appointmentToBeCancelled.getCounsellor().getEmail());
        event.setStudentEmail(appointmentToBeCancelled.getStudent().getEmail());
        event.setEventType(AppointmentEventType.CANCELLED_STUDENT);

        publisher.publishCancelled(event);

        return appointmentToBeCancelled;
    }

    //method to get all appointments of a user
    @Override
    public List<Appointment> getAllAppointments() {
        //fetch the current student from Security Context
        User student = getCurrentUser();

        //fetch the appointments of the student from db
        return appointmentRepository.findAllByStudentOrderByAppointmentTimeAsc(student);
    }
}
