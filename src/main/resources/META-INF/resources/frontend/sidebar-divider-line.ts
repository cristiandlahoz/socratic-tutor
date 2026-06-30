import { LitElement, html } from 'lit';

class SidebarDividerLine extends LitElement {
  connectedCallback(): void {
    super.connectedCallback();
    this.style.display = 'block';
  }

  protected createRenderRoot(): HTMLElement | DocumentFragment {
    return this;
  }

  protected render() {
    return html`
      <style>
        sidebar-divider-line {
          position: absolute;
          inset-block: 0;
          inset-inline-start: 0;
          width: var(--sidebar-divider-line-width, 25px);
          z-index: 2;
          pointer-events: none;
          overflow: visible;
        }

        .sidebar-divider-line {
          position: absolute;
          inset: 0;
          border-left: var(--sidebar-divider-line-border-width, 0.5px) solid var(--sidebar-divider-line-color);
          border-right: var(--sidebar-divider-line-border-width, 0.5px) solid var(--sidebar-divider-line-color);
          opacity: calc(var(--sidebar-divider-opacity) * 0.7);
          pointer-events: none;
        }
      </style>
      <div class="sidebar-divider-line" aria-hidden="true"></div>
    `;
  }
}

if (!customElements.get('sidebar-divider-line')) {
  customElements.define('sidebar-divider-line', SidebarDividerLine);
}
