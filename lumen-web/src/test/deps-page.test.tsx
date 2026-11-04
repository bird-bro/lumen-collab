import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepsPage from '@/pages/system/deps'
import * as depsApi from '@/api/org-deps'
import * as orgsApi from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgDep, OrgNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 科室管理 page (batch 6a, task 6.2; spec `specs/web-system-console`
 * § Department Management: departments listed for the selected org, CRUD
 * round-trips, "the page SHALL present the org tree as the selection context
 * (left aside, right list pattern)").
 *
 * Layout source: `prototype/org-deps.html` `.work` block verbatim - the left
 * `.tree-panel` (250px, tree-hd 「机构」 + `.tree-hint` + the dashed
 * 「新建根机构」 ghost button) as the SELECTION CONTEXT (no row actions - the
 * org verbs belong to the org page) and the right `.list-panel` (`.tool-row`
 * code/name input + 查询 + `.push` 新建科室, `.tbl` 科室名称/科室编码/创建时间/
 * 操作 with `.th-refresh`, centered `.pager`). The create dialog follows the
 * prototype's `.modal` (上级机构 select, 科室编码 required + the DICOM/HL7
 * purpose note, 科室名称).
 *
 * The api seam is stubbed with `vi.mock` (design D9).
 */
vi.mock('@/api/org-deps')
vi.mock('@/api/orgs')

function orgNode(partial: Partial<OrgNode> & { id: number; name: string }): OrgNode {
  return {
    upId: 0,
    level: 1,
    type: 0,
    createBy: null,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    children: null,
    ...partial,
  }
}

function dep(partial: Partial<OrgDep> & { id: number; code: string; name: string }): OrgDep {
  return {
    orgId: 1,
    createBy: null,
    createTime: '2026-11-04 09:00:00',
    updateBy: null,
    updateTime: '2026-11-04 09:00:00',
    ...partial,
  }
}

const HOSPITAL = orgNode({ id: 1, name: '示例医院' })
const BRANCH = orgNode({ id: 2, name: '华东分院', upId: 1, level: 2 })

const SEEDED_DEPS = [
  dep({ id: 1, orgId: 1, code: 'radiology', name: '放射科' }),
  dep({ id: 2, orgId: 1, code: 'us', name: '超声科' }),
  dep({ id: 3, orgId: 1, code: 'endo', name: '内镜科' }),
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <DepsPage />
    </ConfigProvider>,
  )
}

/** Table-scoped row lookup (a closed modal lingers in jsdom and may repeat text). */
function rowOf(name: string): HTMLElement {
  const table = document.querySelector('.ant-table') as HTMLElement
  const row = within(table).getByText(name).closest('tr') as HTMLElement
  expect(row).toBeTruthy()
  return row
}

/** jsdom starts no CSS animation, so a closed AntD modal lingers mid-leave;
 *  the observable close signal is the `*-leave` motion class (see orgs tests). */
const DIALOG_LEAVING = 'ant-zoom-leave'

async function expectDialogClosed() {
  const dialog = screen.getByRole('dialog')
  await waitFor(() => expect(dialog).toHaveClass(DIALOG_LEAVING))
}

beforeEach(() => {
  vi.clearAllMocks()
  // The documented perms string of this page (SEED_MENUS id 8, also the
  // backend @PreAuthorize on the dep list - no new perms code is invented).
  useAuthStore.getState().setSession({ perms: ['system:dep:list'] })
  vi.mocked(orgsApi.orgTree).mockResolvedValue([HOSPITAL])
  vi.mocked(depsApi.listDeps).mockResolvedValue(SEEDED_DEPS)
  vi.mocked(depsApi.createDep).mockResolvedValue(4)
  vi.mocked(depsApi.updateDep).mockResolvedValue(true)
  vi.mocked(depsApi.deleteDep).mockResolvedValue(true)
})

describe('deps page - org tree as selection context', () => {
  it('auto-selects the seeded root and lists only THAT org via listDeps(orgId)', async () => {
    renderPage()
    await screen.findByText('示例医院')
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenCalledWith(1))
    await screen.findByText('放射科')
    expect(screen.getByText('超声科')).toBeInTheDocument()
    expect(screen.getByText('内镜科')).toBeInTheDocument()
    expect(screen.getByText('共 3 条')).toBeInTheDocument()
  })

  it('re-lists for the org node the administrator selects (spec: Departments of a selected org)', async () => {
    const user = userEvent.setup()
    vi.mocked(orgsApi.orgTree).mockResolvedValue([
      orgNode({ ...HOSPITAL, children: [BRANCH] }),
    ])
    vi.mocked(depsApi.listDeps).mockImplementation((orgId?: number) =>
      Promise.resolve(orgId === 2 ? [dep({ id: 9, orgId: 2, code: 'pathology', name: '病理科' })] : SEEDED_DEPS),
    )
    renderPage()
    await screen.findByText('放射科')

    await user.click(screen.getByText('华东分院'))
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenLastCalledWith(2))
    await screen.findByText('病理科')
    expect(screen.queryByText('放射科')).not.toBeInTheDocument()
  })
})

describe('deps page - code/name query', () => {
  it('filters the listed departments when 查询 is pressed', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('放射科')

    await user.type(screen.getByLabelText('科室编码 / 名称'), 'us')
    await user.click(screen.getByRole('button', { name: /查询/ }))

    expect(screen.getByText('超声科')).toBeInTheDocument()
    expect(screen.queryByText('放射科')).not.toBeInTheDocument()
    expect(screen.getByText('共 1 条')).toBeInTheDocument()
  })
})

