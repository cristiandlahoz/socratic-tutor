import test from 'node:test';
import assert from 'node:assert/strict';

import { resolveLoadingLabel } from '../../../main/frontend/conversation/message-item-loading-label.js';

test('resolveLoadingLabel falls back to the generic tutor label when loadingLabel is missing', () => {
  assert.equal(resolveLoadingLabel(undefined), 'Generando respuesta');
  assert.equal(resolveLoadingLabel(null), 'Generando respuesta');
  assert.equal(resolveLoadingLabel(''), 'Generando respuesta');
  assert.equal(resolveLoadingLabel('   '), 'Generando respuesta');
});

test('resolveLoadingLabel preserves contextual loading labels', () => {
  assert.equal(resolveLoadingLabel('Generando pregunta'), 'Generando pregunta');
});
