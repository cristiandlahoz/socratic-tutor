import { LitElement, html } from 'lit';

class SidebarTrail extends LitElement {
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
        sidebar-trail {
          position: absolute;
          inset: 0;
          z-index: 2;
          pointer-events: none;
          overflow: visible;
        }

        .sidebar-trail-line {
          position: absolute;
          inset-block: 0;
          inset-inline-start: var(--sidebar-trail-line-x);
          width: var(--vaadin-input-field-border-width, 1px);
          background: var(--sidebar-trail-line-color);
          pointer-events: none;
        }
      </style>
      <div class="sidebar-trail-line" aria-hidden="true"></div>
    `;
  }
}

if (!customElements.get('sidebar-trail')) {
  customElements.define('sidebar-trail', SidebarTrail);
}
