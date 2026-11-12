import { DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import {
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Tabs,
  Tag,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createRoom,
  deleteRoom,
  getRoom,
  pageRoom,
  setRoomStatus,
  updateRoom,
} from '@/api/orgRoom'
import { listDevices } from '@/api/orgDevice'
import { listDeps } from '@/api/org-deps'
import { orgTree } from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { OrgDep, OrgDevice, OrgNode, Page, RoomRow } from '@/api/types'
import { dictItemsByName, resolveDictLabelByValue } from '@/utils/dict-items'
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
} from '../console-shell'
import { OrgTree } from '../org-tree'

/**
 * 诊室配置 page (org-device-room-web tasks 4.1-4.3; spec delta `web-system-console`
 * § Exam Room Configuration Page). The room is a BUSINESS place: rows carry the
 * server-resolved device summary (devId/devName/devAe/devIp/devPort - the JOIN lives
 * backend-side; the page never renders device fields from the room row itself), and
 * the drawer's 绑定设备 tab picks from the room's own 负责科室's devices with the
 * AE/IP/Port echoed READ-ONLY (the split model's core consequence - the room form
 * never edits device access fields). Tree semantics = the root-means-all D10 rule.
 * Status toggle: the enable-only command reverts on failure (rows re-render from the
 * untouched server state). Permission gating (D6) per F-node string.
 */

const PAGE_SIZE = 20

const DICT_DEVICE_TYPE = '设备类型'

interface RoomFormValues {
  did: number
  name: string
  code?: string
  enable: number
  callIp?: string
  callPort?: string
  callMac?: string
}

type RoomDialog = { kind: 'create' } | { kind: 'edit'; room: RoomRow }

/** The binding lives OUTSIDE the form values (picker + echo own it; body carries it). */
interface BindingState {
  devId: number | null
  devName: string | null
  devAe: string | null
  devIp: string | null
  devPort: string | null
}

const NO_BINDING: BindingState = {
  devId: null,
  devName: null,
  devAe: null,
  devIp: null,
  devPort: null,
}

