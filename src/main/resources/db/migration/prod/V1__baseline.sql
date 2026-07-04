create extension if not exists vector;
create extension if not exists pgcrypto;

-- ── RBAC namespaces ──

create table role_namespace (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    rbac_version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table platform_settings (
    id boolean primary key default true check (id = true),
    role_namespace_id uuid not null unique references role_namespace(id)
);

-- ── Accounts & Tenants ──

create table account (
    id uuid primary key default gen_random_uuid(),
    first_name text null,
    last_name text null,
    email text not null,
    password_hash text not null,
    locked boolean not null default false,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_account_email unique (email)
);

create table tenant (
    id uuid primary key default gen_random_uuid(),
    role_namespace_id uuid not null unique references role_namespace(id),
    created_by_account_id uuid null references account(id) on delete set null,
    name text not null,
    locked boolean not null default false,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table tenant_account (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    account_id uuid not null references account(id) on delete cascade,
    locked boolean not null default false,
    joined_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_tenant_account_tenant_account unique (tenant_id, account_id)
);

-- ── RBAC roles ──

create table role (
    id uuid primary key default gen_random_uuid(),
    role_namespace_id uuid not null references role_namespace(id) on delete cascade,
    code text not null,
    name text not null,
    description text null,
    assignment_level text not null check (assignment_level in ('PLATFORM','TENANT','GROUP_CLASS')),
    permissions text[] not null default '{}',
    priority integer not null default 0,
    system_defined boolean not null default false,
    assignable boolean not null default true,
    active boolean not null default true,
    created_by_account_id uuid null references account(id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_role_namespace_code unique (role_namespace_id, code)
);

create table account_platform_role (
    account_id uuid not null references account(id) on delete cascade,
    role_id uuid not null references role(id) on delete cascade,
    assigned_by_account_id uuid null references account(id) on delete set null,
    assigned_at timestamptz not null default current_timestamp,
    primary key (account_id, role_id)
);

create table tenant_account_role (
    tenant_account_id uuid not null references tenant_account(id) on delete cascade,
    role_id uuid not null references role(id) on delete cascade,
    assigned_by_tenant_account_id uuid null references tenant_account(id) on delete set null,
    assigned_at timestamptz not null default current_timestamp,
    primary key (tenant_account_id, role_id)
);

-- ── Academic structure (UUID PK) ──

create table subject (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    code text not null,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_subject_tenant_code unique (tenant_id, code)
);

create table academic_period (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    code text not null,
    name text not null,
    starts_at date not null,
    ends_at date not null,
    active boolean not null default true,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_academic_period_tenant_code unique (tenant_id, code)
);

create table group_class (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    subject_id uuid not null references subject(id) on delete restrict,
    academic_period_id uuid not null references academic_period(id) on delete restrict,
    created_by_tenant_account_id uuid not null references tenant_account(id) on delete restrict,
    code text not null,
    name text not null,
    active boolean not null default true,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_group_class_tenant_code unique (tenant_id, code)
);

create table account_context_preference (
    account_id uuid primary key references account(id) on delete cascade,
    context_level text null check (context_level in ('PLATFORM','TENANT','GROUP_CLASS')),
    tenant_id uuid null references tenant(id) on delete set null,
    group_class_id uuid null references group_class(id) on delete set null,
    updated_at timestamptz not null default current_timestamp
);

create table group_class_member (
    id uuid primary key default gen_random_uuid(),
    group_class_id uuid not null references group_class(id) on delete cascade,
    tenant_account_id uuid not null references tenant_account(id) on delete cascade,
    member_kind text not null check (member_kind in ('PROFESSOR','STUDENT','ASSISTANT')),
    locked boolean not null default false,
    joined_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint uk_group_class_member_group_tenant unique (group_class_id, tenant_account_id)
);

create table group_class_member_role (
    group_class_member_id uuid not null references group_class_member(id) on delete cascade,
    role_id uuid not null references role(id) on delete cascade,
    assigned_by_group_class_member_id uuid null references group_class_member(id) on delete set null,
    assigned_at timestamptz not null default current_timestamp,
    primary key (group_class_member_id, role_id)
);

-- ── Conversations (UUID for URL exposure) ──

create table conversation (
    id uuid primary key default gen_random_uuid(),
    group_class_id uuid not null references group_class(id) on delete cascade,
    created_by_tenant_account_id uuid not null references tenant_account(id) on delete cascade,
    created_by_group_class_member_id uuid null references group_class_member(id) on delete set null,
    title text not null,
    last_prompt_tokens integer null,
    version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

CREATE TABLE IF NOT EXISTS AI_SESSION (
    id            VARCHAR(255)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(255)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    expires_at    TIMESTAMP,
    metadata      TEXT,
    event_version BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_session_user_id ON AI_SESSION (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_session_expires_at ON AI_SESSION (expires_at);

CREATE TABLE IF NOT EXISTS AI_SESSION_EVENT (
    seq             BIGINT        GENERATED BY DEFAULT AS IDENTITY,
    id              VARCHAR(255)  NOT NULL PRIMARY KEY,
    session_id      VARCHAR(255)  NOT NULL,
    "timestamp"     TIMESTAMP     NOT NULL,
    message_type    VARCHAR(20)   NOT NULL,
    message_content TEXT,
    message_data    TEXT,
    synthetic       BOOLEAN       NOT NULL DEFAULT FALSE,
    archived        BOOLEAN       NOT NULL DEFAULT FALSE,
    branch          VARCHAR(500),
    metadata        TEXT,
    CONSTRAINT fk_ai_session_event_session FOREIGN KEY (session_id) REFERENCES AI_SESSION (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_session_event_session_seq ON AI_SESSION_EVENT (session_id, seq);

-- ── Grounding vectors ──

create table grounding_vector_store (
    id uuid primary key default gen_random_uuid(),
    content text not null,
    metadata json not null default '{}'::json,
    embedding vector(1024) not null
);

-- ── Training activities (UUID for URL exposure) ──

create table training_activity (
    id uuid primary key default gen_random_uuid(),
    group_class_id uuid not null references group_class(id) on delete cascade,
    created_by_tenant_account_id uuid not null references tenant_account(id) on delete restrict,
    created_by_group_class_member_id uuid null references group_class_member(id) on delete restrict,
    title text not null,
    instructions text not null,
    status text not null,
    opens_at timestamptz null,
    closes_at timestamptz null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_training_activity_status check (status in ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED'))
);

create table training_activity_assignment (
    id uuid primary key default gen_random_uuid(),
    training_activity_id uuid not null references training_activity(id) on delete cascade,
    group_class_member_id uuid not null references group_class_member(id) on delete cascade,
    status text not null,
    assigned_at timestamptz not null default current_timestamp,
    started_at timestamptz null,
    submitted_at timestamptz null,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_training_activity_assignment_status check (
        status in ('ASSIGNED', 'STARTED', 'SUBMITTED', 'SKIPPED', 'EXPIRED', 'EXCUSED')
    ),
    constraint uk_training_activity_assignment_activity_member unique (training_activity_id, group_class_member_id)
);

-- ── Invitations (internal, BIGINT identity) ──

create table invitation (
    id bigint generated by default as identity primary key,
    tenant_id uuid not null references tenant(id) on delete cascade,
    group_class_id uuid null references group_class(id) on delete cascade,
    invited_email text not null,
    target_role text not null,
    token_hash text not null,
    status text not null default 'PENDING',
    delivery_error text null,
    expires_at timestamptz not null,
    accepted_at timestamptz null,
    invited_by_account_id uuid null references account(id) on delete set null,
    invited_by_tenant_account_id uuid null references tenant_account(id) on delete set null,
    invited_by_group_class_member_id uuid null references group_class_member(id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_invitation_target_role check (target_role in ('TENANT_ADMIN', 'PROFESSOR', 'STUDENT')),
    constraint chk_invitation_status check (status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED', 'DELIVERY_FAILED')),
    constraint uk_invitation_token_hash unique (token_hash)
);

-- ── Indexes ──

create index idx_tenant_role_namespace_id on tenant (role_namespace_id);
create index idx_role_role_namespace_id on role (role_namespace_id);
create index idx_account_platform_role_account_id on account_platform_role (account_id);
create index idx_account_platform_role_role_id on account_platform_role (role_id);
create index idx_tenant_account_tenant_id on tenant_account (tenant_id);
create index idx_tenant_account_account_id on tenant_account (account_id);
create index idx_tenant_account_role_tenant_account_id on tenant_account_role (tenant_account_id);
create index idx_tenant_account_role_role_id on tenant_account_role (role_id);
create index idx_subject_tenant_id on subject (tenant_id);
create index idx_academic_period_tenant_id on academic_period (tenant_id);
create index idx_group_class_tenant_id on group_class (tenant_id);
create index idx_group_class_subject_id on group_class (subject_id);
create index idx_group_class_academic_period_id on group_class (academic_period_id);
create index idx_group_class_member_group_class_id on group_class_member (group_class_id);
create index idx_group_class_member_tenant_account_id on group_class_member (tenant_account_id);
create index idx_group_class_member_role_member_id on group_class_member_role (group_class_member_id);
create index idx_group_class_member_role_role_id on group_class_member_role (role_id);
create index idx_conversation_group_class_id on conversation (group_class_id);
create index idx_conversation_created_by_tenant_account_id on conversation (created_by_tenant_account_id);
create index idx_conversation_created_by_group_class_member_id on conversation (created_by_group_class_member_id);
create index idx_grounding_vector_store_embedding_hnsw on grounding_vector_store using hnsw (embedding vector_cosine_ops);
create index idx_grounding_vector_store_group_class_id on grounding_vector_store ((metadata ->> 'groupClassId'));
create index idx_grounding_vector_store_created_by_group_class_member_id on grounding_vector_store ((metadata ->> 'createdByGroupClassMemberId'));
create index idx_grounding_vector_store_ingestion_id on grounding_vector_store ((metadata ->> 'ingestionId'));
create index idx_grounding_vector_store_status on grounding_vector_store ((metadata ->> 'status'));
create index idx_training_activity_group_class_id on training_activity (group_class_id);
create index idx_training_activity_created_by_tenant_account_id on training_activity (created_by_tenant_account_id);
create index idx_training_activity_created_by_group_class_member_id on training_activity (created_by_group_class_member_id);
create index idx_training_activity_assignment_activity_id on training_activity_assignment (training_activity_id);
create index idx_training_activity_assignment_group_class_member_id on training_activity_assignment (group_class_member_id);
create index idx_invitation_tenant_id on invitation (tenant_id);
create index idx_invitation_group_class_id on invitation (group_class_id);
create index idx_invitation_invited_email on invitation (invited_email);
create index idx_invitation_status on invitation (status);

-- ── Seed: platform namespace and system admin ──

insert into role_namespace (id, code, rbac_version, created_at, updated_at)
values ('4853812e-99b5-4c7a-b7ee-74bca6f20bdc', 'platform', 0, current_timestamp, current_timestamp);

insert into platform_settings (id, role_namespace_id)
values (true, '4853812e-99b5-4c7a-b7ee-74bca6f20bdc');

insert into role (id, role_namespace_id, code, name, description, assignment_level, permissions, priority, system_defined, assignable, active)
values (
    'bf8d972f-2521-4ab7-aa72-b69f400ad691',
    '4853812e-99b5-4c7a-b7ee-74bca6f20bdc',
    'SYSTEM_ADMIN',
    'System Admin',
    'Platform-level administrator with full visibility.',
    'PLATFORM',
    array['tenant:view','tenant:create','tenant:update','account:view','role:view','role:create','role:update','role:assign'],
    100,
    true,
    false,
    true
);

insert into account (id, email, password_hash, locked)
values (
    '8ebd933e-20e2-42ef-adec-5331b67a0a54',
    'admin@wornux.com',
    coalesce(
        nullif(current_setting('app.initial_system_admin_password_hash', true), ''),
        '$2a$10$wUGCyVy0IhomdLcgCE.VleTywHS.MwF9bhOnqCxIj3ZqeVmG/RnrC'
    ),
    false
);

insert into account_platform_role (account_id, role_id)
values ('8ebd933e-20e2-42ef-adec-5331b67a0a54', 'bf8d972f-2521-4ab7-aa72-b69f400ad691');

insert into account_context_preference (account_id, context_level, updated_at)
values ('8ebd933e-20e2-42ef-adec-5331b67a0a54', 'PLATFORM', current_timestamp);
