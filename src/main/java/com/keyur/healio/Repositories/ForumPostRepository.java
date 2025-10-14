package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, Integer> {
}
