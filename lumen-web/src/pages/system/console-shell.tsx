import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { Button, Pagination, Space, Table, Tooltip, Typography, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import type { FormInstance } from 'antd'
import type { ReactNode } from 'react'

/**
 * Shared console page chrome (batch 6a of web-foundation-auth-console, tasks
 * 6.1-6.3; brief § 页面公共规范 - the three pages must present one OPS pattern).
 *
 * Visual ground truth: the user-confirmed prototypes
 * `openspec/changes/web-foundation-auth-console/prototype/console-shell.html`
 * (users table page: `.main > .panel` > `.search` + `.tbl` + `.pager`) and
 * `prototype/org-deps.html` (dept page: `.work` = `.tree-panel` aside +
 * `.list-panel`). Every block below maps onto one of those selectors.
 *
 * Hex discipline (`specs/web-design-system` § OPS Palette Tokens): no color
 * literal appears in this file - every value reads the AntD token pipeline via
 * `theme.useToken()`, and zebra striping / header colors stay on the existing
 * Table component tokens in `src/theme/tokens.ts` (brief: 不动).
 */

/** Prototype `.tree-panel{flex:0 0 250px}` (org-deps.html). */
export const CONSOLE_ASIDE_WIDTH = 250

/** Prototype `.pager{height:60px}` (rev.6: pagination centered, 60px row). */
const PAGER_HEIGHT = 60

/**
 * OPS compact content card (prototype `.panel`): a bordered white card with a
 * hairline shadow and NO title block - brief 「无页头标题块;内容区直接
 * 工具行 → 表格 → 分页」.
 */
export function ConsolePanel({ children }: { children: ReactNode }) {
  const { token } = theme.useToken()
  return (
    <section
      style={{
        height: '100%',
        flex: 1,
        minWidth: 0,
        background: token.colorBgContainer,
        border: `1px solid ${token.colorSplit}`,
        borderRadius: token.borderRadiusLG,
        boxShadow: '0 1px 2px rgba(15, 53, 106, 0.04)',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {children}
    </section>
  )
}

/**
 * Two-zone work area (org-deps.html `.work{display:flex;gap:16px}`): the fixed
 * 250px org-tree aside + the flexible right panel.
 */
export function ConsoleWorkArea({ aside, children }: { aside: ReactNode; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'stretch', height: '100%' }}>
      <div style={{ flex: `0 0 ${CONSOLE_ASIDE_WIDTH}px`, width: CONSOLE_ASIDE_WIDTH }}>
        {aside}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  )
}

/**
 * Tool row (console-shell.html `.search`, org-deps.html `.tool-row`): query
 * controls LEFT, the primary add action RIGHT (`.push{margin-left:auto}`).
 */
export function ConsoleToolRow({ children, trailing }: { children: ReactNode; trailing?: ReactNode }) {
  const { token } = theme.useToken()
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
        padding: `${token.paddingSM}px ${token.paddingMD}px`,
        borderBottom: `1px solid ${token.colorSplit}`,
      }}
    >
      <Space wrap size={12} style={{ flex: 1, minWidth: 0 }}>
        {children}
      </Space>
      <Space wrap size={12}>
        {trailing}
      </Space>
    </div>
  )
}

/** Primary add button: PlusOutlined + text (the AntD equivalent of the prototype's CirclePlus + 文字). */
export function ConsoleAddButton({
  label,
  disabled,
  onClick,
}: {
  label: string
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <Button type="primary" icon={<PlusOutlined />} disabled={disabled} onClick={onClick}>
      {label}
    </Button>
  )
}

/** Query button carrying the search glyph (prototype `.btn` + magnifier). */
export function ConsoleQueryButton({
  label = '查询',
  disabled,
  onClick,
}: {
  label?: string
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <Button icon={<SearchOutlined />} disabled={disabled} onClick={onClick}>
      {label}
    </Button>
  )
}

/** The icon verbs the 6a pages use; each maps to one prototype `.act` glyph. */
const ACTION_ICONS = {
  edit: EditOutlined,
  remove: DeleteOutlined,
  /** 重置密码 - the prototype's key glyph (console-shell.html `.act title="重置密码"`). */
  key: KeyOutlined,
  /** 菜单授权 - the role-assignment tree glyph (role-menus.html `.act title="菜单授权"`). */
  assign: ApartmentOutlined,
} as const

