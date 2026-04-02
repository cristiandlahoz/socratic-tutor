create table if not exists chat_conversation (
    id uuid primary key,
    client_id uuid not null,
    title text not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_chat_conversation_client_updated
    on chat_conversation (client_id, updated_at desc);

create table if not exists chat_message (
    id bigserial primary key,
    conversation_id uuid not null references chat_conversation(id) on delete cascade,
    role varchar(16) not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint chk_chat_message_role
        check (role in ('user', 'assistant', 'system', 'tool'))
);

create index if not exists idx_chat_message_conversation_id_desc
    on chat_message (conversation_id, id desc);
