import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RepTemplatePage from '@/pages/setting/rep-template'
import * as tempApi from '@/api/rep-temp'
import { ApiError } from '@/api/client'
import type { Page, RepTemp, RepTempNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 报告模板 page (exam-setting-web task 6.1; spec delta `web-system-console`
 * § Report Template Page): the 公共/私有 tab toggle threads scope `type` into
 * EVERY call and reloads both panes; the left tree carries node-level
 * 新建/编辑/删除; the right pane pages the templates under the selected node
 * with the name search; the drawer's 目录/模板 flag radio toggles the
 * finding/impression textareas. Private rows never leak across scopes (the
 * server enforces; the page just switches). Api seam mocked (ruling 2026-11-05).
 */
vi.mock('@/api/rep-temp')
vi.mock('@/utils/dict-items', () => ({
  dictItemsByName: vi.fn(),
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

const PUBLIC_TREE: RepTempNode[] = [
  {
    id: 31, name: '呼吸系统', flag: 0, upId: null, children: [
      { id: 32, name: '肺结节随访', flag: 1, upId: 31, children: [] },
    ],
  },
]
const PRIVATE_TREE: RepTempNode[] = [
  {
    id: 41, name: '我的短语', flag: 0, upId: null, children: [
      { id: 42, name: '我的肺结节', flag: 1, upId: 41, children: [] },
    ],
  },
]

const PUBLIC_TEMP: RepTemp = {
  id: 32, type: 1, ownerUid: null, modality: 'CT', flag: 1, upId: 31,
  name: '肺结节随访', finding: '右肺上叶见磨玻璃结节影…', impression: '建议随访。',
  createTime: '2026-11-12 10:00:00',
}

function pageFixture(records: RepTemp[], total = records.length): Page<RepTemp> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <RepTemplatePage />
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
    perms: ['setting:temp:list', 'setting:temp:create', 'setting:temp:update', 'setting:temp:delete'],
  })
  vi.mocked(tempApi.repTempTree).mockImplementation(async (_modality, type) =>
    type === 2 ? PRIVATE_TREE : PUBLIC_TREE,
  )
  vi.mocked(tempApi.pageRepTemps).mockImplementation(
    async (query: { type: number; upId: number }) => {
      // Arg-aware: the private scope only serves the private tree's node 41.
      if (query.type === 2 && query.upId === 41) {
        return pageFixture([
          { ...PUBLIC_TEMP, id: 42, type: 2, ownerUid: 990001, upId: 41, name: '我的肺结节' },
        ])
      }
      if (query.type === 1 && query.upId === 31) return pageFixture([PUBLIC_TEMP])
      return pageFixture([])
    },
  )
  vi.mocked(tempApi.createRepTemp).mockResolvedValue(50)
  vi.mocked(tempApi.updateRepTemp).mockResolvedValue(true)
  vi.mocked(tempApi.deleteRepTemp).mockResolvedValue(true)
})

describe('report template page - scope tabs', () => {
  it('loads the public tree + files on mount with type=1', async () => {
    renderPage()
    await screen.findByText('肺结节随访')

    expect(tempApi.repTempTree).toHaveBeenCalledWith('CT', 1)
    expect(tempApi.pageRepTemps).toHaveBeenCalledWith(
      expect.objectContaining({ type: 1, modality: 'CT', upId: 31 }),
    )
    expect(screen.getByText('呼吸系统')).toBeTruthy()
  })

  it('switching to the 私有 tab reloads BOTH panes with type=2', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺结节随访')
    vi.mocked(tempApi.repTempTree).mockClear()
    vi.mocked(tempApi.pageRepTemps).mockClear()

    await user.click(screen.getByRole('button', { name: '私有' }))

    await screen.findAllByText('我的肺结节')
    expect(tempApi.repTempTree).toHaveBeenCalledWith('CT', 2)
    // The right pane selects the private tree's first node and pages under it.
    await waitFor(() => {
      expect(tempApi.pageRepTemps).toHaveBeenCalledWith(
        expect.objectContaining({ type: 2, upId: 41 }),
      )
    })
    expect(screen.queryByText('肺结节随访')).toBeNull()
  })
})

describe('report template page - tree node actions', () => {
  it('a tree node 新建 opens the drawer pre-bound to that parent', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: /新建子级|＋/ })[0])
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('呼吸系统')).toBeTruthy()
  })

  it('the flag radio toggles the finding/impression textareas', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: /新建子级|＋/ })[0])
    const drawer = screen.getByRole('dialog')

    expect(within(drawer).getByLabelText('影像学表现')).toBeTruthy()
    await user.click(within(drawer).getByRole('radio', { name: '目录' }))
    await waitFor(() => {
      expect(within(drawer).queryByLabelText('影像学表现')).toBeNull()
    })
  })

  it('saving a template under a tree node POSTs with the parent and refreshes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: /新建子级|＋/ })[0])
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '胸腔积液')
    await user.type(within(drawer).getByLabelText('影像学表现'), '左侧胸腔积液')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ type: 1, modality: 'CT', flag: 1, upId: 31, name: '胸腔积液' }),
      )
    })
    expect(tempApi.repTempTree).toHaveBeenCalledTimes(2)
  })

  it('node delete refuses surface the backend message verbatim', async () => {
    const user = userEvent.setup()
    vi.mocked(tempApi.deleteRepTemp).mockRejectedValue(
      new ApiError('A0400', '该目录下仍有子节点，请先删除子节点'),
    )
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: /删/ })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await screen.findByText('该目录下仍有子节点，请先删除子节点')
    expect(screen.getByText('呼吸系统')).toBeTruthy()
  })
})

describe('report template page - perm gating', () => {
  it('hides node 新建 without setting:temp:create', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:temp:list'] })
    renderPage()
    await screen.findByText('呼吸系统')

    expect(screen.queryByRole('button', { name: /新建子级|＋/ })).toBeNull()
  })
})
