import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExamBodyPage from '@/pages/setting/exam-body'
import * as bodyApi from '@/api/exam-body'
import * as groupApi from '@/api/exam-body-group'
import { ApiError } from '@/api/client'
import type { BodyTreeNode, ExamBodyGroup } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 检查部位 page (exam-setting-web task 4.1; spec delta `web-system-console`
 * § Exam Body Page with In-Page Group Management): the LEFT column manages
 * 部位分组 in-page (no separate menu - coordination ruling 9), the RIGHT table
 * lists the selected group's bodies with the parent column, the group drawer
 * and body drawer round-trip, and a blocked group delete surfaces the backend's
 * reason verbatim with the group kept. Api seam mocked (ruling 2026-11-05).
 */
vi.mock('@/api/exam-body')
vi.mock('@/api/exam-body-group')
vi.mock('@/utils/dict-items', () => ({
  MODALITY_DICT_ID: 1024,
  dictItemsById: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
  resolveDictLabelByValue: (items: { value: string }[], value: string) =>
    items.find((item) => item.value === value)?.value ?? value,
}))

import * as dictUtils from '@/utils/dict-items'

const MODALITY_DICT = [
  { code: 1, value: 'CT' },
  { code: 2, value: 'MR' },
]

const GROUPS: ExamBodyGroup[] = [
  { id: 1, modality: 'CT', name: '胸部', sort: 1, enable: 1, remark: null },
  { id: 2, modality: 'CT', name: '头颅', sort: 2, enable: 1, remark: null },
]

const BODIES: BodyTreeNode[] = [
  {
    id: 9, modality: 'CT', groupId: 1, upId: null, name: '胸部', code: 'XB',
    sort: 1, enable: 1, remark: null, children: [
      { id: 10, modality: 'CT', groupId: 1, upId: 9, name: '肺', code: 'XB-F', sort: 2, enable: 1, remark: '含左肺与右肺', children: [] },
    ],
  },
  // second root - the prune-to-match filter must drop it on a 肺 keyword
  { id: 11, modality: 'CT', groupId: 1, upId: null, name: '腹', code: 'WB', sort: 3, enable: 1, remark: null, children: [] },
]

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <ExamBodyPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsById).mockImplementation(async (catalId: number) => {
    if (catalId === dictUtils.MODALITY_DICT_ID) return MODALITY_DICT
    return []
  })
  useAuthStore.getState().setSession({
    perms: ['setting:body:list', 'setting:body:create', 'setting:body:update', 'setting:body:delete'],
  })
  vi.mocked(groupApi.listBodyGroups).mockResolvedValue(GROUPS)
  vi.mocked(bodyApi.listExamBodies).mockResolvedValue(BODIES)
  vi.mocked(groupApi.createBodyGroup).mockResolvedValue(3)
  vi.mocked(groupApi.updateBodyGroup).mockResolvedValue(true)
  vi.mocked(groupApi.deleteBodyGroup).mockResolvedValue(true)
  vi.mocked(bodyApi.createExamBody).mockResolvedValue(11)
  vi.mocked(bodyApi.updateExamBody).mockResolvedValue(true)
  vi.mocked(bodyApi.deleteExamBody).mockResolvedValue(true)
})

describe('exam body page - in-page group management', () => {
  it('loads groups into the left column and bodies of the selected group', async () => {
    renderPage()
    await screen.findByText('肺')

    expect(groupApi.listBodyGroups).toHaveBeenCalledWith('CT')
    expect(bodyApi.listExamBodies).toHaveBeenCalledWith('CT', 1)
    expect(screen.getByText('XB-F')).toBeTruthy()
    // The LEFT column carries the group; unique anchors avoid the 胸部 collisions.
    expect(screen.getByText('头颅')).toBeTruthy()
  })

  it('creating a group refreshes the left column and the right filter in place', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('头颅')
    vi.mocked(groupApi.listBodyGroups).mockClear()

    await user.click(screen.getByRole('button', { name: /新建分组/ }))
    await user.type(await screen.findByLabelText('分组'), '腹部')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(groupApi.createBodyGroup).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'CT', name: '腹部' }),
      )
    })
    await waitFor(() => {
      expect(groupApi.listBodyGroups).toHaveBeenCalledWith('CT')
    })
  })

  it('a blocked group delete surfaces the backend reason and keeps the group', async () => {
    const user = userEvent.setup()
    vi.mocked(groupApi.deleteBodyGroup).mockRejectedValue(
      new ApiError('A0400', '该分组下仍有 2 个部位，请先移除部位后再删除分组'),
    )
    renderPage()
    await screen.findByText('头颅')

    await user.click(screen.getAllByRole('button', { name: /删除分组/ })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await screen.findByText('该分组下仍有 2 个部位，请先移除部位后再删除分组')
    expect(screen.getByText('头颅')).toBeTruthy()
  })
})

