import { css } from 'lit';

export const instructionLinterStyles = css`
  :host {
    display: block;
    width: 100%;
    min-height: 9rem;
    position: relative;
  }

  .instruction-linter-editor__label {
    display: block;
    margin-bottom: 0.35rem;
    color: var(--vaadin-text-color);
    font-size: var(--aura-font-size-s);
    font-weight: 650;
  }

  .instruction-linter-editor__stale {
    margin-top: 0.5rem;
    color: var(--vaadin-text-color-secondary);
    font-size: var(--aura-font-size-xs);
  }

  .instruction-linter-editor__analysis-line {
    margin-top: 0.5rem;
    color: var(--vaadin-text-color-secondary);
    font-size: var(--aura-font-size-xs);
    line-height: 1.45;
  }

  .instruction-linter-editor__review {
    display: grid;
    gap: 0.65rem;
    margin-top: 0.85rem;
    padding: var(--vaadin-padding-m, 1rem);
    border: 1px solid var(--vaadin-border-color);
    border-radius: var(--vaadin-radius-m, 0.625rem);
    background: var(--vaadin-background-container-strong);
    color: var(--vaadin-text-color);
  }

  .instruction-linter-editor__review-header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.5rem;
  }

  .instruction-linter-editor__review-title {
    font-weight: 700;
  }

  .instruction-linter-editor__badge {
    padding: 0.12rem 0.45rem;
    border-radius: 999px;
    border: 1px solid var(--vaadin-border-color);
    font-size: var(--aura-font-size-xs);
    font-weight: 700;
  }

  .instruction-linter-editor__badge--reviewing,
  .instruction-linter-editor__badge--reviewing_before_save,
  .instruction-linter-editor__badge--pending {
    color: var(--aura-accent-text-color, var(--vaadin-primary-color));
    background: color-mix(in srgb, var(--aura-accent-text-color, var(--vaadin-primary-color)) 10%, transparent);
  }

  .instruction-linter-editor__badge--failed,
  .instruction-linter-editor__badge--review_error,
  .instruction-linter-editor__badge--unavailable,
  .instruction-linter-editor__badge--invalid {
    color: var(--aura-red, #ef4444);
    background: color-mix(in srgb, var(--aura-red, #ef4444) 8%, transparent);
  }

  .instruction-linter-editor__badge--good,
  .instruction-linter-editor__badge--ready_to_save,
  .instruction-linter-editor__badge--completed_from_cache,
  .instruction-linter-editor__badge--skipped_no_changes {
    color: var(--aura-green, #0f766e);
    background: color-mix(in srgb, var(--aura-green, #0f766e) 10%, transparent);
  }

  .instruction-linter-editor__badge--needs_improvement,
  .instruction-linter-editor__badge--needs_user_fix,
  .instruction-linter-editor__badge--local_invalid {
    color: var(--aura-yellow, #d97706);
    background: color-mix(in srgb, var(--aura-yellow, #d97706) 10%, transparent);
  }

  .instruction-linter-editor__verdict {
    margin: 0;
    color: var(--vaadin-text-color-secondary);
    line-height: 1.5;
  }

  .instruction-linter-editor__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .instruction-linter-editor__button {
    border: 1px solid color-mix(in srgb, var(--aura-accent-text-color, var(--vaadin-primary-color)) 38%, var(--vaadin-border-color));
    border-radius: var(--vaadin-radius-m, 0.625rem);
    padding: 0.45rem 0.75rem;
    background: color-mix(in srgb, var(--aura-accent-text-color, var(--vaadin-primary-color)) 9%, var(--vaadin-background-container));
    color: var(--aura-accent-text-color, var(--vaadin-primary-color));
    cursor: pointer;
    font: inherit;
    font-weight: 650;
  }

  .instruction-linter-editor__loading {
    display: inline-flex;
    align-items: center;
    gap: 0.65rem;
  }

  .instruction-linter-editor__spinner {
    width: 0.9rem;
    height: 0.9rem;
    border-radius: 50%;
    border: 2px solid color-mix(in srgb, var(--aura-accent-text-color, var(--vaadin-primary-color)) 20%, transparent);
    border-top-color: var(--aura-accent-text-color, var(--vaadin-primary-color));
    animation: instruction-linter-spin 0.85s linear infinite;
  }

  @keyframes instruction-linter-spin {
    to {
      transform: rotate(360deg);
    }
  }
`;
