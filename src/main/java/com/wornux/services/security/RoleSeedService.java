package com.wornux.services.security;

import com.wornux.data.entities.authorization.RoleNamespace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleSeedService {

    private final RoleTemplateSeeder roleTemplateSeeder;

    public RoleSeedService(RoleTemplateSeeder roleTemplateSeeder) {
        this.roleTemplateSeeder = roleTemplateSeeder;
    }

    @Transactional
    public void seedTenantDefaultRoles(RoleNamespace namespace) {
        roleTemplateSeeder.ensureRole(namespace, RoleTemplate.TENANT_ADMIN);
        roleTemplateSeeder.ensureRole(namespace, RoleTemplate.PROFESSOR);
        roleTemplateSeeder.ensureRole(namespace, RoleTemplate.STUDENT);
    }
}
