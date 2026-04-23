package com.wornux.chat.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTopicMasteryJpaRepository
    extends JpaRepository<StudentTopicMasteryEntity, StudentTopicMasteryId> {

  List<StudentTopicMasteryEntity> findById_ClientId(UUID clientId);
}
