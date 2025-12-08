package com.keyur.healio.Entities;

import com.keyur.healio.Enums.AppointmentStatus;
import com.keyur.healio.Enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne
    @JoinColumn(name = "counsellor_id", nullable = false)
    private User counsellor;

    @NotNull
    private LocalDateTime appointmentTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    private AppointmentStatus appointmentStatus;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @Column
    private LocalDateTime expiresAt;

    @Version
    private int version;
}
