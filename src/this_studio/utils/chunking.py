"""
utils/chunking.py
-----------------
Graduated from notebooks/01-chunking.ipynb.

Provides:
  - Data models : RawDocument, Segment, Chunk
  - Ingestors   : BaseIngestor, EpubIngestor, WebsiteIngestor,
                  TutorialspointIngestor, LitmentorIngestor
  - Parsers     : BaseParser, MarkdownParser, HtmlParser
  - Chunker     : SemanticChunker
  - Runner      : run_all_sources()
"""

from __future__ import annotations

import hashlib
import json
import re
from abc import ABC, abstractmethod
from collections import Counter
from dataclasses import asdict, dataclass, field
from typing import List, Literal, Optional

import tiktoken

# ---------------------------------------------------------------------------
# Types
# ---------------------------------------------------------------------------

SegmentType = Literal["text", "code", "table", "list"]
SourceType = Literal["epub", "website"]

# ---------------------------------------------------------------------------
# Token helpers
# ---------------------------------------------------------------------------

TOKENIZER = tiktoken.get_encoding("cl100k_base")

MAX_CHUNK_TOKENS = 2048

_NOMIC_HARD_LIMIT = 8192
_EMBED_PREFIX = "search_document: "
_EMBED_PREFIX_TOKENS = len(TOKENIZER.encode(_EMBED_PREFIX))

EMBED_MAX_TOKENS = _NOMIC_HARD_LIMIT - _EMBED_PREFIX_TOKENS - 50  # 8139, safe margin


def count_tokens(text: str) -> int:
    return len(TOKENIZER.encode(text))


def count_embed_tokens(text: str) -> int:
    """Count tokens as nomic-embed-text will see them (prefix included)."""
    return _EMBED_PREFIX_TOKENS + count_tokens(text)


def exceeds_embed_limit(text: str) -> bool:
    return count_embed_tokens(text) > EMBED_MAX_TOKENS


# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------


@dataclass
class RawDocument:
    """Output of any ingestor."""

    doc_id: str
    source_name: str
    source_type: SourceType
    content: str
    title: str = ""
    metadata: dict = field(default_factory=dict)


@dataclass
class Segment:
    """Atomic parsed unit."""

    doc_id: str
    source_name: str
    title: str
    segment_type: SegmentType
    content: str
    heading_context: str = ""
    token_count: int = 0


@dataclass
class Chunk:
    """Final embeddable unit."""

    id: str
    source: str
    url: str
    title: str
    heading: str
    content: str
    has_code: bool
    tokens: int

    @property
    def content_hash(self) -> str:
        return hashlib.md5(self.content.encode()).hexdigest()


# ---------------------------------------------------------------------------
# Ingestors
# ---------------------------------------------------------------------------


class BaseIngestor(ABC):
    @abstractmethod
    def ingest(self) -> List[RawDocument]: ...


class EpubIngestor(BaseIngestor):
    def __init__(self, path: str, source_name: str = "epub"):
        self.path = path
        self.source_name = source_name

    def ingest(self) -> List[RawDocument]:
        import ebooklib
        from ebooklib import epub

        book = epub.read_epub(self.path)
        docs = []
        for item in book.get_items():
            if item.get_type() != ebooklib.ITEM_DOCUMENT:
                continue
            chapter_id = item.get_id()
            if chapter_id.lower().startswith("chapter-"):
                docs.append(
                    RawDocument(
                        doc_id=chapter_id,
                        source_name=self.source_name,
                        source_type="epub",
                        content=item.get_body_content().decode(
                            "utf-8", errors="replace"
                        ),
                        title=item.get_name(),
                        metadata={"book_title": book.title},
                    )
                )
        return docs


