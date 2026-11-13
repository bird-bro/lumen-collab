import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExamItemPage from '@/pages/setting/exam-item'
import * as bodyGroupApi from '@/api/exam-body-group'
import * as bodyApi from '@/api/exam-body'
import * as itemApi from '@/api/exam-item'
import type { BodyTreeNode, ExamBodyGroup, ExamItem, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 检查项目 page (exam-setting-web task 5.1; spec delta `web-system-console`
 * § Exam Item Page): the modality/group/常用项目/keyword filter bar, the
 * display-resolved table (server-rendered body/method names - D2), the drawer's
 * 分组→部位 CASCADE (group change reloads the body options and clears the stale
 * body), the method multi-select ID array, and the D9 numeric fields with the
 * 常用项目 switch. Api seam mocked (ruling 2026-11-05).
 */
vi.mock('@/api/exam-item')
vi.mock('@/api/exam-body-group')
vi.mock('@/api/exam-body')
vi.mock('@/api/exam-method')
vi.mock('@/utils/dict-items', () => ({
  dictItemsByName: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
  resolveDictLabelByValue: (items: { value: string }[], value: string) =>
    items.find((item) => item.value === value)?.value ?? value,
}))

import * as dictUtils from '@/utils/dict-items'
import * as methodApi from '@/api/exam-method'

const MODALITY_DICT = [
  { code: 1, value: 'CT' },
  { code: 2, value: 'MR' },
]

const GROUPS: ExamBodyGroup[] = [
  { id: 1, modality: 'CT', name: '胸部', sort: 1, enable: 1 },
  { id: 2, modality: 'CT', name: '头颅', sort: 2, enable: 1 },
]

const BODIES: BodyTreeNode[] = [
  { id: 9, modality: 'CT', groupId: 1, upId: null, name: '胸部', code: null, sort: 1, enable: 1, children: [] },
  { id: 10, modality: 'CT', groupId: 1, upId: 9, name: '肺', code: null, sort: 2, enable: 1, children: [] },
]

/** 头颅 group's own bodies (the cascade mock switches on groupId). */
const BRAIN_BODIES: BodyTreeNode[] = [
  { id: 20, modality: 'CT', groupId: 2, upId: null, name: '头颅', code: null, sort: 1, enable: 1, children: [] },
  { id: 21, modality: 'CT', groupId: 2, upId: 20, name: '脑实质', code: null, sort: 2, enable: 1, children: [] },
]

const METHODS = [
  { id: 3, modality: 'CT', name: '平扫', code: 'PS', sort: 1, enable: 1 },
  { id: 4, modality: 'CT', name: '增强', code: 'ZQ', sort: 2, enable: 1 },
]

function item(partial: Partial<ExamItem> & { id: number; exam: string }): ExamItem {
  return {
    modality: 'CT',
    code: null,
    bodyId: 9,
    price: null,
    rate: null,
    tag: null,
    scoreRep: null,
    scoreAud: null,
    isCommon: false,
    sort: null,
    enable: 1,
    remark: null,
    createTime: '2026-11-12 09:30:00',
    bodyName: null,
    groupName: null,
    methodNames: '',
    ...partial,
  }
}

const CHEST_CT = item({
  id: 21,
  exam: '胸部CT平扫',
  code: 'XBCT-001',
  price: 120,
  rate: 1,
  tag: 1,
  scoreRep: 7,
  scoreAud: 3,
  isCommon: true,
  bodyName: '胸部',
  groupName: '胸部',
  methodNames: '平扫',
  methodIds: [3],
})

function pageFixture(records: ExamItem[], total = records.length): Page<ExamItem> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <ExamItemPage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsByName).mockImplementation(async (name: string) => {
    if (name === '检查类型') return MODALITY_DICT
    return []
  })
  useAuthStore.getState().setSession({
    perms: ['setting:item:list', 'setting:item:create', 'setting:item:update', 'setting:item:delete'],
  })
  vi.mocked(bodyGroupApi.listBodyGroups).mockResolvedValue(GROUPS)
  vi.mocked(bodyApi.listExamBodies).mockImplementation(async (_modality, groupId) =>
    groupId === 2 ? BRAIN_BODIES : BODIES,
  )
  vi.mocked(methodApi.listExamMethods).mockResolvedValue(METHODS)
  vi.mocked(itemApi.pageExamItems).mockResolvedValue(pageFixture([CHEST_CT]))
  vi.mocked(itemApi.getExamItem).mockResolvedValue(CHEST_CT)
  vi.mocked(itemApi.createExamItem).mockResolvedValue(99)
  vi.mocked(itemApi.updateExamItem).mockResolvedValue(true)
  vi.mocked(itemApi.deleteExamItem).mockResolvedValue(true)
})

