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
 * § Report Template Page). TWO typed drawers (2026-11-18 ruling - the mixed
 * 目录/模板 flag drawer is gone): the left 新建目录 / tree 新建子目录 /
 * 编辑目录 route to the DIRECTORY drawer (目录名称 only), and the right-pane
 * 添加模板 / right-table 编辑 route to the WIDE TEMPLATE DRAWER (目录 echo + 名称/影像学表现/
 * 影像学意见 under the selected directory). The DIRECTORY drawer carries a
 * 上级 select (无 = 顶层): the left 新建目录 button prefills the selected
 * directory (top-level when none fits), the tree 新建子目录 prebinds its
 * node, and editing can MOVE a directory - self + subtree drop from the
 * options (a parent-under-own-descendant move cycles both out of the server's
 * tree render). The left tree renders DIRECTORIES ONLY
 * (rep-temp-tree-dirs-only-web 2026-11-20): template leaves never render on
 * the left - a tree-side template edit would PUT content-blind (the tree node
 * carries no finding/impression and the backend update is full-replace), so
 * template edits/deletes live in the right table where the full row is
 * loaded. The
 * 公共/个人 Segmented threads scope `type` into EVERY call and reloads both
 * panes; the server keeps private rows owner-scoped. Structured-content delta
 * (2026-11-18): the template drawer is DUAL-FORM (类型 select - 简单模板/分节模板;
 * 2026-11-19 rename+restyle ex-模板形态 radio, fixed 960 width) with section
 * cards serializing content jsonb; edits backfill by the
 * row's content; conversions mapped/refused per D6; table summary per D7).
 * Scope-keyed perms delta (rep-temp-scope-perms 2026-11-19): the Segmented
 * 公共 option needs `setting:temp:public:list`, the default scope is lazy
 * (个人 without it), and every action consults the CURRENT scope's family -
 * the legacy fixtures grant BOTH families (dual-family users, behavior
 * unchanged); the trimmed-permission cases live in the last describe block.
 * Api seam mocked (ruling 2026-11-05).
 */
vi.mock('@/api/rep-temp')
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

