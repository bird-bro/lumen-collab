/**
 * Shared mock fixtures for the dev mock server (batch 3, web-foundation-auth-console
 * task 3.1). Seed values mirror the backend seed migrations verbatim:
 *
 * - `lumen-sev/lumen-system/src/main/resources/db/migration/V2__seed.sql`
 *   (admin / hospital root / three departments / role / menus / grants)
 * - `lumen-sev/lumen-system/src/main/resources/db/migration/V3__system_data.sql`
 *   (common_status dict catalog + details; log tables seed nothing)
 *
 * Envelope/error behaviors mirror the copied framework classes:
 * - `common/core/R.java` (envelope fields + `yyyy-MM-dd hh:mm:ss.SSS` timestamp,
 *   including its 12-hour `hh` quirk - reproduced verbatim)
 * - `framework/global/GlobalResponseAdvice.java` (body returns -> code "200"/"OK")
 * - `framework/global/GlobalExceptionHandler.java` + `common/exception/BusinessException.java`
 *   (A-class codes and the exact `枚举msg：detail` message composition)
 * - `framework/security/handle/AuthenticationEntryPointImpl.java` (the "203" envelope)
 *
 * IMPORTANT (plugin convention, v2.7.1): each `*.mock.ts` file is bundled INDEPENDENTLY
 * with its relative imports inlined, so module-level state is NOT shared between mock
 * files. Cross-file session state therefore lives on `globalThis` via `state()` below -
 * still plain dev-session memory, never persisted.
 *
 * Fields marked `mock-only` have no backend seed/source of truth (JWT shape, fixed audit
 * timestamps, the six extra console menu nodes, fabricated log rows).
 */

// ---------------------------------------------------------------------------
// Envelope helpers (mirror common R + GlobalResponseAdvice)
// ---------------------------------------------------------------------------

/** Mirrors R's `new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")` - the 12-hour `hh` is the backend's own quirk, kept verbatim. */
export function nowStamp(): string {
  const d = new Date()
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  const h12 = d.getHours() % 12 === 0 ? 12 : d.getHours() % 12
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(h12)}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
  )
}

/** Success with payload - mirrors GlobalResponseAdvice's R.ofSuccess(data) (code "200", msg "OK"). */
export function ok(data: unknown): Record<string, unknown> {
  return { timestamp: nowStamp(), code: '200', msg: 'OK', data }
}

/**
 * Entity audit-field timestamp - mirrors the entities' `@JsonFormat(pattern =
 * "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")` serialization (EntityBase).
 */