class WebsiteIngestor(BaseIngestor):
    """Crawls an entire website using the self-hosted Firecrawl instance."""

    def __init__(
        self,
        start_url: str,
        source_name: str = "web",
        api_url: str = "http://localhost:3002",
        limit: int = 200,
        exclude_patterns: Optional[List[str]] = None,
    ):
        self.start_url = start_url
        self.source_name = source_name
        self.api_url = api_url
        self.limit = limit
        self.exclude_patterns = exclude_patterns or []

    def ingest(self) -> List[RawDocument]:
        from firecrawl import FirecrawlApp
        from firecrawl.v2.types import ScrapeOptions

        fc = FirecrawlApp(api_url=self.api_url)
        crawl_result = fc.crawl(
            self.start_url,
            limit=self.limit,
            scrape_options=ScrapeOptions(
                formats=["markdown"],
                only_main_content=True,
                exclude_tags=[
                    "nav",
                    "footer",
                    "aside",
                    ".sidebar",
                    ".advertisement",
                    ".google-auto-placed",
                    ".site-header",
                    ".nav-links",
                ],
            ),
            exclude_paths=self.exclude_patterns,
        )
        docs = []
        for page in crawl_result.data:
            url = page.metadata.source_url
            markdown = page.markdown or ""
            if not markdown.strip():
                continue
            docs.append(
                RawDocument(
                    doc_id=url,
                    source_name=self.source_name,
                    source_type="website",
                    content=markdown,
                    title=page.metadata.title or "",
                    metadata={
                        "description": page.metadata.description or "",
                        "status_code": page.metadata.status_code,
                    },
                )
            )
        return docs


class TutorialspointIngestor(BaseIngestor):
    def __init__(self, urls: List[str], source_name: str = "tutorialspoint"):
        self.urls = urls
        self.source_name = source_name

    def ingest(self) -> List[RawDocument]:
        from firecrawl import FirecrawlApp

        fc = FirecrawlApp(api_url="http://localhost:3002")
        batch_result = fc.batch_scrape(
            self.urls,
            only_main_content=True,
            exclude_tags=[
                "nav",
                "footer",
                ".sidebar",
                ".advertisement",
                ".google-auto-placed",
            ],
        )
        docs = []
        for page in batch_result.data:
            url = page.metadata.source_url or page.metadata.url or ""
            markdown = page.markdown or ""
            if not markdown.strip():
                continue
            docs.append(
                RawDocument(
                    doc_id=url,
                    source_name=self.source_name,
                    source_type="website",
                    content=markdown,
                    title=page.metadata.title or "",
                )
            )
        print(f"  → {len(docs)}/{len(self.urls)} páginas scrapeadas")
        return docs


class LitmentorIngestor(BaseIngestor):
    def __init__(self, urls: List[str], source_name: str = "litmentor"):
        self.urls = urls
        self.source_name = source_name

    def ingest(self) -> List[RawDocument]:
        from firecrawl import FirecrawlApp

        fc = FirecrawlApp(api_url="http://localhost:3002")
        batch_result = fc.batch_scrape(
            self.urls,
            only_main_content=True,
            exclude_tags=[
                "nav",
                "footer",
                ".col-3",
                ".advertisement",
                ".google-auto-placed",
            ],
        )
        docs = []
        for page in batch_result.data:
            url = page.metadata.source_url or page.metadata.url or ""
            markdown = page.markdown or ""
            if not markdown.strip():
                continue
            markdown = markdown.replace("\\\\", "\\")
            docs.append(
                RawDocument(
                    doc_id=url,
                    source_name=self.source_name,
                    source_type="website",
                    content=markdown,
                    title=page.metadata.title or "",
                )
            )
        print(f"  → {len(docs)}/{len(self.urls)} páginas scrapeadas")
        return docs


# ---------------------------------------------------------------------------
# Parsers
# ---------------------------------------------------------------------------

_SETEXT_RE = re.compile(r"^[=\-]{2,}\s*$")


class BaseParser(ABC):
    @abstractmethod
    def parse(self, doc: RawDocument) -> List[Segment]: ...


