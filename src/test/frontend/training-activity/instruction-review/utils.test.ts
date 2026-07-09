import test from 'node:test';
import assert from 'node:assert/strict';

import { issueReplacementRange } from '../../../../main/frontend/training-activity/instruction-review/utils.js';
import type { InstructionLintIssue } from '../../../../main/frontend/training-activity/instruction-review/types.js';

const currentValue = 'quiero que se hagan preguntas sobre arreglos';

function issue(overrides: Partial<InstructionLintIssue>): InstructionLintIssue {
  return {
    issueKey: 'issue-1',
    code: 'TOO_GENERIC',
    severity: 'WARNING',
    message: 'message',
    ...overrides,
  };
}

test('issueReplacementRange replaces the entire value for full rewrites with out-of-bounds offsets', () => {
  const replacement = `${currentValue}, como recorrerlos, acceder a ellos e introducir valores`;

  assert.deepEqual(
    issueReplacementRange(issue({
      startOffset: currentValue.length + 12,
      endOffset: currentValue.length + 32,
      suggestedReplacement: replacement,
    }), currentValue),
    { from: 0, to: currentValue.length },
  );
});

test('issueReplacementRange keeps end insertion when the suggestion is not a full rewrite', () => {
  assert.deepEqual(
    issueReplacementRange(issue({
      startOffset: currentValue.length + 12,
      endOffset: currentValue.length + 32,
      suggestedReplacement: ' y ejercicios cortos',
    }), currentValue),
    { from: currentValue.length, to: currentValue.length },
  );
});

test('issueReplacementRange treats full-sentence replacement with partial offsets as a whole rewrite', () => {
  const value = 'quiero que hagas preguntas sobre bucles, enfocadas en el recorrido y depuración.';
  const replacement = 'quiero que hagas preguntas sobre bucles, enfocadas en el recorrido';

  assert.equal(value.length, 80);
  const range = issueReplacementRange(issue({
    startOffset: 34,
    endOffset: 76,
    suggestedReplacement: replacement,
  }), value);

  assert.deepEqual(range, { from: 0, to: value.length });
  assert.equal(`${value.slice(0, range.from)}${replacement}${value.slice(range.to)}`, replacement);
});
