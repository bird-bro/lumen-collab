import {
  Alert,
  Avatar,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Switch,
  Tag,
  message,
  theme,
} from 'antd'
import {
  BankOutlined,
  EnvironmentOutlined,
  IdcardOutlined,
  MailOutlined,
  PhoneOutlined,
  TagOutlined,
  UserOutlined,
} from '@ant-design/icons'
import type { TableColumnsType } from 'antd'
import { Fragment, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { createOrg, deleteOrg, orgTree, setOrgStatus, updateOrg } from '@/api/orgs'
import { listCityDicts } from '@/api/dicts'
import { ApiError } from '@/api/client'
import type { OrgNode } from '@/api/types'
import { usePerm } from '@/utils/perm'
import { RegionSelects } from './region-selects'
import { LogoUploadField } from './logo-upload'
import {
  ConsoleAction,
  ConsoleAddButton,
  ConsolePage,
  ConsolePanel,
  ConsoleQueryButton,
  ConsoleTable,
  ConsoleToolRow,
  ConsoleWorkArea,
  readForm,
} from '../console-shell'

/**
 * 机构管理 rewrite (org-management-redesign-web tasks 2.1-2.6; spec delta
 * `web-system-console` § Hospital Profile and Campus Table Management).
 *
 * Layout (design D1): the dictionary-like work area - LEFT the hospital
 * profile card with its edit action, RIGHT the campus table with keyword/
 * status filtering and the create/edit/default/delete actions. The generic
 * multi-root tree and its 「新建根机构」 affordance are gone (D2: root
 * creation is not a normal action; the backend rejects a second live root).
 * There is no parent selector and no type control anywhere (D2/D3): the
 * dialogs do not model a hierarchy control: a campus is owned by the single
 * root hospital, and type stays server-derived.
 *
 * Option B first-run (D7): while the seeded root is still the bootstrap
 * placeholder (`未命名医院` + code `HOSPITAL`), a completion hint shows in the
 * hospital aside; administrators complete the real profile through the normal
 * hospital edit form - no initialization-specific control is invented.
 *
 * Data flow: one orgTree() on mount; every successful mutation re-queries and
 * the card/table reflect the refreshed tree. A-class rejections surface the
 * backend msg and leave the dialog open, the table untouched (delta:
 * Organization rejection surfaces). No mock layer - the typed @/api/orgs
 * client goes through the Vite proxy to the real backend. The campus-table
 * 地址 cell merges the resolved 地区 label and the detailed address as two
 * stacked lines (user direction 2026-11-06; shared resolver with the card).
 *
 * Permission gating (D6): the page's documented `system:org:list` gates every
 * mutation affordance - 编辑医院, 新建院区, and the row actions; without it the
 * page is read-only.
 */

/** The campus-table action glyphs (prototype `.act` verbs). */
const ACTION_COLUMN_WIDTH = 150

/** Option B bootstrap detection: V16 normalizes the seed to exactly this pair. */
function isFirstRunBootstrap(hospital: OrgNode | null): boolean {
  return hospital !== null && hospital.code === 'HOSPITAL' && hospital.name === '未命名医院'
}

/** One compact profile fact; missing facts are hidden instead of leaving noise in the aside. */
function ProfileFact({
  icon,
  label,
  value,
  isLast = false,
}: {
  icon: ReactNode
  label: string
  value: string
  isLast?: boolean
}) {
  const { token } = theme.useToken()

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '20px 72px minmax(0, 1fr)',
        alignItems: 'flex-start',
        gap: 8,
        padding: '9px 0',
        borderBottom: isLast ? 'none' : `1px dashed ${token.colorBorderSecondary}`,
      }}
    >
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: 22,
          color: token.colorPrimary,
          opacity: 0.72,
        }}
      >
        {icon}
      </span>
      <span
        style={{
          fontSize: token.fontSizeSM,
          lineHeight: '22px',
          color: token.colorTextSecondary,
        }}
      >
        {label}
      </span>
      <span
        style={{
          lineHeight: '22px',
          overflowWrap: 'anywhere',
          whiteSpace: 'pre-line',
          color: token.colorText,
        }}
      >
        {value}
      </span>
    </div>
  )
}

