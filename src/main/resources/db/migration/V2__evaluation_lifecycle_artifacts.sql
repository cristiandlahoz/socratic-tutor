create table evaluation_guide_artifact (
    id uuid primary key default uuid_generate_v4(),
    evaluation_id uuid not null references evaluation(id) on delete cascade,
    revision_id uuid not null references evaluation_revision(id) on delete cascade,
    guide_content text not null,
    published_at timestamptz not null default current_timestamp,
    created_at timestamptz not null default current_timestamp
);

create index idx_evaluation_guide_artifact_evaluation_published
    on evaluation_guide_artifact (evaluation_id, published_at desc);

create table evaluation_result_artifact (
    id uuid primary key default uuid_generate_v4(),
    evaluation_id uuid not null references evaluation(id) on delete cascade,
    revision_id uuid not null references evaluation_revision(id) on delete cascade,
    attempt_id uuid not null references evaluation_attempt(id) on delete cascade,
    result_payload jsonb not null default '{}'::jsonb,
    completed_at timestamptz not null default current_timestamp,
    created_at timestamptz not null default current_timestamp
);

create index idx_evaluation_result_artifact_evaluation_completed
    on evaluation_result_artifact (evaluation_id, completed_at desc);

create unique index uq_evaluation_result_artifact_attempt
    on evaluation_result_artifact (attempt_id);

alter table evaluation_attempt
    add column completion_reason varchar(32) null,
    add column completed_at timestamptz null;
