export type ReviewStatus =
  | 'IDLE'
  | 'LOCAL_INVALID'
  | 'READY_TO_SAVE'
  | 'NEEDS_USER_FIX'
  | 'PENDING'
  | 'REVIEWING'
  | 'COMPLETED'
  | 'COMPLETED_FROM_CACHE'
  | 'SKIPPED_NO_CHANGES'
  | 'FAILED'
  | 'UNAVAILABLE';

export type QualityStatus = 'GOOD' | 'NEEDS_IMPROVEMENT';
export type IssueSeverity = 'ERROR' | 'WARNING' | 'INFO';
export type IssuePresentationState = 'OPEN' | 'APPLIED';

export type InstructionLintIssue = {
  issueKey: string;
  code: string;
  severity: IssueSeverity;
  startOffset?: number | null;
  endOffset?: number | null;
  message: string;
  whyItMatters?: string;
  suggestedReplacement?: string;
  suggestionReason?: string;
};

export type InstructionReviewSnapshot = {
  activityId?: string;
  reviewHash?: string;
  reviewStatus?: ReviewStatus;
  qualityStatus?: QualityStatus | null;
  canSave?: boolean;
  message?: string;
  modelCalled?: boolean;
  fromCache?: boolean;
  issues?: InstructionLintIssue[];
  recreatedInstructions?: string;
  reviewedAt?: string;
};
