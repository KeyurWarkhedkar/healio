package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.Slot;
import com.keyur.healio.Entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = ?1")
    public Optional<Slot> findByIdWithLock(int slotId);

    public List<Slot> findByCounsellorAndStartTimeAfter(User counsellor, LocalDateTime currentTime);
}