class MarkdownParser(BaseParser):
    """
    Parses Firecrawl markdown output into Segments.
    Handles ATX headings, Setext headings, fenced code blocks,
    4-space/tab indented code blocks, and paragraphs.
    """

    def parse(self, doc: RawDocument) -> List[Segment]:
        segments: List[Segment] = []
        current_heading = ""
        content = re.sub(r"!\[([^\]]*)\]\([^)]*\)", r"\1", doc.content)
        lines = content.splitlines()
        i = 0

        while i < len(lines):
            line = lines[i]

            heading_match = re.match(r"^(#{1,4})\s+(.*)", line)
            if heading_match:
                current_heading = heading_match.group(2).strip()
                i += 1
                continue

            if (
                i + 1 < len(lines)
                and line.strip()
                and not line.startswith("    ")
                and not line.startswith("\t")
                and _SETEXT_RE.match(lines[i + 1].strip())
            ):
                current_heading = line.strip()
                i += 2
                continue

            fence_match = re.match(r"^(`{3,}|~{3,})", line.strip())
            if fence_match:
                fence_char = fence_match.group(1)[0]
                code_lines = []
                i += 1
                while i < len(lines) and not lines[i].strip().startswith(
                    fence_char * 3
                ):
                    code_lines.append(lines[i])
                    i += 1
                i += 1
                code = "\n".join(code_lines).strip()
                if code:
                    segments.append(
                        Segment(
                            doc_id=doc.doc_id,
                            source_name=doc.source_name,
                            title=doc.title,
                            segment_type="code",
                            content=code,
                            heading_context=current_heading,
                            token_count=count_tokens(code),
                        )
                    )
                continue

            if line.startswith("    ") or line.startswith("\t"):
                code_lines = []
                while i < len(lines) and (
                    lines[i].startswith("    ")
                    or lines[i].startswith("\t")
                    or lines[i].strip() == ""
                ):
                    stripped = (
                        lines[i][4:]
                        if lines[i].startswith("    ")
                        else lines[i].lstrip("\t")
                    )
                    code_lines.append(stripped if lines[i].strip() else "")
                    i += 1
                code = "\n".join(code_lines).strip()
                if code:
                    segments.append(
                        Segment(
                            doc_id=doc.doc_id,
                            source_name=doc.source_name,
                            title=doc.title,
                            segment_type="code",
                            content=code,
                            heading_context=current_heading,
                            token_count=count_tokens(code),
                        )
                    )
                continue

            if line.strip() and not _SETEXT_RE.match(line.strip()):
                para_lines = [line.strip()]
                i += 1
                while (
                    i < len(lines)
                    and lines[i].strip()
                    and not lines[i].startswith("#")
                    and not re.match(r"^(`{3,}|~{3,})", lines[i].strip())
                    and not lines[i].startswith("    ")
                    and not lines[i].startswith("\t")
                    and not _SETEXT_RE.match(lines[i].strip())
                    and not (
                        i + 1 < len(lines) and _SETEXT_RE.match(lines[i + 1].strip())
                    )
                ):
                    para_lines.append(lines[i].strip())
                    i += 1

                text = " ".join(para_lines)
                sub_texts: List[str] = []
                current_toks, current_parts = 0, []
                for sentence in re.split(r"(?<=[.!?])\s+", text):
                    t = count_tokens(sentence)
                    if current_toks + t > MAX_CHUNK_TOKENS and current_parts:
                        sub_texts.append(" ".join(current_parts))
                        current_parts, current_toks = [sentence], t
                    else:
                        current_parts.append(sentence)
                        current_toks += t
                if current_parts:
                    sub_texts.append(" ".join(current_parts))

                for sub_text in sub_texts:
                    if 30 < len(sub_text) <= 20000:
                        segments.append(
                            Segment(
                                doc_id=doc.doc_id,
                                source_name=doc.source_name,
                                title=doc.title,
                                segment_type="text",
                                content=sub_text,
                                heading_context=current_heading,
                                token_count=count_tokens(sub_text),
                            )
                        )
                continue

            i += 1

        return segments