const PUBLIC_TREE: RepTempNode[] = [
  {
    id: 31, name: '呼吸系统', flag: 0, upId: null, children: [
      { id: 32, name: '肺结节随访', flag: 1, upId: 31, children: [] },
    ],
  },
  {
    id: 33, name: '心血管系统', flag: 0, upId: null, children: [
      { id: 35, name: '心律失常', flag: 0, upId: 33, children: [] },
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
  content: null,
  createTime: '2026-11-12 10:00:00',
}

function pageFixture(records: RepTemp[], total = records.length): Page<RepTemp> {
  return { records, total, size: 20, current: 1, pages: 1 }
}

/** Structured row (delta 2026-11-18; v2 unions since rep-temp-normal-range): 2 sections /
 * 5 range measures, prose columns NULL (D1 互斥). */
const STRUCTURED_TEMP: RepTemp = {
  id: 36, type: 1, ownerUid: null, modality: 'CT', flag: 1, upId: 31,
  name: '心脏超声', finding: null, impression: null,
  content: {
    version: 2,
    sections: [
      {
        name: '心腔', finding: '左房增大。', measures: [
          { code: 'LA', label: '左房内径', unit: 'mm', normal: { kind: 'range', min: 19, max: 40 } },
          { code: 'AO', label: '主动脉根部内径', unit: 'mm', normal: { kind: 'range', min: 20, max: 37 } },
        ],
      },
      {
        name: '心功能', finding: null, measures: [
          { code: 'EF', label: '射血分数', unit: '%', normal: { kind: 'range', min: 50, max: 75 } },
          { code: 'FS', label: '短轴缩短率', unit: '%', normal: { kind: 'range', min: 25, max: 45 } },
          { code: 'SV', label: '每搏输出量', unit: 'ml', normal: { kind: 'range', min: 55, max: 90 } },
        ],
      },
    ],
    impression: '先随访后复查。',
  },
  createTime: '2026-11-18 09:00:00',
}

/** Override the paged rows under directory 31 (structured-row scenarios). */
function mockPageRows(rows: RepTemp[]) {
  vi.mocked(tempApi.pageRepTemps).mockImplementation(async (query: { upId: number }) =>
    query.upId === 31 ? pageFixture(rows) : pageFixture([]),
  )
}

/** The open template drawer's wrapper carries the FIXED inline width (960, 2026-11-19
 * ruling - the 类型 select never resizes it). */
function drawerWrapper(): HTMLElement {
  const wrapper = screen.getByRole('dialog').closest('.ant-drawer-content-wrapper') as HTMLElement
  expect(wrapper).toBeTruthy()
  return wrapper
}

/** The 类型 select's shown value (2026-11-19 restyle, ex-模板形态 radio): the antd
 * selection item inside the dialog. The dropdown options portal beside the
 * trigger and hide on close, so text queries would double-match; the template
 * drawer holds exactly ONE Select. */
function shownTemplateType(): string | null {
  return screen.getByRole('dialog').querySelector('.ant-select-selection-item')?.textContent ?? null
}

/** Switch the template drawer's 类型 select: open the named combobox, then click
 * the matching option row (orgs-page idiom - `.at(-1)` because antd keeps the
 * previous dropdown in the DOM while it animates closed). */
async function pickTemplateType(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(within(screen.getByRole('dialog')).getByRole('combobox', { name: '类型' }))
  const option = await waitFor(() => {
    const els = Array.from(document.querySelectorAll(`.ant-select-item-option[title="${label}"]`))
    if (els.length === 0) throw new Error(`template type option ${label} not found`)
    return els.at(-1) as HTMLElement
  })
  await user.click(option)
}

/** Switch a measure row's 正常范围类型 select (kind dropdown, user ruling
 * 2026-11-20 ex-Segmented): open the row's named combobox, then click the
 * matching option row (`.at(-1)` because antd keeps the previous dropdown in
 * the DOM while it animates closed; option titles 区间/文本 are unique - the
 * drawer's 类型 select offers 简单模板/分节模板). */
async function pickNormalKind(user: ReturnType<typeof userEvent.setup>, scope: HTMLElement, label: string) {
  await user.click(within(scope).getByRole('combobox', { name: '正常范围类型' }))
  const option = await waitFor(() => {
    const els = Array.from(document.querySelectorAll(`.ant-select-item-option[title="${label}"]`))
    if (els.length === 0) throw new Error(`normal kind option ${label} not found`)
    return els.at(-1) as HTMLElement
  })
  await user.click(option)
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
  vi.mocked(dictUtils.dictItemsById).mockImplementation(async (catalId: number) => {
    if (catalId === dictUtils.MODALITY_DICT_ID) return MODALITY_DICT
    return []
  })
  useAuthStore.getState().setSession({
    // Dual-family user (rep-temp-scope-perms 2026-11-19): the legacy cases run
    // with BOTH families granted - the base four strings plus the four public
    // variants - so the scope-keyed actions and the lazy 公共 default behave
    // exactly as before the ruling (enrichment, not weakening).
    perms: [
      'setting:temp:list', 'setting:temp:create', 'setting:temp:update', 'setting:temp:delete',
      'setting:temp:public:list', 'setting:temp:public:create', 'setting:temp:public:update', 'setting:temp:public:delete',
    ],
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

  it('switching to the 个人 tab reloads BOTH panes with type=2', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺结节随访')
    vi.mocked(tempApi.repTempTree).mockClear()
    vi.mocked(tempApi.pageRepTemps).mockClear()

    // 2026-11-18 ruling: the 公共/个人 scope switch is a Segmented control
    // (radio options, exam-body aside alignment) - no button pair anymore.
    // Segmented's radio input is pointer-events:none - the click lands on the
    // wrapping label (label activation drives the change event).
    expect(screen.queryByRole('button', { name: '个人' })).toBeNull()
    await user.click(screen.getByRole('radio', { name: '个人' }).closest('label') as HTMLElement)

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

describe('report template page - directory drawer', () => {
  it('新建目录 opens with the 上级 select prefilled to the selected directory', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /新建目录$/ }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('新建目录')).toBeTruthy()
    // The mount auto-selects the first node (呼吸系统, a directory) - the
    // button lands the new directory under it; clearing the select (无) goes
    // top-level. No 节点类型 radio, no content fields.
    expect(within(drawer).getByText('呼吸系统')).toBeTruthy()
    expect(within(drawer).getByLabelText('目录名称')).toBeTruthy()
    expect(within(drawer).queryByLabelText('影像学表现')).toBeNull()
    expect(within(drawer).queryByLabelText('影像学意见')).toBeNull()
    expect(within(drawer).queryByRole('radio')).toBeNull()
  })

  it('saving a directory under the selected parent POSTs flag=0 with its id', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /新建目录$/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('目录名称'), '头颈部')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 1, modality: 'CT', flag: 0, upId: 31, name: '头颈部',
          finding: null, impression: null,
        }),
      )
    })
  })

  it('an empty tree opens the drawer at 顶层 and POSTs upId=null', async () => {
    const user = userEvent.setup()
    vi.mocked(tempApi.repTempTree).mockResolvedValue([])
    renderPage()
    await screen.findByText('暂无节点')

    await user.click(screen.getByRole('button', { name: /新建目录$/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('目录名称'), '胸部分区')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ flag: 0, upId: null, name: '胸部分区' }),
      )
    })
  })

  it('tree 新建子目录 prebinds the parent select', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: /新建子目录/ })[0])
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('呼吸系统')).toBeTruthy()
    await user.type(within(drawer).getByLabelText('目录名称'), '纵隔分区')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ flag: 0, upId: 31, name: '纵隔分区' }),
      )
    })
  })

  it('editing a directory can move it under another directory', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getAllByRole('button', { name: '编辑目录' })[0])
    const drawer = screen.getByRole('dialog')
    expect((within(drawer).getByLabelText('目录名称') as HTMLInputElement).value).toBe('呼吸系统')
    // Root node - the select sits empty; pick the sibling root as the new parent.
    await user.click(await screen.findByLabelText('上级'))
    await user.click(
      within(document.querySelector('.ant-select-dropdown') as HTMLElement).getByText('心血管系统'),
    )
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.updateRepTemp).toHaveBeenCalledWith(
        31,
        expect.objectContaining({ flag: 0, upId: 33, name: '呼吸系统' }),
      )
    })
  })

  it('the 上级 options exclude the edited node and its subtree (no cycles)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('心血管系统')

    // Editing 心血管系统 (id 33): 心律失常 (35) is its child - moving the
    // parent under its own subtree cycles both nodes out of the server's tree
    // render (every member keeps a resolvable parent, none becomes a root).
    await user.click(screen.getAllByRole('button', { name: '编辑目录' })[1])
    await user.click(await screen.findByLabelText('上级'))
    const dropdown = within(document.querySelector('.ant-select-dropdown') as HTMLElement)
    expect(dropdown.getByText('呼吸系统')).toBeTruthy()
    expect(dropdown.queryByText('心血管系统')).toBeNull()
    expect(dropdown.queryByText('心律失常')).toBeNull()
  })

  it('the left tree renders directories only - template leaves stay in the right table', async () => {
    renderPage()
    await screen.findByText('肺结节随访')

    // Dirs-only tree (2026-11-20 ruling): the template leaf (肺结节随访,
    // flag=1) never renders on the left; the three directory nodes keep their
    // full verb set. The template row remains in the right table under the
    // selected directory (its 删除/编辑 live there).
    const treeArea = document.querySelector('div[aria-busy]') as HTMLElement
    expect(within(treeArea).queryByText('肺结节随访')).toBeNull()
    // Directory nodes carry the folder glyph (2026-11-20 swap, ex-▰ text glyph).
    expect(treeArea.querySelector('.anticon-folder')).not.toBeNull()
    expect(screen.getAllByRole('button', { name: '新建子目录' })).toHaveLength(3)
    expect(screen.getAllByRole('button', { name: '编辑目录' })).toHaveLength(3)
    expect(screen.getAllByRole('button', { name: '删除节点' })).toHaveLength(3)
    expect(screen.getByText('肺结节随访')).toBeTruthy()
  })
})

