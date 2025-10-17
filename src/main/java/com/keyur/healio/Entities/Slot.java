package com.keyur.healio.Entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Slot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    @ManyToOne
    @JoinColumn(name = "counsellor_id", nullable = false)
    private User counsellor;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    private boolean isBooked = false;
}
