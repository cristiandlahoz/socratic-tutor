package com.wornux.documentingest;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentSegmentationService {

  private final DocumentIngestionProperties properties;

  public DocumentSegmentationService(DocumentIngestionProperties properties) {
    this.properties = properties;
  }

  public List<SegmentDraft> segmentMarkdown(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return List.of();
    }

    List<MarkdownBlock> blocks = extractBlocks(markdown);
    List<SegmentDraft> segments = new ArrayList<>();
    int ordinal = 1;
    for (var block : blocks) {
      for (String chunk : splitWithOverlap(block.content())) {
        if (!chunk.isBlank()) {
          segments.add(new SegmentDraft(ordinal++, block.headingPath(), chunk));
        }
      }
    }
    return segments;
  }

  private List<MarkdownBlock> extractBlocks(String markdown) {
    List<MarkdownBlock> blocks = new ArrayList<>();
    List<String> headingStack = new ArrayList<>();
    StringBuilder currentBlock = new StringBuilder();

    for (String line : markdown.split("\\R")) {
      if (line.matches("^#{1,6}\\s+.*")) {
        flushBlock(blocks, headingStack, currentBlock);
        updateHeadingStack(headingStack, line);
        continue;
      }

      currentBlock.append(line).append('\n');
      if (line.isBlank()) {
        flushBlock(blocks, headingStack, currentBlock);
      }
    }

    flushBlock(blocks, headingStack, currentBlock);
    return blocks;
  }

  private void updateHeadingStack(List<String> headingStack, String headingLine) {
    int level = 0;
    while (level < headingLine.length() && headingLine.charAt(level) == '#') {
      level++;
    }
    String title = headingLine.substring(level).trim();
    while (headingStack.size() >= level) {
      headingStack.removeLast();
    }
    headingStack.add(title);
  }

  private void flushBlock(
      List<MarkdownBlock> blocks, List<String> headingStack, StringBuilder currentBlock) {
    String text = currentBlock.toString().trim();
    currentBlock.setLength(0);
    if (text.isBlank()) {
      return;
    }
    String headingPath = headingStack.isEmpty() ? "Documento" : String.join(" / ", headingStack);
    blocks.add(new MarkdownBlock(headingPath, text));
  }

  private List<String> splitWithOverlap(String text) {
    int maxChars = properties.getSegmentMaxChars();
    int overlapChars = properties.getSegmentOverlapChars();
    if (text.length() <= maxChars) {
      return List.of(text.trim());
    }

    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(text.length(), start + maxChars);
      if (end < text.length()) {
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + (maxChars / 2)) {
          end = paragraphBreak;
        } else {
          int sentenceBreak = text.lastIndexOf(". ", end);
          if (sentenceBreak > start + (maxChars / 2)) {
            end = sentenceBreak + 1;
          }
        }
      }

      String chunk = text.substring(start, end).trim();
      if (!chunk.isBlank()) {
        chunks.add(chunk);
      }
      if (end >= text.length()) {
        break;
      }
      start = Math.max(end - overlapChars, start + 1);
    }
    return chunks;
  }

  public record SegmentDraft(int ordinal, String headingPath, String content) {}

  private record MarkdownBlock(String headingPath, String content) {}
}
