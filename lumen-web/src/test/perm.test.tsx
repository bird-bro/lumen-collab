import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { usePerm } from '@/utils/perm'
import { hasPerm } from '@/utils/perm'
import { useAuthStore } from '@/stores/auth'

/**
 * perms-gating helper (batch 5b of web-foundation-auth-console, task 5.4;
 * spec `specs/web-auth-flow` § Dynamic Navigation from the Permission Set,
 * "Button gating"): a control whose perms code is absent from the session's
 * permission set is HIDDEN, and a control whose code is present renders. The
 * batch-6 console pages consume `usePerm()`/`hasPerm(code)`; this is the
 * contract they build on.
 */

function GatedControl({ code }: { code: string }) {
  const hasPermCode = usePerm()
  if (!hasPermCode(code)) return null
  return <button type="button">GATED_CONTROL</button>
}

beforeEach(() => {
  useAuthStore.getState().clear()
})

describe('perm gating helper (web-foundation-auth-console 5.4)', () => {
  it('hides a control when the perms code is absent from the session set', () => {
    useAuthStore.getState().setSession({ perms: ['system:user:list'] })
    render(<GatedControl code="system:user:create" />)
    expect(screen.queryByText('GATED_CONTROL')).not.toBeInTheDocument()
  })

  it('renders a control when the perms code is in the session set', () => {
    useAuthStore.getState().setSession({ perms: ['system:user:list', 'system:user:create'] })
    render(<GatedControl code="system:user:create" />)
    expect(screen.getByText('GATED_CONTROL')).toBeInTheDocument()
  })

  it('reads the live store in the non-reactive hasPerm(code) variant', () => {
    useAuthStore.getState().setSession({ perms: ['system:role:list'] })
    expect(hasPerm('system:role:list')).toBe(true)
    expect(hasPerm('system:log:list')).toBe(false)
    useAuthStore.getState().setSession({ perms: ['system:log:list'] })
    expect(hasPerm('system:log:list')).toBe(true)
  })
})
