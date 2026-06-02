create table if not exists evaluation (
    id uuid primary key,
    title text not null,
    instruction text not null,
    questions_json text null,
    answers_json text null,
    report_markdown text null,
    status varchar(32) not null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_evaluation_updated_created
    on evaluation (updated_at desc, created_at desc);
