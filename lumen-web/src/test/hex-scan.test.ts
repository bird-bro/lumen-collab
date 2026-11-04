import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * Hex discipline guard (batch 2 of web-foundation-auth-console, spec
 * `specs/web-design-system` § OPS Palette Tokens: "no inline hex literal
 * appears outside the theme token module").
 *
 * Semantics: every `.ts` / `.tsx` / `.css` file under `src/` EXCEPT the theme
 * module (`src/theme/**`, the single sanctioned home of hex values) and the
 * test directory (`src/test/**`, where literal colors appear inside
 * assertions) is scanned for hex literals matching `#[0-9a-fA-F]{3,8}` with a
 * word boundary. Any hit fails this test and names the file:line, so a page
 * component can never smuggle an ad-hoc color past review.
 *
 * Implemented with `node:fs` recursive readdir: `import.meta.glob` is a Vite
 * build-time transform and would freeze the file list at transform time
 * instead of scanning the tree this test runs against.
 */

const SRC_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** Same pattern the independent `rg` review uses: 3-8 hex digits + boundary. */
const HEX_LITERAL = /#[0-9a-fA-F]{3,8}\b/g

const SCANNED_EXTENSIONS = new Set(['.ts', '.tsx', '.css'])

/** Only these two subtrees may contain hex literals (theme = the token home). */
const EXCLUDED_TOP_LEVEL_DIRS = new Set(['theme', 'test'])

interface Violation {
  file: string
  line: number
  snippet: string
}

async function findHexViolations(dir: string, violations: Violation[]): Promise<void> {
  const entries = await readdir(dir, { withFileTypes: true })
  for (const entry of entries) {
    const absolute = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (dir === SRC_ROOT && EXCLUDED_TOP_LEVEL_DIRS.has(entry.name)) {
        continue
      }
      await findHexViolations(absolute, violations)
      continue
    }
    if (!SCANNED_EXTENSIONS.has(path.extname(entry.name))) {
      continue
    }
    const content = await readFile(absolute, 'utf8')
    const lines = content.split('\n')
    for (let index = 0; index < lines.length; index += 1) {
      // Fresh regex per line: HEX_LITERAL is /g-flagged and matchAll would
      // advance its lastIndex across files otherwise.
      if (new RegExp(HEX_LITERAL.source, HEX_LITERAL.flags).test(lines[index])) {
        violations.push({
          file: path.relative(SRC_ROOT, absolute),
          line: index + 1,
          snippet: lines[index].trim(),
        })
      }
    }
  }
}

describe('hex discipline (spec: no inline hex outside the theme token module)', () => {
  it('finds zero hex literals in src/**/*.{ts,tsx,css} outside src/theme and src/test', async () => {
    const violations: Violation[] = []
    await findHexViolations(SRC_ROOT, violations)

    const report = violations
      .map(({ file, line, snippet }) => `  ${file}:${line} -> ${snippet}`)
      .join('\n')
    expect(
      violations,
      `hex literals found outside src/theme (move them into src/theme/tokens.ts):\n${report}`,
    ).toEqual([])
  })
})