describe('report template page - template drawer', () => {
  it('添加模板 opens the template drawer under the selected directory', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('添加模板')).toBeTruthy()
    expect(within(drawer).getByLabelText('名称')).toBeTruthy()
    expect(within(drawer).getByLabelText('影像学表现')).toBeTruthy()
    expect(within(drawer).getByLabelText('影像学意见')).toBeTruthy()
    expect(within(drawer).queryByLabelText('目录名称')).toBeNull()
    // Delta (structured content 2026-11-18): the template surface is DUAL-FORM -
    // the 类型 select (简单模板/分节模板; 2026-11-19 rename+restyle) replaces the
    // retired 节点类型 one and defaults to 简单模板 at the FIXED 960 width.
    expect(within(drawer).getByRole('combobox', { name: '类型' })).toBeTruthy()
    expect(shownTemplateType()).toBe('简单模板')
    expect(drawerWrapper().style.width).toBe('960px')
    expect(within(drawer).getByText('呼吸系统')).toBeTruthy()
  })

  it('saving a template POSTs flag=1 under the selected directory', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '胸腔积液')
    await user.type(within(drawer).getByLabelText('影像学表现'), '左侧胸腔积液')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 1, modality: 'CT', flag: 1, upId: 31,
          name: '胸腔积液', finding: '左侧胸腔积液',
        }),
      )
    })
  })

  it('right-table 编辑 prefills and PUTs the full template content', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺结节随访')

    // The tree's directory edit is 编辑目录 - the bare 编辑 belongs to the
    // right table's template row alone.
    await user.click(screen.getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    expect(within(drawer).getByText('编辑模板')).toBeTruthy()
    expect((within(drawer).getByLabelText('名称') as HTMLInputElement).value).toBe('肺结节随访')
    expect(
      (within(drawer).getByLabelText('影像学表现') as HTMLTextAreaElement).value,
    ).toBe('右肺上叶见磨玻璃结节影…')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.updateRepTemp).toHaveBeenCalledWith(
        32,
        expect.objectContaining({
          flag: 1, upId: 31,
          finding: '右肺上叶见磨玻璃结节影…',
          impression: '建议随访。',
        }),
      )
    })
  })

  it('添加模板 without a directory warns 请先选择目录 and opens nothing', async () => {
    const user = userEvent.setup()
    vi.mocked(tempApi.repTempTree).mockResolvedValue([])
    renderPage()
    await screen.findByText('暂无节点')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))

    await screen.findByText('请先选择目录')
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('report template page - tree node actions', () => {
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
    // Base-only user: without public:list the lazy default is 个人 - the tree
    // loads the private fixture; the 新建 affordances still key to the (missing)
    // base create perm.
    await screen.findByText('我的短语')

    expect(screen.queryByRole('button', { name: /新建子目录/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /新建目录$/ })).toBeNull()
  })
})

