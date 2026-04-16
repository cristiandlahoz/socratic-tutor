# Chat UI Learnings

- `WidthAwareLabel` truncates by rendered width using `ResizeObserver` + canvas text measurement; keep the full value in `data-full-text`, `title`, and `aria-label`, then replace the last 3 displayable chars with `...` when overflow occurs.
- Leave a small safety reserve (`setSafetyPixels(...)`) for labels that can switch to heavier/gradient active styling; exact-fit truncation looked correct in code but overflowed in the selected sidebar state.
