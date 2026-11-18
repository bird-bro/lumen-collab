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
 * § Exam Item Page): the modality/group/keyword filter bar (the 常用项目 toggle is
 * RETIRED - exam-item-polish-web), the display-resolved table (server-rendered
 * body/method names - D2; the in-hospital code stacks under the 项目 name, the
 * 对照码 / 计费数量 headers, centered count/scores, NO 创建时间 column - the 2026-11-17
 * header polish) with the 备注 column, the drawer's 分组→部位 CASCADE
 * (group change reloads the body options and clears the stale body), the method
 * multi-select ID array, the 常用项目 option line (label + switch on one line,
 * between 检查方法 and the numeric bands), the banded numeric fields with real
 * labels (排序 rides the 4-up band), and 备注 closing the form. Api seam mocked
 * (ruling 2026-11-05).
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
  { id: 1, modality: 'CT', name: '胸部', sort: 1, enable: 1, remark: null },
  { id: 2, modality: 'CT', name: '头颅', sort: 2, enable: 1, remark: null },
]

const BODIES: BodyTreeNode[] = [
  { id: 9, modality: 'CT', groupId: 1, upId: null, name: '胸部', code: null, sort: 1, enable: 1, remark: null, children: [] },
  { id: 10, modality: 'CT', groupId: 1, upId: 9, name: '肺', code: null, sort: 2, enable: 1, remark: null, children: [] },
]

/** 头颅 group's own bodies (the cascade mock switches on groupId). */
const BRAIN_BODIES: BodyTreeNode[] = [
  { id: 20, modality: 'CT', groupId: 2, upId: null, name: '头颅', code: null, sort: 1, enable: 1, remark: null, children: [] },
  { id: 21, modality: 'CT', groupId: 2, upId: 20, name: '脑实质', code: null, sort: 2, enable: 1, remark: null, children: [] },
]

const METHODS = [
  { id: 3, modality: 'CT', name: '平扫', code: 'PS', sort: 1, enable: 1 },
  { id: 4, modality: 'CT', name: '增强', code: 'ZQ', sort: 2, enable: 1 },
]

