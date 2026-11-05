import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrgsPage from '@/pages/system/orgs'
import * as orgsApi from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * 机构管理 page (batch 6a, task 6.1; spec `specs/web-system-console`
 * § Org Tree Management: renders `GET /system/orgs/tree` with the seeded
 * hospital root, creates a child org on any node, edits, deletes, and is
 * multi-root capable).
 *
 * Layout source: `prototype/org-deps.html` left `.tree-panel` (tree-hd /
 * t-node rows / tree-hint / the dashed `.ghost` 「新建根机构」 foot button) -
 * the org page uses that panel as its WHOLE body (brief 6.1: the org page IS
 * the tree side of the prototype), and the modal shape follows the prototype's
 * `.modal` block (上级机构 / 名称 fields, 取消 + 保存).
 *
 * The api seam is stubbed with `vi.mock` (design D9 - never the dev-server
 * mock), so every assertion below is on the documented client call plus the
 * tree re-query that must follow it.
 */
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

const HOSPITAL = orgNode({ id: 1, name: '示例医院' })

/** Successive tree payloads: after a create the new node must appear. */
function treeFixture(...rounds: OrgNode[][]) {
  let call = 0
  vi.mocked(orgsApi.orgTree).mockImplementation(() => {
    const round = rounds[Math.min(call, rounds.length - 1)]
    call += 1
    return Promise.resolve(round)
  })
}

function renderPage() {
  return render(
    <ConfigProvider theme={themeConfig}>
      <OrgsPage />
    </ConfigProvider>,
  )
}

async function waitForTree(name: string) {
  await waitFor(() => expect(screen.getByText(name)).toBeInTheDocument())
}

/**
 * jsdom starts no CSS animation, so a CLOSED AntD modal keeps lingering in the
 * DOM mid-leave and `queryByRole('dialog')` cannot tell "open" from "closed".
 * The library's own signal is used instead: rc-motion puts the `*-leave` class
 * on the dialog element the moment `open` flips to false. So
 * 「成功关闭」 = the dialog entered its leave motion AND the form was reset,
 * 「弹窗不关闭」 = no leave class and the typed value still stands.
 */
const DIALOG_LEAVING = 'ant-zoom-leave'

async function expectDialogClosed() {
  const dialog = screen.getByRole('dialog')
  await waitFor(() => expect(dialog).toHaveClass(DIALOG_LEAVING))
  expect(within(dialog).getByLabelText('机构名称')).toHaveValue('')
}

/** Opens a node's row action (the glyphs only mount while the row is hovered). */
async function hoverNode(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.hover(screen.getByText(name))
}

beforeEach(() => {
  vi.clearAllMocks()
  // The documented perms string of this page (seed menu id 7 in V2__seed.sql);
  // no new perms code is invented - the mutation surface rides on it (brief § 权限门控).
  useAuthStore.getState().setSession({ perms: ['system:org:list'] })
  vi.mocked(orgsApi.createOrg).mockResolvedValue(9)
  vi.mocked(orgsApi.updateOrg).mockResolvedValue(true)
  vi.mocked(orgsApi.deleteOrg).mockResolvedValue(true)
})

describe('org page - tree rendering (spec: Tree renders with seeded root)', () => {
  it('loads the org tree on mount and shows the seeded hospital root', async () => {
    treeFixture([HOSPITAL])
    renderPage()
    await waitForTree('示例医院')
    expect(orgsApi.orgTree).toHaveBeenCalledTimes(1)
  })

  it('renders every nested node and keeps the parent expanded', async () => {
    treeFixture([
      orgNode({ ...HOSPITAL, children: [orgNode({ id: 2, name: '华东分院', upId: 1, level: 2 })] }),
    ])
    renderPage()
    await waitForTree('示例医院')
    await waitForTree('华东分院')
  })
})

