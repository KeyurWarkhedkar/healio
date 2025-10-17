package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.ForumThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForumThreadRepository extends JpaRepository<ForumThread, Integer> {
}
