create table invitation (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenant(id) on delete cascade,
    group_class_id uuid null references group_class(id) on delete cascade,
    invited_email text not null,
    target_role text not null,
    token_hash text not null,
    status text not null default 'PENDING',
    delivery_error text null,
    expires_at timestamptz not null,
    accepted_at timestamptz null,
    invited_by_account_id uuid null references account(id) on delete set null,
    invited_by_tenant_account_id uuid null references tenant_account(id) on delete set null,
    invited_by_group_class_member_id uuid null references group_class_member(id) on delete set null,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint chk_invitation_target_role check (target_role in ('TENANT_ADMIN', 'PROFESSOR', 'STUDENT')),
    constraint chk_invitation_status check (status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED', 'DELIVERY_FAILED')),
    constraint uk_invitation_token_hash unique (token_hash)
);

create index idx_invitation_tenant_id on invitation (tenant_id);
create index idx_invitation_group_class_id on invitation (group_class_id);
create index idx_invitation_invited_email on invitation (invited_email);
create index idx_invitation_status on invitation (status);
