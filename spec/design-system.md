# Design System

> Theme, component usage, visual standards, and UX guidance for Socratic Tutor.

---

## 1. Theme

- **Base theme:** Vaadin Aura (modern, accessible design system)
- **Custom CSS:** `src/main/resources/META-INF/resources/styles.css`

**Aura and Lumo are two different, incompatible design systems.** This project uses **Aura**. Do not use `--lumo-*` CSS variables — they belong to the Lumo theme and must not be mixed with Aura. Use `--aura-*` variables for Aura-specific properties (typography, shadows) and `--vaadin-*` variables for base properties shared across all themes (spacing, radius, colors).

**Always use Aura theme variables instead of hard-coded values** (e.g., `--aura-font-size-xs` through `--aura-font-size-xl` for font sizes). Do not use hardcoded `px`, `rem`, or `em` values when an Aura variable exists. This ensures consistency with the Vaadin Aura theme and allows global adjustments through theme customization.

---

## 2. Color Palette

Aura computes all color variations automatically from a small set of base properties. Override these instead of hard-coding hex values.

| Token | Default | Usage |
|-------|---------|-------|
| `--aura-accent-color-light` | Blue | Primary actions, focus rings, selection highlights (light mode) |
| `--aura-accent-color-dark` | Blue | Primary actions, focus rings, selection highlights (dark mode) |
| `--aura-neutral` / `-light` / `-dark` | Dark gray / off-white | Text, borders, default UI chrome |
| `--aura-red` | Red | Error states, destructive actions |
| `--aura-orange` | Orange | Warnings |
| `--aura-green` | Green | Success states, confirmations |
| `--aura-blue` | Blue | Informational, links |
| `--aura-yellow` | Yellow | Caution, highlights |
| `--aura-purple` | Purple | Decorative accents |

Derived read-only tokens (do not override directly):
- `--aura-accent-contrast-color` — high-contrast text on accent backgrounds
- `--aura-accent-text-color` — accent-derived text color with good contrast
- `--aura-accent-border-color` — border tinted with accent color
- `--aura-accent-surface` — surface tinted with accent color
- `--aura-red-text`, `--aura-green-text`, etc. — palette text variants with better contrast

Base style tokens (shared across all themes):
- `--vaadin-text-color` — main text color
- `--vaadin-text-color-secondary` — secondary/muted text
- `--vaadin-text-color-disabled` — disabled state text
- `--vaadin-border-color` — prominent borders (3:1 contrast)
- `--vaadin-border-color-secondary` — subtle, non-essential borders
- `--vaadin-background-color` — base content background
- `--vaadin-background-container` — buttons, toolbars, highlighted areas
- `--vaadin-background-container-strong` — more prominent container background

Use accent class names (e.g. `.aura-accent-purple`) on `<html>` or individual components to swap accent color contextually.

---

## 3. Typography

Aura uses the **Instrument Sans** web font by default (`--aura-font-family-instrument-sans`), falling back to the system font stack.

| Token | Purpose |
|-------|---------| 
| `--aura-font-family` | App-wide font family (set on `<body>`) |
| `--aura-base-font-size` | Base size (unitless number, represents M size in px) |
| `--aura-font-size-xs` through `-xl` | Computed font sizes (rem, rounded to nearest px) |
| `--aura-base-line-height` | Base line height (unitless, relative to font size) |
| `--aura-line-height-xs` through `-xl` | Computed line heights (rem, rounded to nearest 2px) |
| `--aura-font-weight-regular` | Normal body text |
| `--aura-font-weight-medium` | Emphasis, subheadings |
| `--aura-font-weight-semibold` | Headings, strong emphasis |
| `--aura-font-smoothing` | Set to `auto` to disable grayscale anti-aliasing |

### Text Hierarchy

- **Display / Page Title:** `--aura-font-size-xl` with `--aura-font-weight-semibold`
- **Section Heading:** `--aura-font-size-l` with `--aura-font-weight-semibold`
- **Subheading / Form Label:** `--aura-font-size-m` with `--aura-font-weight-medium`
- **Body Text:** `--aura-font-size-m` or `--aura-font-size-s` with `--aura-font-weight-regular`
- **Secondary / Helper Text:** `--aura-font-size-s` with `--vaadin-text-color-secondary`
- **Caption / Metadata:** `--aura-font-size-xs` with `--vaadin-text-color-secondary`