describe('deps page - CRUD round-trip (spec: Department CRUD round-trip)', () => {
  it('creates a department under the selected org with the documented body, then re-lists', async () => {
    const user = userEvent.setup()
    vi.mocked(depsApi.listDeps).mockResolvedValueOnce(SEEDED_DEPS).mockResolvedValue([
      ...SEEDED_DEPS,
      dep({ id: 4, orgId: 1, code: 'pathology', name: '病理科' }),
    ])
    renderPage()
    await screen.findByText('放射科')

    await user.click(screen.getByRole('button', { name: /新建科室/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建科室' })
    // The prototype modal names the org context (上级机构 row).
    expect(within(dialog).getByText('示例医院')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('科室编码'), 'pathology')
    await user.type(within(dialog).getByLabelText('科室名称'), '病理科')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() =>
      expect(depsApi.createDep).toHaveBeenCalledWith({
        orgId: 1,
        code: 'pathology',
        name: '病理科',
      }),
    )
    await expectDialogClosed()
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenCalledTimes(2))
    await screen.findByText('病理科')
  })

  it('edits a department through PUT /org-deps/{id} and reflects the change', async () => {
    const user = userEvent.setup()
    vi.mocked(depsApi.listDeps)
      .mockResolvedValueOnce(SEEDED_DEPS)
      .mockResolvedValue([
        dep({ id: 1, orgId: 1, code: 'radiology', name: '放射影像科' }),
        SEEDED_DEPS[1],
        SEEDED_DEPS[2],
      ])
    renderPage()
    await screen.findByText('放射科')

    const row = rowOf('放射科')
    await user.click(within(row).getByRole('button', { name: '编辑科室' }))

    const dialog = await screen.findByRole('dialog', { name: '编辑科室' })
    const nameInput = within(dialog).getByLabelText('科室名称')
    expect(nameInput).toHaveValue('放射科')
    await user.clear(nameInput)
    await user.type(nameInput, '放射影像科')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(depsApi.updateDep).toHaveBeenCalledWith(1, { name: '放射影像科' }))
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenCalledTimes(2))
    await screen.findByText('放射影像科')
  })

  it('deletes behind a Popconfirm and re-lists', async () => {
    const user = userEvent.setup()
    vi.mocked(depsApi.listDeps)
      .mockResolvedValueOnce(SEEDED_DEPS)
      .mockResolvedValue([SEEDED_DEPS[1], SEEDED_DEPS[2]])
    renderPage()
    await screen.findByText('放射科')

    const row = rowOf('放射科')
    await user.click(within(row).getByRole('button', { name: '删除科室' }))
    expect(await screen.findByText('确定删除「放射科」吗?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '删 除' }))

    await waitFor(() => expect(depsApi.deleteDep).toHaveBeenCalledWith(1))
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByText('放射科')).not.toBeInTheDocument())
  })

  it('the header refresh glyph re-queries the list', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('放射科')

    await user.click(screen.getByRole('button', { name: '刷新列表' }))
    await waitFor(() => expect(depsApi.listDeps).toHaveBeenCalledTimes(2))
  })
})

describe('deps page - duplicate code feedback (brief 6.2: A0712 于表单,弹窗不关)', () => {
  it('lands the A0712 msg on the code field and keeps the dialog open', async () => {
    const user = userEvent.setup()
    vi.mocked(depsApi.createDep).mockRejectedValue(
      new ApiError('A0712', 'department code already exists: radiology'),
    )
    renderPage()
    await screen.findByText('放射科')

    await user.click(screen.getByRole('button', { name: /新建科室/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建科室' })
    await user.type(within(dialog).getByLabelText('科室编码'), 'radiology')
    await user.type(within(dialog).getByLabelText('科室名称'), '重复科室')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    // the msg appears INSIDE the form (field-level), not as a floating message
    const fieldError = await within(dialog).findByText(
      'department code already exists: radiology',
    )
    expect(fieldError).toBeInTheDocument()
    // dialog did NOT close (no leave motion class) and the input keeps its value
    expect(dialog).not.toHaveClass(DIALOG_LEAVING)
    expect(within(dialog).getByLabelText('科室编码')).toHaveValue('radiology')
  })
})

describe('deps page - A-class errors on read/delete', () => {
  it('surfaces a failed list request through message.error', async () => {
    vi.mocked(depsApi.listDeps).mockRejectedValue(new ApiError('A0400', 'org does not exist: org id 7'))
    renderPage()
    expect(await screen.findByText('org does not exist: org id 7')).toBeInTheDocument()
  })
})

describe('deps page - perms gating', () => {
  it('hides the create/row mutation controls without system:dep:list, keeps the read', async () => {
    useAuthStore.getState().setSession({ perms: ['system:role:list'] })
    renderPage()
    await screen.findByText('放射科')

    expect(screen.queryByRole('button', { name: /新建科室/ })).not.toBeInTheDocument()
    const row = rowOf('放射科')
    expect(within(row).queryByRole('button', { name: '编辑科室' })).not.toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: '删除科室' })).not.toBeInTheDocument()
    expect(depsApi.listDeps).toHaveBeenCalledTimes(1)
  })
})