export default function RoomPage() {
  const hasPerm = usePerm()
  const canCreate = hasPerm('system:room:create')
  const canUpdate = hasPerm('system:room:update')
  const canStatus = hasPerm('system:room:status')
  const canDelete = hasPerm('system:room:delete')
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [orgTreeLoading, setOrgTreeLoading] = useState(true)
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null)
  const [deps, setDeps] = useState<OrgDep[]>([])
  const [deviceTypes, setDeviceTypes] = useState<DictItem[]>([])
  const [depFilter, setDepFilter] = useState<number | null>(null)
  const [nameDraft, setNameDraft] = useState('')
  const [nameFilter, setNameFilter] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZE)
  const [page, setPage] = useState<Page<RoomRow> | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [dialog, setDialog] = useState<RoomDialog | null>(null)
  const [binding, setBinding] = useState<BindingState>(NO_BINDING)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [pickerPicked, setPickerPicked] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<RoomFormValues>()
  /** Reactive did (Form.useWatch) - the picker button/scope depend on it live. */
  const watchedDid = Form.useWatch('did', form)

  const selectedOrg = selectedOrgId === null ? null : findNode(orgs, selectedOrgId)
  const isCampusSelected = selectedOrg !== null && selectedOrg.upId !== 0

  const depNameById = useMemo(() => {
    const map = new Map<number, string>()
    for (const dep of deps) map.set(dep.id, dep.name)
    return map
  }, [deps])

  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const [tree, depRows, devTypes] = await Promise.all([
          orgTree(),
          listDeps(),
          dictItemsByName(DICT_DEVICE_TYPE),
        ])
        if (!alive) return
        setOrgs(tree)
        setDeps(depRows)
        setDeviceTypes(devTypes)
      } catch (error) {
        if (alive) messageApi.error(error instanceof ApiError ? error.msg : '页面数据加载失败')
      } finally {
        if (alive) setOrgTreeLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [messageApi])

  const loadPage = useCallback(
    async (effective: { orgId?: number; depId?: number; name?: string; pageNum: number; pageSize: number }) => {
      setListLoading(true)
      try {
        setPage(await pageRoom(effective))
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '诊室列表加载失败')
      } finally {
        setListLoading(false)
      }
    },
    [messageApi],
  )

  useEffect(() => {
    void loadPage({
      pageNum,
      pageSize,
      orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
      depId: depFilter ?? undefined,
      name: nameFilter.trim() || undefined,
    })
  }, [depFilter, isCampusSelected, loadPage, nameFilter, pageNum, pageSize, selectedOrgId])

  const currentQuery = useCallback(
    () => ({
      pageNum,
      pageSize,
      orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
      depId: depFilter ?? undefined,
      name: nameFilter.trim() || undefined,
    }),
    [depFilter, isCampusSelected, nameFilter, pageNum, pageSize, selectedOrgId],
  )

  const openCreate = useCallback(() => {
    form.setFieldsValue({ name: '', code: '', enable: 1 })
    setBinding(NO_BINDING)
    setDialog({ kind: 'create' })
  }, [form])

  const openEdit = useCallback(
    async (row: RoomRow) => {
      try {
        // The echo comes from the DETAIL response (fresh fetch), never local state.
        const detail = await getRoom(row.id)
        form.setFieldsValue({
          did: detail.did,
          name: detail.name,
          code: detail.code ?? undefined,
          enable: detail.enable,
          callIp: detail.callIp ?? undefined,
          callPort: detail.callPort ?? undefined,
          callMac: detail.callMac ?? undefined,
        })
        setBinding({
          devId: detail.devId,
          devName: detail.devName,
          devAe: detail.devAe,
          devIp: detail.devIp,
          devPort: detail.devPort,
        })
        setDialog({ kind: 'edit', room: detail })
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '诊室详情加载失败')
      }
    },
    [form, messageApi],
  )

  /** The picker scopes to the selected 负责科室's campus + department (design D3). */
  const pickerScope = useCallback((): { orgId: number; depId: number } | null => {
    if (watchedDid == null) return null
    const dep = deps.find((d) => d.id === watchedDid)
    if (dep == null) return null
    return { orgId: dep.orgId, depId: dep.id }
  }, [deps, watchedDid])

  const confirmPicker = useCallback(() => {
    const scope = pickerScope()
    if (scope == null || pickerPicked == null) {
      setPickerOpen(false)
      return
    }
    void (async () => {
      try {
        const devices = await listDevices(scope.orgId, scope.depId)
        const picked = devices.find((d) => d.id === pickerPicked)
        setBinding({
          devId: picked?.id ?? null,
          devName: picked?.name ?? null,
          devAe: picked?.ae ?? null,
          devIp: picked?.ip ?? null,
          devPort: picked?.port ?? null,
        })
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '设备列表加载失败')
      } finally {
        setPickerPicked(null)
        setPickerOpen(false)
      }
    })()
  }, [messageApi, pickerPicked, pickerScope])

  const handleSubmit = useCallback(async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const body = {
        oid: deps.find((d) => d.id === values.did)?.orgId ?? 0,
        did: values.did,
        name: values.name,
        code: values.code ?? null,
        devId: binding.devId,
        callIp: values.callIp ?? null,
        callPort: values.callPort ?? null,
        callMac: values.callMac ?? null,
      }
      if (dialog?.kind === 'edit') {
        await updateRoom(dialog.room.id, body)
        messageApi.success('修改成功')
      } else {
        await createRoom({ ...body, enable: values.enable })
        messageApi.success('添加成功')
      }
      setDialog(null)
      await loadPage(currentQuery())
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.msg.includes('名称') || error.msg.includes('name')) {
          form.setFields([{ name: 'name', errors: [error.msg] }])
        } else {
          messageApi.error(error.msg)
        }
      } else if (error instanceof Error && error.name !== 'ValidateError') {
        messageApi.error(error.message || '保存失败')
      }
    } finally {
      setSaving(false)
    }
  }, [binding.devId, currentQuery, deps, dialog, form, loadPage, messageApi])

  const handleStatus = useCallback(
    async (row: RoomRow, next: 0 | 1) => {
      try {
        await setRoomStatus(row.id, next)
        await loadPage(currentQuery())
      } catch (error) {
        // Revert-on-failure: rows re-render from the untouched server state.
        messageApi.error(error instanceof ApiError ? error.msg : '状态切换失败')
      }
    },
    [currentQuery, loadPage, messageApi],
  )

  const handleDelete = useCallback(
    async (row: RoomRow) => {
      try {
        await deleteRoom(row.id)
        messageApi.success('删除成功')
        await loadPage(currentQuery())
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
      }
    },
    [currentQuery, loadPage, messageApi],
  )

  const columns: TableColumnsType<RoomRow> = [
    {
      title: '诊室名称 / 编码',
      dataIndex: 'name',
      render: (_, row) => (
        <div>
          <div>{row.name}</div>
          {row.code && (
            <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
              {row.code}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enable',
      width: 90,
      align: 'center',
      render: (_, row) => (
        <Switch
          aria-label={`${row.name}启停`}
          checked={row.enable === 1}
          checkedChildren="启用"
          unCheckedChildren="停用"
          disabled={!canStatus}
          onChange={(checked) => void handleStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      title: '绑定设备',
      dataIndex: 'devName',
      width: 170,
      render: (_, row) =>
        row.devId === null ? (
          <span style={{ color: token.colorTextTertiary }}>未绑定</span>
        ) : (
          <div>
            <div>{row.devName}</div>
            {row.devAe && (
              <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
                {row.devAe}
              </div>
            )}
          </div>
        ),
    },
    {
      title: '负责科室',
      dataIndex: 'did',
      width: 110,
      render: (did: number) => depNameById.get(did) ?? String(did),
    },
  ]

  return (
    <div style={{ height: '100%' }}>
      {contextHolder}
      <ConsoleWorkArea
        aside={
          <ConsolePanel>
            <OrgTree
              nodes={orgs}
              loading={orgTreeLoading}
              selectedOrgId={selectedOrgId}
              onSelect={(node) => {
                setSelectedOrgId(node?.id ?? null)
                setPageNum(1)
              }}
              onRefresh={() => {
                setOrgTreeLoading(true)
                void orgTree()
                  .then(setOrgs)
                  .finally(() => setOrgTreeLoading(false))
              }}
              hint="选中根节点查看全部院区,选中院区过滤该院区"
            />
          </ConsolePanel>
        }
      >
        <ConsolePage>
          <ConsoleToolRow
            trailing={
              canCreate ? (
                <ConsoleAddButton label="添加诊室" onClick={openCreate} />
              ) : undefined
            }
          >
            <Select
              aria-label="负责科室过滤"
              allowClear
              placeholder="负责科室"
              style={{ width: 200 }}
              value={depFilter}
              options={deps.map((dep) => ({ value: dep.id, label: dep.name }))}
              onChange={(next) => {
                setDepFilter(next ?? null)
                setPageNum(1)
              }}
            />
            <Input
              allowClear
              placeholder="诊室名称"
              style={{ width: 200 }}
              value={nameDraft}
              onChange={(event) => setNameDraft(event.target.value)}
              onPressEnter={() => {
                setNameFilter(nameDraft.trim())
                setPageNum(1)
              }}
            />
            <ConsoleQueryButton
              onClick={() => {
                setNameFilter(nameDraft.trim())
                setPageNum(1)
              }}
            />
          </ConsoleToolRow>

          <ConsolePanel>
            <ConsoleTable<RoomRow>
              rowKey={(row) => row.id}
              columns={columns}
              dataSource={page?.records ?? []}
              loading={listLoading}
              actions={{
                width: 110,
                render: (row) => (
                  <div style={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
                    <ConsoleAction
                      label="编辑诊室"
                      icon="edit"
                      disabled={!canUpdate}
                      onClick={() => void openEdit(row)}
                    />
                    <Popconfirm
                      title="删除诊室"
                      description={`确定删除「${row.name}」吗?`}
                      okText="确定"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => void handleDelete(row)}
                    >
                      <Button
                        type="text"
                        danger
                        aria-label="删除诊室"
                        icon={<DeleteOutlined />}
                        disabled={!canDelete}
                      />
                    </Popconfirm>
                  </div>
                ),
              }}
            />
            <ConsolePagination
              total={page?.total ?? 0}
              current={page?.current ?? 1}
              pageSize={pageSize}
              onChange={(next, size) => {
                setPageNum(next)
                setPageSize(size)
              }}
              onShowSizeChange={(_, size) => setPageSize(size)}
            />
          </ConsolePanel>
        </ConsolePage>
      </ConsoleWorkArea>

      <Drawer
        open={dialog !== null}
        width={640}
        title={dialog?.kind === 'edit' ? '编辑诊室' : '添加诊室'}
        onClose={() => setDialog(null)}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setDialog(null)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void handleSubmit()}>
              保存
            </Button>
          </div>
        }
      >
        <Form form={form} layout="vertical" requiredMark>
          <Tabs
            items={[
              {
                key: 'base',
                label: '基础信息',
                forceRender: true,
                children: (
                  <>
                    <Form.Item
                      label="负责科室"
                      name="did"
                      rules={[{ required: true, message: '请选择负责科室' }]}
                    >
                      <Select
                        aria-label="负责科室选择"
                        placeholder="请选择科室"
                        options={deps.map((dep) => ({ value: dep.id, label: dep.name }))}
                      />
                    </Form.Item>
                    <Form.Item
                      label="名称"
                      name="name"
                      rules={[{ required: true, message: '请输入诊室名称' }]}
                    >
                      <Input placeholder="诊室名称,同科室下唯一" />
                    </Form.Item>
                    <Form.Item label="编码" name="code">
                      <Input placeholder="选填,如 R-CT-01" />
                    </Form.Item>
                    <Form.Item
                      label="启用"
                      name="enable"
                      valuePropName="checked"
                      getValueFromEvent={(checked: boolean) => (checked ? 1 : 0)}
                    >
                      <Switch checkedChildren="启用" unCheckedChildren="停用" />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'bind',
                label: '绑定设备',
                forceRender: true,
                children: (
                  <>
                    <div style={{ marginBottom: 12, color: token.colorTextSecondary, fontSize: token.fontSizeSM }}>
                      诊室引用设备(可随时换绑/解绑);AE / IP / Port 属于设备档案,此处只读回显
                    </div>
                    <Button
                      icon={<SearchOutlined />}
                      disabled={watchedDid == null}
                      title={watchedDid == null ? '请先选择负责科室' : undefined}
                      onClick={() => {
                        setPickerPicked(null)
                        setPickerOpen(true)
                      }}
                    >
                      选择设备
                    </Button>
                    {binding.devId === null ? (
                      <div
                        style={{
                          marginTop: 10,
                          border: `1px dashed ${token.colorBorder}`,
                          borderRadius: token.borderRadius,
                          padding: 14,
                          textAlign: 'center',
                          color: token.colorTextTertiary,
                          fontSize: token.fontSizeSM,
                        }}
                      >
                        未绑定设备
                      </div>
                    ) : (
                      <div
                        style={{
                          marginTop: 10,
                          border: `1px solid ${token.colorPrimaryBorder}`,
                          background: token.colorPrimaryBg,
                          borderRadius: token.borderRadius,
                          padding: '12px 14px',
                        }}
                      >
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            marginBottom: 8,
                          }}
                        >
                          <span style={{ fontWeight: 600, color: token.colorPrimary }}>
                            {binding.devName}
                          </span>
                          <Button
                            type="link"
                            danger
                            size="small"
                            onClick={() => setBinding(NO_BINDING)}
                          >
                            清除绑定
                          </Button>
                        </div>
                        <div
                          style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(3, 1fr)',
                            gap: '6px 14px',
                            fontSize: token.fontSizeSM,
                          }}
                        >
                          <span>
                            AE <code style={{ background: token.colorBgContainer }}>{binding.devAe ?? '—'}</code>
                          </span>
                          <span>
                            IP <code style={{ background: token.colorBgContainer }}>{binding.devIp ?? '—'}</code>
                          </span>
                          <span>
                            Port <code style={{ background: token.colorBgContainer }}>{binding.devPort ?? '—'}</code>
                          </span>
                        </div>
                      </div>
                    )}
                  </>
                ),
              },
              {
                key: 'call',
                label: '叫号设置',
                forceRender: true,
                children: (
                  <>
                    <Form.Item label="叫号屏 IP" name="callIp">
                      <Input />
                    </Form.Item>
                    <Form.Item label="叫号屏 Port" name="callPort">
                      <Input />
                    </Form.Item>
                    <Form.Item label="叫号屏 MAC" name="callMac">
                      <Input />
                    </Form.Item>
                  </>
                ),
              },
            ]}
          />
        </Form>
      </Drawer>

      <Modal
        open={pickerOpen}
        title="选择设备(已按负责科室过滤)"
        onCancel={() => setPickerOpen(false)}
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setPickerOpen(false)}>取消</Button>
            <Button type="primary" onClick={confirmPicker}>
              确定绑定
            </Button>
          </div>
        }
      >
        <PickerList
          onPick={setPickerPicked}
          picked={pickerPicked}
          deviceTypes={deviceTypes}
          scope={pickerScope()}
        />
      </Modal>
    </div>
  )
}

