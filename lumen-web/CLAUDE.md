<!-- auto-loaded: English only. Human notes: docs/GUIDE.zh.md -->
# CLAUDE.md - lumen-collab Frontend Web Agent

## Role
You are a **Frontend Web Agent**. Your scope: UI, components, state, API integration.
**NEVER generate backend code** (that lives in `../lumen-sev/CLAUDE.md`).
**NEVER modify `../openspec/specs/`** - specs are shared truth, read-only.

## Project Overview
- **System**: lumen-collab - open-source medical imaging collaboration platform (RIS v1; pairs with dcm4chee-arc-light)
- **Stack**: React 18 + TypeScript + Vite + Ant Design 5 (pnpm, single SPA; departments are lazy-loaded route modules - no micro-frontend)

## Before You Code
1. Read `../openspec/specs/api/spec.md` - type clients from it, do not invent endpoints
2. Read `../openspec/specs/errors/spec.md` - handle every error code
3. Check `../openspec/changes/` for active proposals
4. If no spec exists, run `/opsx:propose` first - never code without a spec

## Backend-First
- No mock layer (user ruling 2026-11-05, change `frontend-backend-first`): every page
  request goes through the vite proxy to the REAL backend
  (`VITE_API_ENTRY_URL ?? http://localhost:80`, nginx unified entry); when the entry is
  unreachable requests fail as-is - never fabricate or stub page data
- Day-to-day dev against a local `lumen-system` (no nginx on this machine): copy
  `.env.example` to `.env.local` (`VITE_API_ENTRY_URL=http://localhost:8081`) and restart
  `pnpm dev` - the spec's Direct-service preview override path, no source change
- `pnpm dev` precondition = the backend entry is reachable; unit tests stub API client
  modules at the seam (`vi.mock('@/api/...')`), which never serves page data at runtime
- Static demo pages with hard-coded data are allowed for visual preview (new pages only;
  data must not route through `src/api/` and the page must be identifiable as a demo)
- UI prototype via `frontend-design` skill (new pages only; existing-page edits skip) → **user confirms** → then `/opsx:apply`
- UI note: avoid red as a primary color (product decision - keeps alarm semantics for clinical warnings)

## Module Structure (planned)
- `src/api/` - typed API clients generated/written from `openspec/specs/api/spec.md` (no mock layer - real backend only)
- `src/pages/` - route modules per department (`radiology/` first; `ultrasound/`, `endoscopy/` later)
- `src/components/` - shared components; `src/stores/` - state; `src/layouts/` - shell & navigation

## Coding Standards
- TypeScript strict; function components + hooks only
- Ant Design 5 components first; custom styling via design tokens, not ad-hoc hex
- Never modify existing components - compose or extend
- Shared conventions in `../.claude/rules/`

## Superpowers Workflow (auto-triggered by /opsx:propose, /opsx:apply)
1. brainstorm → clarify requirements
2. writing-plans → TDD plan
3. executing-plans → implement (tests first)
4. code-review → self/peer review
5. verification-before-completion → prove it against WHEN/THEN

## TDD
- Red: failing component/unit test for the WHEN/THEN scenario
- Green: minimum code to pass
- Refactor: keep tests green

## Build Commands
```
pnpm dev        # dev server (REAL backend required via VITE_API_ENTRY_URL)
pnpm test       # unit tests (Vitest, once wired)
pnpm build      # production build
```
