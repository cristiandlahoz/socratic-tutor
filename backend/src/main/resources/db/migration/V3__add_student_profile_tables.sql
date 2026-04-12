create table if not exists student_profile (
    client_id uuid primary key,
    preferred_language varchar(8) not null default 'es',
    overall_level varchar(16) not null default 'developing',
    help_mode varchar(16) not null default 'guided',
    needs_concrete_examples boolean not null default false,
    confidence_score numeric(4, 3) not null default 0.500,
    last_updated_at timestamptz not null default current_timestamp,
    profile_version bigint not null default 1
);

create table if not exists student_topic_mastery (
    client_id uuid not null references student_profile(client_id) on delete cascade,
    topic_key varchar(32) not null,
    mastery_level varchar(16) not null default 'unknown',
    evidence_count integer not null default 0,
    last_seen_at timestamptz not null default current_timestamp,
    primary key (client_id, topic_key)
);

create table if not exists student_misconception (
    id bigserial primary key,
    client_id uuid not null references student_profile(client_id) on delete cascade,
    topic_key varchar(32) not null,
    misconception_key varchar(64) not null,
    description text not null,
    confidence numeric(4, 3) not null,
    status varchar(16) not null default 'active',
    last_seen_at timestamptz not null default current_timestamp
);

create unique index if not exists idx_student_misconception_client_key
    on student_misconception (client_id, misconception_key);

create table if not exists student_profile_signal (
    id bigserial primary key,
    client_id uuid not null references student_profile(client_id) on delete cascade,
    conversation_id uuid null references chat_conversation(id) on delete set null,
    turn_id uuid not null,
    signal_type varchar(32) not null,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp
);

create index if not exists idx_student_profile_signal_client_created
    on student_profile_signal (client_id, created_at desc);
