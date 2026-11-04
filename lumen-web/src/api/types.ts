/**
 * DTO types for the `/api/v1/system/**` surface (batch 4 of
 * web-foundation-auth-console, task 4.3). Centralized here so every typed
 * client imports from one module.
 *
 * Source of truth = the mock fixtures (`mock/_fixtures.ts`, seed-parity with
 * the backend migrations) plus `openspec/specs/api/spec.md`: IDs are Integer
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

/** Profile payload of the auth endpoints (auth.mock.ts `profileOf`). */
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
 * (`mock/_fixtures.ts` MockMenuTree): the id column serializes as `mid`,
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
 * UserItemDto JSON (users.mock.ts list rows - a plain array, no page
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

/** Org tree node (orgs.mock.ts): the org row plus upId-assembled children (leaf children null). */
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

/** Department row (org-deps.mock.ts). */
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
 * Role row plus the role's granted menu ids. `menuIds` is mock-only: the dev
 * mock echoes it (roles.mock.ts) so the full-replace assignment dialog can
 * reopen with the current grant; the api delta's backend `roles/list` is the
 * declared gap and does not promise the field, so it is optional.
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
  /** mock-only grant echo - see the interface note above. */
  menuIds?: number[];
}

export interface CreateRoleBody {
  name: string;
  type?: number;
  remark?: string | null;
}

export type UpdateRoleBody = Partial<Pick<RoleItem, 'name' | 'type' | 'remark'>>;

/** Dict catalog row (dicts.mock.ts, V3 seed shape). */
export interface DictCatal {
  id: number;
  enable: number;
  code: string;
  name: string;
  sort: number;
  remark: string | null;
  cls: number | null;
  initial: string | null;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

export interface CreateDictCatalBody {
  code: string;
  name: string;
  enable?: number;
  sort?: number;
  remark?: string | null;
  cls?: number | null;
  initial?: string | null;
}

export type UpdateDictCatalBody = Partial<CreateDictCatalBody>;

/** Dict detail row (dicts.mock.ts, V3 seed shape). */
export interface DictDetail {
  id: number;
  enable: number;
  caId: number;
  caCode: string;
  code: number | null;
  value: string | null;
  label: string;
  color: string | null;
  css: string | null;
  sort: number;
  back1: string | null;
  back2: string | null;
  remark: string | null;
  initial: string | null;
  createBy: number | null;
  createTime: string;
  updateBy: number | null;
  updateTime: string;
}

export interface CreateDictDetailBody {
  caId: number;
  label: string;
  code?: number | null;
  value?: string | null;
  enable?: number;
  color?: string | null;
  css?: string | null;
  sort?: number;
  back1?: string | null;
  back2?: string | null;
  remark?: string | null;
  initial?: string | null;
}

export type UpdateDictDetailBody = Partial<CreateDictDetailBody>;

/** Operation-log row (logs.mock.ts, the LogInfoEntity JSON field set; mock-only data). */
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
