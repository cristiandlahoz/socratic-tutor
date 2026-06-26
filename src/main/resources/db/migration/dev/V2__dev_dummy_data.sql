-- Development-only seed data. Loaded only when the dev Flyway location is enabled.

insert into account (id, email, first_name, last_name, password_hash, locked)
values
    ('32b92c98-3b76-49bb-9fcf-3b12a7f17b2c', 'cristiandelahooz@wornux.com', 'Cristian', 'De la Hoz', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false),
    ('b17d0169-e8f3-4392-8a42-4f629ae2d7a6', 'alfredo@wornux.com', 'Alfredo', 'Profesor', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false),
    ('aa875f81-98c8-444d-8b32-3bce9e0467b5', 'camacho@wornux.com', 'Camacho', 'Admin', '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC', false);

insert into tenant (id, created_by_account_id, name, locked)
values ('034daffd-5907-48f7-bce6-b2c0e71f4015', '8ebd933e-20e2-42ef-adec-5331b67a0a54', 'Wornux Academy', false);

insert into tenant_account (id, tenant_id, account_id, locked)
values
    ('2d62ec65-2ea6-4be9-80ac-45e07fd65207', '034daffd-5907-48f7-bce6-b2c0e71f4015', '32b92c98-3b76-49bb-9fcf-3b12a7f17b2c', false),
    ('c754e015-2113-403a-96a8-292d1aa137ae', '034daffd-5907-48f7-bce6-b2c0e71f4015', 'b17d0169-e8f3-4392-8a42-4f629ae2d7a6', false),
    ('d1a532a0-0247-41b9-a1c5-5a6275dd61e2', '034daffd-5907-48f7-bce6-b2c0e71f4015', 'aa875f81-98c8-444d-8b32-3bce9e0467b5', false);

insert into tenant_account_role (tenant_account_id, role_id, assigned_by_tenant_account_id)
select 'd1a532a0-0247-41b9-a1c5-5a6275dd61e2', role.id, null
from role
where role.code = 'TENANT_ADMIN';

insert into academic_period (id, tenant_id, code, name, starts_at, ends_at, active)
values
    ('8ed95e10-cdb6-4843-a412-116581828fe2', '034daffd-5907-48f7-bce6-b2c0e71f4015', '2026-1', '2026 First Term', date '2026-01-12', date '2026-05-30', true),
    ('5a3d7750-200e-4150-b87d-6bf455b5c98c', '034daffd-5907-48f7-bce6-b2c0e71f4015', '2026-2', '2026 Second Term', date '2026-08-10', date '2026-12-12', true);

insert into subject (id, tenant_id, code, name, active)
values
    ('d8675849-e396-48b5-b807-adf71cd113e6', '034daffd-5907-48f7-bce6-b2c0e71f4015', 'ICC-101', 'Introduction to Algorithms', true),
    ('5f32eb72-1347-436f-89b7-8d619410cb00', '034daffd-5907-48f7-bce6-b2c0e71f4015', 'MAT-101', 'Discrete Mathematics', true);

insert into group_class (id, tenant_id, subject_id, academic_period_id, created_by_tenant_account_id, code, name, active)
values
    ('c63c4824-8ec7-4f62-9417-efd48b9adc62', '034daffd-5907-48f7-bce6-b2c0e71f4015', 'd8675849-e396-48b5-b807-adf71cd113e6', '8ed95e10-cdb6-4843-a412-116581828fe2', 'd1a532a0-0247-41b9-a1c5-5a6275dd61e2', 'ICC-101-01', 'Algorithms Section 01', true),
    ('61e0d5a3-de6f-4607-a8a7-fd6847c623cb', '034daffd-5907-48f7-bce6-b2c0e71f4015', '5f32eb72-1347-436f-89b7-8d619410cb00', '8ed95e10-cdb6-4843-a412-116581828fe2', 'd1a532a0-0247-41b9-a1c5-5a6275dd61e2', 'MAT-101-01', 'Discrete Mathematics Section 01', true);

insert into group_class_member (id, group_class_id, tenant_account_id, role, locked)
values
    ('19ad75ab-e47b-46a0-9c9e-4ce40559c099', 'c63c4824-8ec7-4f62-9417-efd48b9adc62', 'c754e015-2113-403a-96a8-292d1aa137ae', 'PROFESSOR', false),
    ('4ead0b6d-c90b-4da0-b838-c42123408e06', 'c63c4824-8ec7-4f62-9417-efd48b9adc62', '2d62ec65-2ea6-4be9-80ac-45e07fd65207', 'STUDENT', false);

update account
set last_tenant_account_id = '2d62ec65-2ea6-4be9-80ac-45e07fd65207',
    last_group_class_member_id = '4ead0b6d-c90b-4da0-b838-c42123408e06',
    updated_at = current_timestamp
where id = '32b92c98-3b76-49bb-9fcf-3b12a7f17b2c';

update account
set last_tenant_account_id = 'c754e015-2113-403a-96a8-292d1aa137ae',
    last_group_class_member_id = '19ad75ab-e47b-46a0-9c9e-4ce40559c099',
    updated_at = current_timestamp
where id = 'b17d0169-e8f3-4392-8a42-4f629ae2d7a6';

update account
set last_tenant_account_id = 'd1a532a0-0247-41b9-a1c5-5a6275dd61e2',
    updated_at = current_timestamp
where id = 'aa875f81-98c8-444d-8b32-3bce9e0467b5';