---

## 4. Spacing & Layout

Aura computes gap and padding from `--aura-base-size` (unitless, range 12–24). Use the resulting base style tokens:

| Token | Purpose |
|-------|---------| 
| `--vaadin-gap-xs` through `-xl` | Space between elements in flex/grid layouts |
| `--vaadin-padding-xs` through `-xl` | Internal padding for containers and content areas |
| `--vaadin-padding-inline-container` | Horizontal padding for single-line containers (buttons, inputs) |
| `--vaadin-padding-block-container` | Vertical padding for single-line containers |

**Border radius** (computed from `--aura-base-radius`, unitless, range 0–10):

| Token | Purpose |
|-------|---------| 
| `--vaadin-radius-s` | Small controls (should not become circles) |
| `--vaadin-radius-m` | Default component radius |
| `--vaadin-radius-l` | Large containers, cards, dialogs |

**Shadows** (Aura-specific):

| Token | Purpose |
|-------|---------| 
| `--aura-shadow-xs` | Subtle elevation — buttons, inputs, checkboxes |
| `--aura-shadow-s` | Slight elevation — primary buttons, selected controls, cards |
| `--aura-shadow-m` | Clear elevation — overlays, notifications, dialogs |

**Surface colors** for visual hierarchy (read-only, computed):
- `--aura-surface-color` — semi-transparent elevated background
- `--aura-surface-color-solid` — opaque version
- Control with `--aura-surface-level` (number, higher = more elevation) and `--aura-surface-opacity` (default 0.5)

**Layout approach:** Use Vaadin `VerticalLayout` / `HorizontalLayout` (Flow) or flexbox/grid with `--vaadin-gap-*` / `--vaadin-padding-*` tokens. No hard-coded spacing values.

---

## 5. Component Standards

| Component | When to Use | Notes |
|-----------|-------------|-------| 
| **Button** | Primary and secondary actions | Use `theme="primary"` for main CTA; `theme="secondary"` or default for others |
| **Grid** | Tabular data display | Always enable sorting; use LitRenderer for rich content |
| **TextField** | Text input | Always set placeholder; validate on blur or change |
| **NumberField** | Numeric input | Set min/max constraints |
| **ComboBox** | Select from predefined list | Allow filtering/search; load options from database |
| **DatePicker** | Select date | Use for date range filtering |
| **Checkbox** | Boolean toggle | Clear label to the right |
| **TextArea** | Multi-line text | Set reasonable row height |
| **Dialog** | Confirmation, forms, details | Modal by default; clear action buttons |
| **Notification** | Success, error, warning feedback | Position top-right; auto-close 3-5s (except errors) |
| **FormLayout** | Structure forms with labels | 1-2 columns depending on viewport; responsive |
| **VerticalLayout / HorizontalLayout** | Organize content flow | Use consistent gap/padding from theme tokens |

---

## 6. Form Design

### Form Layout

- Use **FormLayout** or **VerticalLayout** with consistent spacing
- Labels above or to the left of inputs (responsive)
- Group related fields together
- Required fields marked with `*`
- Error messages below field in `--aura-red-text`
- Helper text in `--vaadin-text-color-secondary`

### Form Validation

- **Real-time validation:** Validate on blur or change (Vaadin Binder)
- **Error display:** Field-level errors below input, or notification
- **Submit validation:** Prevent save if form is invalid

---

## 7. Navigation

### Main Layout

- **Header:** Application title, user menu (email, logout)
- **Sidebar:** Role-specific navigation items
- **Mobile behavior:** Hamburger menu, full-width sidebar on toggle

### Routing

- Public routes: `/login`, invitation acceptance, onboarding
- Protected routes: require authentication + permission checks

---

## 8. Responsive Behavior

### Breakpoints