describe('exam body page - body drawer', () => {
  it('creates a body with an optional parent of the same group', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺')

    await user.click(screen.getByRole('button', { name: /新建部位/ }))
    // the drawer's 上级 select still lists the FLATTENED forest (the tree-table
    // ruling changed the table only)
    await user.click(await screen.findByLabelText('上级'))
    expect(
      within(document.querySelector('.ant-select-dropdown') as HTMLElement).getByText('腹'),
    ).toBeTruthy()

    await user.type(await screen.findByLabelText('部位'), '纵隔')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(bodyApi.createExamBody).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'CT', groupId: 1, name: '纵隔' }),
      )
    })
  })

  it('edits echo the row and PUT the D9 surface', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺')

    const lungRow = screen.getByText('肺').closest('tr') as HTMLElement
    await user.click(within(lungRow).getByRole('button', { name: '编辑' }))
    const nameInput = await screen.findByLabelText('部位')
    expect((nameInput as HTMLInputElement).value).toBe('肺')

    await user.clear(nameInput)
    await user.type(nameInput, '肺改')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(bodyApi.updateExamBody).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ modality: 'CT', groupId: 1, name: '肺改', upId: 9 }),
      )
    })
  })
})

describe('exam body page - name query row', () => {
  it('maintains the body-part device field (exam-body-device-field-cleanup)', async () => {
    const user = userEvent.setup()
    const withDevice: BodyTreeNode = {
      id: 12, modality: 'CT', groupId: 1, upId: null, name: '甲状腺', code: 'JZX',
      deviceBodyPart: 'THYROID', sort: 4, enable: 1, remark: null,
      children: [],
    }
    vi.mocked(bodyApi.listExamBodies).mockResolvedValue([
      { ...BODIES[0], children: [...BODIES[0].children, withDevice] },
      { ...BODIES[1], remark: '备注-腹' },
    ])
    renderPage()
    await screen.findByText('甲状腺')

    // the table's 设备 column renders the single Body Part term
    const thyroidRow = screen.getByText('甲状腺').closest('tr') as HTMLElement
    expect(within(thyroidRow).getByText('THYROID')).toBeInTheDocument()
    // the Latin name is gone from the wire and the page
    expect(thyroidRow.textContent).not.toContain('Thyroid')
    // a blank row shows the em-dash in the 设备 column
    const plainRow = screen.getByText('腹').closest('tr') as HTMLElement
    expect(within(plainRow).getByText('—')).toBeInTheDocument()

    // the drawer echoes the field and the PUT carries the edit
    await user.click(within(thyroidRow).getByRole('button', { name: '编辑' }))
    const tokenInput = await screen.findByLabelText('部位token')
    expect((tokenInput as HTMLInputElement).value).toBe('THYROID')
    expect(screen.queryByLabelText('拉丁名')).not.toBeInTheDocument()
    await user.clear(tokenInput)
    await user.type(tokenInput, 'NECK')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(bodyApi.updateExamBody).toHaveBeenCalledWith(
        12,
        expect.objectContaining({ deviceBodyPart: 'NECK' }),
      )
    })
  })

  it('filters the table by 部位名称 and resets the keyword on group switch', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺')

    // two tables now (dicts-style group list + bodies table) - anchor the
    // bodies table via its unique 编码 column header (2026-11-16 rename)
    const table = screen
      .getByRole('columnheader', { name: '编码' })
      .closest('table') as HTMLElement
    expect(within(table).getByText('XB')).toBeTruthy()

    // 查询 commits the draft: prune-to-match keeps the hit 肺 WITH its ancestor
    // 胸部 (code XB stays visible) and drops the unrelated 腹 branch.
    await user.type(screen.getByLabelText('部位模糊查询'), '肺')
    await user.click(screen.getByRole('button', { name: /查\s*询|查询/ }))
    expect(within(table).getByText('肺')).toBeTruthy()
    expect(within(table).getByText('XB')).toBeTruthy()
    expect(within(table).queryByText('腹')).toBeNull()

    // Context switch (another group) clears the filter and the draft - the
    // pruned 腹 branch returns with the fresh payload.
    await user.click(screen.getByText('头颅'))
    await waitFor(() => {
      expect(within(table).getByText('腹')).toBeTruthy()
    })
    expect((screen.getByLabelText('部位模糊查询') as HTMLInputElement).value).toBe('')
  })
})

