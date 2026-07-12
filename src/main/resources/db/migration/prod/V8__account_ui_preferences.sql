alter table account
    add column ui_preferences jsonb not null default '{"theme":"system","baseFontSize":13}'::jsonb;
