/**
 * DTO types for the `/api/v1/system/**` surface (batch 4 of
 * web-foundation-auth-console, task 4.3). Centralized here so every typed
 * client imports from one module.
 *
 * Source of truth = the backend seed migrations (e.g.
 * `lumen-sev/lumen-system/src/main/resources/db/migration/V2__seed.sql`, which
 * the former dev mock fixtures mirrored verbatim) plus
 * `openspec/specs/api/spec.md`: IDs are Integer
 * auto-increment and serialize as JSON numbers (spec section "ID
 * Serialization", user decision 2026-11-02 which withdrew the former
 * snowflake/string clause - `src/api/entity-id.ts` predates that amendment
 * and is therefore NOT referenced by these DTOs).
 */

/** MyBatis-Plus IPage JSON shape (`records/total/size/current/pages`). */
export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** Profile payload of the auth endpoints (the backend `/me` profile shape). */
export interface UserProfile {
  uid: number;
  username: string;
  name: string;
  enable: number;
  mustChangePassword: number;
  roles: string[];
}

/**
 * Menu tree node - mirror of the copied MenuTree POJO JSON shape
 * (framework/common): the id column serializes as `mid`,
 * the Integer visible 1/0 becomes Boolean, trimmed columns stay null and
 * leaf `children` is null (TreeUtils semantics).
 */
export interface MenuNode {
  mid: number;
  title: string;
  upId: number;
  icon: string | null;
  path: string | null;
  query: string | null;
  component: string | null;
  type: string;
  sort: number;
  visible: boolean;
  redirect: string | null;
  app: number | null;
  breadcrumb: string | null;
  action: string | null;
  isCache: boolean | null;
  isFrame: boolean | null;
  perms: string | null;
  remark: string | null;
  children: MenuNode[] | null;
}

/** POST /system/login payload (token + full permission set). */
export interface LoginData {
  token: string;
  profile: UserProfile;
  menus: MenuNode[];
  perms: string[];
}

/** GET /system/me payload - same permission set, no re-issued token. */
export interface MeData {
  profile: UserProfile;
  menus: MenuNode[];
  perms: string[];
}

export interface LoginBody {
  username: string;
  password: string;
}

/**
 * UserItemDto JSON (backend user list rows - a plain array, no page
 * wrapper, per the implemented surface in the api delta; the BCrypt hash
 * never serializes so there is no password field). The user-edit-delete-profile
 * backend change extended the payload with the profile surface: the V26
 * columns plus the batch-resolved roleNames/depName (null when unbound).
 */
export interface UserItem {
  uid: number;
  username: string;
  name: string;
  phone: string | null;
  email: string | null;
  /** Dict 性别 catalog code (null = unset). */
  gender: number | null;
  /** Dict 职称 catalog code (null = unset). */
  title: number | null;
  /** Owning department id (null = unbound). */
  depId: number | null;
  /** Department display name, batch-resolved server-side (null when unbound/dead). */
  depName: string | null;
  /** Exam-type visibility, CSV of dict 检查类型 codes (null = unset). */
  modalitys: string | null;
  /** Report-workflow authority tier, dict 审核权限 code (null = no tier). */
  repAudit: number | null;
  /** Report-signature image path (null = none). */
  signature: string | null;
  /** The bound role display names, batch-resolved server-side (empty when unbound). */
  roleNames: string[];
  enable: number;
  mustChangePassword: number;
  /** Audit creator UID (EntityBase create_by; null on legacy rows). */
  createBy: number | null;
  createTime: string;
}

export interface CreateUserBody {
  username: string;
  password: string;
  name: string;
  roleIds?: number[];
  /**
   * The V26 profile fields (user-edit-delete-profile api delta): the create
   * carries the same profile surface as the update (the backend's
   * `CreateUserRequest` is additive; null/unset leaves the column NULL).
   */
  phone?: string | null;
  email?: string | null;
  gender?: number | null;
  title?: number | null;
  depId?: number | null;
  modalitys?: string | null;
  repAudit?: number | null;
  signature?: string | null;
}

/**
 * UserDetailDto JSON (GET /system/users/{id} - the edit dialog's echo): the
 * full profile plus `roleIds` (the update's full-replace source). No password
 * field by contract - the hash never leaves the backend.
 */
export interface UserDetail {
  uid: number;
  username: string;
  name: string;
  phone: string | null;
  email: string | null;
  gender: number | null;
  title: number | null;
  depId: number | null;
  modalitys: string | null;
  repAudit: number | null;
  signature: string | null;
  roleIds: number[];
}

/**
 * PUT /system/users/{id} body - the partial profile patch. NO username (the
 * backend ignores it by construction) and NO password (the reset stays its own
 * SM2-encrypted endpoint). A present `roleIds` REPLACES the bindings
 * (full-replace); an absent one leaves them untouched.
 */