- **Mobile** (< 640px): Single column, stacked layouts, full-width sidebars
- **Tablet** (640–1024px): Two-column grids, wider sidebars (~40vw)
- **Desktop** (> 1024px): Multi-column layouts

### Mobile Optimizations

- Touch-friendly button sizes (minimum 44px x 44px)
- Larger input fields and labels
- Reduced whitespace
- Grid columns reorder or collapse on narrow screens

---

## 9. Accessibility

- **Color contrast:** Use Aura tokens to ensure 4.5:1 contrast ratio
- **Keyboard navigation:** All interactive elements focusable with Tab
- **ARIA labels:** Add where needed (Vaadin handles most automatically)
- **Form labels:** Always associate labels with inputs
- **Semantic HTML:** Use `<button>`, `<a>`, `<form>` appropriately

---

## 10. Error & Confirmation UX

### Error States

- **Field-level errors:** Below input in `--aura-red-text`
- **Form-level errors:** Notification at top of view in red
- **Toasts:** Use Notification with error theme

### Confirmation Dialogs

- **Destructive actions:** Show modal before delete/deactivate
- **Proceed despite advisory:** Show a modal before saving or publishing against current AI instruction advice; make clear that the model is advisory and the professor remains responsible for the final text.
- **Title:** Clear question
- **Buttons:** Use action-specific labels. For instruction advice use "Volver a editar" and "Guardar de todos modos" or "Publicar de todos modos"; avoid an ambiguous generic "Confirm".
- **Keyboard:** Escape = Cancel, Enter = Confirm

### Success Feedback

- **Notification:** Brief message, top-right, 3-5 seconds auto-close

---

## 11. UX Principles (Socratic Tutor)

### Design Goal

Socratic Tutor should feel like a calm academic workspace, not a generic chatbot. The interface must help each user understand who they are, what role they operate as, which tenant/institution and group class they are inside, and what actions are available.

### Experience Principles

- **Learning First:** Student experience prioritizes reasoning, clarity, low cognitive noise.
- **Context Visible:** Active tenant and group class are always visible.
- **Role Specific:** Each role sees a workspace designed for its job.
- **Safe by Design:** UI never replaces service-layer authorization.
- **Progressive Setup:** Guided chain from tenant creation to student learning.

---

## 12. Workspace Model

| Workspace | Main context | Main purpose |
|-----------|-------------|--------------|
| System Admin | Platform | Create tenants and invite tenant admins |
| Tenant Admin | Tenant | Configure academic structure |
| Professor | Group class | Teach and manage class learning resources |
| Student | Group class | Learn through tutor chat and assigned activities |

---

## 13. User Journeys

### System Admin
1. Log in → lands in platform admin workspace → creates tenant → invites tenant admin.

### Tenant Admin
1. Accepts invitation → lands in tenant admin workspace → selects tenant → creates academic periods, subjects, group classes → invites professors.

### Professor
1. Accepts invitation → lands in professor workspace → selects group class → invites students → configures grounding → creates training activities → uses tutor chat.

### Student
1. Accepts invitation → lands in student workspace → selects group class → starts tutor conversation → views assigned training activities.

---

## 14. Empty and Blocked States

| State | Message |
|-------|---------|
| No role | Account exists but no tenant/class access. Ask for an invitation. |
| No tenant (admin) | No tenant context. Contact a system admin. |
| No group class (professor) | Not assigned to any group class. A tenant admin must invite you. |
| No group class (student) | Not enrolled. Use an invitation from your professor. |
| No grounding material | Tutor can still help generally, but class-specific material unavailable. |
| No assigned activities | No assigned training activities right now. |

---

## 15. Chat UX

Chat should make class context visible: active group class, subject name, grounding availability, conversation title.

Chat supports: new conversation, history, message display, streaming if available, safe error state, empty state for first use.

Tutor messages should feel instructional. Preferred copy:

- "Let's reason through it."
- "What have you tried so far?"
- "Here is a hint, not the full solution yet."

---

## 16. Grounding UX

Professor-facing: show collections, documents, status, upload/text input, processing/ready/failed states.
Students: grounding is reflected as context availability, not as a document management tool.