/** The picker's org-scoped list (fetched on open; single-select rows). */
function PickerList({
  onPick,
  picked,
  deviceTypes,
  scope,
}: {
  onPick: (id: number | null) => void
  picked: number | null
  deviceTypes: DictItem[]
  scope: { orgId: number; depId: number } | null
}) {
  const { token } = theme.useToken()
  const [devices, setDevices] = useState<OrgDevice[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (scope == null) return
    let alive = true
    setLoading(true)
    void listDevices(scope.orgId, scope.depId)
      .then((rows) => {
        if (alive) setDevices(rows)
      })
      .catch(() => {
        if (alive) setDevices([])
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [scope])

  if (scope == null) {
    return <div style={{ color: 'rgba(0,0,0,0.45)' }}>请先选择负责科室</div>
  }
  return (
    <div style={{ maxHeight: 320, overflow: 'auto' }}>
      {devices.map((device) => (
        <div
          key={device.id}
          role="button"
          tabIndex={0}
          onClick={() => onPick(device.id)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '11px 12px',
            border: `1px solid ${picked === device.id ? token.colorPrimary : token.colorBorder}`,
            borderRadius: token.borderRadius,
            marginBottom: 8,
            cursor: 'pointer',
            background: picked === device.id ? token.colorPrimaryBg : undefined,
          }}
        >
          <b>{device.name}</b>
          <span style={{ color: 'rgba(0,0,0,0.65)', fontSize: 12 }}>{device.code}</span>
          <span style={{ marginLeft: 'auto' }}>
            <Tag color="blue">{resolveDictLabelByValue(deviceTypes, device.type)}</Tag>
          </span>
        </div>
      ))}
      {!loading && devices.length === 0 && (
        <div style={{ textAlign: 'center', color: 'rgba(0,0,0,0.45)', padding: 16 }}>
          该科室暂无设备
        </div>
      )}
    </div>
  )
}

/** Depth-first lookup of one org node by id (the device page shape). */
function findNode(nodes: OrgNode[], id: number): OrgNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const hit = node.children ? findNode(node.children, id) : null
    if (hit) return hit
  }
  return null
}
