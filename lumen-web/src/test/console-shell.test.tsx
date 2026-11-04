import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfigProvider } from 'antd'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import {
  ConsoleAction,
  ConsolePagination,
  ConsolePanel,
  ConsoleTable,
  ConsoleToolRow,
} from '@/pages/system/console-shell'
import { themeConfig } from '@/theme'

/**
 * Shared console chrome (batch 6a of web-foundation-auth-console, tasks 6.1-6.3;
 * brief § 页面公共规范 - the three pages must present one OPS pattern):
 *
 * - compact page: NO page-head title block - the panel holds tool row -> table
 *   -> pagination only (prototype `console-shell.html` / `org-deps.html`
 *   `.main > .panel`);
 * - tool row: query controls LEFT, the primary add button pushed to the right
 *   (prototype `.tool-row` + `.push { margin-left:auto }`);
 * - table: 操作 header is the only header carrying a small refresh icon
 *   (prototype `.th-act` + `.th-refresh`);
 * - pagination: centered ~60px row with the OPS `共 N 条` counter, hidden when
 *   there is nothing to page through (prototype `.pager`);
 * - action cells: icon-only buttons whose text lives in the tooltip (and is
 *   therefore the accessible name - `.act` + `title=`).
 *
 * Colors are never asserted here (hex discipline): the components read the
 * AntD token pipeline.
 */

interface Row {
  id: number
  name: string
}

const ROWS: Row[] = [
  { id: 1, name: '放射科' },
  { id: 2, name: '超声科' },
]

function renderWithTheme(ui: ReactNode) {
  return render(<ConfigProvider theme={themeConfig}>{ui}</ConfigProvider>)
}

describe('console page chrome (web-foundation-auth-console 6.1-6.3 shared layer)', () => {
  it('ConsolePanel renders its children as a single card with no title block', () => {
    const { container } = renderWithTheme(
      <ConsolePanel>
        <div>PANEL_CHILD</div>
      </ConsolePanel>,
    )
    expect(screen.getByText('PANEL_CHILD')).toBeInTheDocument()
    // The compact OPS page never grows a heading inside the panel.
    expect(container.querySelector('h1, h2, h3, h4, h5')).toBeNull()
  })

  it('ConsoleToolRow keeps query controls first and the add action last (left/right split)', () => {
    const { container } = renderWithTheme(
      <ConsoleToolRow>
        <input aria-label="科室编码 / 名称" />
        <button type="button">查询</button>
      </ConsoleToolRow>
    ) // no trailing action
    const row = container.firstElementChild as HTMLElement
    expect(row).toHaveStyle({ display: 'flex', 'justify-content': 'space-between' })
    expect(row.firstElementChild).toHaveTextContent('查询')

    const withTrailing = renderWithTheme(
      <ConsoleToolRow trailing={<button type="button">新建科室</button>}>
        <input aria-label="科室编码 / 名称" />
        <button type="button">查询</button>
      </ConsoleToolRow>,
    )
    expect(withTrailing.container.firstElementChild?.lastElementChild).toHaveTextContent('新建科室')
  })

  it('ConsoleTable renders the 操作 header with a refresh icon button that fires onRefresh', async () => {
    const onRefresh = vi.fn()
    renderWithTheme(
      <ConsoleTable<Row>
        rowKey={(row) => row.id}
        dataSource={ROWS}
        columns={[{ title: '科室名称', dataIndex: 'name' }]}
        actions={{
          render: (row) => [
            <ConsoleAction key="e" label="编辑" onClick={() => undefined} />,
            // proves `render` receives the row (row-scoped dialogs depend on it)
            <span key="n" aria-label={`ROW-${row.name}`} />,
          ],
        }}
        onRefresh={onRefresh}
      />,
    )
    expect(screen.getByText('放射科')).toBeInTheDocument()
    expect(screen.getByText('超声科')).toBeInTheDocument()
    expect(screen.getByLabelText('ROW-放射科')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /操作/ })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '刷新列表' }))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('ConsoleTable hides the 操作 column when the page passes no actions (read-only surface)', () => {
    renderWithTheme(
      <ConsoleTable<Row>
        rowKey={(row) => row.id}
        dataSource={ROWS}
        columns={[{ title: '科室名称', dataIndex: 'name' }]}
      />,
    )
    expect(screen.queryByText(/操作/)).not.toBeInTheDocument()
  })

  it('ConsoleAction renders icon-only: its label is the accessible name, never visible text', () => {
    const { container } = renderWithTheme(
      <ConsoleAction label="删除" danger onClick={() => undefined} />,
    )
    expect(screen.getByRole('button', { name: '删除' })).toBeInTheDocument()
    // Icon-only: the button carries no text node of its own.
    expect(container.querySelector('button')?.textContent).toBe('')
    expect(container.querySelector('.anticon')).toBeInTheDocument()
  })

  it('ConsolePagination shows the OPS total counter and is absent when there is nothing to page', () => {
    const { rerender } = renderWithTheme(
      <ConsolePagination total={25} current={1} pageSize={10} onChange={() => undefined} />,
    )
    expect(screen.getByText('共 25 条')).toBeInTheDocument()
    rerender(
      <ConfigProvider theme={themeConfig}>
        <ConsolePagination total={0} current={1} pageSize={10} onChange={() => undefined} />
      </ConfigProvider>,
    )
    expect(screen.queryByText(/共 0 条/)).not.toBeInTheDocument()
  })

  it('ConsolePagination drives onChange with the clicked page (client-side paging seam)', async () => {
    const onChange = vi.fn()
    renderWithTheme(
      <ConsolePagination total={25} current={1} pageSize={10} onChange={onChange} />,
    )
    await userEvent.click(screen.getByText('2'))
    expect(onChange).toHaveBeenCalledWith(2, 10)
  })
})
