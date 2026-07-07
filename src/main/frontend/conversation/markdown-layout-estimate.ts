type MarkdownEstimateOptions = {
  fontSizePx?: number;
  lineHeightRatio?: number;
  proseColumns?: number;
  codeColumns?: number;
  blockGapPx?: number;
  maxBlockSizePx?: number;
};

const DEFAULTS = {
  fontSizePx: 13,
  lineHeightRatio: 1.56,
  proseColumns: 76,
  codeColumns: 88,
  blockGapPx: 12,
  maxBlockSizePx: 50_000,
} satisfies Required<MarkdownEstimateOptions>;

export function estimateMarkdownBlockSize(markdown: string, options: MarkdownEstimateOptions = {}): number {
  if (!markdown.trim()) {
    return 0;
  }

  const settings = { ...DEFAULTS, ...options };
  const lineHeightPx = settings.fontSizePx * settings.lineHeightRatio;

  let totalHeight = 0;
  let blockCount = 0;
  let paragraph = '';
  let listHeight = 0;
  let tableRows = 0;
  let codeVisualLines = 0;
  let inCodeFence = false;

  const wrappedLines = (text: string, columns = settings.proseColumns): number =>
    Math.max(1, Math.ceil(text.trim().length / Math.max(1, columns)));

  const addBlock = (height: number): void => {
    if (height <= 0) {
      return;
    }

    totalHeight += height + (blockCount > 0 ? settings.blockGapPx : 0);
    blockCount += 1;
  };

  const flushParagraph = (): void => {
    if (!paragraph) {
      return;
    }

    addBlock(wrappedLines(paragraph) * lineHeightPx);
    paragraph = '';
  };

  const flushList = (): void => {
    if (listHeight <= 0) {
      return;
    }

    addBlock(listHeight);
    listHeight = 0;
  };

  const flushTable = (): void => {
    if (tableRows <= 0) {
      return;
    }

    addBlock(tableRows * (lineHeightPx * 1.42 + settings.fontSizePx * 1.24) + 2);
    tableRows = 0;
  };

  const flushTextBlocks = (): void => {
    flushParagraph();
    flushList();
    flushTable();
  };

  const addHeading = (text: string, level: number): void => {
    const scale = [2.42, 1.78, 1.42, 1.18, 1, 1][Math.max(0, level - 1)];
    const headingLineHeight = settings.fontSizePx * scale * 1.18;
    const columns = Math.max(28, Math.floor(settings.proseColumns / scale));

    const headingSpacing = level <= 2 ? settings.fontSizePx * 1.25 : settings.fontSizePx * 0.75;
    addBlock(wrappedLines(text, columns) * headingLineHeight + headingSpacing);
  };

  const estimateCodeBlockHeight = (visualLines: number): number =>
    Math.max(1, visualLines) * lineHeightPx * 1.55 + settings.fontSizePx * 2 + 2;

  for (const rawLine of markdown.replace(/\r\n?/g, '\n').split('\n')) {
    const line = rawLine.trim();

    if (line.startsWith('```')) {
      if (inCodeFence) {
        addBlock(estimateCodeBlockHeight(codeVisualLines));
        codeVisualLines = 0;
        inCodeFence = false;
      }
      else {
        flushTextBlocks();
        inCodeFence = true;
      }
      continue;
    }

    if (inCodeFence) {
      codeVisualLines += wrappedLines(rawLine, settings.codeColumns);
      continue;
    }

    if (!line) {
      flushTextBlocks();
      continue;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(line);

    if (heading) {
      flushTextBlocks();
      addHeading(heading[2], heading[1].length);
      continue;
    }

    if (/^\|.+\|$/.test(line)) {
      flushParagraph();
      flushList();
      tableRows += 1;
      continue;
    }

    const listItem = /^(?:[-*+] |\d+\.\s+)(.+)$/.exec(line);

    if (listItem) {
      flushParagraph();
      flushTable();
      listHeight += wrappedLines(listItem[1]) * lineHeightPx + settings.fontSizePx * 0.28 + (listHeight > 0 ? settings.fontSizePx * 0.28 : 0);
      continue;
    }

    flushList();
    flushTable();
    paragraph = paragraph ? `${paragraph} ${line}` : line;
  }

  flushTextBlocks();

  if (inCodeFence) {
    addBlock(estimateCodeBlockHeight(codeVisualLines));
  }

  return Math.min(settings.maxBlockSizePx, Math.ceil(totalHeight));
}