class HtmlParser(BaseParser):
    """Parses EPUB HTML into Segments."""

    def parse(self, doc: RawDocument) -> List[Segment]:
        from bs4 import BeautifulSoup

        soup = BeautifulSoup(doc.content, "html.parser")
        segments: List[Segment] = []
        current_heading = ""
        body = soup.body or soup

        for element in body.find_all(
            ["h1", "h2", "h3", "h4", "pre", "p"], recursive=True
        ):
            if element.name in ("h1", "h2", "h3", "h4"):
                current_heading = element.get_text(strip=True)
            elif element.name == "pre":
                code = element.get_text().strip()
                if code:
                    segments.append(
                        Segment(
                            doc_id=doc.doc_id,
                            source_name=doc.source_name,
                            title=doc.title,
                            segment_type="code",
                            content=code,
                            heading_context=current_heading,
                            token_count=count_tokens(code),
                        )
                    )
            elif element.name == "p":
                if element.find_parent("pre"):
                    continue
                text = element.get_text(separator=" ", strip=True)
                if len(text) > 30:
                    sub_texts: List[str] = []
                    current_toks, current_parts = 0, []
                    for sentence in re.split(r"(?<=[.!?])\s+", text):
                        t = count_tokens(sentence)
                        if current_toks + t > MAX_CHUNK_TOKENS and current_parts:
                            sub_texts.append(" ".join(current_parts))
                            current_parts, current_toks = [sentence], t
                        else:
                            current_parts.append(sentence)
                            current_toks += t
                    if current_parts:
                        sub_texts.append(" ".join(current_parts))
                    for sub_text in sub_texts:
                        if 30 < len(sub_text) <= 20000:
                            segments.append(
                                Segment(
                                    doc_id=doc.doc_id,
                                    source_name=doc.source_name,
                                    title=doc.title,
                                    segment_type="text",
                                    content=sub_text,
                                    heading_context=current_heading,
                                    token_count=count_tokens(sub_text),
                                )
                            )

        return segments


# ---------------------------------------------------------------------------
# Chunker
# ---------------------------------------------------------------------------


