alter table training_activity_assignment
    add column current_question text null,
    add column question_count integer not null default 0,
    add column evaluation_transcript text not null default '[]',
    add column final_report text null;
