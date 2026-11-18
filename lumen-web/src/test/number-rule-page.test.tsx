import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NumberRulePage from '@/pages/patient/number-rule'
import * as patientApi from '@/api/patient'
import type { PatientNumberRule } from '@/api/patient'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 号码规则 page (patient-domain task 5.1; spec delta `web-system-console`
 * § Number Rule Page Shell / § Number Rule Form and Delete): the paged rule
 * table against the REAL backend seam, the `patient:rule:*` affordance
 * gating, the drawer create/edit with the fixed 影像号 type echo, the field
 * -level serial-length validation, and the confirm-delete with the active
 * -rule refusal surfaced verbatim. The api seam is mocked - no page data ever
 * flows through a mock at runtime (ruling 2026-11-05).
 */
vi.mock('@/api/patient')

function rule(partial: Partial<PatientNumberRule> & { id: number }): PatientNumberRule {
  return {
    ruleType: 'pat_id',
    prefix: null,
    suffix: null,
    serialLength: 4,
    serialCur: 7,
    isStamp: 1,
    stamp: 'yyMMdd',
    active: 1,
    createTime: '2026-11-17 15:24:00',
    ...partial,
  }
}

const ACTIVE_RULE = rule({ id: 130, prefix: 'IM', serialCur: 7 })
const DORMANT_RULE = rule({ id: 131, prefix: null, serialCur: 261, isStamp: 0, stamp: null, active: 0 })

function pageFixture(records: PatientNumberRule[]): patientApi.PatientNumberRulePage {
  return { records, total: records.length, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <NumberRulePage />
    </ConfigProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({
    perms: ['patient:rule:list', 'patient:rule:create', 'patient:rule:update', 'patient:rule:delete'],
  })
  vi.mocked(patientApi.pageNumberRules).mockResolvedValue(pageFixture([ACTIVE_RULE, DORMANT_RULE]))
  vi.mocked(patientApi.createNumberRule).mockResolvedValue(132)
  vi.mocked(patientApi.updateNumberRule).mockResolvedValue(true)
  vi.mocked(patientApi.deleteNumberRule).mockResolvedValue(true)
})

describe('number rule page - table', () => {
  it('pages the real list endpoint on mount and renders the active rule', async () => {
    renderPage()
    await screen.findByText('IM')

    expect(patientApi.pageNumberRules).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 20 }),
    )
    // 当前流水 renders zero-padded to the serial length (0007, not 7); both rows
    // carry the 影像号 tag - assert it within each row, not globally
    const row = screen.getByText('IM').closest('tr') as HTMLElement
    expect(within(row).getByText('影像号')).toBeTruthy()
    expect(within(row).getByText('0007')).toBeTruthy()
    expect(within(row).getByText('启用')).toBeTruthy()
    // the dormant sibling renders its state honestly
    const dormant = screen.getByText('0261').closest('tr') as HTMLElement
    expect(within(dormant).getByText('停用')).toBeTruthy()
  })

  it('shows the 日期戳 column as the format when stamped and 不带 otherwise', async () => {
    renderPage()
    await screen.findByText('IM')

    const active = screen.getByText('IM').closest('tr') as HTMLElement
    expect(within(active).getByText('yyMMdd')).toBeTruthy()
    const dormant = screen.getByText('0261').closest('tr') as HTMLElement
    expect(within(dormant).getByText('不带')).toBeTruthy()
  })

  it('rides the shell ops chrome: refresh in the 操作 header re-pages', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    const refresh = screen.getByRole('button', { name: '刷新列表' })
    expect(within(refresh.closest('th') as HTMLElement).getByText('操作')).toBeTruthy()
    vi.mocked(patientApi.pageNumberRules).mockClear()
    await user.click(refresh)
    await waitFor(() => {
      expect(patientApi.pageNumberRules).toHaveBeenCalled()
    })
  })
})

describe('number rule page - create/edit round-trips', () => {
  it('creates a stamped rule through the drawer with the fixed 影像号 type', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('button', { name: /新建规则/ }))
    const echo = await screen.findByText('规则类型：')
    expect(within(echo.parentElement as HTMLElement).getByText('影像号')).toBeTruthy()

    await user.type(screen.getByLabelText('前缀'), 'IM')
    // the drawer opens with the serial length defaulted to 4 - clear before
    // typing or the keystrokes append (44 fails the range rule)
    const serialInput = screen.getByLabelText('流水长度')
    await user.clear(serialInput)
    await user.type(serialInput, '4')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(patientApi.createNumberRule).toHaveBeenCalledWith(
        expect.objectContaining({
          ruleType: 'pat_id',
          prefix: 'IM',
          serialLength: 4,
          isStamp: 1,
        }),
      )
    })
    // the table refreshes after the drawer closes
    expect(patientApi.pageNumberRules).toHaveBeenCalledTimes(2)
  })

  it('edits echo the row into the drawer and PUT the full surface', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getAllByRole('button', { name: /编辑/ })[0])
    const prefixInput = await screen.findByLabelText('前缀')
    expect((prefixInput as HTMLInputElement).value).toBe('IM')

    await user.clear(prefixInput)
    await user.type(prefixInput, 'PT')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(patientApi.updateNumberRule).toHaveBeenCalledWith(
        130,
        expect.objectContaining({ ruleType: 'pat_id', prefix: 'PT', serialLength: 4 }),
      )
    })
  })

  it('surfaces the out-of-range serial length on the field and keeps the drawer open', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('button', { name: /新建规则/ }))
    await user.type(await screen.findByLabelText('流水长度'), '11')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await screen.findByText('流水长度须在 1–10 之间')
    expect(patientApi.createNumberRule).not.toHaveBeenCalled()
    // the drawer stays open for correction (spec: the drawer stays open)
    expect(screen.getByLabelText('流水长度')).toBeTruthy()
  })
})

describe('number rule page - delete', () => {
  it('delete confirms, calls the API, and surfaces the active-rule refusal verbatim', async () => {
    const user = userEvent.setup()
    vi.mocked(patientApi.deleteNumberRule).mockRejectedValue(
      new ApiError('A0400', '启用的规则不可删除，请先停用: rule id 130 (type pat_id)'),
    )
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getAllByRole('button', { name: /删除/ })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await waitFor(() => {
      expect(patientApi.deleteNumberRule).toHaveBeenCalledWith(130)
    })
    await screen.findByText(/启用的规则不可删除，请先停用/)
  })
})

describe('number rule page - perm gating', () => {
  it('hides the 新建 affordance without patient:rule:create', async () => {
    useAuthStore.getState().setSession({ perms: ['patient:rule:list'] })
    renderPage()
    await screen.findByText('IM')

    expect(screen.queryByRole('button', { name: /新建规则/ })).toBeNull()
    // read-only ops column: no 编辑/删除 either
    expect(screen.queryByRole('button', { name: /编辑/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /删除/ })).toBeNull()
  })

  it('hides the 删除 affordance without patient:rule:delete', async () => {
    useAuthStore.getState().setSession({
      perms: ['patient:rule:list', 'patient:rule:create', 'patient:rule:update'],
    })
    renderPage()
    await screen.findByText('IM')

    expect(screen.queryByRole('button', { name: /删除/ })).toBeNull()
    expect(screen.getByRole('button', { name: /新建规则/ })).toBeTruthy()
  })
})