/**
 * Resolves stored division codes into a compact 地区 display label ("省 市 区")
 * by chaining the same read-only divisions source as the profile cascade
 * (level-1 provinces, then each node's children by its parent dict id).
 * Absent codes - and codes unknown to the dict - drop out of the join; no
 * province code at all resolves to null.
 */
async function resolveRegionLabel(codes: {
  provinceCode: string | null
  cityCode: string | null
  districtCode: string | null
}): Promise<string | null> {
  const { provinceCode, cityCode, districtCode } = codes
  if (!provinceCode) return null

  let label: string | null = null
  const provinces = (await listCityDicts({ level: 1 })) ?? []
  const province = provinces.find((row) => row.code === provinceCode)
  if (province) label = province.name

  if (cityCode) {
    const cityRows = province ? ((await listCityDicts({ upId: province.id })) ?? []) : []
    const city = cityRows.find((row) => row.code === cityCode)
    if (city) label = [label, city.name].filter(Boolean).join(' ')

    if (districtCode) {
      const districtRows = city ? ((await listCityDicts({ upId: city.id })) ?? []) : []
      const district = districtRows.find((row) => row.code === districtCode)
      if (district) label = [label, district.name].filter(Boolean).join(' ')
    }
  }

  return label
}

/** Resolves the hospital card's 地区 fact; resets when the stored codes clear. */
function useRegionLabel(hospital: OrgNode): string | null {
  const [label, setLabel] = useState<string | null>(null)
  const { provinceCode, cityCode, districtCode } = hospital

  useEffect(() => {
    if (!provinceCode) {
      setLabel(null)
      return
    }
    let cancelled = false
    void resolveRegionLabel({ provinceCode, cityCode, districtCode }).then((next) => {
      if (!cancelled) setLabel(next)
    })
    return () => {
      cancelled = true
    }
  }, [provinceCode, cityCode, districtCode])

  return label
}

/**
 * The campus-table 地址 cell (user direction 2026-11-06: merge the region and
 * the address into one column, two lines - the same pairing the hospital card
 * shows). Line 1 the detailed address, line 2 the resolved 地区 label
 * (meta style); either line drops out when absent and a bare cell renders —.
 */
function RegionAddressCell({ org }: { org: OrgNode }) {
  const { token } = theme.useToken()
  const [regionLabel, setRegionLabel] = useState<string | null>(null)
  const { provinceCode, cityCode, districtCode, address } = org

  useEffect(() => {
    if (!provinceCode) {
      setRegionLabel(null)
      return
    }
    let cancelled = false
    void resolveRegionLabel({ provinceCode, cityCode, districtCode }).then((next) => {
      if (!cancelled) setRegionLabel(next)
    })
    return () => {
      cancelled = true
    }
  }, [provinceCode, cityCode, districtCode])

  if (!regionLabel && !address) {
    return <span style={{ color: token.colorTextTertiary }}>—</span>
  }

  const ellipsis = { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } as const
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
      {address ? (
        <span style={{ ...ellipsis, color: token.colorTextSecondary }}>{address}</span>
      ) : null}
      {regionLabel ? (
        <span
          style={{
            ...ellipsis,
            fontSize: token.fontSizeSM,
            lineHeight: '18px',
            color: token.colorTextTertiary,
          }}
        >
          {regionLabel}
        </span>
      ) : null}
    </div>
  )
}