function item(partial: Partial<ExamItem> & { id: number; exam: string }): ExamItem {
  return {
    modality: 'CT',
    code: null,
    externalCode: null,
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
  remark: '需空腹；检查前禁食 4 小时',
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
    // The 常用项目 filter is retired (exam-item-polish-web): no checkbox, no param.
    expect(screen.queryByText('常用项目')).toBeNull()
    const query = vi.mocked(itemApi.pageExamItems).mock.calls[0]?.[0]
    expect(query).not.toHaveProperty('isCommon')
    expect(screen.getAllByText('胸部').length).toBeGreaterThan(0)
    expect(screen.getByText('平扫')).toBeTruthy()
    expect(screen.getByText('XBCT-001')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
  })

  it('renders the 备注 column (ellipsis title; — for null remark)', async () => {
    const plain = item({ id: 22, exam: '头颅CT平扫', code: 'TCT-002', remark: null })
    vi.mocked(itemApi.pageExamItems).mockResolvedValue(
      pageFixture([CHEST_CT, plain], 2),
    )
    renderPage()
    await screen.findByText('胸部CT平扫')

    const remarked = screen.getByText('需空腹；检查前禁食 4 小时')
    expect(remarked.getAttribute('title')).toBe('需空腹；检查前禁食 4 小时')
    const plainRow = screen.getByText('头颅CT平扫').closest('tr')
    expect(plainRow).toBeTruthy()
    expect(within(plainRow!).getAllByText('—').length).toBeGreaterThan(0)
  })

  it('merges the in-hospital code into the 项目 cell and renders the 对照码 column', async () => {
    const mapped = item({ id: 23, exam: '对照CT', code: 'DZ-003', externalCode: 'HIS-CT-9' })
    vi.mocked(itemApi.pageExamItems).mockResolvedValue(
      pageFixture([CHEST_CT, mapped], 2),
    )
    renderPage()
    await screen.findByText('胸部CT平扫')

    // 2026-11-17 header polish: no standalone 项目编码 column - the code rides
    // the 项目 cell on one line, and the external column reads 对照码.
    expect(screen.queryByRole('columnheader', { name: '项目编码' })).toBeNull()
    expect(screen.getByRole('columnheader', { name: '对照码' })).toBeTruthy()

    const mappedRow = screen.getByText('HIS-CT-9').closest('tr') as HTMLElement
    const cells = Array.from(mappedRow.querySelectorAll('td')).map((c) => c.textContent)
    expect(cells[0]).toContain('对照CT')
    expect(cells[0]).toContain('DZ-003')
    expect(cells[1]).toBe('HIS-CT-9')

    // The cell STACKS two lines (2026-11-17 review: 分上下 - the deps 科室 /
    // roles 创建人 idiom): name on line 1, code in the tertiary font on line 2.
    const stack = screen.getByText('对照CT').closest('div') as HTMLElement
    expect(stack).toHaveStyle({ flexDirection: 'column' })

    // CHEST_CT maps to nothing: the 对照码 cell renders —, right after 项目.
    const plainRow = screen.getByText('胸部CT平扫').closest('tr') as HTMLElement
    const plainCells = Array.from(plainRow.querySelectorAll('td')).map((c) => c.textContent)
    expect(plainCells[1]).toBe('—')
  })

  it('renders the 2026-11-17 header polish: renamed headers, centered counts, no 创建时间', async () => {
    renderPage()
    await screen.findByText('胸部CT平扫')

    for (const header of ['项目', '对照码', '检查部位', '检查方法', '计费数量', '诊断得分', '审核得分', '备注']) {
      expect(screen.getByRole('columnheader', { name: header })).toBeTruthy()
    }
    for (const retired of ['项目名称', '项目编码', '对照编码', '诊断计费数量', '创建时间']) {
      expect(screen.queryByRole('columnheader', { name: retired })).toBeNull()
    }
    // The ruling's centered set: 计费数量 + the two scores.
    for (const header of ['计费数量', '诊断得分', '审核得分']) {
      expect(screen.getByRole('columnheader', { name: header })).toHaveStyle({
        textAlign: 'center',
      })
    }
    // 单价 / 折扣 were not named - they keep the money-column right alignment.
    expect(screen.getByRole('columnheader', { name: '单价（元）' })).toHaveStyle({
      textAlign: 'right',
    })
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

  it('lays out the drawer: 编码 required, 对照码 below 检查方法, 排序 own row, 常用项目 closes', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getByRole('button', { name: /新建项目/ }))
    const drawer = screen.getByRole('dialog')

    // 2026-11-17 drawer review: shortened labels, 编码 required, 对照码 below
    // 检查方法, 排序 on its own row, the numeric band narrows to 3-up, and
    // 常用项目 closes the form BELOW 备注.
    // The three Selects resolve via combobox roles (their Form.Item label AND
    // aria-label double-match getByLabelText); the plain fields via labelText.
    for (const label of ['编码', '名称', '对照码', '排序', '单价（元）', '折扣', '计费数量', '诊断得分', '审核得分', '备注', '常用项目']) {
      expect(within(drawer).getByLabelText(label)).toBeTruthy()
    }
    for (const label of ['部位分组', '检查部位', '检查方法']) {
      expect(within(drawer).getByRole('combobox', { name: label })).toBeTruthy()
    }
    for (const retired of ['项目编码', '项目名称', '对照编码', '诊断计费数量']) {
      expect(within(drawer).queryByLabelText(retired)).toBeNull()
    }
    // 名称 caps at 60 characters.
    expect(within(drawer).getByLabelText('名称').getAttribute('maxLength')).toBe('60')

    // 编码 is required: an empty save blocks with the field message.
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    expect(await screen.findByText('编码不能为空')).toBeTruthy()
    expect(itemApi.createExamItem).not.toHaveBeenCalled()

    // Order: 编码 → 名称; 检查方法 → 对照码 → 排序 → 单价; 备注 → 常用项目;
    // the flag's closing row spreads label LEFT / switch RIGHT.
    const field = (label: string) => within(drawer).getByLabelText(label).closest('.ant-form-item')
    const comboField = (label: string) =>
      within(drawer).getByRole('combobox', { name: label }).closest('.ant-form-item')
    const precedes = (a: Element | null, b: Element | null) =>
      a != null && b != null && (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
    expect(precedes(field('编码'), field('名称'))).toBe(true)
    expect(precedes(comboField('检查方法'), field('对照码'))).toBe(true)
    expect(precedes(field('对照码'), field('排序'))).toBe(true)
    expect(precedes(field('排序'), field('单价（元）'))).toBe(true)
    const flagRow = within(drawer).getByLabelText('常用项目').closest('[data-item-common-row]') as HTMLElement
    // Round 4: back BELOW 备注 (the round-3 spot above read worse) - the flag
    // closes the form.
    expect(precedes(field('备注'), flagRow)).toBe(true)
    expect(flagRow).toHaveStyle({ justifyContent: 'space-between' })
  })

  it('creates an item with the method ID ARRAY and numeric fields', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getByRole('button', { name: /新建项目/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('编码'), 'TCT-001')
    await user.type(within(drawer).getByLabelText('名称'), '头颅MRI平扫')
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
    const nameInput = within(drawer).getByLabelText('名称')
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

  it('deletes with the Popconfirm and refreshes', async () => {    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getAllByRole('button', { name: '删除' })[0])
    const confirm = await screen.findByRole('tooltip')
    await user.click(within(confirm).getByRole('button', { name: /确\s*定|OK/ }))

    await waitFor(() => {
      expect(itemApi.deleteExamItem).toHaveBeenCalledWith(21)
    })
  })

  it('external mapping code round-trips: 对照码 sits below 检查方法, payload carries it', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getByRole('button', { name: /新建项目/ }))
    const drawer = screen.getByRole('dialog')
    const codeField = within(drawer).getByLabelText('编码').closest('.ant-form-item')
    const externalField = within(drawer).getByLabelText('对照码').closest('.ant-form-item')
    const examField = within(drawer).getByLabelText('名称').closest('.ant-form-item')
    const methodField = within(drawer)
      .getByRole('combobox', { name: '检查方法' })
      .closest('.ant-form-item')
    expect(externalField).toBeTruthy()
    // 编码 → 名称 lead the form; 对照码 reads BELOW 检查方法 (2026-11-17 drawer
    // review - no longer paired with the in-hospital code).
    expect(
      codeField!.compareDocumentPosition(examField!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    expect(
      methodField!.compareDocumentPosition(externalField!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()

    await user.type(within(drawer).getByLabelText('编码'), 'TCT-009')
    await user.type(within(drawer).getByLabelText('名称'), '头颅MRI平扫')
    await user.type(within(drawer).getByLabelText('对照码'), 'HIS-MRI-1')
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
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(itemApi.createExamItem).toHaveBeenCalledWith(
        expect.objectContaining({ exam: '头颅MRI平扫', externalCode: 'HIS-MRI-1' }),
      )
    })
  })

  it('edit echo fills the external mapping code and the update payload carries it', async () => {
    const user = userEvent.setup()
    vi.mocked(itemApi.getExamItem).mockResolvedValue({
      ...CHEST_CT,
      externalCode: 'HIS-CT-21',
    })
    renderPage()
    await screen.findByText('胸部CT平扫')

    await user.click(screen.getAllByRole('button', { name: '编辑' })[0])
    const drawer = await screen.findByRole('dialog')
    const externalInput = (await within(drawer).findByLabelText('对照码')) as HTMLInputElement
    await waitFor(() => expect(externalInput.value).toBe('HIS-CT-21'))

    await user.clear(externalInput)
    await user.type(externalInput, 'HIS-CT-21-V2')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(itemApi.updateExamItem).toHaveBeenCalledWith(
        21,
        expect.objectContaining({ externalCode: 'HIS-CT-21-V2' }),
      )
    })
  })
})

describe('exam item page - ops column chrome (2026-11-17)', () => {
  it('carries the refresh affordance in the 操作 header and re-runs the page query', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('胸部CT平扫')

    const refresh = screen.getByRole('button', { name: '刷新列表' })
    expect(within(refresh.closest('th') as HTMLElement).getByText('操作')).toBeTruthy()

    vi.mocked(itemApi.pageExamItems).mockClear()
    await user.click(refresh)
    await waitFor(() => {
      expect(itemApi.pageExamItems).toHaveBeenCalled()
    })
  })

  it('renders 编辑 with the edit glyph and 删除 with the delete glyph', async () => {
    renderPage()
    await screen.findByText('胸部CT平扫')

    const row = screen.getByText('胸部CT平扫').closest('tr') as HTMLElement
    expect(
      within(row).getByRole('button', { name: '编辑' }).querySelector('.anticon-edit'),
    ).not.toBeNull()
    // 删除 rode ConsoleAction's edit-glyph default (2026-11-17 review) - it
    // carries the delete glyph like every other console table.
    expect(
      within(row).getByRole('button', { name: '删除' }).querySelector('.anticon-delete'),
    ).not.toBeNull()
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
