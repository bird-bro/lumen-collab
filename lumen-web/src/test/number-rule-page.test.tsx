import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NumberRulePage from '@/pages/system/number-rule'
import * as numberRulesApi from '@/api/number-rules'
import type { NumberRule } from '@/api/number-rules'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'
import { dictItemsById } from '@/utils/dict-items'

/**
 * 号码规则 page (patient-domain task 5.1; spec delta `web-system-console`
 * § Number Rule Page Shell / § Number Rule Form and Delete): the paged rule
 * table against the REAL backend seam, the `system:number:*` affordance
 * gating, the drawer create/edit with the fixed 影像号 type echo, the field
 * -level serial-length validation, and the confirm-delete with the active
 * -rule refusal surfaced verbatim. The api seam is mocked - no page data ever
 * flows through a mock at runtime (ruling 2026-11-05).
 */
vi.mock('@/api/number-rules')
vi.mock('@/utils/dict-items', () => ({
  NUMBER_TYPE_DICT_ID: 1028,
  NUMBER_STAMP_FORMAT_DICT_ID: 1029,
  dictItemsById: vi.fn(),
}))

function rule(partial: Partial<NumberRule> & { id: number }): NumberRule {
  return {
    ruleType: '影像号',
    prefix: null,
    suffix: null,
    serialLength: 4,
    serialCur: 7,
    isStamp: 1,
    stamp: 'yyMMdd',
    active: 1,
    remark: null,
    createTime: '2026-11-17 15:24:00',
    ...partial,
  }
}

const ACTIVE_RULE = rule({ id: 130, prefix: 'IM', serialCur: 7 })
const DORMANT_RULE = rule({ id: 131, ruleType: '登记号', prefix: null, serialCur: 261, isStamp: 0, stamp: null, active: 0 })

function pageFixture(records: NumberRule[]): numberRulesApi.NumberRulePage {
  return { records, total: records.length, size: 20, current: 1, pages: 1 }
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <NumberRulePage />
    </ConfigProvider>,
  )
}

async function selectStampFormat(format: string): Promise<void> {
  const user = userEvent.setup()
  await user.click(await screen.findByLabelText('格式'))
  const option = await waitFor(() => {
    const match = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
      (element) => element.getAttribute('title') === format,
    )
    expect(match).toBeTruthy()
    return match as HTMLElement
  })
  await user.click(option)
}

/** Picks a 规则类型 option by its visible text (the disabled family's title attr
 * carries the refusal hint instead of the label - match on textContent). The
 * plain fireEvent click is deliberate: the non-search Select's option responds
 * to onClick, and the userEvent pointer sequence races the dropdown's re-render. */
async function selectRuleType(label: string): Promise<void> {
  const option = await waitFor(() => {
    const match = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
      (element) => element.textContent === label,
    )
    expect(match).toBeTruthy()
    return match as HTMLElement
  })
  fireEvent.click(option)
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.getState().setSession({
    perms: ['system:number:list', 'system:number:create', 'system:number:update', 'system:number:delete'],
  })
  vi.mocked(numberRulesApi.pageNumberRules).mockResolvedValue(pageFixture([ACTIVE_RULE, DORMANT_RULE]))
  vi.mocked(numberRulesApi.createNumberRule).mockResolvedValue(132)
  vi.mocked(numberRulesApi.updateNumberRule).mockResolvedValue(true)
  vi.mocked(numberRulesApi.deleteNumberRule).mockResolvedValue(true)
  // the dict seam dispatches by catalog ID - 编号类型 1028 tokens (rule_type) vs
  // 编号格式 1029 (stamp patterns), each independently degradable
  vi.mocked(dictItemsById).mockImplementation(async (catalId: number) => {
    if (catalId === 1028) {
      return [
        { code: 1, value: '影像号' },
        { code: 2, value: '登记号' },
        { code: 3, value: '预约号' },
      ]
    }
    return [
      { code: 1, value: 'yyMMdd' },
      { code: 2, value: 'yyyyMMdd' },
      { code: 3, value: 'yyyyMMddHHmmss' },
    ]
  })
})

