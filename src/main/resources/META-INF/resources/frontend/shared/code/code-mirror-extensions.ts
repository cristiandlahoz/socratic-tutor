import { cpp } from '@codemirror/lang-cpp';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { json } from '@codemirror/lang-json';
import { python } from '@codemirror/lang-python';
import { xml } from '@codemirror/lang-xml';
import type { Extension } from '@codemirror/state';
import { solarizedDark } from '@fsegurai/codemirror-theme-solarized-dark';
import { solarizedLight } from '@fsegurai/codemirror-theme-solarized-light';

export function codeMirrorLanguageExtensions(lang: string | null | undefined): Extension[] {
  switch ((lang ?? '').toLowerCase()) {
    case 'java':
      return [java()];
    case 'c':
    case 'h':
    case 'hpp':
    case 'cpp':
    case 'c++':
      return [cpp()];
    case 'json':
      return [json()];
    case 'xml':
    case 'html':
      return [xml()];
    case 'js':
    case 'jsx':
    case 'javascript':
    case 'ts':
    case 'tsx':
    case 'typescript':
      return [javascript({ jsx: true, typescript: true })];
    case 'py':
    case 'python':
      return [python()];
    default:
      return [];
  }
}

export function resolveCodeMirrorTheme(
  themePreference: string,
  systemPrefersDark: boolean,
): Extension | undefined {
  if (themePreference === 'light') {
    return solarizedLight;
  }
  if (themePreference === 'dark') {
    return solarizedDark;
  }
  return systemPrefersDark ? solarizedDark : solarizedLight;
}
