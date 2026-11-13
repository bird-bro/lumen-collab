import { Button, Drawer, Form, Input, Popconfirm, Radio, Select, Tag, message, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import {
  createRepTemp,
  deleteRepTemp,
  pageRepTemps,
  repTempTree,
  updateRepTemp,
} from '@/api/rep-temp'
import { ApiError } from '@/api/client'
import type { Page, RepTemp, RepTempNode } from '@/api/types'
import { dictItemsByName } from '@/utils/dict-items'
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
 * § Report Template Page). ONE page with 公共/私有 tabs - the scope `type`
 * (1 公共 / 2 个人) threads into EVERY tree/page/mutation call and switching
 * tabs reloads both panes (the server keeps private rows owner-scoped, so the
 * page never renders another user's phrases). LEFT: the modality select + the
 * folder/template tree with node-level 新建子级/编辑/删除. RIGHT: the paged
 * template table under the selected node + name search. The drawer's 目录/模板
 * flag radio toggles the finding/impression textareas (directories stay
 * content-free).
 */

const DICT_MODALITY = '检查类型'
const TYPE_PUBLIC = 1
const TYPE_PRIVATE = 2

interface TempFormValues {
  flag: number
  name: string
  finding?: string
  impression?: string
}

export default function RepTemplatePage() {
  const hasPerm = usePerm()
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [modalityOpts, setModalityOpts] = useState<DictItem[]>([])
  const [modality, setModality] = useState<string>('')
  const [scope, setScope] = useState<number>(TYPE_PUBLIC)

  const [tree, setTree] = useState<RepTempNode[]>([])
  const [selectedNode, setSelectedNode] = useState<RepTempNode | null>(null)
  const [treeLoading, setTreeLoading] = useState(false)

  const [files, setFiles] = useState<RepTemp[]>([])
  const [total, setTotal] = useState(0)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [nameFilter, setNameFilter] = useState('')
  const [tableLoading, setTableLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<RepTemp | null>(null)
  const [parentLabel, setParentLabel] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<TempFormValues>()

  const loadTree = useCallback(
    async (target: string, type: number) => {
      setTreeLoading(true)
      try {
        const rows = await repTempTree(target, type)
        setTree(rows)
        // Re-anchor the right pane to the same-named node in the new scope (or
        // the first node) - the pane never keeps a stale cross-scope parent.
        setSelectedNode((current) => {
          if (current) {
            const flat = rows.flatMap((node) => [node, ...node.children])
            const same = flat.find((node) => node.id === current.id)
            if (same) return same
          }
          const flat = rows.flatMap((node) => [node, ...node.children])
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
    dictItemsByName(DICT_MODALITY)
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

  // ===================== tree node actions =====================

  const openNodeAdd = (parent: RepTempNode | null) => {
    setEditing(null)
    setParentLabel(parent ? parent.name : '顶层')
    form.resetFields()
    form.setFieldsValue({ flag: 1 })
    setDrawerOpen(true)
  }

  const openNodeEdit = (node: RepTempNode) => {
    // The TREE node carries only the tree fields; templates' content needs the
    // file row - find it in the loaded files when present.
    const fileRow = files.find((row) => row.id === node.id)
    setEditing(
      fileRow ?? {
        id: node.id,
        type: scope,
        ownerUid: null,
        modality,
        flag: node.flag,
        upId: node.upId,
        name: node.name,
        finding: null,
        impression: null,
      },
    )
    setParentLabel(node.upId == null ? '顶层' : '上级节点')
    form.setFieldsValue({
      flag: node.flag,
      name: node.name,
      finding: fileRow?.finding ?? undefined,
      impression: fileRow?.impression ?? undefined,
    })
    setDrawerOpen(true)
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

  const save = async () => {
    try {
      const values = await form.validateFields()
      const isTemplate = values.flag === 1
      if (isTemplate && !values.finding) {
        messageApi.warning('影像学表现不能为空')
        return
      }
      setSaving(true)
      if (editing) {
        await updateRepTemp(editing.id, {
          modality: editing.modality,
          flag: values.flag,
          upId: editing.upId,
          name: values.name,
          finding: isTemplate ? values.finding ?? null : null,
          impression: isTemplate ? values.impression ?? null : null,
        })
        messageApi.success('修改成功')
      } else {
        // The SCOPE comes from the tab state; the owner binds server-side.
        await createRepTemp({
          type: scope,
          modality,
          flag: values.flag,
          upId: selectedNode?.id ?? null,
          name: values.name,
          finding: isTemplate ? values.finding ?? null : null,
          impression: isTemplate ? values.impression ?? null : null,
        })
        messageApi.success('添加成功')
      }
      setDrawerOpen(false)
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
      setSaving(false)
    }
  }

  const columns: TableColumnsType<RepTemp> = [
    { title: '模板名称', dataIndex: 'name', key: 'name', width: 200 },
    {
      title: '影像学表现',
      dataIndex: 'finding',
      key: 'finding',
      ellipsis: true,
      render: (text: string | null) => text ?? '—',
    },
    {
      title: '影像学意见',
      dataIndex: 'impression',
      key: 'impression',
      ellipsis: true,
      render: (text: string | null) => text ?? '—',
    },
    {
      title: '操作',
      key: 'ops',
      width: 130,
      render: (_, row) => (
        <>
          {hasPerm('setting:temp:update') && (
            <ConsoleAction label="编辑" onClick={() => openNodeEdit({
              id: row.id, name: row.name, flag: row.flag, upId: row.upId, children: [],
            })} />
          )}
          {hasPerm('setting:temp:delete') && (
            <Popconfirm title={`确定删除模板“${row.name}”？`} onConfirm={() => void removeNode({
              id: row.id, name: row.name, flag: row.flag, upId: row.upId, children: [],
            })}>
              <ConsoleAction label="删除" danger onClick={() => undefined} />
            </Popconfirm>
          )}
        </>
      ),
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
        <span>{node.flag === 0 ? '▰' : '▤'}</span>
        <span style={{ color: selectedNode?.id === node.id ? token.colorPrimary : undefined }}>
          {node.name}
        </span>
        <span style={{ marginLeft: 'auto', display: 'flex', gap: 2 }}>
          {hasPerm('setting:temp:create') && (
            <ConsoleAction label="新建子级" icon="star" onClick={() => openNodeAdd(node)} />
          )}
          {hasPerm('setting:temp:update') && (
            <ConsoleAction label="编辑节点" icon="edit" onClick={() => openNodeEdit(node)} />
          )}
          {hasPerm('setting:temp:delete') && (
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
            <div style={{ padding: '10px 12px 0', display: 'flex', gap: 8 }}>
              <Select
                aria-label="检查类型"
                style={{ width: 120 }}
                value={modality || undefined}
                onChange={(value) => setModality(value)}
                options={modalityOpts.map((item) => ({ value: item.value, label: item.value }))}
              />
              <Button
                aria-label="公共"
                type={scope === TYPE_PUBLIC ? 'primary' : 'default'}
                onClick={() => setScope(TYPE_PUBLIC)}
              >
                公共
              </Button>
              <Button
                aria-label="私有"
                type={scope === TYPE_PRIVATE ? 'primary' : 'default'}
                onClick={() => setScope(TYPE_PRIVATE)}
              >
                私有
              </Button>
            </div>
            <ConsoleToolRow
              trailing={
                hasPerm('setting:temp:create') && (
                  <ConsoleAddButton label="新建顶层目录" onClick={() => openNodeAdd(null)} />
                )
              }
            >
              {null}
            </ConsoleToolRow>
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
              hasPerm('setting:temp:create') && (
                <ConsoleAddButton
                  label="添加模板"
                  onClick={() => openNodeAdd(selectedNode)}
                />
              )
            }
          >
            <Tag>{scope === TYPE_PUBLIC ? '公共' : '私有'}</Tag>
            <Tag>{selectedNode ? `上级 ${selectedNode.name}` : '未选择节点'}</Tag>
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

      <Drawer
        title={editing ? '编辑节点' : parentLabel === '顶层' ? '新建顶层目录/模板' : '新建子级'}
        width={480}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item label="上级">
            <Tag>{parentLabel === '顶层' ? '顶层' : parentLabel}</Tag>
          </Form.Item>
          <Form.Item name="flag" label="节点类型" initialValue={1}>
            <Radio.Group
              onChange={(event) => form.setFieldValue('flag', event.target.value)}
            >
              <Radio value={1}>模板</Radio>
              <Radio value={0}>目录</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.flag !== next.flag}>
            {({ getFieldValue }) =>
              getFieldValue('flag') === 1 ? (
                <>
                  <Form.Item
                    name="name"
                    label="名称"
                    rules={[{ required: true, message: '名称不能为空' }]}
                  >
                    <Input maxLength={128} />
                  </Form.Item>
                  <Form.Item
                    name="finding"
                    label="影像学表现"
                    rules={[{ required: true, message: '影像学表现不能为空' }]}
                  >
                    <Input.TextArea rows={4} />
                  </Form.Item>
                  <Form.Item name="impression" label="影像学意见">
                    <Input.TextArea rows={3} />
                  </Form.Item>
                </>
              ) : (
                <Form.Item
                  name="name"
                  label="目录名称"
                  rules={[{ required: true, message: '目录名称不能为空' }]}
                >
                  <Input maxLength={128} />
                </Form.Item>
              )
            }
          </Form.Item>
          <Button type="primary" block loading={saving} onClick={() => void save()}>
            保存
          </Button>
        </Form>
      </Drawer>
    </ConsolePage>
  )
}