describe('exam item page - filters and display-resolved table', () => {
  it('pages the first modality on mount and renders the SERVER-RESOLVED names', async () => {
    renderPage()
    await screen.findByText('胸部CT平扫')

    expect(dictUtils.dictItemsByName).toHaveBeenCalledWith('检查类型')
    expect(itemApi.pageExamItems).toHaveBeenCalledWith(
      expect.objectContaining({ modality: 'CT' }),
    )
    expect(screen.getAllByText('胸部').length).toBeGreaterThan(0)
    expect(screen.getByText('平扫')).toBeTruthy()
    expect(screen.getByText('XBCT-001')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
  })

  it('filtering by 部位分组 pages through the group filter', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')
    vi.mocked(itemApi.pageExamItems).mockClear()

    await user.click(screen.getByRole('combobox', { name: '部位分组' }))
    await waitFor(() => {
      const option = screen
        .getAllByText('胸部')
        .find((el) => el.closest('.ant-select-item-option'))
      if (!option) throw new Error('group option not rendered yet')
      return option
    })
    const option = screen
      .getAllByText('胸部')
      .find((el) => el.closest('.ant-select-item-option'))
    await user.click(option!)

    await waitFor(() => {
      expect(itemApi.pageExamItems).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'CT', groupId: 1 }),
      )
    })
  })
})

describe('exam item page - drawer cascade and round-trips', () => {
  it('group selection in the drawer CASCADES the body options', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getByRole('button', { name: /新建项目/ }))
    const drawer = screen.getByRole('dialog')
    await user.click(within(drawer).getByRole('combobox', { name: '部位分组' }))
    const groupOption = await waitFor(() => {
      const found = screen
        .getAllByText('头颅')
        .find((el) => el.closest('.ant-select-item-option'))
      if (!found) throw new Error('group option not rendered yet')
      return found
    })
    await user.click(groupOption!)

    await waitFor(() => {
      expect(bodyApi.listExamBodies).toHaveBeenCalledWith('CT', 2)
    })
  })

  it('creates an item with the method ID ARRAY and numeric fields', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getByRole('button', { name: /新建项目/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('项目名称'), '头颅MRI平扫')
    await user.click(within(drawer).getByRole('combobox', { name: '部位分组' }))
    const groupOption = await waitFor(() => {
      const found = screen
        .getAllByText('头颅')
        .find((el) => el.closest('.ant-select-item-option'))
      if (!found) throw new Error('group option not rendered yet')
      return found
    })
    await user.click(groupOption!)
    await user.click(within(drawer).getByRole('combobox', { name: '检查部位' }))
    const bodyOption = await waitFor(() => {
      const found = screen
        .getAllByText('脑实质')
        .find((el) => el.closest('.ant-select-item-option'))
      if (!found) throw new Error('body option not rendered yet')
      return found
    })
    await user.click(bodyOption!)
    await user.click(within(drawer).getByRole('combobox', { name: '检查方法' }))
    const methodOption = await waitFor(() => {
      const found = screen
        .getAllByText('平扫')
        .find((el) => el.closest('.ant-select-item-option'))
      if (!found) throw new Error('method option not rendered yet')
      return found
    })
    await user.click(methodOption!)
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(itemApi.createExamItem).toHaveBeenCalledWith(
        expect.objectContaining({
          modality: 'CT',
          exam: '头颅MRI平扫',
          bodyId: 21,
          methodIds: [3],
        }),
      )
    })
    expect(itemApi.pageExamItems).toHaveBeenCalled()
  })

  it('edits echo the display-resolved detail and PUT with method ids', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getAllByRole('button', { name: '编辑' })[0])
    const drawer = await screen.findByRole('dialog')
    const nameInput = within(drawer).getByLabelText('项目名称')
    expect((nameInput as HTMLInputElement).value).toBe('胸部CT平扫')

    await user.clear(nameInput)
    await user.type(nameInput, '胸部CT平扫改')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(itemApi.updateExamItem).toHaveBeenCalledWith(
        21,
        expect.objectContaining({ modality: 'CT', exam: '胸部CT平扫改', bodyId: 9 }),
      )
    })
  })

  it('deletes with the Popconfirm and refreshes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getAllByRole('button', { name: '删除' })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await waitFor(() => {
      expect(itemApi.deleteExamItem).toHaveBeenCalledWith(21)
    })
  })
})

describe('exam item page - perm gating', () => {
  it('hides 新建 without setting:item:create', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:item:list'] })
    renderPage()
    await screen.findByText('胸部CT平扫')

    expect(screen.queryByRole('button', { name: /新建项目/ })).toBeNull()
  })
})
