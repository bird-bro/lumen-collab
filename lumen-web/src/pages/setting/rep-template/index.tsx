import { Button, Card, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Radio, Segmented, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { DeleteOutlined, DownOutlined, FolderOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import {
  createRepTemp,
  deleteRepTemp,
  pageRepTemps,
  repTempTree,
  updateRepTemp,
} from '@/api/rep-temp'
import { ApiError } from '@/api/client'
import type { Page, RepTemp, RepTempContent, RepTempMeasure, RepTempMeasureNormal, RepTempNode, RepTempSection } from '@/api/types'
import { dictItemsById, MODALITY_DICT_ID } from '@/utils/dict-items'
import { normalizeLines, renderTemplateText } from '@/utils/template-text'
import type { DictItem } from '@/api/types'
import { usePerm } from '@/utils/perm'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
  ConsolePanel,
  ConsolePagination,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
  ConsoleWorkArea,
} from '@/pages/system/console-shell'

/**
 * 报告模板 page (exam-setting-web task 6.1; spec delta `web-system-console`
 * § Report Template Page). ONE page with a 公共/个人 Segmented scope switch -
 * the scope `type` threads into EVERY tree/page/mutation call and switching
 * scope reloads both panes (the server keeps private rows owner-scoped, so the
 * page never renders another user's phrases). LEFT mirrors the exam-body aside
 * (2026-11-18): the 检查类型 select shares the ConsoleToolRow with 新建目录,
 * and the 公共/个人 scope switch is a block Segmented above the
 * folder/template tree - no button pair. TWO TYPED SURFACES (2026-11-18 ruling,
 * the mixed 目录/模板 flag drawer is gone): 新建目录 / tree 新建子目录 /
 * 编辑目录 open the DIRECTORY DRAWER (上级 select + 目录名称 only), and the
 * right-pane 添加模板 / right-table 编辑 open the WIDE TEMPLATE DRAWER (目录 echo +
 * 名称/影像学表现/影像意见 - long-form content gets a FIXED-WIDTH 960px drawer,
 * 2026-11-19 ruling - the width never follows the 类型 radio) bound to the selected
 * directory - 添加模板 warns
 * 请先选择目录 when no directory node is selected. DUAL-FORM templates
 * (structured-content delta 2026-11-18, D6): the drawer carries a 类型 radio (2026-11-20 follow-up, ex-Select)
 * (简单模板/分节模板; 2026-11-19 rename+restyle, ex-模板形态 radio) -
 * 简单模板 keeps the prose form, 分节模板 swaps in section cards
 * (节名 + measure rows + 所见) serializing into `content` jsonb at save (D1 互斥
 * nulls the prose columns); edits backfill by the row's content, 简单→分节 maps
 * 表现 → first section's 所见, 分节→简单 is refused. The left tree renders
 * DIRECTORIES only (2026-11-20 ruling, rep-temp-tree-dirs-only-web): template
 * leaves (flag=1) never render there - a tree-side template edit would PUT
 * content-blind (tree nodes carry no finding/impression and the backend update
 * is full-replace), so template edit/delete live in the right table where the
 * full row is loaded.
 * Scope-keyed perms (rep-temp-scope-perms 2026-11-19): the 公共 Segmented option
 * renders only for `setting:temp:public:list` holders - without it the page is
 * a personal library defaulting to 个人 (no public requests) - and EVERY action
 * consults the CURRENT scope's family via tempPerm (公共 →
 * `setting:temp:public:*`, 个人 → the base `setting:temp:*`). The right table's
 * 操作 column rides the SHARED console chrome (rep-temp-ops-chrome-web
 * 2026-11-20 - the page had hand-rolled a key:'ops' column, the last one in
 * the repo): verbs go through ConsoleTable's actions/onRefresh (refresh glyph
 * pinned to the 操作 header's right edge) and 删除 passes icon="remove" - the
 * ConsoleAction default is the edit glyph (third recurrence; session memory
 * console-table-ops-chrome-invariants).
 */

const TYPE_PUBLIC = 1
const TYPE_PRIVATE = 2

interface DirFormValues {
  upId?: number | null
  name: string
}

interface TplFormValues {
  name: string
  finding?: string
  impression?: string
}

interface SectionCardProps {
  index: number
  section: RepTempSection
  onPatch: (patch: Partial<RepTempSection>) => void
  onRemove: () => void
}

/**
 * The v2 normal union serialization (rep-temp-normal-range design D3): a range
 * with BOTH ends empty and a blank text degrade to `null` - the measure simply
 * has no range (never an empty union object reaches the wire).
 */
function serializeMeasureNormal(normal: RepTempMeasureNormal | null): RepTempMeasureNormal | null {
  if (!normal) return null
  if (normal.kind === 'range') {
    if (normal.min == null && normal.max == null) return null
    return { kind: 'range', min: normal.min ?? null, max: normal.max ?? null }
  }
  const text = normal.text?.trim()
  return text ? { kind: 'text', text } : null
}

/**
 * Dirs-only tree (user ruling 2026-11-20): the left panel shows DIRECTORIES
 * only - template leaves (flag=1) never render there (a leaf has no children;
 * template edit/delete live in the right table). Pruned recursively at the
 * load boundary so every downstream consumer (render, re-anchor, parent
 * options) sees directories only.
 */
function pruneToDirectories(nodes: RepTempNode[]): RepTempNode[] {
  return nodes
    .filter((node) => node.flag === 0)
    .map((node) => ({ ...node, children: pruneToDirectories(node.children) }))
}

/** Reference range for the preview's mini measure table (B 测值表式, 拍板点 2):
 * `min ~ max`, a blank end is the single-sided open interval, both ends empty
 * is a placeholder-free `—`. */
function formatRange(min: number | null, max: number | null): string {
  if (min == null && max == null) return '—'
  if (min == null) return `~ ${max}`
  if (max == null) return `${min} ~`
  return `${min} ~ ${max}`
}

/**
 * The preview's mini measure table (B 测值表式, 拍板点 2; 检验报告 two-up
 * layout, user rulings 2026-11-20): columns 项目 / 结果 / 参考范围 / 单位 —
 * the unit stays a SEPARATE column (complex English units like g/l cannot
 * fold into a header parenthesis) in that exact order, and the HEADERS render
 * CENTERED. 结果 stays always — on the template (the template carries the
 * reference, the report carries the actual). Measures lay out TWO groups per
 * row like a 检验报告 — the right half opens after a vertical rule (borderRight
 * on the first half's last column, a real table line), an odd tail leaves the
 * second half blank.
 */
function PreviewMeasureTable({
  section,
  token,
}: {
  section: RepTempSection
  token: ReturnType<typeof theme.useToken>['token']
}) {
  const valueOf = (measure: RepTempMeasure) =>
    measure.normal?.kind === 'range'
      ? formatRange(measure.normal.min, measure.normal.max)
      : measure.normal?.kind === 'text'
        ? measure.normal.text || '—'
        : '—'
  // Table lines (user rulings 2026-11-20: 表格线要明显 + keep ONLY the outer
  // frame, the row lines and the ONE center vertical rule — every other
  // vertical column line is suppressed): the table element draws the outer
  // frame, each cell carries a colorBorder row line, and the center rule is
  // the borderRight on the first half's last column (headers included).
  const th = {
    textAlign: 'center' as const,
    padding: '3px 8px',
    borderBottom: `1px solid ${token.colorBorder}`,
    color: token.colorTextSecondary,
    fontWeight: 500,
  }
  const td = { padding: '3px 8px', borderBottom: `1px solid ${token.colorBorder}` }
  const centerRule = { borderRight: `1px solid ${token.colorBorder}` }
  const pairs: RepTempMeasure[][] = []
  for (let i = 0; i < section.measures.length; i += 2) pairs.push(section.measures.slice(i, i + 2))
  return (
    <table
      style={{
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: 12,
        marginBottom: 4,
        border: `1px solid ${token.colorBorder}`,
      }}
    >
      <thead>
        <tr>
          {['项目', '结果', '参考范围', '单位', '项目', '结果', '参考范围', '单位'].map((header, i) => (
            <th key={i} style={{ ...th, ...(i === 3 ? centerRule : {}) }}>
              {header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {pairs.map((pair, pi) => (
          <tr key={pi}>
            {[0, 1].flatMap((slot) => {
              const measure = pair[slot]
              if (!measure) {
                return [
                  <td key={`${slot}-l`} style={td} />,
                  <td key={`${slot}-v`} style={td} />,
                  <td key={`${slot}-r`} style={td} />,
                  <td key={`${slot}-u`} style={{ ...td, ...centerRule }} />,
                ]
              }
              return [
                <td key={`${slot}-l`} style={td}>
                  {measure.label || '—'}
                </td>,
                <td key={`${slot}-v`} style={{ ...td, textAlign: 'center', color: token.colorTextTertiary }}>
                  —
                </td>,
                <td key={`${slot}-r`} style={{ ...td, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>
                  {valueOf(measure)}
                </td>,
                <td key={`${slot}-u`} style={{ ...td, ...centerRule, textAlign: 'center', color: token.colorTextSecondary }}>
                  {measure.unit?.trim() || '—'}
                </td>,
              ]
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * One paragraph per line with the 2em first-line indent — the print shape.
 * The text renders NORMALIZED (leading whitespace stripped per line: the
 * indent is this CSS, never the stored text); empty lines render as short
 * spacers. Placeholder tokens ride through `renderTemplateText` (amber).
 */
function PreviewParagraphs({ text, phStyle }: { text: string; phStyle: CSSProperties }) {
  return (
    <>
      {normalizeLines(text ?? '')
        .split('\n')
        .map((line, i) => (
          <p
            key={i}
            style={{ textIndent: '2em', margin: 0, minHeight: '1.6em', lineHeight: 1.9, fontSize: 13 }}
          >
            {line ? renderTemplateText(line, phStyle) : null}
          </p>
        ))}
    </>
  )
}

/**
 * One 分节 card of the structured template editor (dual-form delta 2026-11-18,
 * D6; ux-polish 2026-11-20). Module-level so the card's 折叠 state survives the
 * page's controlled re-renders - a component defined inside the page function
 * would remount per keystroke. Card = the 节名 edited IN PLACE inside the card
 * head (borderless input + the gray 节 N tag for verbal reference; the former
 * body row and the title-echo rendering are retired) + measure rows
 * (名称 label / 单位 unit / 正常范围 normal as the v2 UNION editor - per-row
 * Select 区间/文本, null renders 区间 with empty ends + row delete) + 所见
 * TextArea. The 项目 code input is RETIRED (rep-template-ux-polish: the
 * printed B-form never shows the code) — the state keeps `code` as a nullable
 * passthrough so legacy rows survive the full-replace PUT. Card verbs are
 * ICON-ONLY (user ruling 2026-11-20): 折叠/展开 (Up/Down chevrons), 删除节 and
 * 删除测量项 (trash), 添加测量项 (plus, dashed) — each keeps its aria-label so
 * the verbs stay announceable. 折叠 hides the measure rows and findings; all
 * state is pure frontend form state - saveTemplate serializes it into the
 * RepTempContent jsonb shape (节名/label required, validated with field-named
 * messages).
 */
function TemplateSectionCard({ index, section, onPatch, onRemove }: SectionCardProps) {
  const [collapsed, setCollapsed] = useState(false)
  const { token } = theme.useToken()
  const patchMeasure = (mi: number, patch: Partial<RepTempMeasure>) =>
    onPatch({ measures: section.measures.map((m, i) => (i === mi ? { ...m, ...patch } : m)) })
  return (
    <div role="group" aria-label={`节卡片 ${index + 1}`}>
      {/* The head row is HAND-ROLLED (ux-polish follow-up 2026-11-20): putting
          the editable 节名 inside antd's `.ant-card-head-title` slot breaks
          editing in real browsers — that slot is an inline-block shrink-to-fit
          container with overflow/nowrap ellipsis styles, where a percentage
          width input resolves unreliably (jsdom tests pass, the browser
          doesn't). A plain flex row with `minWidth: 0` on the input behaves
          like every other working input row in the app. Chevron (view
          control) at the disclosure position; trash (destructive) far right;
          the 节 N tag stays for verbal reference. */}
      <Card size="small" styles={{ body: { padding: '0 12px 12px' } }}>
        <div
          className="rep-sec-head"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            margin: '0 -12px',
            padding: '8px 12px',
            borderBottom: `1px solid ${token.colorSplit}`,
          }}
        >
          <Button
            type="text"
            size="small"
            aria-label={collapsed ? '展开' : '折叠'}
            icon={collapsed ? <DownOutlined /> : <UpOutlined />}
            onClick={() => setCollapsed((c) => !c)}
            style={{ color: token.colorTextSecondary }}
          />
          <Input
            aria-label="节名"
            placeholder="节名（如：心腔、肝脏）"
            variant="borderless"
            size="small"
            maxLength={64}
            value={section.name}
            onChange={(e) => onPatch({ name: e.target.value })}
            style={{ flex: 1, minWidth: 0, fontWeight: 600, paddingInline: 0 }}
          />
          <span
            style={{
              color: token.colorTextTertiary,
              fontSize: token.fontSizeSM,
              fontWeight: 400,
              whiteSpace: 'nowrap',
            }}
          >
            节 {index + 1}
          </span>
          <Button type="text" size="small" danger aria-label="删除节" icon={<DeleteOutlined />} onClick={onRemove} />
        </div>
        {!collapsed && (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, margin: '12px 0' }}>
              {section.measures.map((measure, mi) => (
                <div key={mi} style={{ display: 'flex', gap: 6, alignItems: 'center', minWidth: 0 }}>
                  <Input
                    aria-label="项目"
                    placeholder="项目"
                    maxLength={64}
                    style={{ flex: 1, minWidth: 90 }}
                    value={measure.label}
                    onChange={(e) => patchMeasure(mi, { label: e.target.value })}
                  />
                  <Input
                    aria-label="单位"
                    placeholder="单位"
                    maxLength={32}
                    style={{ width: 66 }}
                    value={measure.unit ?? ''}
                    onChange={(e) => patchMeasure(mi, { unit: e.target.value })}
                  />
                  {/* 正常范围 v2 union editor (rep-temp-normal-range design D2;
                      kind control is a Select per user ruling 2026-11-20 -
                      rep-temp-normal-kind-select-web, the Segmented read as a
                      data field and the dropdown felt handier): a per-row Select
                      区间/文本 - a null normal renders 区间 with empty ends (no
                      third state, D5); 区间 = min/max InputNumbers (a blank end
                      is a single-sided open interval), 文本 = a ≤64 qualitative
                      input. Empty shapes degrade to null at save. */}
                  <Select
                    aria-label="正常范围类型"
                    style={{ width: 96 }}
                    value={measure.normal?.kind ?? 'range'}
                    onChange={(value) =>
                      patchMeasure(mi, {
                        normal:
                          value === 'text'
                            ? { kind: 'text', text: '' }
                            : { kind: 'range', min: null, max: null },
                      })
                    }
                    options={[
                      { label: '区间', value: 'range' },
                      { label: '文本', value: 'text' },
                    ]}
                  />
                  {measure.normal?.kind === 'text' ? (
                    <Input
                      aria-label="正常范围"
                      placeholder="正常范围"
                      maxLength={64}
                      style={{ width: 120 }}
                      value={measure.normal.text}
                      onChange={(e) => patchMeasure(mi, { normal: { kind: 'text', text: e.target.value } })}
                    />
                  ) : (
                    <>
                      <InputNumber
                        aria-label="正常范围最小值"
                        placeholder="最小"
                        style={{ width: 78 }}
                        value={measure.normal?.kind === 'range' ? measure.normal.min : null}
                        onChange={(value) =>
                          patchMeasure(mi, {
                            normal: {
                              kind: 'range',
                              min: value ?? null,
                              max: measure.normal?.kind === 'range' ? measure.normal.max : null,
                            },
                          })
                        }
                      />
                      <span>~</span>
                      <InputNumber
                        aria-label="正常范围最大值"
                        placeholder="最大"
                        style={{ width: 78 }}
                        value={measure.normal?.kind === 'range' ? measure.normal.max : null}
                        onChange={(value) =>
                          patchMeasure(mi, {
                            normal: {
                              kind: 'range',
                              min: measure.normal?.kind === 'range' ? measure.normal.min : null,
                              max: value ?? null,
                            },
                          })
                        }
                      />
                    </>
                  )}
                  <Button
                    type="text"
                    size="small"
                    danger
                    aria-label="删除测量项"
                    icon={<DeleteOutlined />}
                    onClick={() => onPatch({ measures: section.measures.filter((_, i) => i !== mi) })}
                  />
                </div>
              ))}
              {/* The two add buttons mirror each other (ux-polish follow-up:
                  dashed block + plus icon, the primary color tint marking them
                  as the row/card adders). */}
              <Button
                color="primary"
                variant="dashed"
                block
                aria-label="添加测量项"
                icon={<PlusOutlined />}
                onClick={() =>
                  onPatch({ measures: [...section.measures, { code: null, label: '', unit: null, normal: null }] })
                }
              >
                添加测量项
              </Button>
            </div>
            <div style={{ marginBottom: 4 }}>
              <span style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>所见</span>
            </div>
            <Input.TextArea
              aria-label="所见"
              placeholder="所见"
              rows={14}
              value={section.finding ?? ''}
              onChange={(e) => onPatch({ finding: e.target.value })}
            />
          </>
        )}
      </Card>
    </div>
  )
}

export default function RepTemplatePage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  // Scope default is LAZY (rep-temp-scope-perms 2026-11-19): 公共 only when the
  // session holds `setting:temp:public:list` - a base-only user lands on 个人
  // and never issues a public (type=1) tree/table request.
  const [scope, setScope] = useState<number>(() =>
    hasPerm('setting:temp:public:list') ? TYPE_PUBLIC : TYPE_PRIVATE,
  )
  // Scope-keyed perm family (rep-temp-scope-perms 2026-11-19, D4): EVERY action
  // consults the CURRENT scope's family - 公共 keys to `setting:temp:public:*`,
  // 个人 to the base `setting:temp:*`. Single point - no scattered literals.
  const tempPerm = useCallback(
    (op: 'create' | 'update' | 'delete') =>
      (scope === TYPE_PUBLIC ? 'setting:temp:public:' : 'setting:temp:') + op,
    [scope],
  )

  const [tree, setTree] = useState<RepTempNode[]>([])
  const [selectedNode, setSelectedNode] = useState<RepTempNode | null>(null)
  const [treeLoading, setTreeLoading] = useState(false)

  const [files, setFiles] = useState<RepTemp[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [nameFilter, setNameFilter] = useState('')
  const [tableLoading, setTableLoading] = useState(false)

  // ===================== directory drawer (新建目录 / 新建子目录 / 编辑目录)
  const [dirDrawerOpen, setDirDrawerOpen] = useState(false)
  const [editingDir, setEditingDir] = useState<RepTempNode | null>(null)
  const [dirSaving, setDirSaving] = useState(false)
  const [dirForm] = Form.useForm<DirFormValues>()

  // ===================== template drawer (添加模板 / 编辑模板)
  const [tplDrawerOpen, setTplDrawerOpen] = useState(false)
  const [editingTpl, setEditingTpl] = useState<RepTemp | null>(null)
  const [tplParent, setTplParent] = useState<RepTempNode | null>(null)
  const [tplSaving, setTplSaving] = useState(false)
  const [tplForm] = Form.useForm<TplFormValues>()
  // Placeholder/preview (rep-temp-placeholder-preview-web): the live
  // preview's form reads (useWatch re-renders per keystroke; display-only —
  // the preview never touches the wire).
  const findingWatch = Form.useWatch('finding', tplForm) as string | undefined
  const impressionWatch = Form.useWatch('impression', tplForm) as string | undefined
  // The placeholder highlight: amber = the warning-class tint (待编辑占位,
  // the drawer-form-hint semantic); tokens only — no hex.
  const phStyle: CSSProperties = {
    background: token.colorWarningBg,
    borderRadius: 2,
    padding: '0 2px',
  }
  // Dual-form mode (D6): false = 简单模板 (prose columns), true = 分节模板
  // (sections serialize into content jsonb). Pure frontend state - the save
  // chain serializes; the drawer width is FIXED at 960 (2026-11-19 ruling) -
  // switching the mode never resizes the drawer.
  const [tplStructured, setTplStructured] = useState(false)
  const [tplSections, setTplSections] = useState<RepTempSection[]>([])
  // The 报告预览 modal (ux-polish 2026-11-20): the footer 预览 button summons
  // it; the always-on bottom block is retired.
  const [previewOpen, setPreviewOpen] = useState(false)

  // The tree flattened once - the drawers' 上级 echo resolves a node's parent
  // NAME from its upId (tree nodes carry only their own fields).
  const flatTree = useMemo(() => tree.flatMap((node) => [node, ...node.children]), [tree])
  const resolveNodeName = (upId: number | null) =>
    upId == null ? '顶层' : flatTree.find((node) => node.id === upId)?.name ?? '上级目录'

  // The directory drawer's 上级 options: every DIRECTORY node (flag=0) of the
  // current tree; the edited node and its whole subtree drop out when editing
  // - moving a parent under its own descendant cycles the pair out of the
  // server's tree render (every member keeps a resolvable parent, so none
  // becomes a root), unrecoverable from the UI.
  const dirParentOptions = useMemo(() => {
    const excluded = new Set<number>()
    if (editingDir) {
      excluded.add(editingDir.id)
      const stack = [editingDir.id]
      while (stack.length > 0) {
        const current = stack.pop() as number
        for (const node of flatTree) {
          if (node.upId === current && !excluded.has(node.id)) {
            excluded.add(node.id)
            stack.push(node.id)
          }
        }
      }
    }
    return flatTree
      .filter((node) => node.flag === 0 && !excluded.has(node.id))
      .map((node) => ({ value: node.id, label: node.name }))
  }, [flatTree, editingDir])

  const loadTree = useCallback(
    async (target: string, type: number) => {
      setTreeLoading(true)
      try {
        const rows = await repTempTree(target, type)
        // Dirs-only tree (2026-11-20 ruling): the state holds pruned rows, so
        // the anchor below is always a directory (添加模板 lands usable).
        const dirRows = pruneToDirectories(rows)
        setTree(dirRows)
        // Re-anchor the right pane to the same-named node in the new scope (or
        // the first directory) - the pane never keeps a stale cross-scope
        // parent.
        setSelectedNode((current) => {
          if (current) {
            const flat = dirRows.flatMap((node) => [node, ...node.children])
            const same = flat.find((node) => node.id === current.id)
            if (same) return same
          }
          const flat = dirRows.flatMap((node) => [node, ...node.children])
          return flat.length > 0 ? flat[0] : null
        })
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '模板树加载失败')
        setTree([])
        setSelectedNode(null)
      } finally {
        setTreeLoading(false)
      }
    },
    [messageApi],
  )

  const loadFiles = useCallback(
    async (type: number, target: string, upId: number | null, name: string, page: number, size: number) => {
      if (upId == null) {
        setFiles([])
        setTotal(0)
        return
      }
      setTableLoading(true)
      try {
        const result: Page<RepTemp> = await pageRepTemps({
          type,
          modality: target,
          upId,
          name: name || undefined,
          pageNum: page,
          pageSize: size,
        })
        setFiles(result.records)
        setTotal(result.total)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '模板列表加载失败')
      } finally {
        setTableLoading(false)
      }
    },
    [messageApi],
  )

  useEffect(() => {
    let alive = true
    dictItemsById(MODALITY_DICT_ID)
      .then((items) => {
        if (!alive) return
        setModalityOpts(items)
        if (items.length > 0) setModality(items[0].value ?? '')
      })
      .catch(() => {
        if (alive) messageApi.error('检查类型字典加载失败')
      })
    return () => {
      alive = false
    }
  }, [messageApi])

  useEffect(() => {
    if (modality) {
      void loadTree(modality, scope)
    }
  }, [modality, scope, loadTree])

  useEffect(() => {
    if (modality) {
      void loadFiles(scope, modality, selectedNode?.id ?? null, nameFilter, pageNum, pageSize)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modality, scope, selectedNode, pageNum, pageSize])

  const search = () => {
    setPageNum(1)
    void loadFiles(scope, modality, selectedNode?.id ?? null, nameFilter, 1, pageSize)
  }

  const refreshAll = async () => {
    await loadTree(modality, scope)
    await loadFiles(scope, modality, selectedNode?.id ?? null, nameFilter, pageNum, pageSize)
  }

  // ===================== directory drawer actions =====================

  // parentId prefills the 上级 select (null = 顶层): the left button passes
  // the selected directory (nothing fits → top-level), the tree's 新建子目录
  // passes its node.
  const openDirectoryAdd = (parentId: number | null) => {
    setEditingDir(null)
    dirForm.resetFields()
    dirForm.setFieldsValue({ upId: parentId })
    setDirDrawerOpen(true)
  }

  const openDirectoryEdit = (node: RepTempNode) => {
    setEditingDir(node)
    dirForm.setFieldsValue({ upId: node.upId, name: node.name })
    setDirDrawerOpen(true)
  }

  const saveDirectory = async () => {
    try {
      const values = await dirForm.validateFields()
      setDirSaving(true)
      if (editingDir) {
        // The 上级 select doubles as a move - a directory re-parents with its
        // whole subtree (children keep their upId pointing at it).
        await updateRepTemp(editingDir.id, {
          modality,
          flag: 0,
          upId: values.upId ?? null,
          name: values.name,
          finding: null,
          impression: null,
        })
        messageApi.success('修改成功')
      } else {
        await createRepTemp({
          type: scope,
          modality,
          flag: 0,
          upId: values.upId ?? null,
          name: values.name,
          finding: null,
          impression: null,
        })
        messageApi.success('添加成功')
      }
      setDirDrawerOpen(false)
      await refreshAll()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setDirSaving(false)
    }
  }

  // ===================== template drawer actions =====================

  // Templates live UNDER a directory - the dirs-only tree (2026-11-20) makes
  // the selected node a directory whenever one exists; null (no selection /
  // empty tree) warns 请先选择目录 instead of creating a parentless row (the
  // backend does not validate the parent type).
  const openTemplateAdd = (parent: RepTempNode | null) => {
    if (!parent) {
      messageApi.warning('请先选择目录')
      return
    }
    setEditingTpl(null)
    setTplParent(parent)
    setTplStructured(false)
    setTplSections([])
    tplForm.resetFields()
    setTplDrawerOpen(true)
  }

  // Mode select (D6 conversion semantics; 2026-11-19 rename+restyle, ex-模板形态
  // radio): 简单→分节 maps 表现 → the first
  // section's 所见 (auto-created; 节名 left empty for the user - the backend
  // @NotBlank is the guard); the global 意见 form field IS content.impression,
  // so it carries over untouched. 分节→简单 is REFUSED (sections would be
  // dropped) - warn and keep the controlled value on 分节模板.
  const handleTplModeSwitch = (mode: 'simple' | 'structured') => {
    if (mode === 'structured') {
      if (tplSections.length === 0) {
        const finding = normalizeLines((tplForm.getFieldValue('finding') as string | undefined) ?? '').trim()
        setTplSections([{ name: '', finding: finding || null, measures: [] }])
      }
      setTplStructured(true)
      return
    }
    messageApi.warning('切换为简单模板将丢失分节内容')
  }

  const patchTplSection = (si: number, patch: Partial<RepTempSection>) =>
    setTplSections((prev) => prev.map((s, i) => (i === si ? { ...s, ...patch } : s)))
  const addTplSection = () =>
    setTplSections((prev) => [...prev, { name: '', finding: null, measures: [] }])
  const removeTplSection = (si: number) =>
    setTplSections((prev) => prev.filter((_, i) => i !== si))

  // Edit backfill (D6): structured rows (content != null) land in 分节模式 with
  // the section list deep-copied from content (state must not alias the row);
  // prose rows keep 简单模式. The global 意见 field reads content.impression on
  // structured rows (the prose columns are NULL there, D1 互斥).
  const openTemplateEdit = (row: RepTemp) => {
    setEditingTpl(row)
    setTplParent(null)
    if (row.content) {
      setTplStructured(true)
      // Server contract allows measures: null (API-direct writes) - normalize at
      // the boundary so the form state always holds arrays (batch3 review Minor 1).
      setTplSections(row.content.sections.map((s) => ({ ...s, measures: (s.measures ?? []).map((m) => ({ ...m })) })))
      tplForm.setFieldsValue({
        name: row.name,
        finding: undefined,
        impression: row.content.impression ?? undefined,
      })
    } else {
      setTplStructured(false)
      setTplSections([])
      tplForm.setFieldsValue({
        name: row.name,
        finding: row.finding ?? undefined,
        impression: row.impression ?? undefined,
      })
    }
    setTplDrawerOpen(true)
  }

  const saveTemplate = async () => {
    try {
      const values = await tplForm.validateFields()
      // D1 互斥: structured saves serialize content and null the prose columns
      // (the backend re-asserts and clears server-side); prose saves carry
      // content: null - a full-replace that keeps the row prose.
      let content: RepTempContent | null = null
      if (tplStructured) {
        if (tplSections.some((section) => !section.name.trim())) {
          messageApi.error('节名不能为空')
          return
        }
        for (const section of tplSections) {
          for (const measure of section.measures) {
            if (!measure.label.trim()) {
              messageApi.error('测量项项目不能为空')
              return
            }
            // The v2 union client guard (rep-temp-normal-range spec scenario):
            // an inverted range is blocked HERE with a naming message - no
            // create/update request is issued (the server re-asserts, A0400).
            const normal = measure.normal
            if (normal?.kind === 'range' && normal.min != null && normal.max != null
                && normal.min > normal.max) {
              messageApi.error('正常范围最小值不能大于最大值')
              return
            }
          }
        }
        content = {
          version: 2,
          sections: tplSections.map((section) => ({
            name: section.name.trim(),
            finding: normalizeLines(section.finding ?? '').trim() || null,
            measures: section.measures.map((measure) => ({
              // The 项目 code is a nullable passthrough (rep-template-ux-polish):
              // legacy rows keep theirs (backfill), new measures ride null — the
              // printed B-form never shows it.
              code: measure.code?.trim() || null,
              label: measure.label.trim(),
              unit: measure.unit?.trim() || null,
              normal: serializeMeasureNormal(measure.normal),
            })),
          })),
          impression: values.impression?.trim() || null,
        }
      }
      setTplSaving(true)
      if (editingTpl) {
        await updateRepTemp(editingTpl.id, {
          modality: editingTpl.modality,
          flag: 1,
          upId: editingTpl.upId,
          name: values.name,
          finding: content ? null : normalizeLines(values.finding ?? '') || null,
          impression: content ? null : normalizeLines(values.impression ?? '') || null,
          content,
        })
        messageApi.success('修改成功')
      } else {
        // The drawer only opens with a guarded directory parent; the SCOPE
        // comes from the Segmented state and the owner binds server-side.
        await createRepTemp({
          type: scope,
          modality,
          flag: 1,
          upId: tplParent?.id ?? null,
          name: values.name,
          finding: content ? null : normalizeLines(values.finding ?? '') || null,
          impression: content ? null : normalizeLines(values.impression ?? '') || null,
          content,
        })
        messageApi.success('添加成功')
      }
      setTplDrawerOpen(false)
      await refreshAll()
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg)
      } else if (error && typeof error === 'object' && 'errorFields' in error) {
        return
      } else {
        messageApi.error('保存失败')
      }
    } finally {
      setTplSaving(false)
    }
  }

  const removeNode = async (node: RepTempNode) => {
    try {
      await deleteRepTemp(node.id)
      messageApi.success('删除成功')
      await refreshAll()
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
    }
  }

  const columns: TableColumnsType<RepTemp> = [
    { title: '模板名称', dataIndex: 'name', key: 'name', width: 200 },
    {
      title: '影像学表现',
      dataIndex: 'finding',
      key: 'finding',
      ellipsis: true,
      // D7: structured rows collapse to the 「N 节 · M 项」 summary in the
      // tertiary text color (the content rides the page response - no extra
      // request); prose rows render the raw column as before.
      render: (text: string | null, row) => {
        if (row.content) {
          const measures = row.content.sections.reduce((sum, section) => sum + (section.measures ?? []).length, 0)
          return (
            <span style={{ color: token.colorTextTertiary }}>
              {`${row.content.sections.length} 节 · ${measures} 项`}
            </span>
          )
        }
        // Placeholder tokens highlight inline (rep-temp-placeholder-preview-web):
        // the stored text is plain, the amber wrap is render-layer only.
        return text ? renderTemplateText(text, phStyle) : '—'
      },
    },
    {
      title: '影像意见',
      dataIndex: 'impression',
      key: 'impression',
      ellipsis: true,
      // D7: the structured row's conclusion lives in content.impression (the
      // impression column is NULL there, D1 互斥); placeholder tokens
      // highlight in both shapes.
      render: (text: string | null, row) => {
        if (row.content) {
          return row.content.impression ? renderTemplateText(row.content.impression, phStyle) : '—'
        }
        return text ? renderTemplateText(text, phStyle) : '—'
      },
    },
  ]

  const renderTreeNode = (node: RepTempNode, depth: number) => (
    <div key={node.id}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '6px 8px',
          borderRadius: 6,
          cursor: 'pointer',
          background: selectedNode?.id === node.id ? token.colorPrimaryBg : undefined,
          paddingLeft: 8 + depth * 16,
        }}
        onClick={() => {
          setSelectedNode(node)
          setPageNum(1)
          setNameFilter('')
        }}
      >
        {/* Folder glyph (2026-11-20 swap, ex-▰ text glyph): the house outlined
            family; the icon takes the selected tint with the name. */}
        <FolderOutlined style={{ color: selectedNode?.id === node.id ? token.colorPrimary : undefined }} />
        <span style={{ color: selectedNode?.id === node.id ? token.colorPrimary : undefined }}>
          {node.name}
        </span>
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 2 }}>
          {/* Dirs-only tree (2026-11-20 ruling): every node here is a
              directory (leaves pruned at load) - directories manage the
              hierarchy (新建子目录/编辑目录) and delete; template edit/delete
              live in the right table (a tree-side edit would PUT
              content-blind). */}
          {hasPerm(tempPerm('create')) && (
            <ConsoleAction label="新建子目录" icon="folderAdd" onClick={() => openDirectoryAdd(node.id)} />
          )}
          {hasPerm(tempPerm('update')) && (
            <ConsoleAction label="编辑目录" icon="edit" onClick={() => openDirectoryEdit(node)} />
          )}
          {hasPerm(tempPerm('delete')) && (
            <Popconfirm title={`确定删除节点“${node.name}”？`} onConfirm={() => void removeNode(node)}>
              <ConsoleAction label="删除节点" icon="remove" danger onClick={() => undefined} />
            </Popconfirm>
          )}
        </span>
      </div>
      {node.children.length > 0 && (
        <div style={{ marginLeft: 16 + depth * 16, borderLeft: `1px dashed ${token.colorBorderSecondary}` }}>
          {node.children.map((child) => renderTreeNode(child, 0))}
        </div>
      )}
    </div>
  )

  return (
    <ConsolePage>
      {contextHolder}
      <ConsoleWorkArea
        aside={
          <ConsolePanel>
            {/* exam-body aside pattern (2026-11-18): the 检查类型 select shares
                the tool row with the add action; the 公共/个人 scope switch is
                a block Segmented (radio options) between the row and the tree -
                no button pair. */}
            <ConsoleToolRow
              trailing={
                hasPerm(tempPerm('create')) && (
                  <ConsoleAddButton
                    label="新建目录"
                    onClick={() => openDirectoryAdd(selectedNode?.id ?? null)}
                  />
                )
              }
            >
              <Select
                aria-label="检查类型"
                style={{ width: 120 }}
                value={modality || undefined}
                onChange={(value) => setModality(value)}
                options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
              />
            </ConsoleToolRow>
            <div style={{ padding: '10px 12px 0' }}>
              <Segmented
                aria-label="模板范围"
                block
                value={scope}
                onChange={(value) => setScope(value as number)}
                options={[
                  // 公共 option only for public:list holders (2026-11-19
                  // ruling) - without it the page is a personal library; order
                  // stays 公共前个人, just filtered (switching to an unavailable
                  // value is unreachable).
                  ...(hasPerm('setting:temp:public:list') ? [{ label: '公共', value: TYPE_PUBLIC }] : []),
                  { label: '个人', value: TYPE_PRIVATE },
                ]}
              />
            </div>
            <div style={{ padding: '4px 8px', overflow: 'auto' }} aria-busy={treeLoading}>
              {tree.length === 0 && <div style={{ color: token.colorTextTertiary, padding: 16, fontSize: 13 }}>暂无节点</div>}
              {tree.map((node) => renderTreeNode(node, 0))}
            </div>
          </ConsolePanel>
        }
      >
        <ConsolePanel>
          <ConsoleToolRow
            trailing={
              hasPerm(tempPerm('create')) && (
                <ConsoleAddButton
                  label="添加模板"
                  onClick={() => openTemplateAdd(selectedNode)}
                />
              )
            }
          >
            {/* exam-body tool-row ruling (2026-11-16): scope + selection tags
                left the row - the Segmented and the tree's selected highlight
                ARE the context; the row carries the name filter only. */}
            <Input
              aria-label="模板名称"
              style={{ width: 180 }}
              placeholder="模板名称"
              value={nameFilter}
              onChange={(event) => setNameFilter(event.target.value)}
              onPressEnter={search}
            />
            <ConsoleQueryButton onClick={search} />
          </ConsoleToolRow>
          <ConsoleTable<RepTemp>
            rowKey={(row) => row.id}
            loading={tableLoading}
            columns={columns}
            dataSource={files}
            // Shared ops chrome (rep-temp-ops-chrome-web 2026-11-20 - the page
            // had hand-rolled a key:'ops' column, the last one in the repo):
            // the verbs ride ConsoleTable's actions column (刷新 affordance
            // pinned to the 操作 header's right edge, re-running the current
            // page query) and 删除 passes icon="remove" explicitly - the
            // ConsoleAction default is the edit glyph (third recurrence of
            // that bug; the convention now lives in the session memory).
            onRefresh={() => void loadFiles(scope, modality, selectedNode?.id ?? null, nameFilter, pageNum, pageSize)}
            actions={{
              width: 130,
              render: (row) => (
                <>
                  {hasPerm(tempPerm('update')) && (
                    <ConsoleAction label="编辑" onClick={() => openTemplateEdit(row)} />
                  )}
                  {hasPerm(tempPerm('delete')) && (
                    <Popconfirm title={`确定删除模板“${row.name}”？`} onConfirm={() => void removeNode({
                      id: row.id, name: row.name, flag: row.flag, upId: row.upId, children: [],
                    })}>
                      <ConsoleAction label="删除" icon="remove" danger onClick={() => undefined} />
                    </Popconfirm>
                  )}
                </>
              ),
            }}
          />
          <ConsolePagination
            current={pageNum}
            pageSize={pageSize}
            total={total}
            onChange={(page, size) => {
              setPageNum(page)
              setPageSize(size)
            }}
          />
        </ConsolePanel>
      </ConsoleWorkArea>

      {/* 目录抽屉 - hierarchy only (新建目录 / 新建子目录 / 编辑目录); the
          mixed 目录/模板 flag drawer is retired (2026-11-18 ruling). The 上级
          select (无 = 顶层) doubles as a move on edit; options exclude the
          edited node's own subtree (server-side tree cycles). */}
      <Drawer
        title={editingDir ? '编辑目录' : '新建目录'}
        width={440}
        open={dirDrawerOpen}
        onClose={() => setDirDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setDirDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={dirSaving} onClick={() => void saveDirectory()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={dirForm} layout="vertical">
          <Form.Item name="upId" label="上级">
            <Select
              allowClear
              placeholder="无（顶层）"
              options={dirParentOptions}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="目录名称"
            rules={[{ required: true, message: '目录名称不能为空' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 模板抽屉 - content only (添加模板 under the guarded directory parent /
          编辑模板 from the right table's loaded row). WIDE drawer at a FIXED
          960px (2026-11-19 ruling - the 分节模板 section cards' size, bigger
          than the old 640 simple-mode drawer) so the long-form 影像学表现/意见
          text areas breathe (rows 10/8) and switching the 类型 radio never
          resizes the drawer. House drawer shape: close button top-right,
          maskClosable off. */}
      <Drawer
        title={
          // The owning directory rides the TITLE as a tag (ux-polish follow-up
          // 2026-11-20: the form's 目录 echo row retired — 编辑模板 [目录：头颅-1]
          // reads at a glance). Both modes carry it: edit shows the row's
          // stored parent (resolved by name), add shows the guarded parent.
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {editingTpl ? '编辑模板' : '添加模板'}
            <Tag style={{ marginInlineEnd: 0, fontSize: token.fontSize, fontWeight: 600 }}>
              目录：{editingTpl ? resolveNodeName(editingTpl.upId) : tplParent ? tplParent.name : '顶层'}
            </Tag>
          </span>
        }
        width={960}
        open={tplDrawerOpen}
        onClose={() => setTplDrawerOpen(false)}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnClose
        footer={
          // 预览 rides the footer's LEFT (ux-polish 2026-11-20: the always-on
          // bottom preview retired — the doctor summons the print shape on
          // demand in a modal).
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Button onClick={() => setPreviewOpen(true)}>预览</Button>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button onClick={() => setTplDrawerOpen(false)}>取消</Button>
              <Button type="primary" loading={tplSaving} onClick={() => void saveTemplate()}>
                保存
              </Button>
            </div>
          </div>
        }
      >
        <Form form={tplForm} layout="vertical">
          {/* 名称 + 类型 share ONE row (ux-polish follow-up 2026-11-20: the 目录
              echo moved UP into the drawer title as a tag — 编辑模板
              [目录：头颅-1] — and the mode control joins the name field; 名称
              flexes LARGE, 类型 stays a fixed share on the right — the smaller
              proportion). The mode control is a RADIO GROUP (user ruling
              2026-11-20 follow-up — a single-select control reads handier than
              the dropdown; ex-Select, and the original 模板形态 radio lineage).
              类型 is not a Form field (state-driven), so it mimics the
              Form.Item label styling to keep the two column labels aligned.
              The global 影像意见 below is the ONE field shared by both modes
              (structured saves it into content.impression, D2). */}
          <div style={{ display: 'flex', gap: 16, marginBottom: token.marginLG }}>
            <Form.Item
              name="name"
              label="名称"
              rules={[{ required: true, message: '名称不能为空' }]}
              style={{ flex: 1, minWidth: 0, marginBottom: 0 }}
            >
              <Input maxLength={128} />
            </Form.Item>
            <div style={{ flex: '0 0 auto' }}>
              <div
                style={{
                  fontSize: token.fontSize,
                  color: token.colorText,
                  marginBottom: 8,
                  whiteSpace: 'nowrap',
                }}
              >
                类型
              </div>
              <Radio.Group
                value={tplStructured ? 'structured' : 'simple'}
                onChange={(e) => handleTplModeSwitch(e.target.value as 'simple' | 'structured')}
              >
                <Radio value="simple">简单模板</Radio>
                <Radio value="structured">分节模板</Radio>
              </Radio.Group>
            </div>
          </div>
          {!tplStructured && (
            <Form.Item
              name="finding"
              label="影像学表现"
              rules={[{ required: true, message: '影像学表现不能为空' }]}
            >
              {/* Tall text areas (2026-11-18 ruling): the findings/impression
                  prose is long-form - 10/8 rows in the 960px drawer. */}
              <Input.TextArea rows={10} />
            </Form.Item>
          )}
          {tplStructured && (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: token.marginSM,
                marginBottom: token.marginLG,
              }}
            >
              {tplSections.map((section, si) => (
                <TemplateSectionCard
                  key={si}
                  index={si}
                  section={section}
                  onPatch={(patch) => patchTplSection(si, patch)}
                  onRemove={() => removeTplSection(si)}
                />
              ))}
              <Button
                color="primary"
                variant="dashed"
                block
                aria-label="添加节"
                icon={<PlusOutlined />}
                onClick={addTplSection}
              >
                添加节
              </Button>
            </div>
          )}
          <Form.Item name="impression" label="影像意见">
            <Input.TextArea rows={8} />
          </Form.Item>
        </Form>

        {/* 报告预览 as an ON-DEMAND MODAL (rep-temp-placeholder-preview-web
            2026-11-20; ux-polish ruling: the always-on bottom preview retired —
            the footer's 预览 button summons it). The live print shape — one
            paragraph per line with the 2em first-line indent, 【...】 tokens
            highlighted amber (the warning-class tint: 待编辑), and in 分节模式
            the B-form mini measure table (拍板点 2: B 测值表式起步; the unit
            rides the HEADERS in parentheses when homogeneous —
            rep-temp-preview-unit-header-web). Display-only: reads useWatch /
            section state, never the wire; draft values stay live while the
            drawer is open. */}
        <Modal
          title="报告预览"
          open={previewOpen}
          onCancel={() => setPreviewOpen(false)}
          footer={null}
          width={880}
          // Generous default height (ux-polish follow-up 2026-11-20): the
          // preview should read like a report page, not a strip — 70vh with
          // internal scroll.
          styles={{ body: { height: '70vh', overflowY: 'auto' } }}
        >
          <div aria-label="报告预览">
            {tplStructured
              ? tplSections.map((section, si) => (
                  <div key={si} style={{ marginBottom: si < tplSections.length - 1 ? token.marginSM : 0 }}>
                    <div
                      style={{ fontWeight: 600, fontSize: 13, color: token.colorTextHeading, marginBottom: 4 }}
                    >
                      {section.name.trim() || `节 ${si + 1}`}
                    </div>
                    {section.measures.length > 0 && <PreviewMeasureTable section={section} token={token} />}
                    {section.finding && <PreviewParagraphs text={section.finding} phStyle={phStyle} />}
                  </div>
                ))
              : findingWatch && <PreviewParagraphs text={findingWatch} phStyle={phStyle} />}
            {impressionWatch && (
              <div
                style={{
                  marginTop: token.marginSM,
                  paddingTop: token.marginSM,
                  borderTop: `1px dashed ${token.colorBorderSecondary}`,
                }}
              >
                <div style={{ fontWeight: 600, fontSize: 13, color: token.colorTextHeading, marginBottom: 4 }}>
                  影像意见
                </div>
                <PreviewParagraphs text={impressionWatch} phStyle={phStyle} />
              </div>
            )}
          </div>
        </Modal>
      </Drawer>
    </ConsolePage>
  )
}
