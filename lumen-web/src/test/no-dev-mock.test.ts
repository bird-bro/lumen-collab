import { access, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * No-dev-mock guard (`frontend-backend-first`, spec `specs/web-app-shell`
 * § ADDED "Dev Server Serves Only Real Backend": "the repository SHALL carry
 * no dev-server mock layer").
 *
 * Semantics: the dev-server mock layer is physically removed, not gated
 * (user ruling 2026-11-05: pages must never display fabricated data, so no
 * mock plugin wiring, no mock definition directory, and no mock dependency
 * may exist). Unit tests stub API client modules at the module seam instead
 * (`vi.mock('@/api/...')`), which this test deliberately does NOT scan for -
 * those stubs never serve page data at runtime (spec § "Stub and Demo Data
 * Boundaries").
 *
 * Implemented with direct `node:fs` checks against the three places the mock
 * layer lived, so reintroducing any one of them fails this test and names it.
 */

const WEB_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const MOCK_DIR = path.join(WEB_ROOT, 'mock')
const VITE_CONFIG = path.join(WEB_ROOT, 'vite.config.ts')
const PACKAGE_JSON = path.join(WEB_ROOT, 'package.json')

const MOCK_PLUGIN_ID = 'vite-plugin-mock-dev-server'

async function exists(p: string): Promise<boolean> {
  try {
    await access(p)
    return true
  } catch {
    return false
  }
}

describe('no dev-server mock layer (backend-first ruling)', () => {
  it('the mock/ definition directory does not exist', async () => {
    await expect(exists(MOCK_DIR)).resolves.toBe(false)
  })

  it('vite.config.ts mounts no mock plugin', async () => {
    const source = await readFile(VITE_CONFIG, 'utf8')
    expect(source).not.toContain(MOCK_PLUGIN_ID)
    expect(source).not.toContain('mockDevServerPlugin')
  })

  it('package.json carries no mock devDependency', async () => {
    const pkg = JSON.parse(await readFile(PACKAGE_JSON, 'utf8')) as {
      dependencies?: Record<string, string>
      devDependencies?: Record<string, string>
    }
    const all = { ...pkg.dependencies, ...pkg.devDependencies }
    expect(Object.keys(all)).not.toContain(MOCK_PLUGIN_ID)
  })
})
