import { html } from 'lit';

export function renderConversationDisclaimer() {
  return html`
    <p class="conversation-input-disclaimer">
      La IA puede cometer <span class="conversation-input-disclaimer__italic">errores;</span> revisa adecuadamente.
    </p>
  `;
}
