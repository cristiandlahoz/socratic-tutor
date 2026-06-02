create table evaluation (
    id uuid primary key,
    title text not null,
    instruction text not null,

    questions_json jsonb null,
    answers_json jsonb null,

    report_markdown text null,

    status varchar(32) not null check (
        status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')
    ),

    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_evaluation_updated_created
    on evaluation (updated_at desc, created_at desc);