describe('report template page - structured form (dual mode)', () => {
  it('card title echoes the typed 节名 with the 节 N tag; empty shows 节名未填 (2026-11-20 prototype ruling)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await pickTemplateType(user, '分节模板')

    // Untyped card: the placeholder title 节名未填 + the positional 节 1 tag
    // (kept for verbal reference); the 节名 placeholder carries examples.
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    expect(within(card).getByText('节名未填')).toBeTruthy()
    expect(within(card).getByText('节 1')).toBeTruthy()
    expect(within(card).getByPlaceholderText('节名（如：心腔、肝脏）')).toBeTruthy()

    // Typing the name echoes into the title; the positional tag stays.
    await user.type(within(card).getByLabelText('节名'), '心腔')
    expect(within(card).getByText('心腔')).toBeTruthy()
    expect(within(card).getByText('节 1')).toBeTruthy()
    expect(within(card).queryByText('节名未填')).toBeNull()
  })

  it('分节模板 swaps prose fields for section editors at the fixed 960 width', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await pickTemplateType(user, '分节模板')

    expect(drawerWrapper().style.width).toBe('960px')
    expect(within(drawer).queryByLabelText('影像学表现')).toBeNull()
    expect(within(drawer).getByLabelText('节名')).toBeTruthy()
    expect(within(drawer).getByLabelText('节所见')).toBeTruthy()
    expect(within(drawer).getByRole('button', { name: '添加测量项' })).toBeTruthy()
    expect(within(drawer).getByRole('button', { name: '添加节' })).toBeTruthy()
    expect(within(drawer).getByRole('button', { name: '删除节' })).toBeTruthy()
    // The global 影像学意见 stays available in both modes.
    expect(within(drawer).getByLabelText('影像学意见')).toBeTruthy()
  })

  it('structured save validates 节名 and measure 项目/名称, naming the field', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')

    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await screen.findByText('节名不能为空')
    expect(tempApi.createRepTemp).not.toHaveBeenCalled()

    await user.type(within(drawer).getByLabelText('节名'), '心腔')
    await user.click(within(drawer).getByRole('button', { name: '添加测量项' }))
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await screen.findByText('测量项项目不能为空')
    expect(tempApi.createRepTemp).not.toHaveBeenCalled()

    await user.type(within(drawer).getByLabelText('项目'), 'LA')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await screen.findByText('测量项名称不能为空')
    expect(tempApi.createRepTemp).not.toHaveBeenCalled()

    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('名称'), '左房内径')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          flag: 1, name: '心脏超声',
          content: {
            version: 2,
            sections: [
              { name: '心腔', finding: null, measures: [{ code: 'LA', label: '左房内径', unit: null, normal: null }] },
            ],
            impression: null,
          },
        }),
      )
    })
  })

  it('structured save serializes sections/measures into content and nulls the prose columns', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    await user.type(within(card).getByLabelText('节所见'), '左房增大。')
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getByLabelText('项目'), 'LA')
    await user.type(within(card).getByLabelText('名称'), '左房内径')
    await user.type(within(card).getByLabelText('单位'), 'mm')
    await user.type(within(card).getByLabelText('正常范围最小值'), '19')
    await user.type(within(card).getByLabelText('正常范围最大值'), '40')
    await user.type(within(drawer).getByLabelText('影像学意见'), '先随访后复查。')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 1, modality: 'CT', flag: 1, upId: 31, name: '心脏超声',
          finding: null, impression: null,
          content: {
            version: 2,
            sections: [
              { name: '心腔', finding: '左房增大。', measures: [{ code: 'LA', label: '左房内径', unit: 'mm', normal: { kind: 'range', min: 19, max: 40 } }] },
            ],
            impression: '先随访后复查。',
          },
        }),
      )
    })
  })

  it('添加测量项/删除测量项/添加节/删除节 keep the serialized shape in sync', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    // Two measure rows, only the first filled; the empty second row is deleted.
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getAllByLabelText('项目')[0], 'LA')
    await user.type(within(card).getAllByLabelText('名称')[0], '左房内径')
    await user.click(within(card).getAllByRole('button', { name: '删除测量项' })[1])
    // A second section is added then removed - the cards renumber back to 节 1.
    await user.click(within(drawer).getByRole('button', { name: '添加节' }))
    expect(within(drawer).getByRole('group', { name: '节卡片 2' })).toBeTruthy()
    await user.click(
      within(within(drawer).getByRole('group', { name: '节卡片 2' })).getByRole('button', { name: '删除节' }),
    )
    expect(within(drawer).queryByRole('group', { name: '节卡片 2' })).toBeNull()

    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          content: {
            version: 2,
            sections: [
              { name: '心腔', finding: null, measures: [{ code: 'LA', label: '左房内径', unit: null, normal: null }] },
            ],
            impression: null,
          },
        }),
      )
    })
  })
})

