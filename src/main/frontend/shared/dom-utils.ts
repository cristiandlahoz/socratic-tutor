export function ensureDocumentStyle(id: string, css: string): void {
  if (document.getElementById(id)) {
    return;
  }

  const style = document.createElement('style');
  style.id = id;
  style.textContent = css;
  document.head.appendChild(style);
}

export function normalizeArrayProperty<T>(value: unknown): T[] {
  if (typeof value === 'string') {
    return JSON.parse(value) as T[];
  }
  return Array.isArray(value) ? (value as T[]) : [];
}
