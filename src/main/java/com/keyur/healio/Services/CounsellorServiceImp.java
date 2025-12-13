package com.keyur.healio.Services;

import com.keyur.healio.CustomExceptions.DuplicateEmailException;
import com.keyur.healio.CustomExceptions.InvalidOperationException;
import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.CustomExceptions.SlotOverlapException;
import com.keyur.healio.DTOs.*;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentEventType;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import com.keyur.healio.Enums.UserRoles;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.PaymentRepository;
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
    private final UserRepository userRepository;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher publisher;
    private final PaymentRepository paymentRepository;
    private final PayUServiceImp payUService;

    //injecting using dependency injection
    public CounsellorServiceImp(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, AuthenticationManager authenticationManager, JwtService jwtService, CustomUserDetailsService customUserDetailsService,
                                SlotRepository slotRepository,
                                AppointmentRepository appointmentRepository,
                                AppointmentEventPublisher publisher,
                                PaymentRepository paymentRepository,
                                PayUServiceImp payUService) {
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.publisher = publisher;
        this.paymentRepository = paymentRepository;
        this.payUService = payUService;
    }

    //method for getting the user from Security Context
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("No counsellor found with the given email!"));
    }

    //method for counsellors to publish their slots
    @Override
    @Transactional
    public Slot publishSlots(SlotDto slotDto) {
        //get the current counsellor from security context
        User counsellor = getCurrentUser();

        //validate time duration of the slot
        if(!slotDto.getStartTime().isBefore(slotDto.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if(slotDto.getStartTime().isBefore(LocalDateTime.now())) {
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
        newSlot.setPrice(100);
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

    //method to get all appointments of a counsellor
    @Override
    public List<Appointment> getAllAppointments() {
        //get the current counsellor from the security context
        User counsellor = getCurrentUser();

        //fetch all the appointments of this counsellor from appointments table
        return appointmentRepository.findAllByCounsellorOrderByAppointmentTimeAsc(counsellor);
    }

    //method to cancel slot
    @Override
    @Transactional
    public Slot removeSlot(int slotId) {
        //get the current user from the Security Context
        User counsellor = getCurrentUser();

        //fetch the slot from the db
        Slot slotToBeCancelled = slotRepository.findByIdWithLock(slotId).orElseThrow(() -> new ResourceNotFoundException("No slot exists"));

        if(slotToBeCancelled.isCancelled()) {
            throw new InvalidOperationException("Slot already cancelled");
        }

        if (slotToBeCancelled.getCounsellor().getId() != (counsellor.getId())) {
            throw new InvalidOperationException("This slot does not belong to you");
        }

        //check if the slot to be cancelled is before the current time or not
        if(slotToBeCancelled.getStartTime().isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("You cannot cancel a slot from the past");
        }

        //cancel the slot
        slotToBeCancelled.setCancelled(true);
        slotRepository.save(slotToBeCancelled);

        //check if the slot to be cancelled has any appointment scheduled
        if(slotToBeCancelled.isBooked()) {
            Appointment appointmentToBeCancelled = appointmentRepository.findBySlot(slotToBeCancelled);

            Payment paymentForAppointment = paymentRepository.findByAppointmentIdWithLock(appointmentToBeCancelled.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("No payment record found for the given appointment"));

            if(paymentForAppointment.getPaymentStatus().equals(PaymentStatus.REFUNDED)) {
                throw new InvalidOperationException("The refund for this payment is already processed!");
            }

            if(appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.CONFIRMED)
            && paymentForAppointment.getPaymentStatus().equals(PaymentStatus.SUCCESS)) {
                PayURefundResponseDto payURefundResponseDto = payUService.refund(paymentForAppointment.getGatewayPaymentId(), paymentForAppointment.getAmount());

                AppointmentEventDto event = new AppointmentEventDto();
                event.setAppointmentId(appointmentToBeCancelled.getId());
                event.setAppointmentTime(appointmentToBeCancelled.getAppointmentTime());
                event.setCounsellorEmail(appointmentToBeCancelled.getCounsellor().getEmail());
                event.setStudentEmail(appointmentToBeCancelled.getStudent().getEmail());

                //check if the refund was successful or not
                if(payURefundResponseDto.isSuccess()) {
                    paymentForAppointment.setPaymentStatus(PaymentStatus.REFUNDED);
                    event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_REFUND_SUCCESS);
                } else {
                    paymentForAppointment.setPaymentStatus(PaymentStatus.REFUND_FAILED);
                    event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_REFUND_FAILED);
                }
                paymentRepository.save(paymentForAppointment);
                publisher.publishCancelled(event);
            }
            //cancel the appointment for all the case
            appointmentToBeCancelled.setAppointmentStatus(AppointmentStatus.CANCELLED_COUNSELLOR);
            appointmentRepository.save(appointmentToBeCancelled);
        }
        return slotToBeCancelled;
    }

    //method to cancel appointment from counsellor's side
    @Override
    @Transactional
    public Appointment cancelAppointment(int appointmentId) {
        User counsellor = getCurrentUser();

        Appointment appointmentToBeCancelled = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("No appointment found"));

        //check counsellor ownership
        if(appointmentToBeCancelled.getCounsellor().getId() != counsellor.getId()) {
            throw new InvalidOperationException("You cannot cancel an appointment for another counsellor");
        }

        //acquire slot lock first to ensure that concurrent cancellation attempts
        //are serialized. Validation after locking ensures only the first transaction passes.
        Slot slot = slotRepository.findByCounsellorAndStudentAndStartTimeWithLock(
                        appointmentToBeCancelled.getCounsellor(),
                        appointmentToBeCancelled.getStudent(),
                        appointmentToBeCancelled.getAppointmentTime())
                .orElseThrow(() -> new ResourceNotFoundException("No slot found for the current appointment"));

        //check if appointment is valid
        if(appointmentToBeCancelled.getAppointmentTime().isBefore(LocalDateTime.now())
                || appointmentToBeCancelled.getAppointmentStatus() == AppointmentStatus.COMPLETED) {
            throw new InvalidOperationException("Cannot cancel a past appointment");
        }

        //check if already cancelled
        if((appointmentToBeCancelled.getAppointmentStatus() == AppointmentStatus.CANCELLED_STUDENT)
        || appointmentToBeCancelled.getAppointmentStatus() == AppointmentStatus.CANCELLED_COUNSELLOR) {
            return appointmentToBeCancelled; // idempotent: just return
        }

        //fetch the payment for which refund has to be processed
        Optional<Payment> optionalPayment = paymentRepository.findByAppointmentIdWithLock(appointmentToBeCancelled.getId());

        //publish the appointment booked event to the notification queue for sending the
        //email notification to the student about the cancellation
        AppointmentEventDto event = new AppointmentEventDto();
        event.setAppointmentId(appointmentToBeCancelled.getId());
        event.setAppointmentTime(appointmentToBeCancelled.getAppointmentTime());
        event.setCounsellorEmail(appointmentToBeCancelled.getCounsellor().getEmail());
        event.setStudentEmail(appointmentToBeCancelled.getStudent().getEmail());

        //2 cases : if the appointment was cancelled before the payment was made or after the payment was made
        if(optionalPayment.isPresent()) {
            Payment paymentForRefund = optionalPayment.get();
            if(paymentForRefund.getPaymentStatus().equals(PaymentStatus.SUCCESS)
            && appointmentToBeCancelled.getAppointmentStatus().equals(AppointmentStatus.CONFIRMED)) {
                PayURefundResponseDto payURefundResponseDto = payUService.refund(paymentForRefund.getGatewayPaymentId(), paymentForRefund.getAmount());
                if(payURefundResponseDto.isSuccess()) {
                    paymentForRefund.setPaymentStatus(PaymentStatus.REFUNDED);
                    event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_REFUND_SUCCESS);
                } else {
                    paymentForRefund.setPaymentStatus(PaymentStatus.REFUND_FAILED);
                    event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_REFUND_FAILED);
                }
            } else {
                event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_NO_REFUND);
            }
        } else {
            event.setEventType(AppointmentEventType.CANCELLED_COUNSELLOR_NO_REFUND);
        }

        //free slot
        slot.setStudent(null);
        slot.setBooked(false);
        slotRepository.save(slot);

        //cancel appointment
        appointmentToBeCancelled.setAppointmentStatus(AppointmentStatus.CANCELLED_COUNSELLOR);
        appointmentRepository.save(appointmentToBeCancelled);

        publisher.publishCancelled(event);

        return appointmentToBeCancelled;
    }
}