export function rowStamp(): string {
  const d = new Date()
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

/** Failure - mirrors R.ofFail(code, msg): data null, BusinessException msg text verbatim. */
export function fail(code: string, msg: string): Record<string, unknown> {
  return { timestamp: nowStamp(), code, msg, data: null }
}

// ---------------------------------------------------------------------------
// Token guard (mirror AuthenticationEntryPointImpl - HTTP 200, body code "203")
// ---------------------------------------------------------------------------

/** The 203 envelope body - AuthenticationEntryPointImpl: `请求访问：<uri>，认证失败，无法访问系统资源`. */
export function unauthorized(request: { url?: string }): Record<string, unknown> {
  const uri = (request.url ?? '').split('?')[0]
  return fail('203', `请求访问：${uri}，认证失败，无法访问系统资源`)
}

/**
 * Token guard mirroring the copied security chain (`anyRequest().authenticated()`):
 * every endpoint except POST /login requires `Authorization: Bearer <token>`.
 * Returns the 203 envelope to respond with, or null when the request may proceed.
 * Any Bearer value is accepted (mock-only - the mock does not verify JWT signatures).
 */
export function guard(request: { headers?: Record<string, string>; url?: string }):
  | Record<string, unknown>
  | null {
  const auth = request.headers?.authorization
  if (typeof auth === 'string' && auth.startsWith('Bearer ')) return null
  return unauthorized(request)
}

/** mock-only token shape - the seed knows no JWT; `TokenService.createToken` issues the real one. */
export function mockJwt(): string {
  return `mock-jwt-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`
}

// ---------------------------------------------------------------------------
// Cross-file session state (globalThis - see the plugin-convention note above)
// ---------------------------------------------------------------------------

const STATE_KEY = '__lumen_mock_state__'

/** Dev-session singleton bucket shared by every mock file bundle in this dev-server process. */
export function state<T>(key: string, initial: () => T): T {
  const holder = globalThis as Record<string, unknown>
  if (!holder[STATE_KEY] || typeof holder[STATE_KEY] !== 'object') holder[STATE_KEY] = {}
  const bucket = holder[STATE_KEY] as Record<string, unknown>
  if (!bucket[key]) bucket[key] = initial()
  return bucket[key] as T
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

// ---------------------------------------------------------------------------
// Seed truth (V2__seed.sql / V3__system_data.sql)
// ---------------------------------------------------------------------------

/** Fixed audit timestamps - mock-only (the seed rows fall to the DDL CURRENT_TIMESTAMP default). */
export const SEED_STAMP = '2026-11-04 09:00:00'

// --- users (V2: user_info row 1; password stored raw for the mock's BCrypt stand-in) ---
export interface MockUser {
  uid: number
  username: string
  /** mock-only: raw credential for the mock's login compare - the real table stores a BCrypt hash that never serializes. */
  password: string
  name: string
  enable: number
  mustChangePassword: number
  createTime: string
}

export function seedUsers(): MockUser[] {
  return clone<MockUser[]>([
    {
      uid: 1,
      username: 'admin',
      password: 'admin123', // V2 seed credential (BCrypt hash verified backend-side)
      name: '系统管理员',
      enable: 1,
      mustChangePassword: 1,
      createTime: SEED_STAMP,
    },
  ])
}

// --- roles (V2: role_info row 1) ---
export interface MockRole {
  id: number
  name: string
  type: number
  remark: string | null
  createBy: number | null
  createTime: string
  updateBy: number | null
  updateTime: string
}

export function seedRoles(): MockRole[] {
  return clone<MockRole[]>([
    {
      id: 1,
      name: '系统管理员',
      type: 10,
      remark: 'seed default administrator role',
      createBy: null,
      createTime: SEED_STAMP,
      updateBy: null,
      updateTime: SEED_STAMP,
    },
  ])
}

/** user_role bindings (V2: user 1 -> role 1). */
export function seedUserRoles(): { userId: number; roleId: number }[] {
  return clone([{ userId: 1, roleId: 1 }])
}

/**
 * Live role ids for cross-file FK checks (roles.mock.ts maintains it on create/delete;
 * users.mock.ts validates create-request roleIds against it). Seeded with the V2
 * administrator role (id 1).
 */
export function roleIds(): number[] {
  return state('roleIds', () => [1])
}

// --- menus (V2 rows 1-6 + the brief's six extra console nodes 7-12, mock-only) ---
export interface MockMenuInfo {
  id: number
  upId: number
  type: 'M' | 'C' | 'F'
  title: string
  perms: string | null
  path: string | null
  component: string | null
  icon: string | null
  sort: number
  visible: number
}

export const SEED_MENUS: MockMenuInfo[] = [
  // V2 seed rows 1-6 (id/upId/type/title/perms verbatim; department paths follow the
  // brief's router-aligned placeholders - V2's /us /endo become /ultrasound /endoscopy,
  // a recorded mock-only deviation; org_dep codes stay seed-exact).
  { id: 1, upId: 0, type: 'M', title: '系统管理', perms: null, path: '/system', component: null, icon: 'setting', sort: 1, visible: 1 },
  { id: 2, upId: 1, type: 'C', title: '用户管理', perms: 'system:user:list', path: 'user', component: 'system/user/index', icon: null, sort: 1, visible: 1 },
  { id: 3, upId: 0, type: 'C', title: '放射科', perms: null, path: '/radiology', component: 'radiology/index', icon: null, sort: 2, visible: 1 },
  { id: 4, upId: 0, type: 'C', title: '超声科', perms: null, path: '/ultrasound', component: 'ultrasound/index', icon: null, sort: 3, visible: 1 },
  { id: 5, upId: 0, type: 'C', title: '内镜科', perms: null, path: '/endoscopy', component: 'endoscopy/index', icon: null, sort: 4, visible: 1 },
  { id: 6, upId: 3, type: 'F', title: '报告审核', perms: 'radiology:report:audit', path: null, component: null, icon: null, sort: 1, visible: 1 },
  // Brief-mandated console pages under the system directory (mock-only - not in V2).
  { id: 7, upId: 1, type: 'C', title: '机构管理', perms: 'system:org:list', path: 'org', component: 'system/org/index', icon: null, sort: 2, visible: 1 },
  { id: 8, upId: 1, type: 'C', title: '科室管理', perms: 'system:dep:list', path: 'dep', component: 'system/dep/index', icon: null, sort: 3, visible: 1 },
  { id: 9, upId: 1, type: 'C', title: '角色管理', perms: 'system:role:list', path: 'role', component: 'system/role/index', icon: null, sort: 4, visible: 1 },
  { id: 10, upId: 1, type: 'C', title: '菜单管理', perms: 'system:menu:list', path: 'menu', component: 'system/menu/index', icon: null, sort: 5, visible: 1 },
  { id: 11, upId: 1, type: 'C', title: '字典管理', perms: 'system:dict:list', path: 'dict', component: 'system/dict/index', icon: null, sort: 6, visible: 1 },
  { id: 12, upId: 1, type: 'C', title: '日志查询', perms: 'system:log:list', path: 'log', component: 'system/log/index', icon: null, sort: 7, visible: 1 },
]

// --- role_menu grants (V2 grants menus 1-6 to role 1; the mock extends the grant to
// the six extra console nodes so the seeded admin owns every mock menu - mock-only) ---
export function seedRoleMenus(): Record<number, number[]> {
  return clone({ 1: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] })
}

// --- orgs (V2: hospital root id 1) ---
export interface MockOrg {
  id: number
  upId: number
  level: number
  type: number | null
  name: string
  createBy: number | null
  createTime: string
  updateBy: number | null
  updateTime: string
}

export function seedOrgs(): MockOrg[] {
  return clone<MockOrg[]>([
    { id: 1, upId: 0, level: 1, type: 0, name: '示例医院', createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
  ])
}

/**
 * Live org ids for cross-file FK checks (orgs.mock.ts maintains it on create/delete;
 * org-deps.mock.ts validates against it). Seeded with the V2 hospital root (id 1).
 */
export function orgIds(): number[] {
  return state('orgIds', () => [1])
}

// --- departments (V2: org_dep rows 1-3; codes route DICOM/HL7 backend-side) ---
export interface MockDep {
  id: number
  orgId: number
  code: string
  name: string
  createBy: number | null
  createTime: string
  updateBy: number | null
  updateTime: string
}

export function seedDeps(): MockDep[] {
  return clone<MockDep[]>([
    { id: 1, orgId: 1, code: 'radiology', name: '放射科', createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
    { id: 2, orgId: 1, code: 'us', name: '超声科', createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
    { id: 3, orgId: 1, code: 'endo', name: '内镜科', createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
  ])
}

// --- dict catalog (V3: common_status) ---
export interface MockDictCatal {
  id: number
  enable: number
  code: string
  name: string
  sort: number
  remark: string | null
  cls: number | null
  initial: string | null
  createBy: number | null
  createTime: string
  updateBy: number | null
  updateTime: string
}

export function seedDictCatal(): MockDictCatal[] {
  return clone<MockDictCatal[]>([
    { id: 1, enable: 1, code: 'common_status', name: '通用状态', sort: 0, remark: null, cls: null, initial: null, createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
  ])
}

// --- dict details (V3: 启用/停用 pair under common_status) ---
export interface MockDictDetail {
  id: number
  enable: number
  caId: number
  caCode: string
  code: number | null
  value: string | null
  label: string
  color: string | null
  css: string | null
  sort: number
  back1: string | null
  back2: string | null
  remark: string | null
  initial: string | null
  createBy: number | null
  createTime: string
  updateBy: number | null
  updateTime: string
}

export function seedDictDetails(): MockDictDetail[] {
  return clone<MockDictDetail[]>([
    { id: 1, enable: 1, caId: 1, caCode: 'common_status', code: 1, value: '1', label: '启用', color: null, css: null, sort: 1, back1: null, back2: null, remark: null, initial: null, createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
    { id: 2, enable: 1, caId: 1, caCode: 'common_status', code: 0, value: '0', label: '停用', color: null, css: null, sort: 2, back1: null, back2: null, remark: null, initial: null, createBy: null, createTime: SEED_STAMP, updateBy: null, updateTime: SEED_STAMP },
  ])
}

// --- operation logs (mock-only - V3 seeds nothing into log_info; fabricated rows for
// filter/paging demos carry the LogInfoEntity JSON field set) ---
export interface MockLog {
  id: number
  dateYm: number
  title: string
  descr: string
  type: string
  uid: number
  account: string
  actionTime: string
  spent: number
  method: string
  name: string
  pack: string
  url: string
  session: string | null
  mobile: boolean
  ip: string
  os: string
  browser: string
  engine: string
  record: number
  param: string
  result: string
  exception: string | null
  app: string
  createTime: string
}

export function seedLogs(): MockLog[] {
  return clone<MockLog[]>([
    { id: 3, dateYm: 202611, title: '用户管理', descr: '重置用户密码', type: '重置密码', uid: 1, account: 'admin', actionTime: '2026-11-03 16:20:11', spent: 34, method: 'PUT', name: 'resetPassword', pack: 'org.birdbro.lumen.system.controller.UserInfoController', url: '/api/v1/system/users/2/password', session: null, mobile: false, ip: '127.0.0.1', os: 'Mac OS X', browser: 'Chrome', engine: 'Blink', record: 0, param: '{"newPassword":"******"}', result: 'true', exception: null, app: 'lumen-system', createTime: '2026-11-03 16:20:11' },
    { id: 2, dateYm: 202611, title: '角色管理', descr: '角色-菜单全量分配', type: '授权', uid: 1, account: 'admin', actionTime: '2026-11-02 10:05:47', spent: 51, method: 'PUT', name: 'assignMenus', pack: 'org.birdbro.lumen.system.controller.RoleController', url: '/api/v1/system/roles/2/menus', session: null, mobile: false, ip: '127.0.0.1', os: 'Mac OS X', browser: 'Chrome', engine: 'Blink', record: 0, param: '{"menuIds":[1,2,3]}', result: 'true', exception: null, app: 'lumen-system', createTime: '2026-11-02 10:05:47' },
    { id: 1, dateYm: 202610, title: '科室管理', descr: '创建科室', type: '新增', uid: 1, account: 'admin', actionTime: '2026-10-28 09:12:03', spent: 27, method: 'POST', name: 'create', pack: 'org.birdbro.lumen.system.controller.OrgDepController', url: '/api/v1/system/org-deps', session: null, mobile: false, ip: '127.0.0.1', os: 'Mac OS X', browser: 'Chrome', engine: 'Blink', record: 0, param: '{"orgId":1,"code":"radiology","name":"放射科"}', result: 'true', exception: null, app: 'lumen-system', createTime: '2026-10-28 09:12:03' },
  ])
}

// ---------------------------------------------------------------------------
// Menu tree assembly (mirror MenuTreeService + copied MenuTree POJO)
// ---------------------------------------------------------------------------

/** The copied MenuTree JSON shape: `id -> mid`, Integer visible 1/0 -> Boolean, trimmed columns stay null, leaf children null. */
export interface MockMenuTree {
  mid: number
  title: string
  upId: number
  icon: string | null
  path: string | null
  query: string | null
  component: string | null
  type: string
  sort: number
  visible: boolean
  redirect: string | null
  app: number | null
  breadcrumb: string | null
  action: string | null
  isCache: boolean | null
  isFrame: boolean | null
  perms: string | null
  remark: string | null
  children: MockMenuTree[] | null
}

/** The full flat menu list (the unscoped MenuController variant). */
export function allMenus(): MockMenuInfo[] {
  return SEED_MENUS
}

/**
 * Assembles the user-scoped menu tree from the granted menu ids (the AuthServiceImpl
 * `menuTree(userId)` variant): TreeUtils semantics - roots are upId = 0, orphans are
 * dropped, leaf children stay null.
 */
export function buildMenuTree(grantedIds: Iterable<number>): MockMenuTree[] {
  const granted = new Set(grantedIds)
  const nodes = new Map<number, MockMenuTree>()
  for (const menu of SEED_MENUS) {
    if (!granted.has(menu.id)) continue
    nodes.set(menu.id, {
      mid: menu.id,
      title: menu.title,
      upId: menu.upId,
      icon: menu.icon,
      path: menu.path,
      query: null,
      component: menu.component,
      type: menu.type,
      sort: menu.sort,
      visible: menu.visible === 1,
      redirect: null,
      app: null,
      breadcrumb: null,
      action: null,
      isCache: null,
      isFrame: null,
      perms: menu.perms,
      remark: null,
      children: null,
    })
  }
  const roots: MockMenuTree[] = []
  for (const node of nodes.values()) {
    const parent = node.upId === 0 ? null : nodes.get(node.upId)
    if (parent) {
      parent.children = parent.children ?? []
      parent.children.push(node)
    } else if (node.upId === 0) {
      roots.push(node)
    } // else orphan - dropped (TreeUtils semantics)
  }
  const bySort = (a: MockMenuTree, b: MockMenuTree) => a.sort - b.sort
  for (const node of nodes.values()) {
    node.children?.sort(bySort)
  }
  roots.sort(bySort)
  return roots
}

/** Non-blank perms strings of the granted menus (MenuTreeService.permsSet). */
export function permsOf(grantedIds: Iterable<number>): string[] {
  const granted = new Set(grantedIds)
  return [...new Set(SEED_MENUS.filter(m => granted.has(m.id) && m.perms).map(m => m.perms as string))]
}

// ---------------------------------------------------------------------------
// Pagination helper (mirror the copied MyBatis-Plus IPage JSON shape)
// ---------------------------------------------------------------------------

/**
 * IPage-shaped page wrapper. Field names mirror the real serialized Page
 * (`records/total/size/current/pages`); the real JSON also carries boolean
 * bookkeeping fields (searchCount etc.) the mock omits - recorded simplification.
 */
export function pageOf<T>(records: T[], pageNum: number, pageSize: number): {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
} {
  return {
    records,
    total: records.length,
    size: pageSize,
    current: pageNum,
    pages: Math.ceil(records.length / pageSize),
  }
}
