-- Development-only seed data. Loaded only when the dev Flyway location is enabled.

insert into account (id, email, first_name, last_name, password_hash, locked)
values
    ('32b92c98-3b76-49bb-9fcf-3b12a7f17b2c', 'cristiandelahooz@wornux.com', 'Cristian', 'De la Hoz', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false),
    ('b17d0169-e8f3-4392-8a42-4f629ae2d7a6', 'alfredo@wornux.com', 'Alfredo', 'Profesor', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false),
    ('aa875f81-98c8-444d-8b32-3bce9e0467b5', 'camacho@wornux.com', 'Camacho', 'Admin', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false);

insert into tenant (id, created_by_account_id, name, locked)
values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '8ebd933e-20e2-42ef-adec-5331b67a0a54', 'Wornux Academy', false);

insert into tenant_account (id, tenant_id, account_id, locked)
values
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '32b92c98-3b76-49bb-9fcf-3b12a7f17b2c', false),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'b17d0169-e8f3-4392-8a42-4f629ae2d7a6', false),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'aa875f81-98c8-444d-8b32-3bce9e0467b5', false);

insert into tenant_account_role (tenant_account_id, role_id, assigned_by_tenant_account_id)
select 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003', role.id, null
from role
where role.code = 'TENANT_ADMIN';

insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active)
values
    ('cccccccc-cccc-cccc-cccc-cccccccc0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '2026-1', '2026 First Term', date '2026-01-12', date '2026-05-30', true),
    ('cccccccc-cccc-cccc-cccc-cccccccc0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '2026-2', '2026 Second Term', date '2026-08-10', date '2026-12-12', true);

insert into subject (id, tenant_id, code, name, active)
values
    ('dddddddd-dddd-dddd-dddd-dddddddd0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ICC-101', 'Introduction to Algorithms', true),
    ('dddddddd-dddd-dddd-dddd-dddddddd0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'MAT-101', 'Discrete Mathematics', true);

insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active)
values
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-dddd-dddd-dddd-dddddddd0001', 'cccccccc-cccc-cccc-cccc-cccccccc0001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003', 'ICC-101-01', 'Algorithms Section 01', true),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeee0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dddddddd-dddd-dddd-dddd-dddddddd0002', 'cccccccc-cccc-cccc-cccc-cccccccc0001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003', 'MAT-101-01', 'Discrete Mathematics Section 01', true);

insert into group_class_member (id, group_class_id, tenant_account_id, role, locked)
values
    ('ffffffff-ffff-ffff-ffff-ffffffff0001', 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002', 'PROFESSOR', false),
    ('ffffffff-ffff-ffff-ffff-ffffffff0002', 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001', 'STUDENT', false);

update account
set last_tenant_account_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    last_group_class_member_id = 'ffffffff-ffff-ffff-ffff-ffffffff0002',
    updated_at = current_timestamp
where id = '32b92c98-3b76-49bb-9fcf-3b12a7f17b2c';

update account
set last_tenant_account_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0002',
    last_group_class_member_id = 'ffffffff-ffff-ffff-ffff-ffffffff0001',
    updated_at = current_timestamp
where id = 'b17d0169-e8f3-4392-8a42-4f629ae2d7a6';

update account
set last_tenant_account_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0003',
    updated_at = current_timestamp
where id = 'aa875f81-98c8-444d-8b32-3bce9e0467b5';