class SemanticChunker:
    """
    Groups Segments into token-budget-aware Chunks.
    - Code segments anchor a chunk; surrounding text is added until budget fills.
    - Pure text segments are grouped greedily up to budget.
    - Output is clean markdown (## heading + fenced ```c blocks).
    - Guarantees no chunk exceeds nomic's hard limit (prefix included).
    """

    def __init__(self, max_tokens: int = MAX_CHUNK_TOKENS, source_name: str = "epub"):
        self.max_tokens = min(max_tokens, EMBED_MAX_TOKENS)
        self.source_name = source_name
        self._counter = 0

    def build_chunks(self, segments: List[Segment]) -> List[Chunk]:
        chunks: List[Chunk] = []
        i = 0
        while i < len(segments):
            seg = segments[i]
            if seg.segment_type == "code":
                if exceeds_embed_limit(seg.content):
                    chunks.extend(self._split_oversized_segment(seg))
                else:
                    chunks.append(self._build_code_chunk(segments, i))
                i += 1
            else:
                if exceeds_embed_limit(seg.content):
                    chunks.extend(self._split_oversized_segment(seg))
                    i += 1
                else:
                    chunk, consumed = self._build_text_chunk(segments, i)
                    chunks.append(chunk)
                    i += consumed

        enforced: List[Chunk] = []
        for chunk in chunks:
            if not exceeds_embed_limit(chunk.content):
                enforced.append(chunk)
            else:
                enforced.extend(self._enforce_limit(chunk))

        return self._deduplicate(enforced)

    def _build_code_chunk(self, segments: List[Segment], code_idx: int) -> Chunk:
        seg = segments[code_idx]
        _fence_overhead = count_tokens("```c\n\n```")
        _heading_overhead = (
            count_tokens(f"## {seg.heading_context}\n\n") if seg.heading_context else 0
        )
        budget = max(
            0, self.max_tokens - seg.token_count - _fence_overhead - _heading_overhead
        )
        before_texts, after_texts = [], []

        j = code_idx - 1
        while j >= 0 and segments[j].segment_type == "text" and budget > 0:
            if segments[j].token_count <= budget:
                before_texts.insert(0, segments[j].content)
                budget -= segments[j].token_count
            j -= 1
            break

        j = code_idx + 1
        while j < len(segments) and segments[j].segment_type == "text" and budget > 0:
            if segments[j].token_count <= budget:
                after_texts.append(segments[j].content)
                budget -= segments[j].token_count
            j += 1
            break

        surrounding = " ".join(before_texts + after_texts)
        parts = []
        if seg.heading_context:
            parts.append(f"## {seg.heading_context}")
        if surrounding:
            parts.append(surrounding)
        parts.append(f"```c\n{seg.content}\n```")
        content = "\n\n".join(parts)
        return self._make_chunk(seg, content, True)

    def _build_text_chunk(self, segments: List[Segment], start: int):
        group: List[str] = []
        i = start
        while i < len(segments) and segments[i].segment_type == "text":
            s = segments[i]
            candidate_group = group + [s.content]
            parts = []
            if segments[start].heading_context:
                parts.append(f"## {segments[start].heading_context}")
            parts.append(" ".join(candidate_group))
            candidate_content = "\n\n".join(parts)
            if exceeds_embed_limit(candidate_content):
                break
            group.append(s.content)
            i += 1

        if not group:
            group.append(segments[start].content)
            i = start + 1

        text = " ".join(group)
        parts = []
        if segments[start].heading_context:
            parts.append(f"## {segments[start].heading_context}")
        parts.append(text)
        content = "\n\n".join(parts)
        consumed = max(1, i - start)
        return self._make_chunk(segments[start], content, False), consumed

    def _split_oversized_segment(self, seg: Segment) -> List[Chunk]:
        heading_tokens = (
            count_tokens(f"## {seg.heading_context}\n\n") if seg.heading_context else 0
        )
        budget = EMBED_MAX_TOKENS - _EMBED_PREFIX_TOKENS - heading_tokens

        candidates = re.split(r"(?<=[.!?])\s+", seg.content)
        used_newlines = False
        if len(candidates) == 1:
            candidates = seg.content.splitlines(keepends=True)
            used_newlines = True
        if len(candidates) == 1 or all(count_tokens(c) > budget for c in candidates):
            candidates = seg.content.split()
            used_newlines = False

        sub_chunks: List[Chunk] = []
        current_parts: List[str] = []
        current_toks = 0

        for part in candidates:
            t = count_tokens(part)
            if t > budget:
                encoded = TOKENIZER.encode(part)
                part = TOKENIZER.decode(encoded[:budget])
                t = budget
            if current_toks + t > budget and current_parts:
                sep = "" if used_newlines else " "
                content = self._format_content(
                    seg, sep.join(current_parts), seg.segment_type == "code"
                )
                sub_chunks.append(
                    self._make_chunk(seg, content, seg.segment_type == "code")
                )
                current_parts, current_toks = [part], t
            else:
                current_parts.append(part)
                current_toks += t

        if current_parts:
            sep = "" if used_newlines else " "
            content = self._format_content(
                seg, sep.join(current_parts), seg.segment_type == "code"
            )
            sub_chunks.append(
                self._make_chunk(seg, content, seg.segment_type == "code")
            )

        return sub_chunks

    def _enforce_limit(self, chunk: Chunk) -> List[Chunk]:
        heading_prefix = ""
        body = chunk.content
        if chunk.content.startswith("## "):
            parts = chunk.content.split("\n\n", 1)
            if len(parts) == 2:
                heading_prefix = parts[0] + "\n\n"
                body = parts[1]

        heading_tokens = count_tokens(heading_prefix)
        budget = EMBED_MAX_TOKENS - _EMBED_PREFIX_TOKENS - heading_tokens

        candidates = re.split(r"(?<=[.!?])\s+", body)
        used_newlines = False
        if len(candidates) == 1:
            candidates = body.splitlines(keepends=True)
            used_newlines = True
        if len(candidates) == 1 or all(count_tokens(c) > budget for c in candidates):
            candidates = body.split()
            used_newlines = False

        sub_chunks: List[Chunk] = []
        current_parts: List[str] = []
        current_toks = 0

        for part in candidates:
            t = count_tokens(part)
            if t > budget:
                encoded = TOKENIZER.encode(part)
                part = TOKENIZER.decode(encoded[:budget])
                t = budget
            if current_toks + t > budget and current_parts:
                sep = "" if used_newlines else " "
                content = heading_prefix + sep.join(current_parts)
                sub_chunks.append(self._make_chunk_from_chunk(chunk, content))
                current_parts, current_toks = [part], t
            else:
                current_parts.append(part)
                current_toks += t

        if current_parts:
            sep = "" if used_newlines else " "
            content = heading_prefix + sep.join(current_parts)
            sub_chunks.append(self._make_chunk_from_chunk(chunk, content))

        return sub_chunks

    def _make_chunk_from_chunk(self, ref: Chunk, content: str) -> Chunk:
        cid = f"chunk_{self._counter:05d}"
        self._counter += 1
        return Chunk(
            id=cid,
            source=ref.source,
            url=ref.url,
            title=ref.title,
            heading=ref.heading,
            content=content,
            has_code=ref.has_code,
            tokens=count_tokens(content),
        )

    def _format_content(self, seg: Segment, text: str, is_code: bool) -> str:
        parts = []
        if seg.heading_context:
            parts.append(f"## {seg.heading_context}")
        parts.append(f"```c\n{text}\n```" if is_code else text)
        return "\n\n".join(parts)

    def _make_chunk(self, ref_seg: Segment, content: str, has_code: bool) -> Chunk:
        cid = f"chunk_{self._counter:05d}"
        self._counter += 1
        return Chunk(
            id=cid,
            source=ref_seg.source_name,
            url=ref_seg.doc_id,
            title=ref_seg.title,
            heading=ref_seg.heading_context,
            content=content,
            has_code=has_code,
            tokens=count_tokens(content),
        )

    @staticmethod
    def _deduplicate(chunks: List[Chunk]) -> List[Chunk]:
        seen, unique = set(), []
        for c in chunks:
            h = c.content_hash
            if h not in seen:
                seen.add(h)
                unique.append(c)
        return unique


