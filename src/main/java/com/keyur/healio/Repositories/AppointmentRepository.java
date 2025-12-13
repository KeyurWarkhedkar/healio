package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import com.keyur.healio.Enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    public Optional<Appointment> findByIdWithLock(@Param("id") int appointmentId);
    public List<Appointment> findAllByCounsellorOrderByAppointmentTimeAsc(User counsellor);
    public List<Appointment> findAllByStudentOrderByAppointmentTimeAsc(User student);
    public Appointment findBySlot(Slot slot);
    public List<Appointment> findAllByAppointmentStatusAndCreatedAtBefore(AppointmentStatus status, LocalDateTime time);
}