/**
 * Icon-only operation button (prototype `.act` / `.act.danger`: the cell shows
 * the glyph alone, the Chinese verb lives in `title=`). The verb is handed to
 * Tooltip + aria-label so it stays announceable while no text renders - the
 * test asserts the button carries no text node of its own.
 */
export function ConsoleAction({
  label,
  icon = 'edit',
  danger,
  disabled,
  onClick,
}: {
  label: string
  icon?: keyof typeof ACTION_ICONS
  danger?: boolean
  disabled?: boolean
  onClick: () => void
}) {
  const Icon = ACTION_ICONS[icon]
  return (
    <Tooltip title={label}>
      <Button
        type="text"
        size="small"
        danger={danger}
        disabled={disabled}
        aria-label={label}
        icon={<Icon />}
        onClick={onClick}
      />
    </Tooltip>
  )
}

/** The operation column: a page composes its icon-only verbs for one row. */
export interface ConsoleActionColumn<RowType> {
  width?: number
  render: (row: RowType) => ReactNode
}

/**
 * Console table (prototype `.tbl`): the 操作 header carries the small refresh
 * glyph (`.th-act` + `.th-refresh`), its cells hold icon-only actions, and
 * omitting `actions` renders a purely read-only table - no 操作 header at all.
 * Pagination is NOT owned here: pages render `ConsolePagination` beneath.
 * Zebra/header colors come from the existing Table component tokens.
 */
export function ConsoleTable<RowType extends object>({
  rowKey,
  columns,
  dataSource,
  loading,
  actions,
  onRefresh,
  onRow,
}: {
  rowKey: (row: RowType) => number | string
  columns: TableColumnsType<RowType>
  dataSource: RowType[]
  loading?: boolean
  actions?: ConsoleActionColumn<RowType>
  onRefresh?: () => void
  onRow?: (row: RowType) => React.HTMLAttributes<HTMLTableRowElement>
}) {
  const { token } = theme.useToken()
  const actionColumns: TableColumnsType<RowType> = actions
    ? [
        {
          key: 'actions',
          width: actions.width ?? 120,
          align: 'center',
          title: (
            <span
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
              }}
            >
              操作
              {onRefresh ? (
                <Tooltip title="刷新列表">
                  <Button
                    type="text"
                    size="small"
                    aria-label="刷新列表"
                    icon={<ReloadOutlined style={{ color: token.colorPrimary }} />}
                    onClick={onRefresh}
                  />
                </Tooltip>
              ) : null}
            </span>
          ),
          render: (_, row) => (
            <Space size={4} key={`actions-${String(rowKey(row))}`}>
              {actions.render(row)}
            </Space>
          ),
        },
      ]
    : []

  return (
    <Table<RowType>
      rowKey={rowKey}
      columns={[...columns, ...actionColumns]}
      dataSource={dataSource}
      loading={loading}
      pagination={false}
      size="middle"
      onRow={onRow}
    />
  )
}

/**
 * Centered ~60px pager row (prototype `.pager{justify-content:center;height:60px}`
 * + the OPS `共 N 条` counter). Renders nothing when `total` is 0 - a page with
 * no rows has nothing to page through (the table's own empty state shows).
 */
export function ConsolePagination({
  total,
  current,
  pageSize,
  showSizeChanger = true,
  onChange,
  onShowSizeChange,
}: {
  total: number
  current: number
  pageSize: number
  showSizeChanger?: boolean
  onChange: (page: number, pageSize: number) => void
  onShowSizeChange?: (current: number, size: number) => void
}) {
  const { token } = theme.useToken()
  if (total === 0) return null
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        height: PAGER_HEIGHT,
        borderTop: `1px solid ${token.colorSplit}`,
        padding: `0 ${token.paddingMD}px`,
        marginTop: 'auto',
      }}
    >
      <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
        共 {total} 条
      </Typography.Text>
      <Pagination
        current={current}
        total={total}
        pageSize={pageSize}
        showSizeChanger={showSizeChanger}
        onChange={onChange}
        onShowSizeChange={onShowSizeChange}
      />
    </div>
  )
}

/**
 * Reads a dialog form, swallowing AntD's validation rejection: a rejected
 * `validateFields()` inside a Modal `onOk` would leave an unhandled promise
 * (AntD renders the field errors itself). `null` means "invalid - stay open,
 * do nothing". Shared by the 6a console dialogs (orgs / deps / users).
 */
export async function readForm<ValueT extends object>(
  form: FormInstance<ValueT>,
): Promise<ValueT | null> {
  try {
    return await form.validateFields()
  } catch {
    return null
  }
}
