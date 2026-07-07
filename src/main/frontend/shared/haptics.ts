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

const defaultIntervalMs = 120;
const repeatedIntentIntervalMs = 300;

let lastVibrationAt = 0;
const lastIntentAt = new Map<HapticIntent, number>();

export function haptic(intent: HapticIntent): void {
  try {
    if (!canVibrate()) {
      return;
    }

    const now = globalThis.performance?.now?.() ?? Date.now();
    const lastIntent = lastIntentAt.get(intent) ?? 0;

    if (now - lastVibrationAt < defaultIntervalMs || now - lastIntent < repeatedIntentIntervalMs) {
      return;
    }

    lastVibrationAt = now;
    lastIntentAt.set(intent, now);
    globalThis.navigator.vibrate(patterns[intent]);
  } catch {
    // Haptics are progressive enhancement only.
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
