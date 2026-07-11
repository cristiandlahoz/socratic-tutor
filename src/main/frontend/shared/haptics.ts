import { play, type SoundName } from 'cuelume';

export type HapticIntent =
  | 'selection'
  | 'toggle'
  | 'messageSent'
  | 'success'
  | 'done'
  | 'error'
  | 'confirmation';

const patterns: Record<HapticIntent, VibratePattern> = {
  selection: 10,
  toggle: 12,
  messageSent: 15,
  success: 20,
  done: 15,
  error: [25, 35, 25],
  confirmation: [30, 40, 30],
};

const sounds: Partial<Record<HapticIntent, SoundName>> = {
  selection: 'tick',
  toggle: 'toggle',
  messageSent: 'release',
  success: 'success',
  done: 'bloom',
  confirmation: 'chime',
};

const defaultIntervalMs = 120;
const repeatedIntentIntervalMs = 300;

let lastVibrationAt = 0;
const lastIntentAt = new Map<HapticIntent, number>();

document.addEventListener('click', (event) => {
  const target = event.target as Element;
  if (target.closest?.('.c-runner-control-button')) {
    play('press');
  }
  else if (target.closest?.('.conversation-view__debugger-toggle, .c-runner-panel-toggle')) {
    play('whisper');
  }
  else if (target.closest?.([
    '.sidebar-actions__item-link',
    '.shell-drawer-toggle',
    '.shell-drawer-toggle-inside',
  ].join(', '))) {
    haptic('toggle');
  }
});

export function haptic(intent: HapticIntent): void {
  try {
    const sound = sounds[intent];
    if (sound) {
      play(sound);
    }

    const now = globalThis.performance?.now?.() ?? Date.now();
    const lastIntent = lastIntentAt.get(intent) ?? 0;
    if (canVibrate()
      && now - lastVibrationAt >= defaultIntervalMs
      && now - lastIntent >= repeatedIntentIntervalMs) {
      lastVibrationAt = now;
      lastIntentAt.set(intent, now);
      globalThis.navigator.vibrate(patterns[intent]);
    }
  } catch {
    // Interaction feedback is progressive enhancement only.
  }
}

function canVibrate(): boolean {
  if (prefersReducedMotion()) {
    return false;
  }

  return typeof globalThis.navigator?.vibrate === 'function';
}

function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}
