import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExamMethodPage from '@/pages/setting/exam-method'
import * as methodApi from '@/api/exam-method'
import type { ExamMethod, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 检查方法 page (exam-setting-web task 3.1; spec delta `web-system-console`
 * § Exam Method Page): the dict-检查类型 filter, the 名称/编码 keyword search,
 * the server-paginated table, the create/edit drawer, the confirm-delete with
 * the backend refusal surfaced verbatim, and the `setting:method:*` affordance
 * gating. The api seam is mocked - no page data ever flows through the mock at
 * runtime (ruling 2026-11-05).
 */
vi.mock('@/api/exam-method')
vi.mock('@/utils/dict-items', () => ({
  dictItemsByName: vi.fn(),
  resolveDictLabel: (items: { code: number; value: string }[], code: number) =>
    items.find((item) => item.code === code)?.value ?? String(code),
  resolveDictLabelByValue: (items: { value: string }[], value: string) =>
    items.find((item) => item.value === value)?.value ?? value,
}))

import * as dictUtils from '@/utils/dict-items'

/** Mirrors the real 1021 seed order: CT/MR/US/ENDO first, DR added later (V26/V27). */
const MODALITY_DICT = [
  { code: 1, value: 'CT' },
  { code: 2, value: 'MR' },
  { code: 3, value: 'US' },
  { code: 4, value: 'ENDO' },
  { code: 5, value: 'DR' },
]

function method(partial: Partial<ExamMethod> & { id: number; name: string }): ExamMethod {
  return {
    modality: 'CT',
    code: null,
    sort: null,
    enable: 1,
    createTime: '2026-11-12 08:40:00',
    ...partial,
  }
}

const PS = method({ id: 3, name: '平扫', code: 'PS' })

function pageFixture(records: ExamMethod[], total = records.length): Page<ExamMethod> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <ExamMethodPage />
    </ConfigProvider>,
  )
}

/** Opens the aria-labeled AntD Select and clicks one option (device-page pattern). */
async function pickOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  optionText: string,
) {
  await user.click(screen.getByRole('combobox', { name: label }))
  await waitFor(() => {
    const option = screen
      .getAllByText(optionText)
      .find((el) => el.closest('.ant-select-item-option'))
    if (!option) throw new Error(`option ${optionText} not rendered yet`)
    return option
  })
  const option = screen
    .getAllByText(optionText)
    .find((el) => el.closest('.ant-select-item-option'))
  await user.click(option!)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(dictUtils.dictItemsByName).mockImplementation(async (name: string) => {
    if (name === '检查类型') return MODALITY_DICT
    return []
  })
  useAuthStore.getState().setSession({
    perms: [
      'setting:method:list',
      'setting:method:create',
      'setting:method:update',
      'setting:method:delete',
    ],
  })
  vi.mocked(methodApi.pageExamMethods).mockResolvedValue(pageFixture([PS]))
  vi.mocked(methodApi.listExamMethods).mockResolvedValue([PS])
  vi.mocked(methodApi.createExamMethod).mockResolvedValue(99)
  vi.mocked(methodApi.updateExamMethod).mockResolvedValue(true)
  vi.mocked(methodApi.deleteExamMethod).mockResolvedValue(true)
})

describe('exam method page - filters and table', () => {
  it('loads the modality dict and pages the first modality on mount', async () => {
    renderPage()
    await screen.findByText('平扫')

    expect(dictUtils.dictItemsByName).toHaveBeenCalledWith('检查类型')
    expect(methodApi.pageExamMethods).toHaveBeenCalledWith(
      expect.objectContaining({ modality: 'CT' }),
    )
    expect(screen.getByText('PS')).toBeTruthy()
  })

  it('keyword search pages with the keyword and resets to page 1', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('平扫')
    vi.mocked(methodApi.pageExamMethods).mockClear()

    await user.type(screen.getByPlaceholderText('请输入关键词'), '平扫')
    await user.click(screen.getByRole('button', { name: /查 询|查询/ }))

    await waitFor(() => {
      expect(methodApi.pageExamMethods).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'CT', keyword: '平扫' }),
      )
    })
  })

  it('switching the modality re-pages under that modality', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('平扫')
    vi.mocked(methodApi.pageExamMethods).mockClear()

    await pickOption(user, '检查类型', 'DR')

    await waitFor(() => {
      expect(methodApi.pageExamMethods).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'DR' }),
      )
    })
  })
})

describe('exam method page - create/edit/delete round-trips', () => {
  it('creates a method through the drawer and refreshes the table', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('平扫')

    await user.click(screen.getByRole('button', { name: /新建方法/ }))
    await user.type(await screen.findByLabelText('方法名称'), '增强')
    await user.type(screen.getByLabelText('方法编码'), 'ZQ')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(methodApi.createExamMethod).toHaveBeenCalledWith(
        expect.objectContaining({ modality: 'CT', name: '增强', code: 'ZQ' }),
      )
    })
    expect(methodApi.pageExamMethods).toHaveBeenCalled()
  })

  it('edits echo the row into the drawer and PUT the full surface', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('平扫')

    await user.click(screen.getAllByRole('button', { name: /编辑/ })[0])
    const nameInput = await screen.findByLabelText('方法名称')
    expect((nameInput as HTMLInputElement).value).toBe('平扫')

    await user.clear(nameInput)
    await user.type(nameInput, '平扫改')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(methodApi.updateExamMethod).toHaveBeenCalledWith(
        3,
        expect.objectContaining({ modality: 'CT', name: '平扫改', code: 'PS' }),
      )
    })
  })

  it('delete confirms, calls the API, and surfaces a refusal message verbatim', async () => {
    const user = userEvent.setup()
    vi.mocked(methodApi.deleteExamMethod).mockRejectedValue(
      new (await import('@/api/client')).ApiError('A0400', '该方法仍被 2 个检查项目使用，请先调整项目后再删除'),
    )
    renderPage()
    await screen.findByText('平扫')

    await user.click(screen.getAllByRole('button', { name: /删除/ })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await waitFor(() => {
      expect(methodApi.deleteExamMethod).toHaveBeenCalledWith(3)
    })
    await screen.findByText('该方法仍被 2 个检查项目使用，请先调整项目后再删除')
  })
})

describe('exam method page - perm gating', () => {
  it('hides the 新建 affordance without setting:method:create', async () => {
    useAuthStore.getState().setSession({ perms: ['setting:method:list'] })
    renderPage()
    await screen.findByText('平扫')

    expect(screen.queryByRole('button', { name: /新建方法/ })).toBeNull()
  })

  it('hides the 删除 affordance without setting:method:delete', async () => {
    useAuthStore.getState().setSession({
      perms: ['setting:method:list', 'setting:method:create', 'setting:method:update'],
    })
    renderPage()
    await screen.findByText('平扫')

    expect(screen.queryByRole('button', { name: /删除/ })).toBeNull()
  })
})
