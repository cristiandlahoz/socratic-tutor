alter table document_segment
    add column if not exists source_page_numbers jsonb not null default '[]'::jsonb,
    add column if not exists doc_items jsonb not null default '[]'::jsonb,
    add column if not exists captions jsonb not null default '[]'::jsonb,
    add column if not exists raw_text text null,
    add column if not exists chunker varchar(64) not null default 'DOCLING_HYBRID';
