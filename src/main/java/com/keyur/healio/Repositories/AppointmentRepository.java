package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.Appointment;
import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    public List<Appointment> findAllByCounsellorOrderByStartTimeAsc(User counsellor);
    public List<Appointment> findAllByStudentOrderByStartTimeAsc(User student);
    public Appointment findBySlot(Slot slot);
}