export interface UpdateUserBody {
  name?: string;
  phone?: string | null;
  email?: string | null;
  gender?: number | null;
  title?: number | null;
  depId?: number | null;
  roleIds?: number[];
  /** Exam-type visibility CSV - the page joins the multi-select array. */
  modalitys?: string | null;
  repAudit?: number | null;
  signature?: string | null;
}

/**
 * Org tree node (backend org tree): the org row plus upId-assembled children
 * (leaf children null). Carries the V16 hospital/campus profile surface - the
 * controlled model: at most one live hospital root (`upId = 0`), campuses as
 * its leaf children (`type = 1`), `isDefault` marks the one default campus.
 */
export interface OrgNode {
  id: number;
  upId: number;
  level: number;
  type: number | null;
  name: string;
  /** Stable organization code, unique among live siblings (backend V16). */
  code: string;
  shortName: string | null;
  /** 1 = enabled, 0 = disabled. */
  status: number;
  provinceCode: string | null;
  cityCode: string | null;
  districtCode: string | null;
  address: string | null;
  contact: string | null;
  phone: string | null;
  email: string | null;
  logoUrl: string | null;
  sort: number;
  isDefault: number;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
  children: OrgNode[] | null;
}

/**
 * POST /system/orgs body. `upId` is 0 for the bootstrap hospital root
 * (rejected by the backend once a live root exists) or the hospital id for a
 * campus; `type`/`level` are server-derived and are not client inputs. The
 * campus form requires `code` (spec: code SHALL be required) - the type keeps
 * it optional because the backend generates one when the field is absent.
 */
export interface CreateOrgBody {
  /** Parent org id, 0 for a root. */
  upId: number;
  name: string;
  code?: string;
  shortName?: string | null;
  status?: number;
  provinceCode?: string | null;
  cityCode?: string | null;
  districtCode?: string | null;
  address?: string | null;
  contact?: string | null;
  phone?: string | null;
  email?: string | null;
  logoUrl?: string | null;
  sort?: number;
}

/**
 * PUT /system/orgs/{id} accepts a partial profile update (null fields are
 * skipped server-side). Deliberately excludes `upId`/`type`/`level`: the
 * controlled model rejects reparenting and type changes, and level is
 * server-derived - the UI cannot express them (org-management-redesign-web 1.3).
 */
export type UpdateOrgBody = Partial<
  Pick<
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
    | 'isDefault'
  >
>;

/**
 * Department row (backend org_dep table shape). `orgId` MUST reference a live,
 * enabled campus (org-management-redesign-api) - the same `code` may exist on
 * different campuses, but is unique within one campus among live rows.
 */
