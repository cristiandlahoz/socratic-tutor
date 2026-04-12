package com.wornux.chat.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudentTopicMasteryJpaRepository extends JpaRepository<StudentTopicMasteryEntity, StudentTopicMasteryId> {

    List<StudentTopicMasteryEntity> findById_ClientId(UUID clientId);
}
