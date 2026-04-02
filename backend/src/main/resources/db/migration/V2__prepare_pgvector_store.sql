create extension if not exists vector;
create extension if not exists hstore;
create extension if not exists "uuid-ossp";

create table if not exists vector_store (
    id uuid primary key default uuid_generate_v4(),
    content text,
    metadata json,
    embedding vector(1024)
);

create index if not exists vector_store_embedding_hnsw_idx
    on vector_store using hnsw (embedding vector_cosine_ops);
