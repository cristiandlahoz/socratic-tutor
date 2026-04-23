create table if not exists ingested_document (
    id uuid primary key,
    client_id uuid not null,
    original_filename text not null,
    mime_type text not null,
    source_type varchar(32) not null,
    docling_format varchar(32) not null,
    checksum_sha256 varchar(64) not null,
    status varchar(24) not null,
    reviewed_markdown text null,
    page_count integer null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_ingested_document_client_updated
    on ingested_document (client_id, updated_at desc);

create table if not exists document_ingestion_job (
    id uuid primary key,
    document_id uuid not null references ingested_document(id) on delete cascade,
    stage varchar(24) not null,
    progress_label text not null,
    failure_message text null,
    started_at timestamptz not null default current_timestamp,
    completed_at timestamptz null
);

create index if not exists idx_document_ingestion_job_document_started
    on document_ingestion_job (document_id, started_at desc);

create table if not exists document_segment (
    id uuid primary key,
    document_id uuid not null references ingested_document(id) on delete cascade,
    ordinal integer not null,
    heading_path text null,
    content text not null,
    approved boolean not null default false,
    edited boolean not null default false,
    char_count integer not null,
    token_count integer not null,
    page_number integer null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create index if not exists idx_document_segment_document_ordinal
    on document_segment (document_id, ordinal asc);
