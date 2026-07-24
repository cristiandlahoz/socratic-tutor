-- Training Activities development data is intentionally reset while consolidating the post-main schema.
delete from training_activity;

create table safe_browser_session (
    id uuid primary key,
    training_activity_assignment_id uuid not null references training_activity_assignment(id) on delete cascade,
    token_hash text not null,
    status text not null,
    started_at timestamptz not null,
    last_heartbeat_at timestamptz null,
    ended_at timestamptz null,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

alter table safe_browser_event
    add column safe_browser_session_id uuid null references safe_browser_session(id) on delete set null,
    add column client_event_id uuid null,
    add column client_occurred_at timestamptz null,
    add column metadata jsonb null;
create unique index uk_safe_browser_event_assignment_client_event
    on safe_browser_event (training_activity_assignment_id, client_event_id)
    where client_event_id is not null;

drop index if exists idx_training_activity_instruction_review_hash;
drop index if exists idx_training_activity_instruction_review_instructions_hash;
drop table if exists instruction_review_cache;

alter table training_activity
    drop constraint if exists chk_training_activity_instruction_review_status,
    drop constraint if exists chk_training_activity_instruction_review_quality_status,
    drop constraint if exists chk_training_activity_instruction_review_validity,
    drop column if exists instruction_review_instructions_hash,
    drop column if exists instruction_review_hash,
    drop column if exists instruction_review_status,
    drop column if exists instruction_review_message,
    drop column if exists instruction_review_valid_instruction,
    drop column if exists instruction_review_quality_status,
    drop column if exists instruction_review_summary,
    drop column if exists instruction_review_issues_json,
    drop column if exists instruction_review_improved_instructions,
    drop column if exists instruction_review_model_name,
    drop column if exists instruction_review_rubric_version,
    drop column if exists instruction_review_prompt_version,
    drop column if exists instruction_reviewed_at,
    add column published_at timestamptz null,
    add column version bigint not null default 0;

create unique index uk_training_activity_one_published_per_professor
    on training_activity (created_by_tenant_account_id) where status = 'PUBLISHED';

alter table training_activity_assignment
    drop column if exists current_question,
    drop column if exists question_count,
    drop column if exists evaluation_transcript,
    drop column if exists final_report,
    drop column if exists last_tutor_decision_json,
    drop column if exists tutor_answer_quality,
    drop column if exists tutor_evidence_status,
    drop column if exists tutor_coverage_status,
    drop column if exists tutor_pedagogical_move,
    drop column if exists covered_instruction_aspects_json,
    drop column if exists missing_instruction_aspects_json,
    drop column if exists unproductive_pattern_detected,
    drop column if exists insufficient_evidence,
    drop column if exists tutor_decision_reason,
    drop column if exists tutor_model_name,
    drop column if exists tutor_prompt_version,
    add column evidence_status text null,
    add column completion_reason text null,
    add column version bigint not null default 0;
alter table training_activity_assignment drop constraint if exists chk_training_activity_assignment_status;
alter table training_activity_assignment add constraint chk_training_activity_assignment_status check (status in (
    'ASSIGNED', 'STARTING', 'WAITING_FOR_ANSWER', 'WAITING_FOR_TUTOR', 'TEMPORARILY_UNAVAILABLE',
    'SUBMITTED', 'SKIPPED', 'EXPIRED', 'EXCUSED'
));

create table training_activity_turn (
    id uuid primary key, training_activity_assignment_id uuid not null references training_activity_assignment(id) on delete cascade,
    sequence_number integer not null, question_text text not null, question_created_at timestamptz not null,
    answer_text text null, answer_submission_id uuid null, answer_submitted_at timestamptz null,
    decision_type text null, answer_quality text null, evidence_status text null, coverage_status text null,
    pedagogical_move text null, decision_metadata jsonb null, created_at timestamptz not null, updated_at timestamptz not null,
    unique (training_activity_assignment_id, sequence_number), unique (training_activity_assignment_id, answer_submission_id)
);

create table training_activity_report (
    id uuid primary key, training_activity_assignment_id uuid not null unique references training_activity_assignment(id) on delete cascade,
    status text not null, evidence_status text null, summary text null, strengths jsonb null, weaknesses jsonb null,
    observations jsonb null, recommendations jsonb null, model_name text not null, prompt_version text not null,
    attempt_count integer not null default 0, last_error_code text null, version bigint not null default 0,
    requested_at timestamptz not null, completed_at timestamptz null, updated_at timestamptz not null
);

create table training_activity_ai_job (
    id uuid primary key, job_type text not null, priority integer not null,
    training_activity_id uuid null references training_activity(id) on delete cascade,
    review_professor_id uuid null, review_title text null, review_instructions text null,
    training_activity_assignment_id uuid null references training_activity_assignment(id) on delete cascade,
    training_activity_turn_id uuid null references training_activity_turn(id) on delete cascade,
    training_activity_report_id uuid null references training_activity_report(id) on delete cascade,
    input_version bigint not null default 0, semantic_key text not null, generation integer not null default 0,
    status text not null, attempt_count integer not null default 0, max_attempts integer not null,
    available_at timestamptz not null, lease_until timestamptz null, last_error_code text null,
    created_at timestamptz not null, updated_at timestamptz not null,
    unique (semantic_key, generation)
);
create unique index uk_training_activity_ai_job_live_semantic on training_activity_ai_job (semantic_key)
    where status in ('PENDING', 'RUNNING', 'RETRYABLE');
create index idx_training_activity_ai_job_available on training_activity_ai_job (status, available_at, priority, created_at);

create table outbox_event (
    id uuid primary key, aggregate_type text not null, aggregate_id uuid not null references training_activity(id) on delete cascade,
    event_type text not null, deduplication_key text not null unique, status text not null,
    attempt_count integer not null default 0, available_at timestamptz not null, lease_until timestamptz null,
    last_error_code text null, created_at timestamptz not null, published_at timestamptz null, version bigint not null default 0
);
create table outbox_recipient_delivery (
    id uuid primary key, outbox_event_id uuid not null references outbox_event(id) on delete cascade,
    group_class_member_id uuid not null references group_class_member(id) on delete restrict,
    idempotency_key text not null unique, status text not null, attempt_count integer not null default 0,
    available_at timestamptz not null, lease_until timestamptz null, last_error_code text null,
    sent_at timestamptz null, created_at timestamptz not null, version bigint not null default 0,
    unique (outbox_event_id, group_class_member_id)
);

alter table training_activity_turn add constraint training_activity_turn_answer_quality_check
    check (answer_quality is null or answer_quality in ('EMPTY', 'ABSURD', 'OFF_TOPIC', 'TOO_VAGUE', 'PARTIALLY_CORRECT', 'GOOD', 'EXCELLENT'));
