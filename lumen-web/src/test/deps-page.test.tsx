import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepsPage from '@/pages/system/deps'
import * as orgDepsApi from '@/api/org-deps'
import * as orgsApi from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgDep, OrgNode, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 科室管理 page rewrite (org-management-redesign-web tasks 3.1-3.6; spec delta
 * `web-system-console` § Department Campus Filter Tree / § Department Table
 * Management / § Department Code Handling).
 *
 * The old client-side-filtered list tests are REPLACED: the page is now the
 * hospital/campus FILTER tree (never a department editor) plus a server-side
 * paginated department table. Selecting the hospital root queries ALL
 * campuses (no orgId); selecting a campus filters to it. The create/edit
 * dialog requires a live, enabled campus owner, the code is read-only in
 * edit, and a same-campus duplicate surfaces on the code field with the
 * dialog staying open.
 *
 * The api seam is stubbed with `vi.mock` (unit-test seam only - the runtime
 * page always talks to the real backend through the typed clients, no mock
 * layer).
 */
vi.mock('@/api/org-deps')
vi.mock('@/api/orgs')

function orgNode(partial: Partial<OrgNode> & { id: number; name: string }): OrgNode {
  return {
    upId: 0,
    level: 1,
    type: 0,
    code: '',
    shortName: null,
    status: 1,
    provinceCode: null,
    cityCode: null,
    districtCode: null,
    address: null,
    contact: null,
    phone: null,
    email: null,
    logoUrl: null,
    sort: 0,
    isDefault: 0,
    createBy: null,
    createTime: '2026-11-06 10:00:00',
    updateBy: null,
    updateTime: '2026-11-06 10:00:00',
    children: null,
    ...partial,
  }
}

function dep(partial: Partial<OrgDep> & { id: number; code: string; name: string }): OrgDep {
  return {
    orgId: 274,
    status: 1,
    createBy: null,
    createTime: '2026-11-06 10:00:00',
    updateBy: null,
    updateTime: '2026-11-06 10:00:00',
    ...partial,
  }
}

const HOSPITAL = orgNode({ id: 1, name: '示例医院' })
const CAMPUS_A = orgNode({ id: 274, upId: 1, level: 2, type: 1, name: '总院区', code: 'CAMPUS-1', isDefault: 1 })
const CAMPUS_B = orgNode({ id: 275, upId: 1, level: 2, type: 1, name: '东院区', code: 'CAMPUS-EAST' })
/** ORG_TREE: hospital + campuses ONLY - departments never ride the tree. */
const ORG_TREE = [{ ...HOSPITAL, children: [CAMPUS_A, CAMPUS_B] }]

const DEP_RADIOLOGY_A = dep({ id: 11, orgId: 274, code: 'radiology', name: '放射科' })
const DEP_RADIOLOGY_B = dep({ id: 12, orgId: 275, code: 'radiology', name: '放射科' })

function pageOf(rows: OrgDep[], total = rows.length): Page<OrgDep> {
  return { records: rows, total, size: 10, current: 1, pages: Math.ceil(total / 10) }
}

/** Successive pageDep payloads keyed by the orgId filter the page issued. */
function pageDepFixture(byOrg: Record<string, Page<OrgDep>>) {
  vi.mocked(orgDepsApi.pageDep).mockImplementation((query) => {
    const key = query?.orgId === undefined ? 'all' : String(query.orgId)
    return Promise.resolve(byOrg[key] ?? pageOf([]))
  })
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <DepsPage />
    </ConfigProvider>,
  )
}

/** The left filter tree root (the aside panel's AntD Tree). */
function tree(): HTMLElement {
  return document.querySelector('.ant-tree') as HTMLElement
}

/** AntD's Table spin container carries pointer-events:none - row-action clicks
 * must wait for the CURRENT load to finish, or userEvent refuses (and which
 * test loses the race varies with machine load). */
async function waitForTableReady() {
  await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull())
  // The tree's auto-select effect fires a second identical load right after the
  // first settles - require the calm to HOLD across a macrotask boundary or the
  // click lands on the new spin's pointer-events:none container.
  await new Promise((resolve) => setTimeout(resolve, 60))
  await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull())
}

/** Same code may live on several campuses - any row counts as "rendered". */
async function waitForRow(code: string) {
  await waitFor(() => expect(screen.getAllByText(code).length).toBeGreaterThan(0))
}

async function expectDialogClosed() {
  await waitFor(() => expect(document.querySelector('.ant-drawer-open')).toBeNull())
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
}

function rowOf(code: string): HTMLElement {
  const cell = screen.getAllByText(code)[0]
  return cell.closest('tr') as HTMLElement
}

async function openStatusDropdown(user: ReturnType<typeof userEvent.setup>): Promise<HTMLElement> {
  await user.click(screen.getByRole('combobox', { name: '状态过滤' }))
  await waitFor(() => {
    expect(document.querySelector('.ant-select-dropdown')).not.toBeNull()
  })
  return document.querySelector('.ant-select-dropdown') as HTMLElement
}