---

## 17. Training Activity UX

Product-facing term: formative activities. Persistence follows the normalized SPEC-005 activity, review, assignment, turn, report, Safe Browser, job, and outbox model.

Professor UI: class-scoped activity grid, draft create/edit, advisory instruction review, explicit save/publish override, publish/close/archive, immutable published detail, assignment progress, Safe Browser incidents, and report state.

Student UI: assigned activities list, explicit protected-session entry when required, one tutor question at a time, nonblank answer validation, persisted pending states, start/continue/completed status, and immediate return to the workspace after submission.

### Asynchronous states

Model work must never look like a frozen screen. Use calm explicit states:

| Persisted/derived state | User-facing label | Expected interaction |
|-------------------------|-------------------|----------------------|
| Review queued/running | Revisando instrucción… | Editor remains usable; stale results never replace current text |
| `STARTING` | Preparando primera pregunta… | Navigation remains available |
| `WAITING_FOR_ANSWER` | Tu turno | Composer enabled only for nonblank input |
| `WAITING_FOR_TUTOR` | Analizando respuesta… | Accepted answer visible; duplicate submit disabled |
| Tutor temporary failure | No pudimos continuar todavía | Keep evidence; allow persisted retry/return later |
| Report `PENDING`/`GENERATING` | Preparando reporte… | Professor can already inspect question-answer history |
| Report `FAILED` | No se pudo generar el reporte | Show transcript and authorized retry path |

Loading indicators must be accompanied by text and cannot be the only state signal.

### Instruction advice

- `GOOD` is a favorable suggestion, not a guarantee.
- Warnings show summary, why it matters, and an actionable replacement.
- Highlight text only when backend-validated source ranges match the current editor value.
- Applying a suggestion is explicit and never autosaves.
- Missing, failed, or unfavorable review permits an explicit professor override after deterministic validation passes.

### Answer validation

- Disable normal submit for empty/whitespace-only text and show a field-level message if submission is attempted.
- Backend rejection remains authoritative and preserves the current question/input.
- Do not confuse blank transport input with a meaningful response such as “no sé”; meaningful nonblank responses are accepted for tutor evaluation.

### Safe Browser and report states

- Safe Browser copy must explain both detectable rules and technical limitations; do not claim absolute browser/OS control.
- A blocked student sees what happened and that professor review is required, without incident internals or secret tokens.
- Professor report detail renders structured report sections, followed by the ordered question-answer list sourced from turns.

Status labels:
| DB status | Student label |
|-----------|--------------|
| ASSIGNED | Not started |
| STARTING | Preparing first question |
| WAITING_FOR_ANSWER | In progress — your turn |
| WAITING_FOR_TUTOR | In progress — analyzing |
| SUBMITTED | Submitted |
| SKIPPED | Skipped |
| EXPIRED | Expired |
| EXCUSED | Excused |

---

## 18. Visual Style

Calm, academic, modern, readable. Not gamified, not visually noisy.

Use Vaadin theme tokens instead of hard-coded colors/sizes. If Aura is the active theme, use Aura-compatible tokens.

Preferred visual patterns: cards for dashboards, clear sidebars, compact badges, readable tables, calm empty states, clear primary actions, destructive actions with confirmation.

---

## 19. Design Review Checklist

- [ ] All colors use `--aura-*` or `--vaadin-*` tokens
- [ ] All font sizes use `--aura-font-size-*` tokens
- [ ] All spacing uses `--vaadin-gap-*` or `--vaadin-padding-*` tokens
- [ ] All shadows use `--aura-shadow-*` tokens
- [ ] Border radius uses `--vaadin-radius-*` tokens
- [ ] Forms have clear labels and validation feedback
- [ ] Confirmation dialogs exist for destructive actions
- [ ] Active role is clear in UI
- [ ] Active tenant or group class is visible when relevant
- [ ] Mobile responsiveness tested (< 640px)
- [ ] Keyboard navigation works
- [ ] Color contrast meets WCAG AA
- [ ] No hard-coded colors, sizes, or spacing
- [ ] No UI flow creates academic data without valid context