describe('org page - CRUD round-trip (spec: Org CRUD round-trip)', () => {
  it('creates a child org from a hovered node, then re-queries the tree', async () => {
    const user = userEvent.setup()
    treeFixture(
      [HOSPITAL],
      [orgNode({ ...HOSPITAL, children: [orgNode({ id: 9, name: '华东分院', upId: 1, level: 2 })] })],
    )
    renderPage()
    await waitForTree('示例医院')

    await hoverNode(user, '示例医院')
    await user.click(await screen.findByRole('button', { name: '新建下级机构 示例医院' }))

    const dialog = await screen.findByRole('dialog', { name: '新建机构' })
    // The parent context line (prototype modal's 上级机构 row) names the dialog's target.
    expect(within(dialog).getByText('上级机构:示例医院')).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText('机构名称'), '华东分院')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() =>
      expect(orgsApi.createOrg).toHaveBeenCalledWith(
        expect.objectContaining({ upId: 1, name: '华东分院' }),
      ),
    )
    // success closes the dialog and re-queries the tree
    await expectDialogClosed()
    await waitFor(() => expect(orgsApi.orgTree).toHaveBeenCalledTimes(2))
    await waitForTree('华东分院')
  })

  it('edits an org name through PUT /orgs/{id} and reflects it in the tree', async () => {
    const user = userEvent.setup()
    treeFixture([HOSPITAL], [orgNode({ ...HOSPITAL, name: '示例总院' })])
    renderPage()
    await waitForTree('示例医院')

    await hoverNode(user, '示例医院')
    await user.click(await screen.findByRole('button', { name: '编辑机构 示例医院' }))

    const dialog = await screen.findByRole('dialog', { name: '编辑机构' })
    const input = within(dialog).getByLabelText('机构名称')
    expect(input).toHaveValue('示例医院') // prefilled
    await user.clear(input)
    await user.type(input, '示例总院')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(orgsApi.updateOrg).toHaveBeenCalledWith(1, { name: '示例总院' }))
    await waitFor(() => expect(orgsApi.orgTree).toHaveBeenCalledTimes(2))
    await waitForTree('示例总院')
  })

  it('asks for confirmation before DELETE, and cancels without calling the endpoint', async () => {
    const user = userEvent.setup()
    treeFixture([HOSPITAL])
    renderPage()
    await waitForTree('示例医院')

    await hoverNode(user, '示例医院')
    await user.click(await screen.findByRole('button', { name: '删除机构 示例医院' }))

    // The Popconfirm body text (the trigger's tooltip carries the same verb, so
    // the question line is the assertion target).
    expect(await screen.findByText('确定删除「示例医院」吗?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '取 消' }))
    expect(orgsApi.deleteOrg).not.toHaveBeenCalled()

    await hoverNode(user, '示例医院')
    await user.click(screen.getByRole('button', { name: '删除机构 示例医院' }))
    await user.click(await screen.findByRole('button', { name: '删 除' }))
    await waitFor(() => expect(orgsApi.deleteOrg).toHaveBeenCalledWith(1))
    await waitFor(() => expect(orgsApi.orgTree).toHaveBeenCalledTimes(2))
  })
})

describe('org page - multi-root capability (spec: multi-root)', () => {
  it('the dashed 「新建根机构」 control creates a second root with upId 0 and both roots render', async () => {
    const user = userEvent.setup()
    treeFixture([HOSPITAL], [HOSPITAL, orgNode({ id: 10, name: '第二医院', upId: 0 })])
    renderPage()
    await waitForTree('示例医院')

    await user.click(screen.getByRole('button', { name: /新建根机构/ }))
    const dialog = await screen.findByRole('dialog', { name: '新建根机构' })
    await user.type(within(dialog).getByLabelText('机构名称'), '第二医院')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    await waitFor(() => expect(orgsApi.createOrg).toHaveBeenCalledWith({ upId: 0, name: '第二医院' }))
    await waitForTree('第二医院')
    expect(screen.getByText('示例医院')).toBeInTheDocument()
  })
})

describe('org page - A-class errors', () => {
  it('surfaces the backend msg on a failed create and keeps the dialog open', async () => {
    const user = userEvent.setup()
    treeFixture([HOSPITAL])
    vi.mocked(orgsApi.createOrg).mockRejectedValue(
      new ApiError('A0400', 'new parent org does not exist: org id 8'),
    )
    renderPage()
    await waitForTree('示例医院')

    await hoverNode(user, '示例医院')
    await user.click(await screen.findByRole('button', { name: '新建下级机构 示例医院' }))
    const dialog = await screen.findByRole('dialog', { name: '新建机构' })
    await user.type(within(dialog).getByLabelText('机构名称'), '不存在的下级')
    await user.click(within(dialog).getByRole('button', { name: '保 存' }))

    // msg verbatim in a floating message, dialog still open (brief: A 类错误 message.error)
    expect(await screen.findByText('new parent org does not exist: org id 8')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: '新建机构' })).toBeInTheDocument()
    // the typed name survives the failed submit (the dialog never reset itself)
    expect(within(screen.getByRole('dialog', { name: '新建机构' })).getByLabelText('机构名称')).toHaveValue('不存在的下级')
  })
})

describe('org page - perms gating', () => {
  it('hides every mutation control when the session lacks system:org:list, but still reads the tree', async () => {
    const user = userEvent.setup()
    useAuthStore.getState().setSession({ perms: ['system:user:list'] })
    treeFixture([HOSPITAL])
    renderPage()
    await waitForTree('示例医院')

    expect(screen.queryByRole('button', { name: /新建根机构/ })).not.toBeInTheDocument()
    await hoverNode(user, '示例医院')
    await waitFor(() => expect(screen.getByText('示例医院')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /新建下级机构/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /编辑机构/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除机构/ })).not.toBeInTheDocument()
    // the read path still fires
    expect(orgsApi.orgTree).toHaveBeenCalledTimes(1)
  })
})
