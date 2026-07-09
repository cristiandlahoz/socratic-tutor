import type { Extension } from '@codemirror/state';
import { Decoration, type DecorationSet, EditorView, ViewPlugin } from '@codemirror/view';

import type { InstructionLintIssue } from './types.js';
import { hasInlineRange, issueFromTo } from './utils.js';

export interface IssueDecorationHost {
  get visibleIssues(): InstructionLintIssue[];
  inlineIssueMarkClass(): string | null;
  handleIssuePointerLeave(): void;
  showIssueAtPointer(event: MouseEvent, view: EditorView): void;
  supportsInlineIssueCards(): boolean;
}

export function editorTheme(): Extension {
  return EditorView.theme({
    '&': {
      minHeight: '9rem',
      border: 'var(--vaadin-input-field-border-width, 1px) solid var(--vaadin-border-color)',
      borderRadius: 'var(--vaadin-radius-m, 0.625rem)',
      background: 'var(--vaadin-background-container)',
      color: 'var(--vaadin-text-color)',
    },
    '&.cm-focused': {
      outline: 'none',
      borderColor: 'var(--aura-accent-text-color, var(--vaadin-focus-ring-color))',
      boxShadow: '0 0 0 2px color-mix(in srgb, var(--aura-accent-text-color, var(--vaadin-focus-ring-color)) 18%, transparent)',
    },
    '.cm-scroller': {
      minHeight: '9rem',
      maxHeight: '18rem',
      overflow: 'auto',
      fontFamily: 'var(--aura-font-family, var(--vaadin-font-family), system-ui, sans-serif)',
      lineHeight: 'var(--aura-line-height-m, 1.5)',
    },
    '.cm-content': {
      minHeight: '9rem',
      padding: 'var(--vaadin-padding-s, 0.75rem)',
      fontFamily: 'var(--aura-font-family, var(--vaadin-font-family), system-ui, sans-serif)',
      fontSize: 'var(--aura-font-size-m, 0.9375rem)',
    },
    '.cm-gutters': {
      display: 'none !important',
    },
    '.instruction-linter-mark': {
      cursor: 'help',
    },
    '.instruction-linter-mark--error': {
      backgroundImage: 'none !important',
      textDecorationLine: 'underline',
      textDecorationStyle: 'wavy',
      textDecorationColor: 'var(--aura-red, #ef4444)',
      textUnderlineOffset: '3px',
    },
    '.instruction-linter-mark--warning': {
      backgroundImage: 'none !important',
      textDecorationLine: 'underline',
      textDecorationStyle: 'wavy',
      textDecorationColor: 'var(--aura-yellow, #d97706)',
      textUnderlineOffset: '3px',
    },
  });
}

export function issueDecorationExtension(host: IssueDecorationHost): Extension {
  return [
    ViewPlugin.fromClass(class {
      decorations: DecorationSet;

      constructor(view: EditorView) {
        this.decorations = this.buildDecorations(view);
      }

      update(update: { view: EditorView }): void {
        this.decorations = this.buildDecorations(update.view);
      }

      private buildDecorations(view: EditorView): DecorationSet {
        const markClass = host.inlineIssueMarkClass();
        if (!markClass || !host.supportsInlineIssueCards()) {
          return Decoration.none;
        }
        const mark = Decoration.mark({ class: markClass });
        const ranges = host.visibleIssues
          .filter((issue) => hasInlineRange(issue))
          .map((issue) => {
            const { from, to } = issueFromTo(issue, view.state.doc);
            return mark.range(from, to);
          })
          .sort((a, b) => a.from - b.from || a.to - b.to);
        return Decoration.set(ranges, true);
      }
    }, {
      decorations: (plugin) => plugin.decorations,
    }),
    EditorView.domEventHandlers({
      mousemove: (event, view) => {
        host.showIssueAtPointer(event, view);
        return false;
      },
      click: (event, view) => {
        host.showIssueAtPointer(event, view);
        return false;
      },
      mouseleave: () => {
        host.handleIssuePointerLeave();
        return false;
      },
    }),
  ];
}