describe('report template page - mode conversion (3.3)', () => {
  it('简单→分节 maps 影像学表现 into the first section 节所见;意见 stays global', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('影像学表现'), '左肺上叶磨玻璃结节')
    await user.type(within(drawer).getByLabelText('影像学意见'), '建议三个月随访')
    await pickTemplateType(user, '分节模板')

    // Delta: the first section's 节所见 carries the former 表现 text; the 意见
    // stays as the global conclusion. 节名 stays empty for the user to fill
    // (backend @NotBlank is the validation guard).
    expect((within(drawer).getByLabelText('节所见') as HTMLTextAreaElement).value).toBe('左肺上叶磨玻璃结节')
    expect((within(drawer).getByLabelText('影像学意见') as HTMLTextAreaElement).value).toBe('建议三个月随访')
    expect((within(drawer).getByLabelText('节名') as HTMLInputElement).value).toBe('')
  })

  it('分节→简单 is refused with a warning and stays in 分节模式', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await pickTemplateType(user, '分节模板')
    await user.type(within(drawer).getByLabelText('节名'), '心腔')
    await pickTemplateType(user, '简单模板')

    // Delta: switching back would drop sections - warn and keep 分节模式.
    await screen.findByText('切换为简单模板将丢失分节内容')
    expect(within(drawer).getByLabelText('节名')).toBeTruthy()
    expect(within(drawer).queryByLabelText('影像学表现')).toBeNull()
    expect(shownTemplateType()).toBe('分节模板')
  })
})

describe('report template page - structured edit backfill (3.4)', () => {
  it('editing a structured row opens 分节模式 with sections backfilled and PUTs content verbatim', async () => {
    const user = userEvent.setup()
    mockPageRows([STRUCTURED_TEMP])
    renderPage()
    await screen.findByText('心脏超声')

    await user.click(screen.getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    // content != null lands in 分节模式 at the fixed wide drawer - no prose 表现 field.
    expect(shownTemplateType()).toBe('分节模板')
    expect(drawerWrapper().style.width).toBe('960px')
    expect(within(drawer).queryByLabelText('影像学表现')).toBeNull()
    const card1 = within(drawer).getByRole('group', { name: '节卡片 1' })
    expect((within(card1).getByLabelText('节名') as HTMLInputElement).value).toBe('心腔')
    expect((within(card1).getByLabelText('节所见') as HTMLTextAreaElement).value).toBe('左房增大。')
    expect((within(card1).getAllByLabelText('项目')[0] as HTMLInputElement).value).toBe('LA')
    const card2 = within(drawer).getByRole('group', { name: '节卡片 2' })
    expect((within(card2).getByLabelText('节名') as HTMLInputElement).value).toBe('心功能')
    expect((within(drawer).getByLabelText('影像学意见') as HTMLTextAreaElement).value).toBe('先随访后复查。')

    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(tempApi.updateRepTemp).toHaveBeenCalledWith(
        36,
        expect.objectContaining({
          flag: 1, upId: 31, name: '心脏超声', finding: null, impression: null,
          content: STRUCTURED_TEMP.content,
        }),
      )
    })
  })

  it('editing a prose row stays 简单模式 and PUTs content null', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺结节随访')

    await user.click(screen.getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    expect(shownTemplateType()).toBe('简单模板')
    expect(within(drawer).getByLabelText('影像学表现')).toBeTruthy()
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.updateRepTemp).toHaveBeenCalledWith(
        32,
        expect.objectContaining({ finding: '右肺上叶见磨玻璃结节影…', impression: '建议随访。' }),
      )
      const arg = vi.mocked(tempApi.updateRepTemp).mock.calls.at(-1)?.[1]
      expect(arg?.content).toBeNull()
    })
  })
})

