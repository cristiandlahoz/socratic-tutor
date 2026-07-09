---
name: visual-verification
description: Visually verify implemented Vaadin UI changes using agent-browser. Use after implementing UI changes or when reviewing/debugging Vaadin views, theme CSS, component styling, layouts, shadow DOM parts, Aura/Lumo/base variables, stale selectors, and visual behavior.
---

# Visual Verification

Use this skill whenever a task changes or reviews a Vaadin UI, theme, component CSS, layout, or visual behavior. It is based on the global `vaadin-view-debugging` workflow, renamed locally as `visual-verification`, with the project-specific visual QA checklist included below.

Use `agent-browser` for normal visual verification, DOM inspection, screenshots, route walkthroughs, and CSS debugging. Use Playwright only when checking animations or scroll interactions where frame-by-frame timing, smooth scroll behavior, or gesture simulation matters.

## Required setup

1. Verify the Vaadin version from project files, such as `pom.xml`. Do not assume a version.
2. Call the Vaadin primer tool for the verified version before using Vaadin-specific guidance.
3. If browser interaction is needed, load the `agent-browser` skill and use `agent-browser skills get core` before running commands.
4. Prefer stable authenticated sessions for UI review:

```bash
SESSION="$(agent-browser session id --scope worktree --prefix <project-role>)"
agent-browser --session "$SESSION" --restore open "http://localhost:${PORT:-3321}"
```

If the app has saved auth profiles, use `agent-browser auth login <profile>` only when restore is not already authenticated.

Unless the use case specifies a particular resolution, use **1920x1080** as the browser resolution.

## Vaadin-specific inspection checklist

Vaadin UI is made of web components. Inspect both the light DOM host and shadow DOM internals.

Check:

- host tag names: `vaadin-button`, `vaadin-grid`, `vaadin-text-field`, etc.
- host classes added from Java (`addClassName`, class helper enums, etc.)
- host attributes: `theme`, `disabled`, `focused`, `has-value`, `invalid`, `opened`, `selected`
- shadow parts available under each component (`[part]` values)
- theme tokens actually defined at runtime (`--aura-*`, `--lumo-*`, `--vaadin-*`)
- computed styles on the exact host or shadow part that should be affected

Useful probes:

```bash
agent-browser --session "$SESSION" snapshot -i
```

```bash
agent-browser --session "$SESSION" eval '
[...document.querySelectorAll("vaadin-button,vaadin-text-field,vaadin-password-field,vaadin-grid")].map(el => ({
  tag: el.tagName.toLowerCase(),
  text: el.textContent.trim().replace(/\s+/g, " ").slice(0, 80),
  class: el.className,
  theme: el.getAttribute("theme"),
  attrs: [...el.attributes].map(a => a.name),
  parts: el.shadowRoot ? [...el.shadowRoot.querySelectorAll("[part]")].map(p => p.getAttribute("part")) : []
}))
'
```

```bash
agent-browser --session "$SESSION" eval '
const root = getComputedStyle(document.documentElement);
({
  auraAppBackground: root.getPropertyValue("--aura-app-background").trim(),
  auraFontFamily: root.getPropertyValue("--aura-font-family").trim(),
  lumoPrimary: root.getPropertyValue("--lumo-primary-color").trim(),
  vaadinTextColor: root.getPropertyValue("--vaadin-text-color").trim(),
  vaadinRadiusM: root.getPropertyValue("--vaadin-radius-m").trim()
})
'
```

## CSS selector audit

For every CSS rule you add, change, or suspect is stale, verify it in the running page.

Rules:

- Normal selectors must match at least one intended element on the relevant page.
- `::part(...)` selectors must be checked in two steps:
  1. host selector matches a Vaadin component
  2. the component shadow root exposes that part name
- Theme-token usage must match the active theme:
  - Aura: `--aura-*`
  - Lumo: `--lumo-*`
  - Base/all themes: `--vaadin-*`
- Do not assume styles are applied just because the CSS file contains the rule. Check computed style.

Probe a `::part` rule manually:

```bash
agent-browser --session "$SESSION" eval '
[...document.querySelectorAll("vaadin-button.some-class")].map(el => ({
  text: el.textContent.trim(),
  hasButtonPart: !!el.shadowRoot?.querySelector("[part~=button]"),
  parts: [...(el.shadowRoot?.querySelectorAll("[part]") ?? [])].map(p => p.getAttribute("part")),
  computed: el.shadowRoot?.querySelector("[part~=button]")
    ? getComputedStyle(el.shadowRoot.querySelector("[part~=button]")).cssText
    : null
}))
'
```

When a selector has no matching host, targets a nonexistent part, references undefined theme variables, or is overridden so it has no effect, trace it back to source. If it is dead because of your UI change or clearly stale for the target page/component, remove it surgically.

## Debugging workflow

1. Navigate to the target route and authenticate if needed.
2. Capture a semantic snapshot and screenshot.
3. Inspect relevant Vaadin hosts, attributes, shadow parts, variables, and computed styles.
4. Make the smallest Java/CSS change.
5. Reload or navigate back to the target route.
6. Re-run the exact probes to prove the style now applies.
7. Remove verified dead or stale selectors introduced or exposed by the change.
8. Run the relevant build/test command when practical.

## Project visual verification workflow

When visually validating an implemented use case, all the steps listed here must be done and all details are important. The goal is to be thorough instead of quick.

1. Ensure the application is running
2. Navigate to every route defined in the use case's UI/Routes section
3. Perform each step from the use case's main flow
4. Take screenshots of key interaction points
5. Validate the visual appearance according to the validation rules below
6. Record results -- note any visual issues in the per-use-case checklist below

## Validating visual appearance

The most important part is to verify what the user sees, i.e. a screenshot.
DOM, CSS rules etc can be used as helpers but the screenshot is what really matters.

1. Layout matches expectations (spacing, alignment, sizing)
2. Spacing & padding are consistent -- content has appropriate breathing room, no cramped or excessively spaced areas. Verify that nested layouts (e.g., AppLayout > VerticalLayout > card) don't double-up padding or collapse it.
Compare padding between similar views (e.g., all admin views should have the same content padding).
3. Typography is readable and consistent
4. Interactive elements are clearly identifiable
5. Responsive behaviour works at common breakpoints (mobile, tablet, desktop)
6. Text contrast and readability
  - All text is clearly readable against its background (titles, labels, values, badges)
  - Colored text (warning/error values, status badges) has sufficient contrast
  - Elements that inherit from a different color scheme (e.g., dark sidebar vs light content) render correctly -- CSS custom properties like `var(--vaadin-background-color)` may resolve differently depending on the inherited color scheme
  - No backgrounds swallow their content text

## Guardrails

- Do not style internal shadow DOM elements with unsupported selectors. Prefer host classes, documented CSS custom properties, `theme` variants, and documented `::part(...)` names.
- Do not mix Aura and Lumo tokens blindly. Undefined CSS variables can fail silently.
- Do not refactor unrelated styles while cleaning dead CSS.
- Keep CSS removals evidence-based: note what runtime check showed the selector was stale.
