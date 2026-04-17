create extension if not exists vector;
create extension if not exists "uuid-ossp";

create table if not exists chat (
    id uuid primary key,
    client_id uuid not null,
    title text not null,
    current_transcript_id uuid null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_chat_client_updated
    on chat (client_id, updated_at desc, created_at desc);

create table if not exists chat_transcript (
    id uuid primary key,
    chat_id uuid not null references chat(id) on delete cascade,
    memory jsonb not null default '{"text": ""}'::jsonb,
    input_tokens integer null,
    created_at timestamptz not null default current_timestamp
);

create index if not exists idx_chat_transcript_chat_created
    on chat_transcript (chat_id, created_at asc);

alter table chat
    add constraint fk_chat_current_transcript
    foreign key (current_transcript_id) references chat_transcript(id) on delete set null;

create table if not exists chat_message (
    id bigserial primary key,
    transcript_id uuid not null references chat_transcript(id) on delete cascade,
    role varchar(16) not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint chk_chat_message_role
        check (role in ('user', 'assistant', 'tool'))
);

create index if not exists idx_chat_message_transcript_id_id
    on chat_message (transcript_id, id asc);

create table if not exists vector_store (
    id uuid primary key default uuid_generate_v4(),
    content text,
    metadata json,
    embedding vector(1024)
);

create index if not exists vector_store_embedding_hnsw_idx
    on vector_store using hnsw (embedding vector_cosine_ops);

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
    conversation_id uuid null references chat(id) on delete set null,
    turn_id uuid not null,
    signal_type varchar(32) not null,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp
);

create index if not exists idx_student_profile_signal_client_created
    on student_profile_signal (client_id, created_at desc);
