---
name: vaadin-ui-tuner-final-pass
description: Interpret pasted Socratic UI tuner exports and turn them into production Vaadin UI/CSS changes. Use when the user pastes UI tuner target/params/customCss or asks to apply tuner values. Never apply values verbatim; use them as intent/reference.
---

# Vaadin UI Tuner Final Pass

Use this skill when the user pastes output from the in-app UI tuner or asks to apply/tune/finalize UI values from it.

## Core rule

Treat tuner output as **design intent**, not implementation instructions.

Never copy the tuner selector or values blindly. The tuner uses temporary runtime attributes and inline preview styles. Your job is to infer what the user liked, locate the real component/source, and implement a durable, responsive, Vaadin-compliant style.

## Workflow

1. Read the pasted tuner output.
   - `target` tells you what the user visually selected.
   - `values` are approximate visual preferences.
   - `customCss` lists extra property/value experiments.
   - `selector` is usually temporary and should not be used in final CSS.
2. Locate the real source element.
   - Search Java components, Lit/TS components, and CSS by target clues: class names, tag names, text, Vaadin component type, route/page context.
   - For Vaadin web components, identify whether the final style belongs on:
     - the host class (`vaadin-text-area.foo`),
     - a documented `::part(...)`,
     - a CSS custom property/token,
     - or the wrapping Flow/Lit component.
3. Verify Vaadin semantics.
   - Prefer existing project classes/tokens.
   - Prefer Aura/base variables already used by the app.
   - Do not use Lumo variables in this project unless explicitly verified.
   - Do not style unsupported shadow internals. Use documented parts or component CSS properties.
4. Translate values thoughtfully.
   - Convert exact pixels to existing tokens when appropriate.
   - Make responsive values when the surface changes across mobile/tablet/desktop.
   - Use `clamp()` or media queries only when they solve real responsive behavior.
   - Preserve existing states: hover, focus, disabled, invalid, loading, selected.
   - Avoid ad-hoc one-off rules if a component-level or token-level rule is the real fix.
5. Keep scope surgical.
   - Implement only the intended visual change.
   - Do not refactor unrelated CSS.
   - If the tuner selected an internal generated or temporary element, map it to the nearest stable source class or documented part.
6. Verify visually.
   - Use the Vaadin UI debugging workflow when changing Vaadin UI/CSS.
   - Inspect computed styles and shadow parts in the browser.
   - Check at least the relevant desktop/mobile breakpoint if the tuner values came from viewport previews.

## Interpretation guide

- `radius`, `padding`, `gap`, `borderWidth`, `shadowBlur`, etc. indicate magnitude/preference, not exact final tokens.
- `surfaceAlpha`, `borderAlpha`, `shadowAlpha` indicate desired visual weight/subtlety.
- `customCss.color` or a color picker value means “make this feel like this color direction,” not necessarily that exact hex.
- If the user selected a shadow DOM part, final CSS should normally target the host plus `::part(part-name)` or a component variable.
- If the selected target is a pseudo-like visual (`::before`, header line, helper area), implement through the existing CSS rule that creates it; do not invent runtime selectors.

## Output expectation

When applying tuner output, summarize:

- Which real element/source you mapped the tuner target to.
- Which values you treated as design references.
- Which files changed.
- How you verified the final result.