describe('report template page - normal range union editor (rep-temp-normal-range)', () => {
  /** v2 union fixture: a range row, a text row, and a null-normal row (version 2). */
  const UNION_TEMP: RepTemp = {
    id: 37, type: 1, ownerUid: null, modality: 'CT', flag: 1, upId: 31,
    name: '甲状腺超声', finding: null, impression: null,
    content: {
      version: 2,
      sections: [
        {
          name: '甲状腺', finding: null, measures: [
            { code: 'TG', label: '甲状腺左叶', unit: 'mm', normal: { kind: 'range', min: 3, max: 20 } },
            { code: 'NOD', label: '结节边界', unit: null, normal: { kind: 'text', text: '清晰' } },
            { code: 'FLW', label: '血流信号', unit: null, normal: null },
          ],
        },
      ],
      impression: null,
    },
  }

  it('区间 normal serializes the v2 range variant (two-sided and single-sided)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    // Row 0: two-sided decimal range.
    await user.type(within(card).getByLabelText('项目'), 'LA')
    await user.type(within(card).getByLabelText('名称'), '左房内径')
    await user.type(within(card).getByLabelText('正常范围最小值'), '19.5')
    await user.type(within(card).getByLabelText('正常范围最大值'), '40')
    // Row 1: min-only open interval (max left blank).
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getAllByLabelText('项目')[1], 'LV')
    await user.type(within(card).getAllByLabelText('名称')[1], '左室内径')
    await user.type(within(card).getAllByLabelText('正常范围最小值')[1], '3')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          content: expect.objectContaining({
            version: 2,
            sections: [expect.objectContaining({
              measures: [
                { code: 'LA', label: '左房内径', unit: null, normal: { kind: 'range', min: 19.5, max: 40 } },
                { code: 'LV', label: '左室内径', unit: null, normal: { kind: 'range', min: 3, max: null } },
              ],
            })],
          }),
        }),
      )
    })
  })

  it('文本 normal serializes the v2 text variant', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getByLabelText('项目'), 'MR')
    await user.type(within(card).getByLabelText('名称'), '二尖瓣反流')
    // Switch the row to 文本 and type the qualitative phrase.
    await pickNormalKind(user, card, '文本')
    await user.type(within(card).getByLabelText('正常范围'), '阴性')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({
          content: expect.objectContaining({
            version: 2,
            sections: [expect.objectContaining({
              measures: [
                { code: 'MR', label: '二尖瓣反流', unit: null, normal: { kind: 'text', text: '阴性' } },
              ],
            })],
          }),
        }),
      )
    })
  })

  it('empty normal serializes null (cleared 区间 stays null, never an empty object)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getByLabelText('项目'), 'PE')
    await user.type(within(card).getByLabelText('名称'), '心包积液')
    // Switch to 文本 and back to 区间 - the round-trip leaves both ends empty.
    await pickNormalKind(user, card, '文本')
    await pickNormalKind(user, card, '区间')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      const arg = vi.mocked(tempApi.createRepTemp).mock.calls.at(-1)?.[0]
      const measure = arg?.content?.sections[0]?.measures[0]
      expect(measure?.code).toBe('PE')
      expect(measure?.normal).toBeNull()
    })
  })

  it('inverted range is blocked client-side with a naming message and no request', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    await user.type(within(card).getByLabelText('节名'), '心腔')
    await user.click(within(card).getByRole('button', { name: '添加测量项' }))
    await user.type(within(card).getByLabelText('项目'), 'AO')
    await user.type(within(card).getByLabelText('名称'), '主动脉')
    await user.type(within(card).getByLabelText('正常范围最小值'), '40')
    await user.type(within(card).getByLabelText('正常范围最大值'), '19')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await screen.findByText('正常范围最小值不能大于最大值')
    expect(tempApi.createRepTemp).not.toHaveBeenCalled()
  })

  it('backfill reads the stored kind (text row / range row / null row)', async () => {
    const user = userEvent.setup()
    mockPageRows([UNION_TEMP])
    renderPage()
    await screen.findByText('甲状腺超声')

    await user.click(screen.getByRole('button', { name: '编辑' }))
    const drawer = screen.getByRole('dialog')
    const card = within(drawer).getByRole('group', { name: '节卡片 1' })
    // Row 0 (range): the stored ends backfilled.
    expect((within(card).getAllByLabelText('正常范围最小值')[0] as HTMLInputElement).value).toBe('3')
    expect((within(card).getAllByLabelText('正常范围最大值')[0] as HTMLInputElement).value).toBe('20')
    // Row 1 (text): the 文本-mode input EXISTS (only a text-kind row renders it)
    // with the stored phrase - the mode itself is the kind proof.
    expect((within(card).getByLabelText('正常范围') as HTMLInputElement).value).toBe('清晰')
    // Row 2 (null): 区间 default with empty ends (no third state).
    expect((within(card).getAllByLabelText('正常范围最小值')[1] as HTMLInputElement).value).toBe('')
    expect(within(card).getAllByLabelText('正常范围')).toHaveLength(1)
  })
})

