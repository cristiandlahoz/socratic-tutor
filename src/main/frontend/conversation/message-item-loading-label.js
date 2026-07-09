/**
 * @param {string | null | undefined} loadingLabel
 * @returns {string}
 */
export function resolveLoadingLabel(loadingLabel) {
  if (typeof loadingLabel !== 'string') {
    return 'Generando respuesta';
  }

  const normalizedLabel = loadingLabel.trim();
  return normalizedLabel.length > 0 ? normalizedLabel : 'Generando respuesta';
}
