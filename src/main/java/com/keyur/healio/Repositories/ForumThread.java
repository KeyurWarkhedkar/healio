package com.keyur.healio.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForumThread extends JpaRepository<ForumThread, Integer> {
}