describe('number rule page - table', () => {
  it('pages the real list endpoint on mount and renders the active rule', async () => {
    renderPage()
    await screen.findByText('IM')

    expect(numberRulesApi.pageNumberRules).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 20 }),
    )
    // 当前流水 renders zero-padded to the serial length (0007, not 7); both rows
    // carry the 影像号 tag - assert it within each row, not globally
    const row = screen.getByText('IM').closest('tr') as HTMLElement
    expect(within(row).getByText('影像号')).toBeTruthy()
    expect(within(row).getByText('0007')).toBeTruthy()
    // the status column rides the orgs-style switch (✓/✕ children, not text)
    expect(within(row).getByRole('switch', { name: '启用 影像号' })).toHaveAttribute('aria-checked', 'true')
    // the 示例 column composes prefix + the CURRENT date in the rule's pattern
    // (Java yyMMdd → dayjs YYMMDD) + a fresh zero-padded serial
    expect(within(row).getByText(/IM\d{6}0001/)).toBeTruthy()
    // the dormant sibling renders its state honestly
    const dormant = screen.getByText('0261').closest('tr') as HTMLElement
    expect(within(dormant).getByRole('switch', { name: '启用 登记号' })).toHaveAttribute('aria-checked', 'false')
  })

  it('shows the 格式 column as the format when stamped and 不带 otherwise', async () => {
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
    vi.mocked(numberRulesApi.pageNumberRules).mockClear()
    await user.click(refresh)
    await waitFor(() => {
      expect(numberRulesApi.pageNumberRules).toHaveBeenCalled()
    })
  })
})