describe('report template page - structured table summary (3.5)', () => {
  it('structured rows render the 「N 节 · M 项」 summary and content.impression; prose rows unchanged', async () => {
    mockPageRows([PUBLIC_TEMP, STRUCTURED_TEMP])
    renderPage()
    await screen.findByText('心脏超声')

    // D7: the structured row's 表现 cell collapses to the section/measure
    // summary and the 意见 cell reads content.impression.
    expect(screen.getByText('2 节 · 5 项')).toBeTruthy()
    expect(screen.getByText('先随访后复查。')).toBeTruthy()
    // The prose row's cells render the raw columns as before.
    expect(screen.getByText('右肺上叶见磨玻璃结节影…')).toBeTruthy()
    expect(screen.getByText('建议随访。')).toBeTruthy()
  })
})

describe('report template page - structured save chain (3.6)', () => {
  it('directory saves carry no content key (D8: directories are content-free)', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /新建目录$/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('目录名称'), '头颈部')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ flag: 0, name: '头颈部' }),
      )
      const arg = vi.mocked(tempApi.createRepTemp).mock.calls.at(-1)?.[0]
      expect(arg && 'content' in arg).toBe(false)
    })
  })

  it('structured save surfaces backend ApiError.msg verbatim and keeps the drawer open', async () => {
    const user = userEvent.setup()
    vi.mocked(tempApi.createRepTemp).mockRejectedValue(new ApiError('A0400', 'content.version 必须为 1'))
    renderPage()
    await screen.findByText('呼吸系统')

    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '心脏超声')
    await pickTemplateType(user, '分节模板')
    await user.type(within(drawer).getByLabelText('节名'), '心腔')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))

    await screen.findByText('content.version 必须为 1')
    expect(screen.getByRole('dialog')).toBeTruthy()
  })
})

/**
 * Shell ops chrome (rep-temp-ops-chrome-web 2026-11-20, the exam-method-page
 * "rides the shell ops chrome" idiom): the right table's 操作 column rides the
 * shared ConsoleTable chrome - the 操作 header carries the refresh glyph at its
 * right edge re-running the current page query, and the row verbs keep their
 * proper glyphs (删除 the delete glyph - ConsoleAction's default is the edit
 * glyph, so the verb must pass icon="remove" explicitly).
 */
describe('report template page - shell ops chrome (rep-temp-ops-chrome-web)', () => {
  it('rides the shell ops chrome: refresh in the 操作 header, delete glyph on 删除', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('肺结节随访')

    const refresh = screen.getByRole('button', { name: '刷新列表' })
    expect(within(refresh.closest('th') as HTMLElement).getByText('操作')).toBeTruthy()
    vi.mocked(tempApi.pageRepTemps).mockClear()
    await user.click(refresh)
    await waitFor(() => {
      expect(tempApi.pageRepTemps).toHaveBeenCalled()
    })

    const row = screen.getByText('右肺上叶见磨玻璃结节影…').closest('tr') as HTMLElement
    expect(
      within(row).getByRole('button', { name: '删除' }).querySelector('.anticon-delete'),
    ).not.toBeNull()
    expect(
      within(row).getByRole('button', { name: '编辑' }).querySelector('.anticon-edit'),
    ).not.toBeNull()
  })
})

/**
 * Scope-keyed perm families (rep-temp-scope-perms 2026-11-19): the Segmented
 * 公共 option renders only for `setting:temp:public:list` holders (default
 * scope flips to 个人 without it - no public tree/table request), and EVERY
 * action keys its hasPerm check on the CURRENT scope - 公共 consults the
 * `setting:temp:public:*` family, 个人 the base `setting:temp:*` family
 * (tempPerm helper, D4). These cases use TRIMMED permission sets (re-setSession
 * overrides); the 26 legacy cases above run as dual-family users.
 */
