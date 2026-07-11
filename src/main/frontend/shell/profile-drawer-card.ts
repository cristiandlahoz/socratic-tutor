import { LitElement, html } from 'lit';
import { haptic } from 'Frontend/shared/haptics.js';

class ProfileDrawerCard extends LitElement {
  private readonly expandedClass = 'is-expanded';
  private readonly headerClass = 'profile-drawer-card__header';
  private readonly themeButtonClass = 'theme-switcher__button';
  private readonly onHostClick = (event: Event) => this.handleHostClick(event);
  private readonly onDocumentPointerDown = (event: Event) => this.handleDocumentPointerDown(event);

  connectedCallback(): void {
    super.connectedCallback();
    this.addEventListener('click', this.onHostClick);
    document.addEventListener('pointerdown', this.onDocumentPointerDown, true);
    this.syncHeaderExpanded();
  }

  disconnectedCallback(): void {
    document.removeEventListener('pointerdown', this.onDocumentPointerDown, true);
    this.removeEventListener('click', this.onHostClick);
    super.disconnectedCallback();
  }

  protected render() {
    return html`<slot></slot>`;
  }

  private handleHostClick(event: Event): void {
    if (this.eventPathHasInactiveThemeButton(event)) {
      haptic('selection');
      return;
    }
    if (this.eventPathHasClass(event, this.headerClass)) {
      this.setExpanded(!this.classList.contains(this.expandedClass));
    }
  }

  private handleDocumentPointerDown(event: Event): void {
    if (!this.classList.contains(this.expandedClass)) {
      return;
    }
    const path = event.composedPath?.() ?? [];
    if (path.includes(this)) {
      return;
    }
    this.setExpanded(false);
  }

  private eventPathHasClass(event: Event, className: string): boolean {
    return (event.composedPath?.() ?? []).some(
      (target) => target instanceof HTMLElement && target.classList.contains(className),
    );
  }

  private eventPathHasInactiveThemeButton(event: Event): boolean {
    return (event.composedPath?.() ?? []).some(
      (target) => target instanceof HTMLElement
        && target.classList.contains(this.themeButtonClass)
        && target.getAttribute('aria-pressed') !== 'true',
    );
  }

  private setExpanded(expanded: boolean): void {
    this.classList.toggle(this.expandedClass, expanded);
    haptic('toggle');
    this.syncHeaderExpanded();
  }

  private syncHeaderExpanded(): void {
    const expanded = this.classList.contains(this.expandedClass);
    this.querySelector(`.${this.headerClass}`)?.setAttribute('aria-expanded', String(expanded));
  }
}

customElements.define('profile-drawer-card', ProfileDrawerCard);
