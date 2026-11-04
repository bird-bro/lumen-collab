import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

// Batch 1 smoke test (task 1.1): proves the jsdom + Testing Library pipeline
// is wired end to end (vitest run -> jsdom environment -> jest-dom matchers
// via setup.ts -> RTL render). Not a business test - delete-safe placeholder.
describe('test pipeline smoke', () => {
  it('runs assertions through the vitest runner', () => {
    expect(true).toBe(true)
  })

  it('renders a node in jsdom via Testing Library', () => {
    render(<div>hello</div>)
    expect(screen.getByText('hello')).toBeInTheDocument()
  })
})