/** The tree fetch races the table fetch - wait for the payload INSIDE the tree. */
async function waitForTree() {
  await waitFor(() => expect(within(tree()).getByText('总院区')).toBeInTheDocument())
}

/** The open drawer dialog (jsdom has no leave animation timing to race). */
function drawer(): HTMLElement {
  return document.querySelector('.ant-drawer-open') as HTMLElement
}

beforeEach(() => {
  vi.clearAllMocks()
  // The documented perms string of this page (seed menu id 8); no new perms
  // code is invented - the mutation surface rides on it (design D6).
  useAuthStore.getState().setSession({ perms: ['system:dep:list', 'system:dep:create', 'system:dep:update', 'system:dep:delete'] })
  vi.mocked(orgsApi.orgTree).mockResolvedValue(ORG_TREE)
  vi.mocked(orgDepsApi.createDep).mockResolvedValue(13)
  vi.mocked(orgDepsApi.updateDep).mockResolvedValue(true)
  vi.mocked(orgDepsApi.deleteDep).mockResolvedValue(true)
  pageDepFixture({
    all: pageOf([DEP_RADIOLOGY_A, DEP_RADIOLOGY_B]),
    '274': pageOf([DEP_RADIOLOGY_A]),
    '275': pageOf([DEP_RADIOLOGY_B]),
  })
})

describe('dep page - filter tree (delta: Department Campus Filter Tree)', () => {
  it('renders hospital/campus nodes only - the tree never shows a department', async () => {
    renderPage()
    await waitForRow('radiology')
    await waitForTree()
    expect(within(tree()).getByText('示例医院')).toBeInTheDocument()
    expect(within(tree()).getByText('总院区')).toBeInTheDocument()
    expect(within(tree()).queryByText('放射科')).toBeNull()
    expect(within(tree()).queryByText('radiology')).toBeNull()
  })

  it('hospital-root selection queries ALL campuses and rows show their campus names', async () => {
    renderPage()
    await waitForRow('radiology')
    // Both same-code departments render; each labeled with its campus.
    expect(orgDepsApi.pageDep).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
    )
    const calls = vi.mocked(orgDepsApi.pageDep).mock.calls
    expect(calls[calls.length - 1][0]).not.toHaveProperty('orgId')
    expect(within(rowOf('radiology')).getAllByText(/总院区|东院区/).length).toBeGreaterThan(0)
  })

  it('selecting a campus filters the table to that campus only', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')
    await waitForTree()

    await user.click(within(tree()).getByText('东院区'))
    await waitFor(() => {
      const calls = vi.mocked(orgDepsApi.pageDep).mock.calls
      expect(calls[calls.length - 1][0]).toEqual(
        expect.objectContaining({ orgId: 275, pageNum: 1, pageSize: 10 }),
      )
    })
    await waitFor(() => expect(screen.getAllByText('radiology')).toHaveLength(1))
  })
})

describe('dep page - server-side table (delta: Department Table Management)', () => {
  it('pager reports the total matched count from the server page', async () => {
    vi.mocked(orgDepsApi.pageDep).mockResolvedValue(
      pageOf([DEP_RADIOLOGY_A, DEP_RADIOLOGY_B], 42),
    )
    renderPage()
    await waitForRow('radiology')
    expect(screen.getByText(/共 42 条/)).toBeInTheDocument()
  })

  it('keyword query re-issues the page request and resets to the first page', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')

    await user.type(screen.getByPlaceholderText('科室编码 / 名称'), '放射')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() => {
      const calls = vi.mocked(orgDepsApi.pageDep).mock.calls
      expect(calls[calls.length - 1][0]).toEqual(
        expect.objectContaining({ keyword: '放射', pageNum: 1 }),
      )
    })
  })

  it('status filter re-issues the page request with the status value', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')

    const dropdown = await openStatusDropdown(user)
    await user.click(within(dropdown).getByText('停用'))
    await waitFor(() => {
      const calls = vi.mocked(orgDepsApi.pageDep).mock.calls
      expect(calls[calls.length - 1][0]).toEqual(expect.objectContaining({ status: 0 }))
    })
  })

  it('create posts through the typed client and refreshes the current page', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')

    await user.click(screen.getByRole('button', { name: /新建科室/ }))
    // The hospital root is selected: the dialog requires an explicit campus.
    expect(screen.getByLabelText('所属院区')).toBeInTheDocument()
    await user.click(screen.getByLabelText('所属院区'))
    await user.click(within(document.querySelector('.ant-select-dropdown') as HTMLElement).getByText('总院区'))
    await user.type(screen.getByLabelText('科室编码'), 'pathology')
    await user.type(screen.getByLabelText('科室名称'), '病理科')
    await user.click(within(drawer()).getByRole('button', { name: '保 存' }))

    await expectDialogClosed()
    expect(orgDepsApi.createDep).toHaveBeenCalledWith(
      expect.objectContaining({ orgId: 274, code: 'pathology', name: '病理科' }),
    )
  })

  it('prefills the campus owner when the filter is on a campus', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')
    await waitForTree()
    await user.click(within(tree()).getByText('总院区'))
    await waitFor(() => expect(screen.getAllByText('radiology')).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: /新建科室/ }))
    // AntD Select renders the chosen option as the selection item inside the drawer.
    await waitFor(() => {
      expect(within(drawer()).getByText('总院区')).toBeInTheDocument()
    })
  })
})

