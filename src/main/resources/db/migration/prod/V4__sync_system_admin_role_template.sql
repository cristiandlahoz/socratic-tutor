-- Keep the bootstrap platform role aligned with the type-safe SYSTEM_ADMIN role template.

update role
set name = 'System Admin',
    description = 'Platform-level administrator with full visibility.',
    assignment_level = 'PLATFORM',
    permissions = array['tenant:view','tenant:create','tenant:update','account:view','account:update','role:view','role:create','role:update','role:delete','role:assign'],
    priority = 100,
    system_defined = true,
    assignable = false,
    active = true,
    updated_at = current_timestamp
where role_namespace_id = '4853812e-99b5-4c7a-b7ee-74bca6f20bdc'
  and code = 'SYSTEM_ADMIN';
