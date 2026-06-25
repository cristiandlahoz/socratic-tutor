---
name: visual-verification
description: Visually verify an implemented use case using Playwright MCP. Use after implementing UI changes.
---

# Visual Verification

Use Playwright MCP to visually validate the implemented use case.

Unless the use case specifies a particular resolution, use **1920x1080** as the browser resolution.

## Steps

All the steps listed here must be done and all details are important. The goal is to be thorough instead of quick.

1. Ensure the application is running
2. Navigate to every route defined in the use case's UI/Routes section
3. Perform each step from the use case's main flow
4. Take screenshots of key interaction points
5. Validate the visual appearance according to the validation rules below
6. Record results -- note any visual issues in the per-use-case checklist below

## Validating Visual Appearance

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
