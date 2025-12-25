package com.keyur.healio.ServiceTests;

import com.keyur.healio.CustomExceptions.InvalidOperationException;
import com.keyur.healio.CustomExceptions.SlotOverlapException;
import com.keyur.healio.DTOs.PayURefundResponseDto;
import com.keyur.healio.DTOs.SlotDto;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Payment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.PaymentRepository;
import com.keyur.healio.Repositories.SlotRepository;
import com.keyur.healio.Repositories.UserRepository;
import com.keyur.healio.Services.AppointmentEventPublisher;
import com.keyur.healio.Services.CounsellorServiceImp;
import com.keyur.healio.Services.PayUServiceImp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CounsellorServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    AppointmentRepository appointmentRepository;

    @Mock
    SlotRepository slotRepository;

    @Mock
    AppointmentEventPublisher publisher;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    PayUServiceImp payUServiceImp;

    @InjectMocks
    CounsellorServiceImp counsellorServiceImp;

    //helper method to mock authentication
    void mockAuthentication() {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("counsellor@example.com");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void shouldPublishSlot() {
        //arrange
        SlotDto slotDto = new SlotDto();
        slotDto.setStartTime(LocalDateTime.now().plusMinutes(30));
        slotDto.setEndTime(LocalDateTime.now().plusHours(1));

        User counsellor = new User();

        mockAuthentication();
        when(userRepository.findByEmail("counsellor@example.com")).thenReturn(Optional.of(counsellor));

        //act
        Slot slot = counsellorServiceImp.publishSlots(slotDto);

        //assert and verify
        assertFalse(slot.isBooked());
        assertFalse(slot.isCancelled());

        verify(slotRepository).save(slot);
    }

    @Test
    void shouldThrowExceptionIfThereAreOverlappingSlot() {
        //arrange
        User counsellor = new User();

        LocalDateTime existingStart = LocalDateTime.of(2025, 12, 30, 10, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2025, 12, 30, 11, 0);

        Slot existingSlot = new Slot();
        existingSlot.setStartTime(existingStart);
        existingSlot.setEndTime(existingEnd);
        existingSlot.setCounsellor(counsellor);

        SlotDto slotDto = new SlotDto();
        slotDto.setStartTime(LocalDateTime.of(2025, 12, 30, 10, 30));
        slotDto.setEndTime(LocalDateTime.of(2025, 12, 30, 11, 30));

        mockAuthentication();
        when(userRepository.findByEmail("counsellor@example.com")).thenReturn(Optional.of(counsellor));

        when(slotRepository.findByCounsellorAndStartTimeAfter(
                eq(counsellor),
                any(LocalDateTime.class)
        )).thenReturn(List.of(existingSlot));

        assertThrows(SlotOverlapException.class, () -> counsellorServiceImp.publishSlots(slotDto));
    }

    @Test
    void shouldPublishIfThereAreNoOverlappingSlot() {
        //arrange
        User counsellor = new User();

        LocalDateTime existingStart = LocalDateTime.of(2025, 12, 30, 10, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2025, 12, 30, 11, 0);

        Slot existingSlot = new Slot();
        existingSlot.setStartTime(existingStart);
        existingSlot.setEndTime(existingEnd);
        existingSlot.setCounsellor(counsellor);

        SlotDto slotDto = new SlotDto();
        slotDto.setStartTime(LocalDateTime.of(2025, 12, 30, 8, 30));
        slotDto.setEndTime(LocalDateTime.of(2025, 12, 30, 9, 30));

        mockAuthentication();
        when(userRepository.findByEmail("counsellor@example.com")).thenReturn(Optional.of(counsellor));

        when(slotRepository.findByCounsellorAndStartTimeAfter(
                eq(counsellor),
                any(LocalDateTime.class)
        )).thenReturn(List.of(existingSlot));

        //act
        Slot slot = counsellorServiceImp.publishSlots(slotDto);

        //assert and verify
        assertNotNull(slot);
        verify(slotRepository).save(slot);
    }

    //helper method to do setup before all the cancelAppointment() test
    void setup(Slot slot, User counsellor) {
        counsellor.setId(1);

        slot.setId(10);
        slot.setCounsellor(counsellor);
        slot.setCancelled(false);
        slot.setBooked(false);
        slot.setStartTime(LocalDateTime.now().plusDays(1));

        when(userRepository.findByEmail("counsellor@example.com")).thenReturn(Optional.of(counsellor));
        when(slotRepository.findByIdWithLock(10)).thenReturn(Optional.of(slot));
    }

    @Test
    void shouldRefundAndCancelAppointmentWhenBookedAndRefundSuccess() {
        Slot slot = new Slot();
        User counsellor = new User();

        mockAuthentication();

        setup(slot, counsellor);

        slot.setBooked(true);

        Appointment appointment = new Appointment();
        appointment.setId(20);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setAppointmentTime(LocalDateTime.now().plusDays(1));
        appointment.setCounsellor(counsellor);
        appointment.setStudent(new User());

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(500);
        payment.setGatewayPaymentId("payu123");

        PayURefundResponseDto payURefundResponseDto = new PayURefundResponseDto();
        payURefundResponseDto.setSuccess(true);

        when(appointmentRepository.findBySlot(slot)).thenReturn(appointment);
        when(paymentRepository.findByAppointmentIdWithLock(20)).thenReturn(Optional.of(payment));
        when(payUServiceImp.refund("payu123", 500))
                .thenReturn(payURefundResponseDto);

        Slot result = counsellorServiceImp.removeSlot(10);

        assertTrue(result.isCancelled());
        assertEquals(PaymentStatus.REFUNDED, payment.getPaymentStatus());
        assertEquals(AppointmentStatus.CANCELLED_COUNSELLOR, appointment.getAppointmentStatus());

        verify(publisher).publishCancelled(any());
    }

    @Test
    void shouldMarkRefundFailedWhenRefundFails() {
        Slot slot = new Slot();
        User counsellor = new User();
        User student = new User();
        counsellor.setEmail("counsellor@example.com");

        setup(slot, counsellor);

        mockAuthentication();

        slot.setBooked(true);

        Appointment appointment = new Appointment();
        appointment.setId(20);
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setCounsellor(counsellor);
        appointment.setStudent(student);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId("payu123");

        PayURefundResponseDto payURefundResponseDto = new PayURefundResponseDto();
        payURefundResponseDto.setSuccess(false);

        when(appointmentRepository.findBySlot(slot)).thenReturn(appointment);
        when(paymentRepository.findByAppointmentIdWithLock(20)).thenReturn(Optional.of(payment));
        when(payUServiceImp.refund(anyString(), anyDouble()))
                .thenReturn(payURefundResponseDto);

        counsellorServiceImp.removeSlot(10);

        assertEquals(PaymentStatus.REFUND_FAILED, payment.getPaymentStatus());
    }

    @Test
    void shouldThrowWhenPaymentAlreadyRefunded() {
        Slot slot = new Slot();
        User counsellor = new User();

        setup(slot, counsellor);

        mockAuthentication();

        slot.setBooked(true);

        Appointment appointment = new Appointment();
        appointment.setId(20);

        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.REFUNDED);

        when(appointmentRepository.findBySlot(slot)).thenReturn(appointment);
        when(paymentRepository.findByAppointmentIdWithLock(20))
                .thenReturn(Optional.of(payment));

        assertThrows(
                InvalidOperationException.class,
                () -> counsellorServiceImp.removeSlot(10)
        );
    }
}