describe('number rule page - create/edit round-trips', () => {
  it('creates a stamped rule with the default 登记号 family - the active 影像号 is disabled', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('button', { name: /新建规则/ }))
    await screen.findByLabelText('规则类型')
    // the picker defaults to the FIRST family without a live rule (登记号);
    // 影像号 already holds the active rule so its option renders disabled
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('登记号')).toBeTruthy()
    await user.click(screen.getByLabelText('规则类型'))
    const disabledOption = await waitFor(() => {
      const match = Array.from(document.querySelectorAll('.ant-select-item-option')).find(
        (element) => element.textContent === '影像号',
      )
      expect(match).toBeTruthy()
      return match as HTMLElement
    })
    expect(disabledOption.className).toContain('ant-select-item-option-disabled')
    await user.click(screen.getByLabelText('前缀'))

    await user.type(screen.getByLabelText('前缀'), 'RG')
    // the drawer opens with the serial length defaulted to 4 - clear before
    // typing or the keystrokes append (44 fails the range rule)
    const serialInput = screen.getByLabelText('编号长度')
    await user.clear(serialInput)
    await user.type(serialInput, '4')

    await selectStampFormat('yyyyMMdd')

    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(numberRulesApi.createNumberRule).toHaveBeenCalledWith(
        expect.objectContaining({
          ruleType: '登记号',
          prefix: 'RG',
          serialLength: 4,
          isStamp: 1,
          stamp: 'yyyyMMdd',
        }),
      )
    })
    // the table refreshes after the drawer closes
    expect(numberRulesApi.pageNumberRules).toHaveBeenCalledTimes(2)
  })

  it('edits echo the row into the drawer and PUT the full surface', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getAllByRole('button', { name: /编辑/ })[0])
    const echo = await screen.findByText('规则类型：')
    expect(within(echo.parentElement as HTMLElement).getByText('影像号')).toBeTruthy()
    const prefixInput = await screen.findByLabelText('前缀')
    expect((prefixInput as HTMLInputElement).value).toBe('IM')

    await user.clear(prefixInput)
    await user.type(prefixInput, 'PT')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(numberRulesApi.updateNumberRule).toHaveBeenCalledWith(
        130,
        expect.objectContaining({ ruleType: '影像号', prefix: 'PT', serialLength: 4 }),
      )
    })
  })

  it('create drawer selects an available family - 预约号 rides the payload', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('button', { name: /新建规则/ }))
    await user.click(await screen.findByLabelText('规则类型'))
    // TEMP PROBE
    const opts = Array.from(document.querySelectorAll('.ant-select-item-option'))
    console.log('PROBE-OPTS:', JSON.stringify(opts.map((o) => ({ t: o.textContent, title: o.getAttribute('title'), cls: o.className.slice(0, 70) }))))
    await selectRuleType('预约号')
    // END TEMP PROBE

    await user.type(await screen.findByLabelText('前缀'), 'AC')
    const serialInput = screen.getByLabelText('编号长度')
    await user.clear(serialInput)
    await user.type(serialInput, '6')
    await selectStampFormat('yyMMdd')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(numberRulesApi.createNumberRule).toHaveBeenCalledWith(
        expect.objectContaining({
          ruleType: '预约号',
          prefix: 'AC',
          serialLength: 6,
          stamp: 'yyMMdd',
        }),
      )
    })
  })

  it('surfaces the out-of-range serial length on the field and keeps the drawer open', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('button', { name: /新建规则/ }))
    await user.type(await screen.findByLabelText('编号长度'), '11')
    await user.click(screen.getByRole('button', { name: /保 存|保存/ }))

    await screen.findByText('编号长度须在 1–10 之间')
    expect(numberRulesApi.createNumberRule).not.toHaveBeenCalled()
    // the drawer stays open for correction (spec: the drawer stays open)
    expect(screen.getByLabelText('编号长度')).toBeTruthy()
  })

  it('the 状态 switch rides the dedicated lifecycle endpoint (org-status pattern)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getByRole('switch', { name: '启用 影像号' }))
    await waitFor(() => {
      expect(numberRulesApi.setNumberRuleActive).toHaveBeenCalledWith(130, 0)
    })
    // the list reloads after the flip (the switch state rides the rows)
    await waitFor(() => {
      expect(numberRulesApi.pageNumberRules).toHaveBeenCalledTimes(2)
    })
  })

  it('the duplicate-active refusal surfaces verbatim on the flip', async () => {
    const user = userEvent.setup()
    vi.mocked(numberRulesApi.setNumberRuleActive).mockRejectedValue(
      new ApiError('A0714', '数据重复：号码规则冲突: rule_type=登记号 已存在启用规则（同类型仅允许一条启用规则）'),
    )
    renderPage()
    await screen.findByText('IM')

    // activating the dormant 登记号 row while its sibling is live
    await user.click(screen.getByRole('switch', { name: '启用 登记号' }))
    await screen.findByText(/同类型仅允许一条启用规则/)
  })
})

describe('number rule page - delete', () => {
  it('delete confirms, calls the API, and surfaces the active-rule refusal verbatim', async () => {
    const user = userEvent.setup()
    vi.mocked(numberRulesApi.deleteNumberRule).mockRejectedValue(
      new ApiError('A0400', '启用的规则不可删除，请先停用: rule id 130 (type 影像号)'),
    )
    renderPage()
    await screen.findByText('IM')

    await user.click(screen.getAllByRole('button', { name: /删除/ })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await waitFor(() => {
      expect(numberRulesApi.deleteNumberRule).toHaveBeenCalledWith(130)
    })
    await screen.findByText(/启用的规则不可删除，请先停用/)
  })
})

describe('number rule page - perm gating', () => {
  it('hides the 新建 affordance without system:number:create', async () => {
    useAuthStore.getState().setSession({ perms: ['system:number:list'] })
    renderPage()
    await screen.findByText('IM')

    expect(screen.queryByRole('button', { name: /新建规则/ })).toBeNull()
    // read-only ops column: no 编辑/删除 either
    expect(screen.queryByRole('button', { name: /编辑/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /删除/ })).toBeNull()
  })

  it('hides the 删除 affordance without system:number:delete', async () => {
    useAuthStore.getState().setSession({
      perms: ['system:number:list', 'system:number:create', 'system:number:update'],
    })
    renderPage()
    await screen.findByText('IM')

    expect(screen.queryByRole('button', { name: /删除/ })).toBeNull()
    expect(screen.getByRole('button', { name: /新建规则/ })).toBeTruthy()
  })
})
