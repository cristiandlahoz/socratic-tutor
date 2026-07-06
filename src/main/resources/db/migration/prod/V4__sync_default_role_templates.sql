-- Keep default tenant role templates aligned with the type-safe RoleTemplate enum.

update role
set name = 'Tenant Admin',
    description = 'Tenant-scoped academic administrator.',
    assignment_level = 'TENANT',
    permissions = array['role:view','role:create','role:update','role:assign','subject:view','subject:create','subject:update','subject:delete','academic-period:view','academic-period:create','academic-period:update','academic-period:delete','group-class:view','group-class:create','group-class:update','group-class:delete','group-class-member:view','group-class-member:invite','group-class-member:update','group-class-member:delete','group-class-join-code:view','group-class-join-code:create','group-class-join-code:update','group-class-join-code:delete','training-activity:view','training-activity:create','training-activity:update','training-activity:delete','training-activity-assignment:view','training-activity-assignment:create','training-activity-assignment:update','training-activity-assignment:delete','course-material:view','course-material:create','course-material:update','course-material:delete','conversation:view'],
    priority = 80,
    system_defined = true,
    assignable = true,
    active = true,
    updated_at = current_timestamp
where code = 'TENANT_ADMIN'
  and system_defined = true;

update role
set name = 'Professor',
    description = 'Professor operating inside assigned group classes.',
    assignment_level = 'GROUP_CLASS',
    permissions = array['role:view','role:update','role:assign','group-class:view','group-class:update','group-class-member:view','group-class-member:invite','group-class-member:update','group-class-join-code:view','group-class-join-code:create','group-class-join-code:update','group-class-join-code:delete','training-activity:view','training-activity:create','training-activity:update','training-activity:delete','training-activity-assignment:view','training-activity-assignment:create','training-activity-assignment:update','training-activity-assignment:delete','course-material:view','course-material:create','course-material:update','course-material:delete','conversation:view'],
    priority = 60,
    system_defined = true,
    assignable = true,
    active = true,
    updated_at = current_timestamp
where code = 'PROFESSOR'
  and system_defined = true;

update role
set name = 'Student',
    description = 'Student operating only inside owned group-class scope.',
    assignment_level = 'GROUP_CLASS',
    permissions = array['group-class:view','conversation:view','conversation:create','conversation:update','conversation:delete','training-activity:view','training-activity-assignment:view','training-activity-assignment:update'],
    priority = 40,
    system_defined = true,
    assignable = true,
    active = true,
    updated_at = current_timestamp
where code = 'STUDENT'
  and system_defined = true;
