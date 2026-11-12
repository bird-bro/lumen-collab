import {
  DeleteOutlined,
} from '@ant-design/icons'
import {
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Switch,
  Tabs,
  Tag,
  message,
  theme,
} from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createDevice, deleteDevice, getDevice, pageDevice, updateDevice } from '@/api/orgDevice'
import { listDeps } from '@/api/org-deps'
import { orgTree } from '@/api/orgs'
import { ApiError } from '@/api/client'
import type { CreateDeviceBody, DevicePageQuery, OrgDep, OrgDevice, OrgNode, Page } from '@/api/types'
import { dictItemsByName, resolveDictLabel, resolveDictLabelByValue } from '@/utils/dict-items'
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
 * 设备档案 page (org-device-room-web tasks 3.1-3.3; spec delta `web-system-console`
 * § Device Archive Page). Layout = the 科室 shape: LEFT OrgTree as a PURE selection
 * context, RIGHT the server-paginated device table. Tree semantics (second-round
 * ruling 2026-11-12, design D10): the hospital root means ALL campuses (the page
 * query carries NO orgId key at all - absent = every campus); a campus node filters.
 *
 * The device is the asset + DICOM access point (the split ruling): the drawer's
 * 接入与采集 tab owns ae/ip/port + the Box block + 图像预设; 所属部门 is REQUIRED
 * (the 使用科室 anchor). 归属类型 = 自购 prefills 使用期限 (today → 2099-12-31) on
 * ADD only (OPS behavior). Duplicate codes (A0400) surface on the 编号 field with
 * the drawer open; the delete guard's room-count message surfaces as an error and
 * the row stays.
 *
 * Permission gating (D6): `system:device:list` gates the page surface; each mutation
 * affordance answers to its own F-node string.
 */

const PAGE_SIZE = 20

/** The dict catalog NAMES (the by-name lookup contract, design D5). */
const DICT_DEVICE_TYPE = '设备类型'
const DICT_DEVICE_STATUS = '设备状态'
const DICT_TAG = '归属类型'

interface DeviceFormValues {
  code: string
  name: string
  /** The dict-1024 VALUE token ('CT'/'DR') - the machine value since the
   * 2026-11-12 ruling; the select binds item.value, not the numeric code. */
  type: string
  status: number
  tag: number
  did: number
  brand?: string
  model?: string
  spec?: string
  serialNum?: string
  manufacturer?: string
  productionDate?: Dayjs | null
  purchaseDate?: Dayjs | null
  acceptanceDate?: Dayjs | null
  responsible?: string
  responsiblePhone?: string
  startDate?: Dayjs | null
  endDate?: Dayjs | null
  remark?: string
  ae?: string
  ip?: string
  port?: string
  boxAdapter?: string
  boxIp?: string
  boxPort?: string
  boxMac?: string
  boxPrint?: string
  captureW?: number
  captureH?: number
  prune?: boolean
  pruneU?: number
  pruneD?: number
  pruneL?: number
  pruneR?: number
}

type DeviceDialog = { kind: 'create' } | { kind: 'edit'; device: OrgDevice }

const EMPTY_FORM: DeviceFormValues = {
  code: '',
  name: '',
  type: undefined as unknown as string,
  status: 1,
  tag: 10,
  did: undefined as unknown as number,
}

