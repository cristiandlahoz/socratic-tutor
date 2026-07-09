import {
  bindCodeBlockPlaceholders,
  renderMarkdownWithCodeBlockPlaceholders,
  type CodeBlockViewerBindingTarget,
  type MarkdownCodeBlock,
} from './markdown-code-blocks.js';

export type SanitizedMarkdownRender = {
  html: string;
  blocks: MarkdownCodeBlock[];
};

export type MarkdownHtmlSanitizer = (html: string) => string;

export function isCodeBlockViewerPlaceholderTag(tagName: string): boolean {
  return tagName === 'code-block-viewer';
}

export function isCodeBlockViewerPlaceholderAttribute(attributeName: string): boolean {
  return attributeName === 'data-code-block-index';
}

export function renderSanitizedMarkdownRender(
  markdown: string,
  sanitizeHtml: MarkdownHtmlSanitizer,
): SanitizedMarkdownRender {
  const rendered = renderMarkdownWithCodeBlockPlaceholders(markdown);

  return {
    html: sanitizeHtml(rendered.html),
    blocks: rendered.blocks,
  };
}

export function bindSanitizedMarkdownRender(
  target: CodeBlockViewerBindingTarget,
  rendered: SanitizedMarkdownRender,
  debuggableCodeBlocks: boolean,
): void {
  bindCodeBlockPlaceholders(target, rendered.blocks, debuggableCodeBlocks);
}
