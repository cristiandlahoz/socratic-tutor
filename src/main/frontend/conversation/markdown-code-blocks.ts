import { Marked, type RendererObject, type Tokens } from 'marked';

export type MarkdownCodeBlock = {
  code: string;
  lang: string;
};

export type CodeBlockViewerElement = {
  dataset: {
    codeBlockIndex?: string;
  };
  value: string;
  lang: string;
  debuggable: boolean;
};

export type CodeBlockViewerBindingTarget = {
  querySelectorAll(selectors: string): Iterable<CodeBlockViewerElement>;
};

function parseLanguage(info: string | undefined): string {
  return info?.trim().split(/\s+/)[0] ?? '';
}

export function renderMarkdownWithCodeBlockPlaceholders(markdown: string): {
  html: string;
  blocks: MarkdownCodeBlock[];
} {
  const blocks: MarkdownCodeBlock[] = [];
  const renderer: RendererObject = {
    code(token: Tokens.Code): string {
      const index = blocks.length;
      blocks.push({
        code: token.text,
        lang: parseLanguage(token.lang),
      });
      return `<code-block-viewer data-code-block-index="${index}"></code-block-viewer>`;
    },
  };

  const marked = new Marked({
    gfm: true,
    breaks: true,
    renderer,
  });

  return {
    html: marked.parse(markdown || '') as string,
    blocks,
  };
}

export function bindCodeBlockPlaceholders(
  target: CodeBlockViewerBindingTarget,
  blocks: MarkdownCodeBlock[],
  debuggableCodeBlocks: boolean,
): void {
  for (const viewer of target.querySelectorAll('code-block-viewer[data-code-block-index]')) {
    const index = Number(viewer.dataset.codeBlockIndex);
    const block = blocks[index];

    if (!block) {
      continue;
    }

    viewer.value = block.code;
    viewer.lang = block.lang;
    viewer.debuggable = debuggableCodeBlocks;
  }
}