/** The refined root-organization identity card for the fixed console aside. */
function HospitalProfileCard({
  hospital,
  canManage,
  showFirstRunHint,
  onEdit,
}: {
  hospital: OrgNode
  canManage: boolean
  showFirstRunHint: boolean
  onEdit: () => void
}) {
  const { token } = theme.useToken()
  const regionLabel = useRegionLabel(hospital)

  const profileFacts = [
    {
      key: 'identity',
      icon: <IdcardOutlined />,
      label: 'ID',
      value: `${hospital.id}`,
    },
    ...(hospital.shortName
      ? [{ key: 'shortName', icon: <TagOutlined />, label: '简称', value: hospital.shortName }]
      : []),
    ...(hospital.contact
      ? [{ key: 'contact', icon: <UserOutlined />, label: '联系人', value: hospital.contact }]
      : []),
    ...(hospital.phone
      ? [{ key: 'phone', icon: <PhoneOutlined />, label: '电话', value: hospital.phone }]
      : []),
    ...(hospital.email
      ? [{ key: 'email', icon: <MailOutlined />, label: '邮箱', value: hospital.email }]
      : []),
    ...(hospital.address || regionLabel
      ? [{
          key: 'address',
          icon: <EnvironmentOutlined />,
          label: '地址',
          value: [regionLabel, hospital.address].filter(Boolean).join('\n'),
        }]
      : []),
  ]

  return (
    <div style={{ display: 'flex', flex: 1, minHeight: 0, flexDirection: 'column' }}>
      <div
        style={{
          flex: '0 0 auto',
          padding: '16px 16px 14px',
          background: `linear-gradient(155deg, ${token.colorPrimaryBg}, ${token.colorBgContainer})`,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
          <Avatar
            size={42}
            shape="square"
            src={hospital.logoUrl || undefined}
            icon={<BankOutlined />}
            style={{
              flex: '0 0 auto',
              color: token.colorPrimary,
              background: token.colorBgContainer,
              border: `1px solid ${token.colorPrimaryBorder}`,
              boxShadow: `0 4px 12px ${token.colorPrimaryBg}`,
            }}
          />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div
              style={{
                fontSize: token.fontSizeLG,
                lineHeight: 1.3,
                fontWeight: 600,
                color: token.colorText,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
              title={hospital.name}
            >
              {hospital.name}
            </div>
          </div>
        </div>
      </div>

      <div style={{ flex: 1, minHeight: 0, padding: '10px 16px 16px', overflowY: 'auto' }}>
        {profileFacts.map((fact, index) => (
          <ProfileFact
            key={fact.key}
            icon={fact.icon}
            label={fact.label}
            value={fact.value}
            isLast={index === profileFacts.length - 1}
          />
        ))}
      </div>

      <div style={{ flex: '0 0 auto', padding: '12px 16px 16px' }}>
        {canManage ? (
          <Button block style={{ marginTop: 12 }} aria-label="编辑" onClick={onEdit}>
            编辑
          </Button>
        ) : null}
        {showFirstRunHint ? (
          <Alert
            style={{
              marginTop: canManage ? 12 : 0,
              fontSize: 12,
              lineHeight: '18px',
            }}
            type="info"
            showIcon
            message="首次部署:请编辑完善医院信息。"
          />
        ) : null}
      </div>
    </div>
  )
}

type OrgDialog =
  | { kind: 'create-campus'; hospital: OrgNode }
  | { kind: 'edit-campus'; campus: OrgNode }
  | { kind: 'edit-hospital'; hospital: OrgNode }

/**
 * The shared profile fields (delta: Campus Profile Management bullet list).
 * The region triple is NOT here - divisions are picked, not typed: the 地区
 * cascade (see RegionSelects) renders before 地址 and submits the same
 * provinceCode/cityCode/districtCode codes.
 */
const PROFILE_FIELDS = [
  { name: 'shortName', label: '机构简称', placeholder: '如 总院 / 东院' },
  { name: 'address', label: '地址', placeholder: '详细地址' },
  { name: 'contact', label: '联系人', placeholder: '联系人姓名' },
  { name: 'phone', label: '联系电话', placeholder: '如 021-60000000' },
  { name: 'email', label: '邮箱', placeholder: 'name@example.org' },
] as const

interface OrgFormValues {
  code: string
  name: string
  shortName?: string | null
  status: number
  provinceCode?: string | null
  cityCode?: string | null
  districtCode?: string | null
  address?: string | null
  contact?: string | null
  phone?: string | null
  email?: string | null
  logoUrl?: string | null
  sort?: number
}

export default function OrgsPage() {
  const hasPerm = usePerm()
  const canManage = hasPerm('system:org:list')
  // button-level gating (button-perm-enforcement-web): campus affordances answer to
  // their own perms strings (the V15 seeded F nodes pinned server-side).
  const canCreateCampus = canManage && hasPerm('system:org:create')
  const canUpdateOrg = canManage && hasPerm('system:org:update')
  const canDeleteCampus = canManage && hasPerm('system:org:delete')
  const { token } = theme.useToken()

  const [nodes, setNodes] = useState<OrgNode[]>([])
  const [loading, setLoading] = useState(true)
  const [keywordDraft, setKeywordDraft] = useState('')
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | null>(null)
  const [dialog, setDialog] = useState<OrgDialog | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm<OrgFormValues>()
  const [messageApi, messageHolder] = message.useMessage()

  const reload = useCallback(async (name: string = keyword) => {
    setLoading(true)
    try {
      setNodes(await orgTree({ name }))
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.msg : '机构树加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, messageApi])

  useEffect(() => {
    void reload()
  }, [reload])

  const hospital = useMemo(() => nodes.find((node) => node.upId === 0) ?? null, [nodes])
  const campuses = useMemo(() => hospital?.children ?? [], [hospital])

  const visibleCampuses = useMemo(() => {
    return campuses.filter((campus) => statusFilter === null || campus.status === statusFilter)
  }, [campuses, statusFilter])

  const runQuery = () => {
    // The keyword state drives the mount/query effect; issuing reload here too
    // would start the identical backend read twice (user report 2026-11-06).
    setKeyword(keywordDraft)
  }

  const closeDialog = useCallback(() => {
    setDialog(null)
    form.resetFields()
  }, [form])

  const openDialog = useCallback(
    (next: OrgDialog) => {
      const current: Pick<
        OrgNode,
        | 'code'
        | 'name'
        | 'shortName'
        | 'status'
        | 'provinceCode'
        | 'cityCode'
        | 'districtCode'
        | 'address'
        | 'contact'
        | 'phone'
        | 'email'
        | 'logoUrl'
        | 'sort'
      > =
        next.kind === 'create-campus'
          ? {
              code: '',
              name: '',
              shortName: null,
              status: 1,
              provinceCode: null,
              cityCode: null,
              districtCode: null,
              address: null,
              contact: null,
              phone: null,
              email: null,
              logoUrl: null,
              sort: 0,
            }
          : next.kind === 'edit-campus'
            ? next.campus
            : next.hospital
      form.setFieldsValue({
        code: current.code,
        name: current.name,
        shortName: current.shortName,
        status: current.status,
        // null → undefined: the region Selects treat undefined as "no choice"
        // (placeholder) - a Select must never hold the model's null sentinel.
        provinceCode: current.provinceCode ?? undefined,
        cityCode: current.cityCode ?? undefined,
        districtCode: current.districtCode ?? undefined,
        address: current.address,
        contact: current.contact,
        phone: current.phone,
        email: current.email,
        logoUrl: current.logoUrl,
        sort: current.sort ?? 0,
      })
      setDialog(next)
    },
    [form],
  )

  const submitDialog = useCallback(async () => {
    if (!dialog || hospital === null) return
    // A rejected validateFields() must never escape the drawer footer submit handler.
    const values = await readForm(form)
    if (!values) return
    // Cleared region selects arrive as undefined, but the backend partial
    // update skips absent/null fields (MyBatis-Plus NOT-NULL strategy) - the
    // empty string is the wire form of "clear this column" (Input-era parity).
    const withRegionClears = {
      ...values,
      provinceCode: values.provinceCode ?? '',
      cityCode: values.cityCode ?? '',
      districtCode: values.districtCode ?? '',
    }
    setSaving(true)
    try {
      if (dialog.kind === 'create-campus') {
        // Absent keys (untouched selects) stay absent - create maps them to
        // NULL columns; no clear semantics exist on insert.
        await createOrg({ upId: dialog.hospital.id, ...values })
      } else if (dialog.kind === 'edit-campus') {
        await updateOrg(dialog.campus.id, { ...withRegionClears, sort: values.sort ?? undefined })
      } else {
        // The hospital form never carries the default-campus flag - that is
        // campus-only semantics (delta: Campus Profile Management).
        await updateOrg(dialog.hospital.id, { ...withRegionClears, sort: values.sort ?? undefined })
      }
      closeDialog()
      await reload(keyword)
    } catch (error) {
      // A-class failures: the backend msg lands on the floating message and
      // the dialog STAYS OPEN (delta: Organization rejection surfaces).
      messageApi.error(error instanceof ApiError ? error.msg : '保存机构失败')
    } finally {
      setSaving(false)
    }
  }, [closeDialog, dialog, form, hospital, keyword, messageApi, reload])

  const removeCampus = useCallback(
    async (campus: OrgNode) => {
      try {
        await deleteOrg(campus.id)
        await reload()
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '删除院区失败')
      }
    },
    [messageApi, reload],
  )

  // The campus status switch mirrors users: optimistic flip, server rollback,
  // then a reload re-syncs every cell from the tree response.
  const toggleOrgStatus = useCallback(
    async (campus: OrgNode, next: 0 | 1) => {
      setNodes((prev) =>
        prev.map((row) => (row.id === campus.id ? { ...row, status: next } : row)),
      )
      try {
        await setOrgStatus(campus.id, next)
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.msg : '启用状态更新失败')
      }
      await reload()
    },
    [messageApi, reload],
  )

  const columns: TableColumnsType<OrgNode> = [
    {
      title: '院区 / 代码',
      dataIndex: 'name',
      width: 240,
      render: (_: unknown, row: OrgNode) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
            <span
              style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {row.name}
            </span>
            {row.isDefault === 1 ? (
              <Tag color="blue" bordered={false} style={{ marginInlineEnd: 0, flex: '0 0 auto' }}>
                默认院区
              </Tag>
            ) : null}
          </div>
          <span
            style={{
              fontSize: token.fontSizeSM,
              lineHeight: '18px',
              color: token.colorTextTertiary,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {row.code}
          </span>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      align: 'center',
      render: (status: number, row: OrgNode) => (
        <Switch
          checked={status === 1}
          disabled={!canUpdateOrg}
          aria-label={`启用 ${row.name}`}
          checkedChildren="✓"
          unCheckedChildren="✕"
          onChange={(checked) => void toggleOrgStatus(row, checked ? 1 : 0)}
        />
      ),
    },
    {
      // Merged cell (user direction 2026-11-06): line 1 the resolved 地区
      // label, line 2 the detailed address - the pairing the hospital card shows.
      title: '地址',
      dataIndex: 'address',
      width: 220,
      ellipsis: true,
      render: (_: unknown, row: OrgNode) => <RegionAddressCell org={row} />,
    },
    {
      // The org API currently exposes the audit creator as a numeric UID; the
      // stacked cell keeps that provenance visible without inventing a join.
      title: '创建人 / 创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (_: unknown, row: OrgNode) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {row.createBy === null ? '—' : `UID ${row.createBy}`}
          </span>
          <span
            style={{
              fontSize: token.fontSizeSM,
              lineHeight: '18px',
              color: token.colorTextTertiary,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {row.createTime}
          </span>
        </div>
      ),
    },
  ]

  const dialogTitle =
    dialog?.kind === 'create-campus'
      ? '新建院区'
      : dialog?.kind === 'edit-campus'
        ? '编辑院区'
        : '编辑医院'

  return (
    <div style={{ height: '100%' }}>
      {messageHolder}
      <ConsoleWorkArea
        aside={
          <ConsolePanel>
            {hospital ? (
              <HospitalProfileCard
                hospital={hospital}
                canManage={canUpdateOrg}
                showFirstRunHint={isFirstRunBootstrap(hospital)}
                onEdit={() => openDialog({ kind: 'edit-hospital', hospital })}
              />
            ) : null}
          </ConsolePanel>
        }
      >
        <ConsolePage>
          <ConsoleToolRow
            trailing={
              canCreateCampus && hospital ? (
                <ConsoleAddButton
                  label="新建院区"
                  onClick={() => openDialog({ kind: 'create-campus', hospital })}
                />
              ) : undefined
            }
          >
            <Input
              allowClear
              style={{ width: 200 }}
              placeholder="院区名称"
              aria-label="院区名称"
              value={keywordDraft}
              onChange={(event) => setKeywordDraft(event.target.value)}
              onPressEnter={runQuery}
            />
            <Select<number | null>
              allowClear
              style={{ width: 120 }}
              placeholder="状态"
              aria-label="状态过滤"
              value={statusFilter}
              onChange={(next) => setStatusFilter(next ?? null)}
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
            <ConsoleQueryButton onClick={runQuery} />
          </ConsoleToolRow>

          <ConsoleTable<OrgNode>
            rowKey={(campus) => campus.id}
            columns={columns}
            dataSource={visibleCampuses}
            loading={loading}
            actions={
              canManage
                ? {
                    width: ACTION_COLUMN_WIDTH,
                    render: (campus) => [
                      canUpdateOrg ? (
                        <ConsoleAction
                          key="edit"
                          label="编辑院区"
                          onClick={() => openDialog({ kind: 'edit-campus', campus })}
                        />
                      ) : null,
                      canDeleteCampus ? (
                        <Popconfirm
                          key="remove"
                          title="删除院区"
                          description={`确定删除「${campus.name}」吗?`}
                          okText="删除"
                          cancelText="取消"
                          okButtonProps={{ danger: true }}
                          onConfirm={() => void removeCampus(campus)}
                        >
                          <ConsoleAction label="删除院区" icon="remove" danger onClick={() => undefined} />
                        </Popconfirm>
                      ) : null,
                    ],
                  }
                : undefined
            }
            onRefresh={() => void reload()}
          />
        </ConsolePage>
      </ConsoleWorkArea>

      <Drawer
        open={!!dialog}
        title={dialogTitle}
        width={520}
        onClose={closeDialog}
        closable={{ placement: 'end' }}
        maskClosable={false}
        destroyOnHidden
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeDialog}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void submitDialog()}>
              保存
            </Button>
          </div>
        }
      >
        <Form<OrgFormValues> form={form} layout="vertical" requiredMark disabled={saving}>
          <Form.Item
            name="code"
            label="机构代码"
            rules={[{ required: true, message: '请输入机构代码' }]}
          >
            <Input placeholder="如 HOSP-001 / CAMPUS-1" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="name"
            label="机构名称"
            rules={[{ required: true, message: '请输入机构名称' }]}
          >
            <Input placeholder="如 总院区" autoComplete="off" />
          </Form.Item>
          {PROFILE_FIELDS.map((field) => (
            <Fragment key={field.name}>
              {field.name === 'address' ? (
                // 地区 cascade (OPS org-form parity): one label, three inline
                // selects fed by the real divisions dict - see RegionSelects.
                <Form.Item label="地区">
                  <RegionSelects />
                </Form.Item>
              ) : null}
              <Form.Item name={field.name} label={field.label}>
                <Input placeholder={field.placeholder} autoComplete="off" />
              </Form.Item>
            </Fragment>
          ))}
          {/* Logo upload (org-logo-upload-web; spec `web-system-console` § Organization
              Logo Upload Control) - replaces the former `Logo 地址` URL input. The named
              Form.Item makes LogoUploadField a controlled form control (injected
              value/onChange): backfill via openDialog and the submit body contract are
              unchanged - `validateFields` keeps carrying `logoUrl`. */}
          <Form.Item name="logoUrl" label="Logo" extra="png/jpg/jpeg/webp ≤2MB">
            <LogoUploadField />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select
              aria-label="状态"
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="sort" label="排序" initialValue={0}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  )
}
