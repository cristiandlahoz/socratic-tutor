package com.wornux.services.security;

import java.time.Instant;
import java.util.UUID;

import com.wornux.data.entities.authorization.RoleNamespace;
import com.wornux.data.repositories.authorization.RoleNamespaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleNamespaceService {

    private final RoleNamespaceRepository roleNamespaceRepository;

    public RoleNamespaceService(RoleNamespaceRepository roleNamespaceRepository) {
        this.roleNamespaceRepository = roleNamespaceRepository;
    }

    @Transactional
    public RoleNamespace create(String code) {
        var now = Instant.now();
        var namespace = new RoleNamespace();
        namespace.setId(UUID.randomUUID());
        namespace.setCode(code);
        namespace.setRbacVersion(0);
        namespace.setCreatedAt(now);
        namespace.setUpdatedAt(now);
        return roleNamespaceRepository.save(namespace);
    }
}