describe('report template page - scope-keyed perms (rep-temp-scope-perms)', () => {
  const BASE_FAMILY = ['setting:temp:list', 'setting:temp:create', 'setting:temp:update', 'setting:temp:delete']

  it('公共 option hidden without setting:temp:public:list - defaults to 个人 with no public requests', async () => {
    // Delta scenario: without the public list perm the page IS a personal
    // template library - Segmented shows 个人 alone, initial scope 个人.
    useAuthStore.getState().setSession({ perms: [...BASE_FAMILY] })
    renderPage()
    await screen.findByText('我的短语')

    expect(screen.queryByRole('radio', { name: '公共' })).toBeNull()
    expect(screen.getByRole('radio', { name: '个人' })).toBeTruthy()
    const treeCalls = vi.mocked(tempApi.repTempTree).mock.calls
    expect(treeCalls.length).toBeGreaterThan(0)
    expect(treeCalls.every((call) => call[1] !== 1)).toBe(true)
    const pageCalls = vi.mocked(tempApi.pageRepTemps).mock.calls
    expect(pageCalls.every((call) => call[0].type !== 1)).toBe(true)
  })

  it('公共 scope actions consult the public family - read-only without public create/update, base family restored on 个人', async () => {
    const user = userEvent.setup()
    // Base family full + public:list only: 公共 scope reads fine while every
    // write affordance keys to the (missing) public family and hides.
    useAuthStore.getState().setSession({ perms: [...BASE_FAMILY, 'setting:temp:public:list'] })
    renderPage()
    await screen.findByText('呼吸系统')

    // Table read unaffected (prose finding cell renders), but the write
    // affordances are all hidden in the 公共 scope.
    await screen.findByText('右肺上叶见磨玻璃结节影…')
    expect(screen.queryByRole('button', { name: /新建目录$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /新建子目录/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /添加模板/ })).toBeNull()
    expect(screen.queryByRole('button', { name: '编辑' })).toBeNull()

    // The same affordances reappear on the 个人 scope (base family held).
    await user.click(screen.getByRole('radio', { name: '个人' }).closest('label') as HTMLElement)
    await screen.findByRole('button', { name: '编辑' })
    expect(screen.getByRole('button', { name: /新建目录$/ })).toBeTruthy()
    expect(screen.getAllByRole('button', { name: /新建子目录/ }).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /添加模板/ })).toBeTruthy()
  })

  it('个人 scope actions keep the base family - base-only user behaves exactly as before', async () => {
    const user = userEvent.setup()
    useAuthStore.getState().setSession({ perms: [...BASE_FAMILY] })
    renderPage()
    await screen.findByText('我的短语')

    // All base-family affordances render in the 个人 scope...
    expect(screen.getByRole('button', { name: /新建目录$/ })).toBeTruthy()
    expect(screen.getAllByRole('button', { name: /新建子目录/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '编辑目录' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '删除节点' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /添加模板/ })).toBeTruthy()
    await screen.findByRole('button', { name: '编辑' })

    // ...and a write consults the base family: the directory POST carries type=2.
    await user.click(screen.getByRole('button', { name: /新建目录$/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('目录名称'), '私人目录')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ type: 2, flag: 0, name: '私人目录' }),
      )
    })
  })

  it('end-to-end: base-only user gets a single-option Segmented, full personal actions, zero type=1 calls', async () => {
    const user = userEvent.setup()
    useAuthStore.getState().setSession({ perms: [...BASE_FAMILY] })
    renderPage()
    await screen.findByText('我的短语')

    expect(screen.queryByRole('radio', { name: '公共' })).toBeNull()
    expect(screen.getByRole('radio', { name: '个人' })).toBeTruthy()

    // The full personal write path works under the base family.
    await screen.findByRole('button', { name: '编辑' })
    await user.click(screen.getByRole('button', { name: /添加模板/ }))
    const drawer = screen.getByRole('dialog')
    await user.type(within(drawer).getByLabelText('名称'), '个人模板')
    await user.type(within(drawer).getByLabelText('影像学表现'), '个人所见')
    await user.click(within(drawer).getByRole('button', { name: /保 存|保存/ }))
    await waitFor(() => {
      expect(tempApi.createRepTemp).toHaveBeenCalledWith(
        expect.objectContaining({ type: 2, flag: 1, name: '个人模板' }),
      )
    })

    // The whole session never issued a public (type=1) tree or page request.
    const treeTypes = vi.mocked(tempApi.repTempTree).mock.calls.map((call) => call[1])
    expect(treeTypes).not.toContain(1)
    const pageTypes = vi.mocked(tempApi.pageRepTemps).mock.calls.map((call) => call[0].type)
    expect(pageTypes).not.toContain(1)
  })
})
