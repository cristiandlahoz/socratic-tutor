alter table training_activity
    add column instruction_review_instructions_hash text null,
    add column instruction_review_hash text null,
    add column instruction_review_status text null,
    add column instruction_review_message text null,
    add column instruction_review_valid_instruction boolean null,
    add column instruction_review_quality_status text null,
    add column instruction_review_summary text null,
    add column instruction_review_issues_json text null,
    add column instruction_review_improved_instructions text null,
    add column instruction_review_model_name text null,
    add column instruction_review_rubric_version text null,
    add column instruction_review_prompt_version text null,
    add column instruction_reviewed_at timestamptz null,
    add constraint chk_training_activity_instruction_review_status
        check (
            instruction_review_status in (
                'IDLE',
                'LOCAL_INVALID',
                'PENDING',
                'REVIEWING',
                'COMPLETED',
                'COMPLETED_FROM_CACHE',
                'SKIPPED_NO_CHANGES',
                'FAILED',
                'UNAVAILABLE'
            ) or instruction_review_status is null
        ),
    add constraint chk_training_activity_instruction_review_quality_status
        check (
            instruction_review_quality_status in ('GOOD', 'NEEDS_IMPROVEMENT')
            or instruction_review_quality_status is null
        ),
    add constraint chk_training_activity_instruction_review_validity
        check (
            instruction_review_quality_status is not null
            or instruction_review_valid_instruction is null
            or instruction_review_valid_instruction = false
        );

create index idx_training_activity_instruction_review_hash
    on training_activity (instruction_review_hash);

create index idx_training_activity_instruction_review_instructions_hash
    on training_activity (instruction_review_instructions_hash);

create table instruction_review_cache (
    review_hash text primary key,
    prompt_version text not null,
    model_name text not null,
    normalized_title_hash text not null,
    normalized_instructions_hash text not null,
    review_status text not null,
    quality_status text null,
    valid_instruction boolean null,
    issues_json text null,
    review_message text null,
    recreated_instructions text null,
    created_at timestamptz not null,
    completed_at timestamptz null,
    constraint chk_instruction_review_cache_status
        check (review_status in ('COMPLETED')),
    constraint chk_instruction_review_cache_quality_status
        check (quality_status in ('GOOD', 'NEEDS_IMPROVEMENT') or quality_status is null),
    constraint chk_instruction_review_cache_validity
        check (
            quality_status is not null
            or valid_instruction is null
            or valid_instruction = false
        )
);

create index idx_instruction_review_cache_normalized_instructions_hash
    on instruction_review_cache (normalized_instructions_hash);

alter table training_activity_assignment
    add column last_tutor_decision_json text null,
    add column tutor_answer_quality text null,
    add column tutor_evidence_status text null,
    add column tutor_coverage_status text null,
    add column tutor_pedagogical_move text null,
    add column covered_instruction_aspects_json text null,
    add column missing_instruction_aspects_json text null,
    add column unproductive_pattern_detected boolean not null default false,
    add column insufficient_evidence boolean not null default false,
    add column tutor_decision_reason text null,
    add column tutor_model_name text null,
    add column tutor_prompt_version text null,
    add constraint chk_training_activity_assignment_tutor_answer_quality
        check (
            tutor_answer_quality in (
                'EMPTY',
                'ABSURD',
                'OFF_TOPIC',
                'TOO_VAGUE',
                'PARTIALLY_CORRECT',
                'GOOD',
                'EXCELLENT'
            ) or tutor_answer_quality is null
        ),
    add constraint chk_training_activity_assignment_tutor_evidence_status
        check (
            tutor_evidence_status in (
                'NO_EVIDENCE',
                'WEAK_EVIDENCE',
                'PARTIAL_EVIDENCE',
                'STRONG_EVIDENCE'
            ) or tutor_evidence_status is null
        ),
    add constraint chk_training_activity_assignment_tutor_coverage_status
        check (
            tutor_coverage_status in ('NONE', 'WEAK', 'PARTIAL', 'SUFFICIENT')
            or tutor_coverage_status is null
        ),
    add constraint chk_training_activity_assignment_tutor_pedagogical_move
        check (
            tutor_pedagogical_move in (
                'REFOCUS',
                'REPHRASE',
                'ASK_FOR_CLARITY',
                'ASK_FOR_EXAMPLE',
                'ASK_FOR_JUSTIFICATION',
                'PROBE_MISCONCEPTION',
                'INCREASE_DIFFICULTY',
                'MOVE_TO_NEXT_ASPECT',
                'TRANSFER_TO_NEW_CASE',
                'COMPLETE_SUCCESSFULLY',
                'COMPLETE_WITH_INSUFFICIENT_EVIDENCE'
            ) or tutor_pedagogical_move is null
        );
