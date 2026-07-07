alter table training_activity
    add column safe_browser_enabled boolean not null default false;

alter table training_activity_assignment
    add column safe_browser_locked boolean not null default false,
    add column safe_browser_locked_at timestamptz null,
    add column safe_browser_lock_reason text null,
    add column safe_browser_session_active boolean not null default false,
    add column safe_browser_last_heartbeat_at timestamptz null;

create table safe_browser_event (
    id uuid primary key default gen_random_uuid(),
    training_activity_assignment_id uuid not null references training_activity_assignment(id) on delete cascade,
    actor_group_class_member_id uuid null references group_class_member(id) on delete set null,
    event_type text not null,
    severity text not null,
    occurred_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    constraint chk_safe_browser_event_type check (
        event_type in ('SESSION_STARTED', 'HEARTBEAT', 'SESSION_ENDED', 'FULLSCREEN_EXIT', 'TAB_HIDDEN', 'WINDOW_BLUR', 'BEFORE_UNLOAD', 'HEARTBEAT_LOST', 'MANUAL_UNLOCK', 'ACTIVITY_CLOSED')
    ),
    constraint chk_safe_browser_event_severity check (severity in ('INFO', 'VIOLATION'))
);

create table safe_browser_alert (
    id uuid primary key default gen_random_uuid(),
    training_activity_id uuid not null references training_activity(id) on delete cascade,
    professor_tenant_account_id uuid not null references tenant_account(id) on delete cascade,
    professor_group_class_member_id uuid null references group_class_member(id) on delete set null,
    status text not null,
    incident_count integer not null default 0,
    last_event_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_safe_browser_alert_status check (status in ('OPEN', 'REVIEWED', 'RESOLVED')),
    constraint uk_safe_browser_alert_open unique (professor_tenant_account_id, training_activity_id, status)
);

create index idx_safe_browser_event_assignment_id on safe_browser_event (training_activity_assignment_id);
create index idx_safe_browser_event_actor_group_class_member_id on safe_browser_event (actor_group_class_member_id);
create index idx_safe_browser_alert_activity_id on safe_browser_alert (training_activity_id);
create index idx_safe_browser_alert_professor_tenant_account_id on safe_browser_alert (professor_tenant_account_id);
