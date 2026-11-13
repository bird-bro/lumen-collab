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

const GROUPS: ExamBodyGroup[] = [
  { id: 1, modality: 'CT', name: '胸部', sort: 1, enable: 1 },
  { id: 2, modality: 'CT', name: '头颅', sort: 2, enable: 1 },
]

const BODIES: BodyTreeNode[] = [
  {
    id: 9, modality: 'CT', groupId: 1, upId: null, name: '胸部', code: 'XB',
    sort: 1, enable: 1, children: [
      { id: 10, modality: 'CT', groupId: 1, upId: 9, name: '肺', code: 'XB-F', sort: 2, enable: 1, children: [] },
    ],
  },
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
  vi.mocked(dictUtils.dictItemsByName).mockImplementation(async (name: string) => {
    if (name === '检查类型') return MODALITY_DICT
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
    await user.type(await screen.findByLabelText('分组名称'), '腹部')
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
    await user.type(await screen.findByLabelText('部位名称'), '纵隔')
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
    const nameInput = await screen.findByLabelText('部位名称')
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

describe('exam body page - perm gating', () => {
  it('hides 新建分组/新建部位 without setting:body:create', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:body:list'] })
    renderPage()
    await screen.findByText('头颅')

    expect(screen.queryByRole('button', { name: /新建分组/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /新建部位/ })).toBeNull()
  })
})