describe('dep page - code handling (delta: Department Code Handling)', () => {
  it('edit keeps the code read-only and the PUT never carries it', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')

    await waitForTableReady()
    await user.click(within(rowOf('radiology')).getByTitle('编辑科室'))
    expect(screen.getByLabelText('科室编码')).toBeDisabled()
    expect(screen.getByLabelText('科室编码')).toHaveValue('radiology')
    await user.clear(screen.getByLabelText('科室名称'))
    await user.type(screen.getByLabelText('科室名称'), '放射科改名')
    await user.click(within(drawer()).getByRole('button', { name: '保 存' }))

    await expectDialogClosed()
    const putBody = vi.mocked(orgDepsApi.updateDep).mock.calls[0][1]
    expect(putBody).toEqual(expect.objectContaining({ name: '放射科改名' }))
    expect(putBody).not.toHaveProperty('code')
  })

  it('surfaces the duplicate-code rejection on the code field and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(orgDepsApi.createDep).mockRejectedValue(
      new ApiError('A0712', 'department code already exists on this campus: radiology'),
    )
    renderPage()
    await waitForRow('radiology')

    await user.click(screen.getByRole('button', { name: /新建科室/ }))
    await user.click(screen.getByLabelText('所属院区'))
    await user.click(within(document.querySelector('.ant-select-dropdown') as HTMLElement).getByText('总院区'))
    await user.type(screen.getByLabelText('科室编码'), 'radiology')
    await user.type(screen.getByLabelText('科室名称'), '重复科室')
    await user.click(within(drawer()).getByRole('button', { name: '保 存' }))

    expect(
      await screen.findByText('department code already exists on this campus: radiology'),
    ).toBeInTheDocument()
    // Dialog STAYS open with the typed values standing.
    expect(drawer()).not.toBeNull()
    expect(screen.getByLabelText('科室编码')).toHaveValue('radiology')
  })

  it('campus reassignment validates the target campus through the same dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    await waitForRow('radiology')

    await waitForTableReady()
    await user.click(within(rowOf('radiology')).getByTitle('编辑科室'))
    await user.click(screen.getByLabelText('所属院区'))
    await user.click(within(document.querySelector('.ant-select-dropdown') as HTMLElement).getByText('东院区'))
    await user.click(within(drawer()).getByRole('button', { name: '保 存' }))

    await expectDialogClosed()
    expect(orgDepsApi.updateDep).toHaveBeenCalledWith(11, expect.objectContaining({ orgId: 275 }))
  })
})

describe('dep page - delete and perms (delta: mutation affordances follow system:dep:list)', () => {
  it('deletes a department after confirmation and refreshes the page', async () => {
    const user = userEvent.setup()
    pageDepFixture({ all: pageOf([DEP_RADIOLOGY_A, DEP_RADIOLOGY_B]), '274': pageOf([DEP_RADIOLOGY_A]) })
    renderPage()
    await waitForRow('radiology')

    await waitForTableReady()
    const row = rowOf('radiology')
    await user.click(within(row).getByTitle('删除科室'))
    await user.click(screen.getByRole('button', { name: '删 除' }))
    await waitFor(() => expect(orgDepsApi.deleteDep).toHaveBeenCalledWith(11))
  })

  it('hides every mutation control without system:dep:list but keeps tree and table readable', async () => {
    useAuthStore.getState().setSession({ perms: ['system:org:list'] })
    renderPage()
    await waitForRow('radiology')
    await waitForTree()

    expect(screen.queryByRole('button', { name: /新建科室/ })).toBeNull()
    expect(screen.queryByTitle('编辑科室')).toBeNull()
    expect(screen.queryByTitle('删除科室')).toBeNull()
    expect(screen.getAllByText('radiology').length).toBeGreaterThan(0)
    expect(within(tree()).getByText('总院区')).toBeInTheDocument()
  })
})

describe('deps page - button-level gating (button-perm-enforcement-web)', () => {
  it('hides ungranted affordances when only the page perm is held', async () => {
    useAuthStore.getState().setSession({ perms: ['system:dep:list'] })
    renderPage()
    await screen.findAllByText('radiology')
    expect(screen.queryByRole('button', { name: /新建科室/ })).not.toBeInTheDocument()
    expect(screen.queryByTitle('编辑科室')).not.toBeInTheDocument()
    expect(screen.queryByTitle('删除科室')).not.toBeInTheDocument()
  })

  it('reveals the three affordances when their button perms are granted', async () => {
    useAuthStore.getState().setSession({ perms: ['system:dep:list', 'system:dep:create', 'system:dep:update', 'system:dep:delete'] })
    renderPage()
    await screen.findAllByText('radiology')
    expect(screen.getByRole('button', { name: /新建科室/ })).toBeInTheDocument()
    expect(screen.getAllByTitle('编辑科室').length).toBeGreaterThan(0)
    expect(screen.getAllByTitle('删除科室').length).toBeGreaterThan(0)
  })
})
