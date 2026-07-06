# UI implementation guidelines

- Follow `spec/design-system.md` for all UI styling decisions.
- This project uses Vaadin Aura. Do not use Lumo CSS variables (`--lumo-*`).
- Prefer named classes from `com.wornux.ui.css.UiCss` over inline `getStyle()` calls.
- Add component styles to CSS files under `src/main/resources/META-INF/resources/styles/` and import them from `styles.css`.
