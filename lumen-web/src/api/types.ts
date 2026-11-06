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
 * never serializes so there is no password field).
 */
export interface UserItem {
  uid: number;
  username: string;
  name: string;
  enable: number;
  mustChangePassword: number;
  createTime: string;
}

export interface CreateUserBody {
  username: string;
  password: string;
  name: string;
  roleIds?: number[];
}

/** Org tree node (backend org tree): the org row plus upId-assembled children (leaf children null). */
export interface OrgNode {
  id: number;
  upId: number;
  level: number;
  type: number | null;
  name: string;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
  children: OrgNode[] | null;
}

export interface CreateOrgBody {
  /** Parent org id, 0 for a root. */
  upId: number;
  name: string;
  level?: number;
  type?: number | null;
}

/** PUT /system/orgs/{id} accepts a partial update (null fields are skipped server-side). */
export type UpdateOrgBody = Partial<Pick<OrgNode, 'upId' | 'level' | 'type' | 'name'>>;

/** Department row (backend org_dep table shape). */
export interface OrgDep {
  id: number;
  orgId: number;
  code: string;
  name: string;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

export interface CreateDepBody {
  orgId: number;
  code: string;
  name: string;
}

export type UpdateDepBody = Partial<Pick<OrgDep, 'orgId' | 'code' | 'name'>>;

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
 * POST /system/menus payload (menu-management-api family): a node of the
 * `upId` tree (`upId = 0` = top level) typed `M` directory / `C` menu / `F`
 * button. `perms` is free text (the `模块:资源:动作` convention) - the server
 * stores it verbatim; creation auto-grants the node to the admin role.
 */
export interface CreateMenuBody {
  upId: number;
  type: 'M' | 'C' | 'F';
  title: string;
  perms?: string | null;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort?: number | null;
  visible?: number | null;
}

/** PUT /system/menus/{id} payload - full-field update including reparenting via `upId`. */
export interface UpdateMenuBody {
  upId: number;
  type: 'M' | 'C' | 'F';
  title: string;
  perms?: string | null;
  path?: string | null;
  component?: string | null;
  icon?: string | null;
  sort?: number | null;
  visible?: number | null;
}

/** Operation-log row (the LogInfoEntity JSON field set). */
export interface LogItem {
  id: number;
  dateYm: number;
  title: string;
  descr: string;
  type: string;
  uid: number;
  account: string;
  actionTime: string;
  spent: number;
  method: string;
  name: string;
  pack: string;
  url: string;
  session: string | null;
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
 * GET /system/logs query - `page`/`pageSize` (the logs mock's convention),
 * optional filters. A type ALIAS (not an interface) so the bag stays
 * assignable to the transport client's Record-typed query option.
 */
export type ListLogsQuery = {
  page: number;
  pageSize: number;
  type?: string;
  uid?: number;
  dateYm?: number;
};

/**
 * Sign-log row (the copied LogSignEntity JSON field set MINUS `token` - design
 * D7 of sign-log-query: credential material never leaves the service, so no
 * token field exists on this type either). `type`/`deleted`/`createTime` are
 * the entity's remaining copied columns (modeled optional alongside the audit
 * list per the batch brief); `type` is the copied login/logout success/failure
 * integer the UI deliberately does not interpret (design D5 - `msg` carries
 * the outcome in prose).
 */
export interface LogSignItem {
  id: number;
  dateYm: number;
  msg: string;
  descr: string;
  uid: number;
  account: string;
  actionTime: string;
  mobile: boolean;
  ip: string;
  os: string;
  browser: string;
  engine: string;
  session: string | null;
  app: string;
  type?: number | null;
  deleted?: number;
  createTime?: string;
}

/**
 * GET /system/logs/sign query - the same `page`/`pageSize` convention as
 * `ListLogsQuery` (the logs endpoints' own keys), with the documented filters.
 * A type ALIAS (not an interface) so the bag stays assignable to the transport
 * client's Record-typed query option (same reasoning as `ListLogsQuery`).
 */
export type ListSignLogsQuery = {
  page: number;
  pageSize: number;
  account?: string;
  uid?: number;
  dateYm?: number;
};
