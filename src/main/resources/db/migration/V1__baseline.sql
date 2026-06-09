create extension if not exists vector;
create extension if not exists pgcrypto;

create table chat (
    id uuid primary key,
    client_id uuid not null,
    title varchar(255) not null,
    current_transcript_id uuid null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index idx_chat_client_updated
    on chat (client_id, updated_at desc, created_at desc);

create table chat_transcript (
    id uuid primary key,
    chat_id uuid not null references chat(id) on delete cascade,
    memory jsonb not null default '{"text": ""}'::jsonb,
    input_tokens integer null,
    compacted_from_transcript_id uuid null references chat_transcript(id) on delete set null,
    compaction_level integer not null default 0,
    created_at timestamptz not null default current_timestamp
);

create index idx_chat_transcript_chat_created
    on chat_transcript (chat_id, created_at asc);

alter table chat
    add constraint fk_chat_current_transcript
    foreign key (current_transcript_id) references chat_transcript(id) on delete set null;

create table chat_message (
    id bigserial primary key,
    transcript_id uuid not null references chat_transcript(id) on delete cascade,
    role varchar(16) not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    constraint chk_chat_message_role
        check (role in ('user', 'assistant', 'tool'))
);

create index idx_chat_message_transcript_id_id
    on chat_message (transcript_id, id asc);

create table vector_store (
    id uuid primary key default gen_random_uuid(),
    content text,
    metadata json,
    embedding vector(1024)
);

create index vector_store_embedding_hnsw_idx
    on vector_store using hnsw (embedding vector_cosine_ops);

create table student_profile (
    client_id uuid primary key,
    preferred_language varchar(8) not null default 'es',
    needs_concrete_examples boolean not null default false,
    theme_preference varchar(16) not null default 'SYSTEM',
    learning_profile jsonb not null default '{}'::jsonb,
    last_updated_at timestamptz not null default current_timestamp,
    profile_version bigint not null default 1,
    constraint chk_student_profile_theme_preference
        check (theme_preference in ('SYSTEM', 'LIGHT', 'DARK'))
);

create table student_misconception (
    id bigserial primary key,
    client_id uuid not null references student_profile(client_id) on delete cascade,
    topic_key varchar(32) not null,
    misconception_key varchar(64) not null,
    description text not null,
    status varchar(16) not null default 'ACTIVE',
    last_seen_at timestamptz not null default current_timestamp,
    constraint chk_student_misconception_status
        check (status in ('ACTIVE', 'COOLDOWN', 'RESOLVED'))
);

create unique index idx_student_misconception_client_key
    on student_misconception (client_id, misconception_key);

create table student_profile_signal (
    id bigserial primary key,
    client_id uuid not null references student_profile(client_id) on delete cascade,
    conversation_id uuid null references chat(id) on delete set null,
    turn_id uuid not null,
    signal_type varchar(32) not null,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp
);

create index idx_student_profile_signal_client_created
    on student_profile_signal (client_id, created_at desc);

create table ingested_document (
    id uuid primary key,
    client_id uuid not null,
    original_filename varchar(255) not null,
    mime_type varchar(255) not null,
    source_type varchar(255) not null,
    docling_format varchar(255) not null,
    checksum_sha256 varchar(64) not null,
    status varchar(24) not null,
    reviewed_markdown text null,
    page_count integer null,
    catalog_title varchar(255) null,
    catalog_topic varchar(255) null,
    catalog_summary varchar(255) null,
    catalog_tags jsonb not null default '[]'::jsonb,
    catalog_entities jsonb not null default '[]'::jsonb,
    catalog_question_examples jsonb not null default '[]'::jsonb,
    catalog_stale boolean not null default false,
    catalog_updated_at timestamptz null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index idx_ingested_document_client_updated
    on ingested_document (client_id, updated_at desc);

create index idx_ingested_document_indexed_client_updated
    on ingested_document (client_id, updated_at desc)
    where status = 'INDEXED';

create table document_ingestion_job (
    id uuid primary key,
    document_id uuid not null references ingested_document(id) on delete cascade,
    stage varchar(24) not null,
    progress_label varchar(255) not null,
    failure_message varchar(255) null,
    started_at timestamptz not null default current_timestamp,
    completed_at timestamptz null
);

create index idx_document_ingestion_job_document_started
    on document_ingestion_job (document_id, started_at desc);

create table document_segment (
    id uuid primary key,
    document_id uuid not null references ingested_document(id) on delete cascade,
    ordinal integer not null,
    heading_path varchar(255) null,
    content text not null,
    approved boolean not null default false,
    edited boolean not null default false,
    char_count integer not null,
    token_count integer not null,
    page_number integer null,
    source_page_numbers jsonb not null default '[]'::jsonb,
    doc_items jsonb not null default '[]'::jsonb,
    captions jsonb not null default '[]'::jsonb,
    raw_text text null,
    chunker varchar(255) not null default 'DOCLING_HYBRID',
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index idx_document_segment_document_ordinal
    on document_segment (document_id, ordinal asc);

create table subject (
    id uuid primary key default gen_random_uuid(),
    slug varchar(96) not null unique,
    display_name text not null,
    status varchar(24) not null,
    current_config_revision_id uuid null unique,
    config_version bigint not null default 1,
    lock_version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_subject_status
        check (status in ('ACTIVE', 'ARCHIVED'))
);

create table subject_config_revision (
    id uuid primary key default gen_random_uuid(),
    subject_id uuid not null references subject(id) on delete cascade,
    version bigint not null,
    config jsonb not null default '{}'::jsonb,
    rubric_defaults jsonb not null default '{}'::jsonb,
    question_policy jsonb not null default '{}'::jsonb,
    created_by varchar(255) not null default 'system',
    created_at timestamptz not null default current_timestamp,
    unique (subject_id, version)
);

alter table subject
    add constraint fk_subject_current_config_revision
    foreign key (current_config_revision_id) references subject_config_revision(id) on delete set null;

create table evaluation (
    id uuid primary key default gen_random_uuid(),
    subject_id uuid not null references subject(id) on delete cascade,
    slug varchar(96) not null,
    title text not null,
    status varchar(24) not null,
    current_revision_id uuid null unique,
    lock_version bigint not null default 0,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_evaluation_status
        check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    unique (subject_id, slug)
);

create table evaluation_revision (
    id uuid primary key default gen_random_uuid(),
    evaluation_id uuid not null references evaluation(id) on delete cascade,
    subject_config_revision_id uuid not null references subject_config_revision(id),
    version bigint not null,
    instructions text not null,
    settings jsonb not null default '{}'::jsonb,
    rubric jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default current_timestamp,
    unique (evaluation_id, version)
);

alter table evaluation
    add constraint fk_evaluation_current_revision
    foreign key (current_revision_id) references evaluation_revision(id) on delete set null;

create table evaluation_question_example (
    id uuid primary key default gen_random_uuid(),
    evaluation_revision_id uuid not null references evaluation_revision(id) on delete cascade,
    example_key varchar(96) not null,
    ordinal integer not null,
    guidance text not null,
    rubric jsonb not null default '{}'::jsonb,
    unique (evaluation_revision_id, example_key)
);

create table evaluation_attempt (
    id uuid primary key default gen_random_uuid(),
    evaluation_revision_id uuid not null references evaluation_revision(id),
    client_id uuid not null,
    chat_id uuid null references chat(id) on delete set null,
    status varchar(24) not null,
    started_at timestamptz not null default current_timestamp,
    submitted_at timestamptz null,
    graded_at timestamptz null,
    profile_snapshot jsonb not null default '{}'::jsonb,
    profile_version bigint not null default 0,
    score numeric(5, 2) null,
    feedback jsonb not null default '{}'::jsonb,
    lock_version bigint not null default 0,
    constraint chk_evaluation_attempt_status
        check (status in ('IN_PROGRESS', 'SUBMITTED', 'GRADED'))
);

create index idx_evaluation_attempt_client_started
    on evaluation_attempt (client_id, started_at desc);

create table evaluation_attempt_question (
    id uuid primary key default gen_random_uuid(),
    attempt_id uuid not null references evaluation_attempt(id) on delete cascade,
    source_example_id uuid null references evaluation_question_example(id) on delete set null,
    question_key varchar(96) not null,
    blueprint_key varchar(96) not null,
    ordinal integer not null,
    question_snapshot jsonb not null default '{}'::jsonb,
    question_hash varchar(64) not null
);

create index idx_evaluation_attempt_question_attempt_ordinal
    on evaluation_attempt_question (attempt_id, ordinal asc);

create table evaluation_attempt_response (
    id bigserial primary key,
    attempt_question_id uuid not null references evaluation_attempt_question(id) on delete cascade,
    free_text text null,
    selected_options jsonb not null default '[]'::jsonb,
    score numeric(5, 2) null,
    rubric_result jsonb not null default '{}'::jsonb,
    feedback text null,
    answered_at timestamptz not null default current_timestamp
);

create index idx_evaluation_attempt_response_question
    on evaluation_attempt_response (attempt_question_id, answered_at desc);

insert into subject (id, slug, display_name, status, config_version)
values (
    '11111111-1111-1111-1111-111111111111',
    'introduccion-algoritmia',
    'Introducción a la Algoritmia',
    'ACTIVE',
    1
);

insert into subject_config_revision (
    id,
    subject_id,
    version,
    config,
    rubric_defaults,
    question_policy,
    created_by
)
values (
    '11111111-1111-1111-1111-111111111112',
    '11111111-1111-1111-1111-111111111111',
    1,
    '{
      "scope": "Tutoría de introducción a la algoritmia con ejemplos en C cuando sea útil.",
      "learningObjectives": [
        "razonar con variables y tipos",
        "trazar condicionales",
        "trazar bucles",
        "distinguir contador y acumulador",
        "descomponer problemas en pseudocódigo"
      ],
      "topicTaxonomy": ["variables", "conditionals", "loops", "functions", "arrays"],
      "misconceptionCatalog": ["counter_vs_accumulator", "array_index_origin", "loop_condition"],
      "allowedLanguages": ["es", "en"]
    }'::jsonb,
    '{
      "scale": "0-100",
      "dimensions": ["conceptual_accuracy", "trace_quality", "decomposition", "edge_case_reasoning"],
      "evidenceRule": "Never assign strong mastery without answer evidence."
    }'::jsonb,
    '{"maxQuestions": 3, "difficultyBands": ["foundation", "developing"]}'::jsonb,
    'system'
);

update subject
set current_config_revision_id = '11111111-1111-1111-1111-111111111112',
    updated_at = current_timestamp
where id = '11111111-1111-1111-1111-111111111111';

insert into evaluation (id, subject_id, slug, title, status)
values (
    '22222222-2222-2222-2222-222222222221',
    '11111111-1111-1111-1111-111111111111',
    'diagnostico-inicial',
    'Diagnóstico inicial',
    'PUBLISHED'
);

insert into evaluation_revision (
    id,
    evaluation_id,
    subject_config_revision_id,
    version,
    instructions,
    settings,
    rubric
)
values (
    '22222222-2222-2222-2222-222222222222',
    '22222222-2222-2222-2222-222222222221',
    '11111111-1111-1111-1111-111111111112',
    1,
    'Genera un diagnóstico breve y personalizado para detectar razonamiento inicial, fortalezas, debilidades y misconceptions sin asumir un nivel global ambiguo.',
    '{"timeBoxMinutes": 20, "allowFreeText": true, "showReviewBeforeSubmit": true, "questionCount": 3}'::jsonb,
    '{"passingScore": 70, "profileEvidenceOnly": true}'::jsonb
);

update evaluation
set current_revision_id = '22222222-2222-2222-2222-222222222222',
    updated_at = current_timestamp
where id = '22222222-2222-2222-2222-222222222221';

insert into evaluation_question_example (
    id,
    evaluation_revision_id,
    example_key,
    ordinal,
    guidance,
    rubric
)
values
(
    '33333333-3333-3333-3333-333333333331',
    '22222222-2222-2222-2222-222222222222',
    'loop-trace-counter',
    1,
    'Probe whether the student can trace variable state through a loop and distinguish a counter from an accumulator. Generate a fresh prompt; do not reuse this wording.',
    '{"dimensions": ["trace_quality", "counter_vs_accumulator"]}'::jsonb
),
(
    '33333333-3333-3333-3333-333333333332',
    '22222222-2222-2222-2222-222222222222',
    'conditional-boundary',
    2,
    'Probe boundary-condition reasoning with a fresh scenario where inclusive vs exclusive comparison matters.',
    '{"dimensions": ["conceptual_accuracy", "edge_case_reasoning"]}'::jsonb
);
