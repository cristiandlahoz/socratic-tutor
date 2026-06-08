create table evaluation_run (
    id uuid primary key,
    evaluation_id uuid not null references evaluation(id) on delete cascade,
    student_client_id uuid not null,

    questions_asked_json jsonb null,
    answers_given_json jsonb null,
    report_markdown text null,

    status varchar(32) not null check (
        status in ('IN_PROGRESS', 'COMPLETED', 'FAILED')
    ),

    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_evaluation_run_evaluation_id
    on evaluation_run (evaluation_id);

create index if not exists idx_evaluation_run_student_client_id
    on evaluation_run (student_client_id);

create index if not exists idx_evaluation_run_status
    on evaluation_run (status);