export interface OrgDep {
  id: number;
  orgId: number;
  code: string;
  name: string;
  /** 1 = enabled, 0 = disabled (V16). */
  status: number;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

/** POST /system/org-deps body - the owner must be a live, enabled campus. */
export interface CreateDepBody {
  /** Owning campus org id (a hospital root or disabled campus is rejected A0400). */
  orgId: number;
  code: string;
  name: string;
  status?: number;
}

/**
 * PUT /system/org-deps/{id} partial update. Deliberately excludes `code`:
 * the code is immutable after creation (it routes DICOM/HL7 traffic and is
 * unique per campus) - the edit dialog cannot send it
 * (org-management-redesign-web 1.3).
 */
export type UpdateDepBody = Partial<Pick<OrgDep, 'orgId' | 'name' | 'status'>>;

/** GET /system/org-deps/page query (the department table's server-side filters). */
export interface DepPageQuery {
  /** Absent = all campuses; present = a campus (hospital id is rejected). */
  orgId?: number;
  keyword?: string;
  status?: number;
  pageNum?: number;
  pageSize?: number;
}

/**
 * Role row plus the role's granted menu ids. `menuIds` is not yet promised by
 * the backend `roles/list` (the api delta's declared gap): the former dev mock
 * echoed it so the full-replace assignment dialog could reopen with the
 * current grant; the field stays optional until the backend follow-up change
 * supplies it.
 */
export interface RoleItem {
  id: number;
  name: string;
  type: number;
  remark: string | null;
  /** Lifecycle flag (role-type-dict-web D7): 1 enabled / 0 disabled - display only. */
  enable: number;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
  /** Grant echo pending the backend follow-up - see the interface note above. */
  menuIds?: number[];
}

export interface CreateRoleBody {
  name: string;
  type?: number;
  remark?: string | null;
}

export type UpdateRoleBody = Partial<Pick<RoleItem, 'name' | 'type' | 'remark'>>;

/**
 * Dict catalog row (V3 seed shape, slimmed by backend V9 - initial dropped; V12
 * dropped the `code` column - the catalog anchors by id, the name carries the
 * uniqueness).
 */
export interface DictCatal {
  id: number;
  enable: number;
  name: string;
  sort: number;
  remark: string | null;
  cls: number | null;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

export interface CreateDictCatalBody {
  name: string;
  enable?: number;
  sort?: number;
  remark?: string | null;
  cls?: number | null;
}

export type UpdateDictCatalBody = Partial<CreateDictCatalBody>;

/**
 * Slim dict item projection (role-type-dict-web): the label-lookup pair served by
 * `GET /system/dicts/detail/items/{catalogName}` - the numeric `code` consumers
 * store plus the V9 single-text display `value`. NOT the full DictDetail row
 * (tag/flag/color/audit stay server-side).
 */
export interface DictItem {
  code: number | null;
  value: string | null;
}

/**
 * Dict detail row (backend V9 shape): `value` is the single text field (display +
 * stored text; the duplicated `label` was dropped), the catalog anchors by `caId`
 * alone (denormalized `caCode` dropped), and the spare pair is `tag`/`flag`
 * (renamed from `back1`/`back2`). `initial` (pinyin search) was dropped.
 */
export interface DictDetail {
  id: number;
  enable: number;
  caId: number;
  code: number | null;
  value: string | null;
  tag: string | null;
  flag: string | null;
  color: string | null;
  css: string | null;
  sort: number;
  remark: string | null;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

export interface CreateDictDetailBody {
  caId: number;
  value: string;
  code?: number | null;
  enable?: number;
  color?: string | null;
  css?: string | null;
  sort?: number;
  tag?: string | null;
  flag?: string | null;
  remark?: string | null;
}

export type UpdateDictDetailBody = Partial<CreateDictDetailBody>;

/**
 * dict_city administrative-divisions node (spec `system-data` § City Reference
 * Query; seeded by backend V4): the 13-column 随访 lineage shape, 3751 rows.
 * Levels: 0 中国 root / 1 province / 2 city / 3 district; on levels 1-3 the
 * lineage `id` IS the GB division code the org profile stores. READ-ONLY - the
 * seed migrations are the only data source (no write path, no CRUD page).
 */
export interface CityDict {
  id: number;
  upId: number;
  code: string;
  name: string;
  abbreviation: string | null;
  level: number;
  cityCode: string | null;
  zipCode: string | null;
  merger: string;
  pinyin: string;
  lng: string;
  lat: string;
  initial: string | null;
  children: CityDict[] | null;
}

/**
 * POST /system/menus payload (menu-management-api family): a node of the
 * `upId` tree (`upId = 0` = top level) typed `M` directory / `C` menu / `F`
 * button. `perms` is free text (the `模块:资源:动作` convention) - the server
 * stores it verbatim; creation auto-grants the node to the admin role.
 */
export interface CreateMenuBody {
  upId: number;
  type: 'M' | 'C' | 'F' | 'W';
  title: string;
  perms?: string | null;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort?: number | null;
  visible?: number | null;
  remark?: string | null;
}

/** PUT /system/menus/{id} payload - full-field update including reparenting via `upId`. */
export interface UpdateMenuBody {
  upId: number;
  type: 'M' | 'C' | 'F' | 'W';
  title: string;
  perms?: string | null;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort?: number | null;
  visible?: number | null;
  remark?: string | null;
}

/**
 * Audit-log row (audit-log-unify-web 1.2: the unified `log_info` JSON field set
 * — BOTH event families since the backend merge; V25 dropped `log_sign`). `type`
 * is the Integer code at rest (operations 1–5 via the backend's `codeOf`
 * translation; sign events 10/11/12 verbatim `Constants`). The `session` column
 * (the sign-event credential anchor) is select-excluded server-side — this type
 * carries NO session field either, so nothing in the console can render it
 * (D5: same guarantee as the old D7 token exclusion, new column).
 */
export interface LogItem {
  id: number;
  dateYm: number;
  title: string;
  descr: string;
  type: number;
  uid: number;
  account: string;
  actionTime: string;
  spent: number;
  method: string;
  name: string;
  pack: string;
  url: string;
  mobile: boolean;
  ip: string;
  os: string;
  browser: string;
  engine: string;
  record: number;
  param: string;
  result: string;
  exception: string | null;
  app: string;
  createTime: string;
}

/**
 * GET /system/logs query - `page`/`pageSize` (the logs endpoint's own keys),
 * optional filters AND-combined server-side. `type` is the Integer code
 * (1–5 operations / 10–12 sign events); `account` is the sign-page-parity
 * filter; `beginDate`/`endDate` are the `yyyy-MM-dd` date-range bounds
 * (log-query-date-range: additive — `uid`/`dateYm` stay on the contract, the
 * page just no longer sends them). A type ALIAS (not an interface) so the bag
 * stays assignable to the transport client's Record-typed query option.
 */
export type ListLogsQuery = {
  page: number;
  pageSize: number;
  type?: number;
  uid?: number;
  account?: string;
  dateYm?: number;
  beginDate?: string;
  endDate?: string;
};

/**
 * POST /system/common/upload/image response data (api spec § Common Image
 * Upload Endpoint): the stored file path, e.g.
 * `/profile/avatar/2026/11/06/<uuid>.png` - directly usable as an `<img src>`
 * via the `/profile/**` static read-back segment.
 */
export interface UploadedImage {
  path: string;
}
