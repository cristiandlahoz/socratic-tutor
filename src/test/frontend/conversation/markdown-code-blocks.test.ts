import test from 'node:test';
import assert from 'node:assert/strict';

import { bindCodeBlockPlaceholders, renderMarkdownWithCodeBlockPlaceholders } from '../../../main/frontend/conversation/markdown-code-blocks.js';
import {
  bindSanitizedMarkdownRender,
  isCodeBlockViewerPlaceholderAttribute,
  isCodeBlockViewerPlaceholderTag,
  renderSanitizedMarkdownRender,
} from '../../../main/frontend/conversation/markdown-render-pipeline.js';

test('renderMarkdownWithCodeBlockPlaceholders renders fenced c blocks as code-block-viewer placeholders', () => {
  const rendered = renderMarkdownWithCodeBlockPlaceholders(`Observa esta variante:\n\n\`\`\`c\nfor (int i = 0; i < 3; i++)\n    printf("%d", i);\n\`\`\`\n\n¿Cuántas veces se ejecuta printf y por qué?`);

  assert.match(rendered.html, /code-block-viewer/);
  assert.equal(rendered.blocks.length, 1);
  assert.equal(rendered.blocks[0].lang, 'c');
  assert.match(rendered.blocks[0].code, /for \(int i = 0; i < 3; i\+\+\)/);
});

test('bindCodeBlockPlaceholders maps placeholder metadata into custom viewer properties', () => {
  const rendered = renderMarkdownWithCodeBlockPlaceholders(`Observa esta variante:\n\n\`\`\`c\nfor (int i = 0; i < 3; i++)\n    printf("%d", i);\n\`\`\``);
  const viewers = [
    { dataset: { codeBlockIndex: '0' }, value: '', lang: '', debuggable: false },
    { dataset: { codeBlockIndex: '99' }, value: '', lang: '', debuggable: false },
  ];

  bindCodeBlockPlaceholders(
    {
      querySelectorAll(selector: string) {
        assert.equal(selector, 'code-block-viewer[data-code-block-index]');
        return viewers;
      },
    },
    rendered.blocks,
    true,
  );

  assert.equal(viewers[0].value, rendered.blocks[0].code);
  assert.equal(viewers[0].lang, 'c');
  assert.equal(viewers[0].debuggable, true);
  assert.equal(viewers[1].value, '');
  assert.equal(viewers[1].lang, '');
  assert.equal(viewers[1].debuggable, false);
});

test('renderSanitizedMarkdownRender preserves code-block placeholders through sanitization and binds them', () => {
  const rendered = renderSanitizedMarkdownRender(
    `Observa esta variante:\n\n<script>alert('xss')</script>\n\n\`\`\`c\nfor (int i = 0; i < 3; i++)\n    printf("%d", i);\n\`\`\``,
    (html) => {
      assert.match(html, /<script>alert\('xss'\)<\/script>/);
      assert.match(html, /<code-block-viewer data-code-block-index="0"><\/code-block-viewer>/);
      return html.replace(/<script[\s\S]*?<\/script>/g, '');
    },
  );
  const viewers = [
    { dataset: { codeBlockIndex: '0' }, value: '', lang: '', debuggable: false },
  ];

  assert.doesNotMatch(rendered.html, /<script>/);
  assert.match(rendered.html, /<code-block-viewer data-code-block-index="0"><\/code-block-viewer>/);

  bindSanitizedMarkdownRender(
    {
      querySelectorAll(selector: string) {
        assert.equal(selector, 'code-block-viewer[data-code-block-index]');
        return viewers;
      },
    },
    rendered,
    true,
  );

  assert.equal(viewers[0].value, rendered.blocks[0].code);
  assert.equal(viewers[0].lang, 'c');
  assert.equal(viewers[0].debuggable, true);
});

test('markdown renderer placeholder retention only allows the custom element tag and data index attribute', () => {
  assert.equal(isCodeBlockViewerPlaceholderTag('code-block-viewer'), true);
  assert.equal(isCodeBlockViewerPlaceholderTag('script'), false);
  assert.equal(isCodeBlockViewerPlaceholderAttribute('data-code-block-index'), true);
  assert.equal(isCodeBlockViewerPlaceholderAttribute('onclick'), false);
});