export default function DevicePage() {
  const hasPerm = usePerm()
  const canCreate = hasPerm('system:device:create')
  const canUpdate = hasPerm('system:device:update')
  const canDelete = hasPerm('system:device:delete')
  const { token } = theme.useToken()
  const [messageApi, contextHolder] = message.useMessage()

  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [orgTreeLoading, setOrgTreeLoading] = useState(true)
  const [selectedOrgId, setSelectedOrgId] = useState<number | null>(null)
  const [deps, setDeps] = useState<OrgDep[]>([])
  const [deviceTypes, setDeviceTypes] = useState<DictItem[]>([])
  const [deviceStatuses, setDeviceStatuses] = useState<DictItem[]>([])
  const [tags, setTags] = useState<DictItem[]>([])
  const [keywordDraft, setKeywordDraft] = useState('')
  const [keyword, setKeyword] = useState('')
  const [typeFilter, setTypeFilter] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<number | null>(null)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZE)
  const [page, setPage] = useState<Page<OrgDevice> | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [dialog, setDialog] = useState<DeviceDialog | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<DeviceFormValues>()
  const dialogKindRef = useRef<DeviceDialog['kind']>('create')

  const selectedOrg = selectedOrgId === null ? null : findNode(orgs, selectedOrgId)
  /** Root (upId 0) = all campuses: the query omits orgId entirely (design D10). */
  const isCampusSelected = selectedOrg !== null && selectedOrg.upId !== 0

  const depNameById = useMemo(() => {
    const map = new Map<number, string>()
    for (const dep of deps) map.set(dep.id, dep.name)
    return map
  }, [deps])

  // Tree + deps + dicts: one fetch each on mount (deps span every campus so the
  // 负责科室 column resolves under the root-means-all view too).
  useEffect(() => {
    let alive = true
    void (async () => {
      try {
        const [tree, depRows, typeItems, statusItems, tagItems] = await Promise.all([
          orgTree(),
          listDeps(),
          dictItemsByName(DICT_DEVICE_TYPE),
          dictItemsByName(DICT_DEVICE_STATUS),
          dictItemsByName(DICT_TAG),
        ])
        if (!alive) return
        setOrgs(tree)
        setDeps(depRows)
        setDeviceTypes(typeItems)
        setDeviceStatuses(statusItems)
        setTags(tagItems)
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
    async (effective: DevicePageQuery) => {
      setListLoading(true)
      try {
        // The all-campuses view carries NO orgId key at all (absent = every campus).
        setPage(await pageDevice(effective))
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '设备列表加载失败')
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
      type: typeFilter ?? undefined,
      status: statusFilter ?? undefined,
      keyword: keyword.trim() || undefined,
    })
  }, [isCampusSelected, keyword, loadPage, pageNum, pageSize, selectedOrgId, statusFilter, typeFilter])

  const searchFromFirstPage = useCallback(() => {
    setPageNum(1)
  }, [])

  const openCreate = useCallback(() => {
    dialogKindRef.current = 'create'
    form.setFieldsValue({ ...EMPTY_FORM })
    setDialog({ kind: 'create' })
  }, [form])

  const openEdit = useCallback(
    async (row: OrgDevice) => {
      try {
        const detail = await getDevice(row.id)
        dialogKindRef.current = 'edit'
        // The detail row is a superset of the form values (id/audit fields ride along);
        // the date strings convert to Dayjs for the DatePicker patch.
        form.setFieldsValue({
          ...(detail as unknown as DeviceFormValues),
          productionDate: detail.productionDate ? dayjs(detail.productionDate) : null,
          purchaseDate: detail.purchaseDate ? dayjs(detail.purchaseDate) : null,
          acceptanceDate: detail.acceptanceDate ? dayjs(detail.acceptanceDate) : null,
          startDate: detail.startDate ? dayjs(detail.startDate) : null,
          endDate: detail.endDate ? dayjs(detail.endDate) : null,
        })
        setDialog({ kind: 'edit', device: detail })
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '设备详情加载失败')
      }
    },
    [form, messageApi],
  )

  /** OPS prefill: 自购 on ADD sets 使用期限 today → 2099-12-31. */
  const onTagChange = useCallback(
    (tag: number) => {
      if (dialogKindRef.current !== 'create') return
      if (tag === 10) {
        form.setFieldsValue({ startDate: dayjs(), endDate: dayjs('2099-12-31') })
      } else if (tag === 11) {
        form.setFieldsValue({ startDate: null, endDate: null })
      }
    },
    [form],
  )

  const handleSubmit = useCallback(async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      // validateFields has guaranteed the required surface (incl. did) - the cast
      // narrows the Dayjs-to-string converted patch to the wire type.
      const body = {
        ...values,
        productionDate: values.productionDate ? values.productionDate.format('YYYY-MM-DD') : null,
        purchaseDate: values.purchaseDate ? values.purchaseDate.format('YYYY-MM-DD') : null,
        acceptanceDate: values.acceptanceDate ? values.acceptanceDate.format('YYYY-MM-DD') : null,
        startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : null,
        endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : null,
      } as CreateDeviceBody
      if (dialog?.kind === 'edit') {
        await updateDevice(dialog.device.id, body)
        messageApi.success('修改成功')
      } else {
        await createDevice(body)
        messageApi.success('添加成功')
      }
      setDialog(null)
      await loadPage({
        pageNum,
        pageSize,
        orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
        type: typeFilter ?? undefined,
        status: statusFilter ?? undefined,
        keyword: keyword.trim() || undefined,
      })
    } catch (error) {
      if (error instanceof ApiError) {
        // The duplicate-code rejection lands on the 编号 field; the drawer stays open.
        if (error.msg.includes('编号') || error.msg.toLowerCase().includes('code')) {
          form.setFields([{ name: 'code', errors: [error.msg] }])
        } else {
          messageApi.error(error.msg)
        }
      } else if (error instanceof Error && error.name !== 'ValidateError') {
        messageApi.error(error.message || '保存失败')
      }
    } finally {
      setSaving(false)
    }
  }, [
    dialog,
    form,
    isCampusSelected,
    keyword,
    loadPage,
    messageApi,
    pageNum,
    pageSize,
    selectedOrgId,
    statusFilter,
    typeFilter,
  ])

  const handleDelete = useCallback(
    async (row: OrgDevice) => {
      try {
        await deleteDevice(row.id)
        messageApi.success('删除成功')
        await loadPage({
          pageNum,
          pageSize,
          orgId: isCampusSelected && selectedOrgId !== null ? selectedOrgId : undefined,
          type: typeFilter ?? undefined,
          status: statusFilter ?? undefined,
          keyword: keyword.trim() || undefined,
        })
      } catch (error) {
        // The room-reference guard's message (with the count) surfaces; the row stays.
        messageApi.error(error instanceof ApiError ? error.msg : '删除失败')
      }
    },
    [isCampusSelected, keyword, loadPage, messageApi, pageNum, pageSize, selectedOrgId, statusFilter, typeFilter],
  )

  const columns: TableColumnsType<OrgDevice> = [
    {
      title: '设备名称 / 编号',
      dataIndex: 'name',
      render: (_, row) => (
        <div>
          <div>{row.name}</div>
          <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
            {row.code}
          </div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      align: 'center',
      render: (status: number) => (
        <Tag>{resolveDictLabel(deviceStatuses, status)}</Tag>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 96,
      align: 'center',
      render: (type: string) => (
        <Tag color="blue">{resolveDictLabelByValue(deviceTypes, type)}</Tag>
      ),
    },
    {
      title: '归属类型',
      dataIndex: 'tag',
      width: 96,
      align: 'center',
      render: (tag: number) => <Tag>{resolveDictLabel(tags, tag)}</Tag>,
    },
    {
      title: '负责科室',
      dataIndex: 'did',
      width: 110,
      render: (did: number | null) => (did === null ? '—' : (depNameById.get(did) ?? String(did))),
    },
    {
      title: '使用期限',
      dataIndex: 'startDate',
      width: 200,
      render: (_, row) =>
        row.startDate === null ? '—' : `${row.startDate}${row.endDate ? ` 至 ${row.endDate}` : ''}`,
    },
    {
      title: '品牌 / 型号规格',
      dataIndex: 'brand',
      render: (_, row) => (
        <div>
          <div>{row.brand ?? '—'}</div>
          {(row.model || row.spec) && (
            <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
              {[row.model, row.spec].filter(Boolean).join(' · ')}
            </div>
          )}
        </div>
      ),
    },
    {
      title: '负责人 / 电话',
      dataIndex: 'responsible',
      width: 140,
      render: (_, row) =>
        row.responsible === null ? (
          '—'
        ) : (
          <div>
            <div>{row.responsible}</div>
            {row.responsiblePhone && (
              <div style={{ fontSize: token.fontSizeSM, color: token.colorTextSecondary }}>
                {row.responsiblePhone}
              </div>
            )}
          </div>
        ),
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
                searchFromFirstPage()
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
                <ConsoleAddButton label="添加设备" onClick={openCreate} />
              ) : undefined
            }
          >
            <Select
              aria-label="设备类型过滤"
              allowClear
              placeholder="设备类型"
              style={{ width: 160 }}
              value={typeFilter}
              options={deviceTypes.map((item) => ({ value: item.value!, label: item.value! }))}
              onChange={(next) => {
                setTypeFilter(next ?? null)
                searchFromFirstPage()
              }}
            />
            <Select
              aria-label="设备状态过滤"
              allowClear
              placeholder="设备状态"
              style={{ width: 160 }}
              value={statusFilter}
              options={deviceStatuses.map((item) => ({ value: item.code!, label: item.value! }))}
              onChange={(next) => {
                setStatusFilter(next ?? null)
                searchFromFirstPage()
              }}
            />
            <Input
              allowClear
              placeholder="名称 / 编号"
              style={{ width: 200 }}
              value={keywordDraft}
              onChange={(event) => setKeywordDraft(event.target.value)}
              onPressEnter={() => {
                setKeyword(keywordDraft.trim())
                searchFromFirstPage()
              }}
            />
            <ConsoleQueryButton
              onClick={() => {
                setKeyword(keywordDraft.trim())
                searchFromFirstPage()
              }}
            />
          </ConsoleToolRow>

          <ConsolePanel>
            <ConsoleTable<OrgDevice>
              rowKey={(row) => row.id}
              columns={columns}
              dataSource={page?.records ?? []}
              loading={listLoading}
              actions={{
                width: 110,
                render: (row) => (
                  <div style={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
                    <ConsoleAction
                      label="编辑设备"
                      icon="edit"
                      disabled={!canUpdate}
                      onClick={() => void openEdit(row)}
                    />
                    <Popconfirm
                      title="删除设备"
                      description={`确定删除「${row.name}」吗?`}
                      okText="确定"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => void handleDelete(row)}
                    >
                      <Button
                        type="text"
                        danger
                        aria-label="删除设备"
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
        title={dialog?.kind === 'edit' ? '编辑设备' : '添加设备'}
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
                      label="编号"
                      name="code"
                      rules={[{ required: true, message: '请输入设备编号' }]}
                    >
                      <Input placeholder="设备编号,院区内唯一" />
                    </Form.Item>
                    <Form.Item
                      label="名称"
                      name="name"
                      rules={[{ required: true, message: '请输入设备名称' }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      label="类型"
                      name="type"
                      rules={[{ required: true, message: '请选择设备类型' }]}
                    >
                      <Select
                        aria-label="设备类型选择"
                        options={deviceTypes.map((item) => ({
                          value: item.value!,
                          label: item.value!,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      label="状态"
                      name="status"
                      rules={[{ required: true, message: '请选择设备状态' }]}
                    >
                      <Select
                        aria-label="设备状态选择"
                        options={deviceStatuses.map((item) => ({
                          value: item.code!,
                          label: item.value!,
                        }))}
                      />
                    </Form.Item>
                    <Form.Item
                      label="归属类型"
                      name="tag"
                      rules={[{ required: true, message: '请选择归属类型' }]}
                    >
                      <Select
                        aria-label="归属类型选择"
                        options={tags.map((item) => ({ value: item.code!, label: item.value! }))}
                        onChange={onTagChange}
                      />
                    </Form.Item>
                    <Form.Item
                      label="所属部门"
                      name="did"
                      rules={[{ required: true, message: '请选择使用科室' }]}
                    >
                      <Select
                        aria-label="所属部门选择"
                        placeholder="请选择使用科室(设备必属科室)"
                        options={deps.map((dep) => ({ value: dep.id, label: dep.name }))}
                      />
                    </Form.Item>
                    <Form.Item label="备注" name="remark">
                      <Input.TextArea rows={2} />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'purchase',
                label: '采购信息',
                forceRender: true,
                children: (
                  <>
                    <Form.Item label="使用期限" style={{ marginBottom: 0 }}>
                      <Input.Group compact style={{ display: 'flex' }}>
                        <Form.Item name="startDate" noStyle>
                          <DatePicker style={{ width: '50%' }} placeholder="开始日期" />
                        </Form.Item>
                        <Form.Item name="endDate" noStyle>
                          <DatePicker style={{ width: '50%' }} placeholder="结束日期" />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                    <Form.Item label="采购日期" name="purchaseDate">
                      <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item label="验收日期" name="acceptanceDate">
                      <DatePicker style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item label="权属单位" name="responsible">
                      <Input />
                    </Form.Item>
                    <Form.Item label="联系电话" name="responsiblePhone">
                      <Input />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'access',
                label: '接入与采集',
                forceRender: true,
                children: (
                  <>
                    <Form.Item label="AE" name="ae">
                      <Input placeholder="DICOM AE" />
                    </Form.Item>
                    <Form.Item label="IP" name="ip">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Port" name="port">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Box 适配器" name="boxAdapter">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Box IP" name="boxIp">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Box Port" name="boxPort">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Box MAC" name="boxMac">
                      <Input />
                    </Form.Item>
                    <Form.Item label="Box 打印机" name="boxPrint">
                      <Input />
                    </Form.Item>
                    <Form.Item label="采集尺寸" style={{ marginBottom: 0 }}>
                      <Input.Group compact style={{ display: 'flex' }}>
                        <Form.Item name="captureW" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="宽 W" min={0} />
                        </Form.Item>
                        <Form.Item name="captureH" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="高 H" min={0} />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                    <Form.Item label="是否剪裁" name="prune" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item label="剪裁 上/下" style={{ marginBottom: 0 }}>
                      <Input.Group compact style={{ display: 'flex' }}>
                        <Form.Item name="pruneU" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="上 U" min={0} />
                        </Form.Item>
                        <Form.Item name="pruneD" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="下 D" min={0} />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                    <Form.Item label="剪裁 左/右" style={{ marginBottom: 0 }}>
                      <Input.Group compact style={{ display: 'flex' }}>
                        <Form.Item name="pruneL" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="左 L" min={0} />
                        </Form.Item>
                        <Form.Item name="pruneR" noStyle>
                          <InputNumber style={{ width: '50%' }} placeholder="右 R" min={0} />
                        </Form.Item>
                      </Input.Group>
                    </Form.Item>
                  </>
                ),
              },
            ]}
          />
        </Form>
      </Drawer>
    </div>
  )
}

/** Depth-first lookup of one org node by id (the deps page shape). */
function findNode(nodes: OrgNode[], id: number): OrgNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const hit = node.children ? findNode(node.children, id) : null
    if (hit) return hit
  }
  return null
}