# ---------------------------------------------------------------------------
# Pipeline runner
# ---------------------------------------------------------------------------


def run_all_sources(
    sources: list,
    output_path: str,
    max_chunk_tokens: int = 2048,
    min_chunk_tokens: int = 80,
) -> List[Chunk]:
    """
    Runs the full ingestion → parse → chunk pipeline for every source
    and merges into a single JSON file.

    Each element of `sources` is a dict: {"ingestor": <instance>, "parser_cls": <class>}
    """
    all_chunks: List[Chunk] = []
    global_counter = 0

    for cfg in sources:
        ingestor = cfg["ingestor"]
        parser_cls = cfg["parser_cls"]
        print(f"\n── {type(ingestor).__name__} ──")

        print("  Step 1: Ingesting...")
        raw_docs = ingestor.ingest()
        print(f"    → {len(raw_docs)} documents")

        print("  Step 2: Parsing...")
        parser = parser_cls()
        all_segments: List[Segment] = []
        for doc in raw_docs:
            all_segments.extend(parser.parse(doc))
        print(f"    → {len(all_segments)} segments")

        all_segments = [
            s
            for s in all_segments
            if not re.search(r"(www\.|ISBN|©|All rights reserved)", s.content, re.I)
        ]

        print("  Step 3: Chunking...")
        source_name = raw_docs[0].source_name if raw_docs else "unknown"
        chunker = SemanticChunker(max_tokens=max_chunk_tokens, source_name=source_name)
        chunker._counter = global_counter
        chunks = chunker.build_chunks(all_segments)
        global_counter = chunker._counter

        before = len(chunks)
        chunks = [c for c in chunks if c.has_code or c.tokens >= min_chunk_tokens]
        print(
            f"    → {len(chunks)} chunks  (dropped {before - len(chunks)} micro-chunks)"
        )
        print(f"       With code : {sum(1 for c in chunks if c.has_code)}")
        print(f"       Text only : {sum(1 for c in chunks if not c.has_code)}")

        violations = [c for c in chunks if exceeds_embed_limit(c.content)]
        if violations:
            print(f"    WARN: {len(violations)} chunks exceed nomic hard limit!")
        else:
            print(f"    OK: All chunks within nomic embed limit")

        all_chunks.extend(chunks)

    seen, unique = set(), []
    for c in all_chunks:
        h = c.content_hash
        if h not in seen:
            seen.add(h)
            unique.append(c)

    print(f"\n── TOTAL ──")
    print(f"  {len(unique)} chunks across {len(sources)} sources")
    dist = Counter(c.source for c in unique)
    for src, n in dist.items():
        print(f"    {src}: {n}")

    print("\n  Exporting...")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump([asdict(c) for c in unique], f, ensure_ascii=False, indent=2)
    print(f"  → Saved to {output_path}")

    return unique
