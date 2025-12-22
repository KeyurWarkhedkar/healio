package com.keyur.healio.ServiceTests;

import com.keyur.healio.CustomExceptions.InvalidOperationException;
import com.keyur.healio.CustomExceptions.ResourceNotFoundException;
import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Repositories.AppointmentRepository;
import com.keyur.healio.Repositories.SlotRepository;
import com.keyur.healio.Repositories.UserRepository;
import com.keyur.healio.Services.AppointmentEventPublisher;
import com.keyur.healio.Services.StudentServiceImp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StudentServiceTest {
    @Mock
    AppointmentRepository appointmentRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    SlotRepository slotRepository;

    @Mock
    AppointmentEventPublisher publisher;

    @InjectMocks
    StudentServiceImp studentServiceImp;

    //helper method to create mock Authentication
    void mockAuthentication() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(authentication.getName()).thenReturn("student@example.com");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void shouldCreateAppointmentWhenSlotIsAvailable() {
        //arrange
        User student = new User();
        student.setId(5);

        Slot slot = new Slot();
        slot.setCancelled(false);
        slot.setBooked(false);
        slot.setStartTime(LocalDateTime.now().plusHours(1));

        mockAuthentication();

        //mock behaviour
        when(slotRepository.findByIdWithLock(5)).thenReturn(Optional.of(slot));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(student));
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));


        //act
        Appointment newAppointment = studentServiceImp.bookAppointment(5);

        //assert
        assertNotNull(newAppointment);
        assertEquals(student, newAppointment.getStudent());
        assertTrue(slot.isBooked());
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, com.keyur.healio.CustomExceptions.InvalidOperationException",
            "false, true, com.keyur.healio.CustomExceptions.SlotAlreadyBookedException"
    })
    void shouldThrowCorrectExceptionForInvalidSlot(boolean cancelled, boolean booked, String exceptionClassName) throws Exception {
        //arrange
        Slot slot = new Slot();
        slot.setCancelled(cancelled);
        slot.setBooked(booked);
        slot.setStartTime(LocalDateTime.now().plusHours(1));

        when(slotRepository.findByIdWithLock(1)).thenReturn(Optional.of(slot));

        Class<? extends Throwable> expectedException = (Class<? extends Throwable>) Class.forName(exceptionClassName);

        //act and assert
        assertThrows(expectedException, () -> studentServiceImp.bookAppointment(1));


        verify(appointmentRepository, never()).save(any());
        verify(slotRepository, never()).save(any());

    }

    @Test
    void shouldThrowExceptionWhenSlotNotFound() {
        //arrange
        when(slotRepository.findByIdWithLock(4)).thenReturn(Optional.empty());

        //assert and act
        assertThrows(ResourceNotFoundException.class, () -> studentServiceImp.bookAppointment(4));
    }

    @Test
    void shouldCreateAppointmentAndSlotWithDetailsExactlyPassesToIt() {
        //arrange
        User student = new User();
        student.setEmail("student@example.com");
        student.setId(1);
        student.setName("Keyur");

        Slot slot = new Slot();
        slot.setBooked(false);
        slot.setCancelled(false);
        slot.setStartTime(LocalDateTime.now().plusHours(1));
        slot.setStudent(student);

        mockAuthentication();

        when(slotRepository.findByIdWithLock(4)).thenReturn(Optional.of(slot));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(student));

        //act
        Appointment newAppointment = studentServiceImp.bookAppointment(4);

        //assert and verify
        assertEquals(slot.getId(), newAppointment.getSlot().getId());
        assertEquals(student.getId(), newAppointment.getStudent().getId());

        verify(appointmentRepository).save(any(Appointment.class));
        verify(slotRepository).save(slot);
    }

    //helper method for mocking and stubbing cancelAppointment()
    void arrangeAndMockForCancelAppointment(Slot slot, User student, Appointment appointment) {
        slot.setId(1);

        student.setId(2);
        student.setEmail("student@example.com");

        appointment.setId(3);
        appointment.setStudent(student);
        appointment.setSlot(slot);
        appointment.setAppointmentTime(LocalDateTime.now().plusHours(1));
        appointment.setAppointmentStatus(AppointmentStatus.CANCELLED_STUDENT);

        mockAuthentication();

        //mock behaviour
        when(appointmentRepository.findById(3)).thenReturn(Optional.of(appointment));
        when(slotRepository.findByIdWithLock(1)).thenReturn(Optional.of(slot));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(student));
    }

    @Test
    void shouldCancelAppointment() {
        //arrange
        Slot slot = new Slot();
        slot.setId(1);

        User student = new User();
        student.setId(2);
        student.setEmail("student@example.com");

        User counsellor = new User();

        Appointment appointment = new Appointment();
        appointment.setId(3);
        appointment.setStudent(student);
        appointment.setSlot(slot);
        appointment.setAppointmentTime(LocalDateTime.now().plusHours(1));
        appointment.setAppointmentStatus(AppointmentStatus.CONFIRMED);
        appointment.setCounsellor(counsellor);

        mockAuthentication();

        //mock behaviour
        when(appointmentRepository.findById(3)).thenReturn(Optional.of(appointment));
        when(slotRepository.findByIdWithLock(1)).thenReturn(Optional.of(slot));
        when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(student));

        //act
        Appointment cancelledAppointment = studentServiceImp.cancelAppointment(3);

        //assert and verify
        assertNull(cancelledAppointment.getSlot());
        assertEquals(AppointmentStatus.CANCELLED_STUDENT, cancelledAppointment.getAppointmentStatus());
        assertFalse(slot.isBooked());

        verify(appointmentRepository).save(cancelledAppointment);
        verify(slotRepository).save(slot);
    }


    @Test
    void shouldThrowExceptionWhenAppointmentStatusIsNotConfirmed() {
        //arrange
        Appointment appointment = new Appointment();
        Slot slot = new Slot();
        User student = new User();
        arrangeAndMockForCancelAppointment(slot, student, appointment);

        //act and assert
        assertThrows(InvalidOperationException.class, () -> studentServiceImp.cancelAppointment(3));
    }

    @Test
    void shouldThrowExceptionWhenAppointmentIsOfPast() {
        //arrange
        Appointment appointment = new Appointment();
        Slot slot = new Slot();
        User student = new User();
        arrangeAndMockForCancelAppointment(slot, student, appointment);

        //act and assert
        assertThrows(InvalidOperationException.class, () -> studentServiceImp.cancelAppointment(3));
    }
}
