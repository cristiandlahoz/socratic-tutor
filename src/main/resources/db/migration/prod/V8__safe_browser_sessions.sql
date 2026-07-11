create table safe_browser_session (
    id uuid primary key default gen_random_uuid(),
    training_activity_assignment_id uuid not null references training_activity_assignment(id) on delete cascade,
    token_hash text not null,
    status text not null,
    started_at timestamptz not null,
    last_heartbeat_at timestamptz null,
    ended_at timestamptz null,
    version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_safe_browser_session_status check (status in ('PENDING', 'ACTIVE', 'VIOLATED', 'EXPIRED', 'ENDED'))
);

alter table safe_browser_event
    add column safe_browser_session_id uuid null references safe_browser_session(id) on delete restrict,
    add column client_event_id uuid null,
    add column client_occurred_at timestamptz null,
    add column metadata jsonb null;

create unique index uk_safe_browser_session_live_assignment
    on safe_browser_session(training_activity_assignment_id)
    where status in ('PENDING', 'ACTIVE');

create unique index uk_safe_browser_event_session_client_event
    on safe_browser_event(safe_browser_session_id, client_event_id)
    where client_event_id is not null;

create index idx_safe_browser_session_pending_created
    on safe_browser_session(status, created_at)
    where status = 'PENDING';

create index idx_safe_browser_session_active_heartbeat
    on safe_browser_session(status, last_heartbeat_at)
    where status = 'ACTIVE';
