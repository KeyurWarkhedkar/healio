package com.keyur.healio.Entities;

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
public class ForumPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "thread_id")
    private ForumThread thread;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @NotNull
    private String content;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
