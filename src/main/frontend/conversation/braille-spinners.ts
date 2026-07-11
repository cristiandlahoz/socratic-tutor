export interface Spinner {
  readonly frames: readonly string[];
  readonly interval: number;
}

export type BrailleSpinnerName = string;

const BRAILLE_DOT_MAP = [
  [0x01, 0x08],
  [0x02, 0x10],
  [0x04, 0x20],
  [0x40, 0x80],
];

function gridToBraille(grid: boolean[][]): string {
  let result = '';
  for (let charCol = 0; charCol < 2; charCol++) {
    let bits = 0;
    for (let row = 0; row < 4; row++) {
      for (let dotCol = 0; dotCol < 2; dotCol++) {
        if (grid[row]?.[charCol * 2 + dotCol]) bits |= BRAILLE_DOT_MAP[row][dotCol];
      }
    }
    result += String.fromCharCode(0x2800 + bits);
  }
  return result;
}

function snakeFrames(): string[] {
  const path: [number, number][] = [];
  for (let row = 0; row < 4; row++) {
    for (let column = 0; column < 4; column++) {
      path.push([row, row % 2 === 0 ? column : 3 - column]);
    }
  }

  return path.map((_, index) => {
    const grid = Array.from({ length: 4 }, () => Array<boolean>(4).fill(false));
    for (let tail = 0; tail < 4; tail++) {
      const [row, column] = path[(index - tail + path.length) % path.length];
      grid[row][column] = true;
    }
    return gridToBraille(grid);
  });
}

const SNAKE: Spinner = { frames: snakeFrames(), interval: 80 };

export const BRAILLE_SPINNER_DEFAULT: BrailleSpinnerName = 'snake';

export function resolveSpinner(_name?: string | null): Spinner {
  return SNAKE;
}
