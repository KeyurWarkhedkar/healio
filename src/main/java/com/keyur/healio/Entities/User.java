package com.keyur.healio.Entities;

import com.keyur.healio.Enums.UserRoles;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @NotBlank
    private String name;

    @NotBlank
    @Email
    private String email;

    @Enumerated(EnumType.STRING)
    @NotNull
    private UserRoles role;

    @NotBlank
    private String passwordHash;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
