alter table ingested_document
    add column if not exists catalog_title text null,
    add column if not exists catalog_topic text null,
    add column if not exists catalog_summary text null,
    add column if not exists catalog_tags jsonb not null default '[]'::jsonb,
    add column if not exists catalog_entities jsonb not null default '[]'::jsonb,
    add column if not exists catalog_question_examples jsonb not null default '[]'::jsonb,
    add column if not exists catalog_stale boolean not null default false,
    add column if not exists catalog_updated_at timestamptz null;

create index if not exists idx_ingested_document_indexed_client_updated
    on ingested_document (client_id, updated_at desc)
    where status = 'INDEXED';
