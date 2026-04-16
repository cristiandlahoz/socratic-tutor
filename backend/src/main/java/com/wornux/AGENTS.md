# Main Layout Learnings

- `MainLayout` sidebar timeline is a coupled Java/CSS/SVG feature: if you change node lanes or row structure in `MainLayout.java`, update `styles/chat-sidebar.css` too or the graph edges will misalign.
- `Button#setIcon(...)` can leave Vaadin buttons behaving like icon buttons in this drawer context; for full-width icon+text buttons or buttons wrapping custom row content, call `getThemeNames().remove("icon")` after setting the icon.
- The sidebar timeline uses an SVG overlay anchored to `.chat-sidebar-node` positions, not CSS pseudo-lines; redraw it after row content changes, active-conversation changes, resize events, and mobile drawer open/close.