describe('exam body page - tree table (2026-11-16 ruling)', () => {
  const bodiesTable = () =>
    screen.getByRole('columnheader', { name: '编码' }).closest('table') as HTMLElement

  it('renders the forest as tree rows, expanded by default, without 上级部位', async () => {
    renderPage()
    await screen.findByText('肺')
    const table = bodiesTable()

    // the parent row carries an expand control, opened (controlled expand-all)
    const chestRow = within(table).getByText('XB').closest('tr') as HTMLElement
    const expand = chestRow.querySelector('.ant-table-row-expand-icon') as HTMLElement
    expect(expand).not.toBeNull()
    expect(expand.className).toContain('expanded')

    // leaf rows carry only antd's invisible `-spaced` alignment placeholder -
    // a toggleable control would be `-collapsed`/`-expanded`
    const lungRow = screen.getByText('肺').closest('tr') as HTMLElement
    const lungExpand = lungRow.querySelector('.ant-table-row-expand-icon') as HTMLElement
    expect(lungExpand.className).toContain('-spaced')
    expect(lungExpand.className).not.toContain('-collapsed')
    expect(lungExpand.className).not.toContain('-expanded')

    // the hierarchy IS the parent read - the 上级部位 column is gone
    expect(within(table).queryByRole('columnheader', { name: '上级部位' })).toBeNull()
  })

  it('a no-hit keyword empties the tree', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺')
    const table = bodiesTable()

    await user.type(screen.getByLabelText('部位模糊查询'), '骨')
    await user.click(screen.getByRole('button', { name: /查\s*询|查询/ }))
    expect(within(table).queryByText('肺')).toBeNull()
    expect(within(table).queryByText('XB')).toBeNull()
  })
})

describe('exam body page - right table polish (2026-11-16)', () => {
  // Anchor the bodies table via its unique 编码 header (the group table has no
  // code column - the NAME is the operational key).
  const bodiesTable = () =>
    screen.getByRole('columnheader', { name: '编码' }).closest('table') as HTMLElement

  it('carries the refresh affordance in the 操作 header and re-fetches bodies', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺')
    const table = bodiesTable()

    const refresh = within(table).getByRole('button', { name: '刷新列表' })
    // the glyph rides the 操作 header cell, pinned right of the centered label
    expect(within(refresh.closest('th') as HTMLElement).getByText('操作')).toBeTruthy()

    vi.mocked(bodyApi.listExamBodies).mockClear()
    await user.click(refresh)
    await waitFor(() => {
      expect(bodyApi.listExamBodies).toHaveBeenCalledWith('CT', 1)
    })
  })

  it('renders 编辑 with the edit glyph and 删除 with the delete glyph', async () => {
    renderPage()
    await screen.findByText('肺')

    const lungRow = screen.getByText('肺').closest('tr') as HTMLElement
    expect(
      within(lungRow).getByRole('button', { name: '编辑' }).querySelector('.anticon-edit'),
    ).not.toBeNull()
    // 删除 used to ride ConsoleAction's edit-glyph default (user request
    // 2026-11-16) - it must carry the delete glyph like every other page.
    expect(
      within(lungRow).getByRole('button', { name: '删除' }).querySelector('.anticon-delete'),
    ).not.toBeNull()
  })

  it('centers 编码, keeps 部位 / 备注 default-aligned, drops 排序 and 创建时间', async () => {
    renderPage()
    await screen.findByText('肺')
    const table = bodiesTable()

    expect(within(table).getByRole('columnheader', { name: '编码' })).toHaveStyle({
      textAlign: 'center',
    })
    // 创建时间 left the list (2026-11-17 - initialization-imported catalog data
    // reads no create time); the payload keeps the audit field.
    expect(within(table).queryByRole('columnheader', { name: '创建时间' })).toBeNull()
    // unspecified columns keep AntD's default (no inline center)
    expect(within(table).getByRole('columnheader', { name: '部位' })).not.toHaveStyle({
      textAlign: 'center',
    })
    expect(within(table).getByRole('columnheader', { name: '备注' })).not.toHaveStyle({
      textAlign: 'center',
    })
    // 排序 left the table (user ruling 2026-11-16); the drawer field stays.
    expect(within(table).queryByRole('columnheader', { name: '排序' })).toBeNull()

    // 备注 column (user request 2026-11-16): row remarks render verbatim.
    expect(within(table).getByText('含左肺与右肺')).toBeTruthy()

    const lungRow = screen.getByText('肺').closest('tr') as HTMLElement
    expect(within(lungRow).getByText('XB-F').closest('td')).toHaveStyle({ textAlign: 'center' })
  })
})

describe('exam body page - group list table', () => {
  it('renders the dicts-style paged list: pager counter + row-click rescopes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('头颅')

    // the dicts-style pager row carries the OPS counter
    expect(screen.getByText(/共 2 条/)).toBeTruthy()
    // row click selects the group and rescopes the right table
    await user.click(screen.getByText('头颅'))
    await waitFor(() => {
      expect(bodyApi.listExamBodies).toHaveBeenCalledWith('CT', 2)
    })
  })
})

describe('exam body page - perm gating', () => {
  it('hides 新建分组/新建部位 without setting:body:create', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:body:list'] })
    renderPage()
    await screen.findByText('头颅')

    expect(screen.queryByRole('button', { name: /新建分组/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /新建部位/ })).toBeNull()
  })
})
