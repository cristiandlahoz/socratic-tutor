alter table student_profile
    add column if not exists theme_preference varchar(16) not null default 'SYSTEM';
