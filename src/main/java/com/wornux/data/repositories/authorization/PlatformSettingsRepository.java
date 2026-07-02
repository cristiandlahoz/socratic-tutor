package com.wornux.data.repositories.authorization;

import com.wornux.data.entities.authorization.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Boolean> {}
