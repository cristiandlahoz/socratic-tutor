import type { Text } from '@codemirror/state';

import type { InstructionLintIssue, InstructionReviewSnapshot, QualityStatus, ReviewStatus } from './types.js';

const MIN_WHOLE_REPLACEMENT_PREFIX_CHARS = 12;

export function normalizeSnapshot(value: string | InstructionReviewSnapshot | null): InstructionReviewSnapshot | null {
  if (!value) {
    return null;
  }
  if (typeof value === 'string') {
    return value.trim() ? JSON.parse(value) as InstructionReviewSnapshot : null;
  }
  return value;
}

export function issueFromTo(issue: InstructionLintIssue, doc: Text): { from: number; to: number } {
  const from = Math.max(0, Math.min(issue.startOffset ?? 0, doc.length));
  const to = Math.max(from + 1, Math.min(issue.endOffset ?? doc.length, doc.length));
  return { from, to };
}

export function issueReplacementRange(issue: InstructionLintIssue, value: string): { from: number; to: number } {
  const from = Math.max(0, Math.min(issue.startOffset ?? 0, value.length));
  const to = Math.max(from, Math.min(issue.endOffset ?? value.length, value.length));
  if (shouldReplaceEntireValue(issue, value)) {
    return { from: 0, to: value.length };
  }
  return { from, to };
}

export function hasInstructionReviewFeedback(
  reviewStatus: ReviewStatus,
  qualityStatus: QualityStatus | undefined,
  message: string,
  issues: InstructionLintIssue[],
): boolean {
  return reviewStatus !== 'IDLE' || qualityStatus !== undefined || message.trim() !== '' || issues.length > 0;
}

export function hasInlineRange(issue: InstructionLintIssue | undefined | null): issue is InstructionLintIssue {
  return !!issue
    && typeof issue.startOffset === 'number'
    && typeof issue.endOffset === 'number'
    && issue.endOffset > issue.startOffset;
}

export function hasSuggestion(issue: InstructionLintIssue | undefined | null): boolean {
  return !!issue?.suggestedReplacement?.trim();
}

function shouldReplaceEntireValue(issue: InstructionLintIssue, value: string): boolean {
  if (!hasInlineRange(issue) || !hasSuggestion(issue)) {
    return false;
  }
  const normalizedCurrentValue = normalizeForComparison(value);
  const normalizedReplacement = normalizeForComparison(issue.suggestedReplacement ?? '');
  if (!normalizedCurrentValue) {
    return false;
  }
  if (normalizedReplacement.startsWith(normalizedCurrentValue)) {
    return true;
  }
  const rangeStart = Math.max(0, Math.min(issue.startOffset ?? 0, value.length));
  const prefixBeforeRange = normalizeForComparison(value.slice(0, rangeStart));
  if (prefixBeforeRange.length >= MIN_WHOLE_REPLACEMENT_PREFIX_CHARS
    && normalizedReplacement.startsWith(prefixBeforeRange)) {
    return true;
  }
  return false;
}

function normalizeForComparison(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}
