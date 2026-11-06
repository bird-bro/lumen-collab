import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiUpload, configureApiClient } from '@/api/client'
import { login, me } from '@/api/auth'
import { createUser, enableUser, listUsers, resetPassword } from '@/api/users'
import { createOrg, deleteOrg, orgTree, setOrgStatus, updateOrg } from '@/api/orgs'
import { createDep, deleteDep, listDeps, pageDep, updateDep } from '@/api/org-deps'
import { assignRoleMenus, createRole, deleteRole, listRoles, updateRole } from '@/api/roles'
import { createMenu, menuTree, removeMenu, updateMenu } from '@/api/menus'
import type { CreateMenuBody, UpdateMenuBody } from '@/api/types'
import {
  createCatal,
  createDetail,
  deleteCatal,
  deleteDetail,
  listCatal,
  listDetails,
  updateCatal,
  updateDetail,
} from '@/api/dicts'
import { listLogs, listSignLogs } from '@/api/logs'

/**
 * Typed per-resource clients (batch 4 of web-foundation-auth-console, task
 * 4.3). Fetch is stubbed at the transport seam; each client is asserted on
 * method + path + query/body (paths verbatim against the api delta / main
 * spec) AND on the unwrapped payload shape (plain arrays vs IPage `Page<T>`,
 * JSON-number ids). DTO truth = the backend seed migrations (the former dev
 * mock fixtures mirrored them verbatim).
 */

const PROFILE = {
  uid: 1,
  username: 'admin',
  name: '系统管理员',
  enable: 1,
  mustChangePassword: 1,
  roles: ['系统管理员'],
}

const MENU_NODE = {
  mid: 1,
  title: '系统管理',
  upId: 0,
  icon: 'setting',
  path: '/system',
  query: null,
  component: null,
  type: 'M',
  sort: 1,
  visible: true,
  redirect: null,
  app: null,
  breadcrumb: null,
  action: null,
  isCache: null,
  isFrame: null,
  perms: null,
  remark: null,
  children: null,
}

function stubOk(data: unknown): { mock: { calls: unknown[][] } } {
  const fetchMock = vi.fn(async () => ({
    json: async () => ({ timestamp: '2026-11-04 10:00:00.000', code: '200', msg: 'OK', data }),
  }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** Stub whose envelopes carry the given payloads in call order (for multi-call round-trips). */
function stubOkSequence(datas: unknown[]): { mock: { calls: unknown[][] } } {
  const fetchMock = vi.fn(async () => ({
    json: async () => ({
      timestamp: '2026-11-04 10:00:00.000',
      code: '200',
      msg: 'OK',
      data: datas[Math.min(fetchMock.mock.calls.length - 1, datas.length - 1)],
    }),
  }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function lastCall(fetchMock: { mock: { calls: unknown[][] } }): { url: string; init: RequestInit } {
  const call = fetchMock.mock.calls[0]
  return { url: call[0] as string, init: call[1] as RequestInit }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('auth client (task 4.3)', () => {
  it('login posts credentials to /system/login and unwraps token+profile+menus+perms', async () => {
    const fetchMock = stubOk({ token: 'jwt-1', profile: PROFILE, menus: [MENU_NODE], perms: ['system:user:list'] })
    const data = await login({ username: 'admin', password: 'admin123' })
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/login')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ username: 'admin', password: 'admin123' }))
    expect(data.token).toBe('jwt-1')
    expect(data.profile).toEqual(PROFILE)
    expect(data.menus).toHaveLength(1)
    expect(data.menus[0].mid).toBe(1)
    expect(data.perms).toEqual(['system:user:list'])
  })

  it('me gets /system/me and unwraps profile+menus+perms (no token field)', async () => {
    const fetchMock = stubOk({ profile: PROFILE, menus: [MENU_NODE], perms: ['system:user:list'] })
    const data = await me()
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/me')
    expect(init.method).toBe('GET')
    expect(data.profile.uid).toBe(1)
    expect(data.menus[0].title).toBe('系统管理')
    expect(data.perms).toEqual(['system:user:list'])
  })
})

describe('users client (task 4.3)', () => {
  it('listUsers GETs /system/users/list and unwraps a plain array', async () => {
    const fetchMock = stubOk([
      { uid: 1, username: 'admin', name: '系统管理员', enable: 1, mustChangePassword: 1, createTime: '2026-11-04 09:00:00' },
      { uid: 2, username: 'op1', name: '操作员', enable: 1, mustChangePassword: 0, createTime: '2026-11-04 09:00:00' },
    ])
    const data = await listUsers()
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/users/list')
    expect(init.method).toBe('GET')
    expect(Array.isArray(data)).toBe(true)
    expect(data).toHaveLength(2)
    expect(data[0].uid).toBe(1)
  })

  it('createUser posts /system/users and unwraps the new numeric id', async () => {
    const fetchMock = stubOk(3)
    const body = { username: 'op2', password: 'pw', name: '操作员2', roleIds: [1] }
    const data = await createUser(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/users')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(3)
    expect(typeof data).toBe('number')
  })

  it('resetPassword puts /system/users/{id}/password with the newPassword body', async () => {
    const fetchMock = stubOk(true)
    const data = await resetPassword(5, 'n3wPass')
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/users/5/password')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ newPassword: 'n3wPass' }))
    expect(data).toBe(true)
  })

  it('enableUser puts /system/users/{id}/enable with the 0|1 flag', async () => {
    const fetchMock = stubOk(true)
    const data = await enableUser(5, 0)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/users/5/enable')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ enable: 0 }))
    expect(data).toBe(true)
  })
})

describe('orgs client (task 4.3)', () => {
  it('orgTree GETs /system/orgs/tree and unwraps the nested tree', async () => {
    const fetchMock = stubOk([
      {
        id: 1, upId: 0, level: 1, type: 0, name: '示例医院',
        createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00',
        children: null,
      },
    ])
    const data = await orgTree()
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/tree')
    expect(init.method).toBe('GET')
    expect(data).toHaveLength(1)
    expect(data[0].id).toBe(1)
    expect(data[0].name).toBe('示例医院')
  })

  it('orgTree sends only the name query for campus-name filtering', async () => {
    const fetchMock = stubOk([])
    await orgTree({ name: ' 东 ' })
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/tree?name=%E4%B8%9C')
  })

  it('createOrg posts /system/orgs and unwraps the new id', async () => {
    const fetchMock = stubOk(2)
    const body = { upId: 1, name: '分院' }
    const data = await createOrg(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(2)
  })

  it('updateOrg puts /system/orgs/{id} and unwraps true', async () => {
    const fetchMock = stubOk(true)
    const data = await updateOrg(2, { name: '改名' })
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/2')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ name: '改名' }))
    expect(data).toBe(true)
  })

  it('setOrgStatus puts /system/orgs/{id}/status and unwraps true', async () => {
    const fetchMock = stubOk(true)
    const data = await setOrgStatus(2, 0)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/2/status')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ status: 0 }))
    expect(data).toBe(true)
  })

  it('deleteOrg deletes /system/orgs/{id} and unwraps true', async () => {
    const fetchMock = stubOk(true)
    const data = await deleteOrg(2)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/2')
    expect(init.method).toBe('DELETE')
    expect(data).toBe(true)
  })
})

describe('org-deps client (task 4.3)', () => {
  it('listDeps GETs /system/org-deps/list?orgId= and unwraps a plain array', async () => {
    const fetchMock = stubOk([
      { id: 1, orgId: 1, code: 'radiology', name: '放射科', createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00' },
    ])
    const data = await listDeps(1)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps/list?orgId=1')
    expect(init.method).toBe('GET')
    expect(data).toHaveLength(1)
    expect(data[0].code).toBe('radiology')
  })

  it('listDeps omits the query string when no org filter is given', async () => {
    const fetchMock = stubOk([])
    await listDeps()
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps/list')
  })

  it('createDep posts /system/org-deps and unwraps the new id', async () => {
    const fetchMock = stubOk(4)
    const body = { orgId: 1, code: 'pathology', name: '病理科' }
    const data = await createDep(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(4)
  })

  it('updateDep puts /system/org-deps/{id} and deleteDep deletes it', async () => {
    const fetchMock = stubOk(true)
    expect(await updateDep(4, { name: '病理科2' })).toBe(true)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps/4')
    expect(init.method).toBe('PUT')
    expect(await deleteDep(4)).toBe(true)
    const [deleteUrl, deleteInit] = [
      (fetchMock.mock.calls[1][0] as string),
      fetchMock.mock.calls[1][1] as RequestInit,
    ]
    expect(deleteUrl).toBe('/api/v1/system/org-deps/4')
    expect(deleteInit.method).toBe('DELETE')
  })
})

describe('roles client (task 4.3)', () => {
  it('listRoles GETs /system/roles/list?pageNum=&pageSize= and unwraps the IPage shape with the optional menuIds echo', async () => {
    const fetchMock = stubOk({
      records: [
        {
          id: 1, name: '系统管理员', type: 10, remark: 'seed default administrator role',
          createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00',
          menuIds: [1, 2, 3],
        },
      ],
      total: 1,
      size: 20,
      current: 2,
      pages: 1,
    })
    const page = await listRoles(2, 20)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/roles/list?pageNum=2&pageSize=20')
    expect(init.method).toBe('GET')
    expect(page.records).toHaveLength(1)
    expect(page.total).toBe(1)
    expect(page.current).toBe(2)
    expect(page.pages).toBe(1)
    expect(page.records[0].menuIds).toEqual([1, 2, 3])
  })

  it('listRoles falls back to the backend pageSize defaults (pageNum=1, pageSize=20)', async () => {
    const fetchMock = stubOk({ records: [], total: 0, size: 20, current: 1, pages: 0 })
    await listRoles()
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/roles/list?pageNum=1&pageSize=20')
  })

  it('createRole posts /system/roles and unwraps the new id', async () => {
    const fetchMock = stubOk(2)
    const body = { name: '随访医师', type: 11, remark: null }
    const data = await createRole(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/roles')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(2)
  })

  it('updateRole puts /system/roles/{id} and deleteRole deletes it', async () => {
    const fetchMock = stubOk(true)
    expect(await updateRole(2, { name: '改名' })).toBe(true)
    expect((lastCall(fetchMock).url)).toBe('/api/v1/system/roles/2')
    expect(lastCall(fetchMock).init.method).toBe('PUT')
    expect(await deleteRole(2)).toBe(true)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/system/roles/2')
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe('DELETE')
  })

  it('assignRoleMenus puts the FULL-REPLACE menu set to /system/roles/{id}/menus', async () => {
    const fetchMock = stubOk(true)
    const data = await assignRoleMenus(2, [1, 2, 3])
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/roles/2/menus')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ menuIds: [1, 2, 3] }))
    expect(data).toBe(true)
  })
})

describe('menus client (task 4.3)', () => {
  it('menuTree GETs /system/menus/tree and unwraps the full MenuNode tree keyed by mid', async () => {
    const fetchMock = stubOk([
      {
        ...MENU_NODE,
        children: [
          {
            ...MENU_NODE,
            mid: 2,
            title: '用户管理',
            upId: 1,
            type: 'C',
            path: 'user',
            component: 'system/user/index',
            icon: null,
            perms: 'system:user:list',
          },
        ],
      },
    ])
    const data = await menuTree()
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/menus/tree')
    expect(init.method).toBe('GET')
    expect(data).toHaveLength(1)
    expect(data[0].mid).toBe(1)
    expect(data[0].children?.[0].mid).toBe(2)
    expect(data[0].children?.[0].perms).toBe('system:user:list')
  })

  it('createMenu posts the node body to /system/menus and unwraps the new id', async () => {
    const fetchMock = stubOk(33)
    const body: CreateMenuBody = { upId: 9, type: 'F', title: '删除', perms: 'system:role:delete', sort: 9, visible: 1 }
    const data = await createMenu(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/menus')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(33)
  })

  it('updateMenu puts the full field body to /system/menus/{id}', async () => {
    const fetchMock = stubOk(true)
    const body: UpdateMenuBody = { upId: 15, type: 'C', title: '改名', sort: 3, visible: 0 }
    const data = await updateMenu(33, body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/menus/33')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(true)
  })

  it('removeMenu deletes /system/menus/{id}', async () => {
    const fetchMock = stubOk(true)
    const data = await removeMenu(33)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/menus/33')
    expect(init.method).toBe('DELETE')
    expect(data).toBe(true)
  })

})

describe('dicts client (task 4.3)', () => {
  const CATAL = {
    id: 1, enable: 1, name: '通用状态', sort: 0, remark: null,
    cls: null, createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00',
  }

  it('listCatal GETs /system/dicts/catal/list?pageNum=&pageSize= and unwraps the IPage shape', async () => {
    const fetchMock = stubOk({ records: [CATAL], total: 1, size: 20, current: 1, pages: 1 })
    const page = await listCatal(1, 20)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/dicts/catal/list?pageNum=1&pageSize=20')
    expect(init.method).toBe('GET')
    expect(page.records).toHaveLength(1)
    expect(page.records[0].name).toBe('通用状态')
    expect(page.size).toBe(20)
  })

  it('listCityDicts coalesces identical read-only city queries', async () => {
    vi.resetModules()
    const { listCityDicts } = await import('@/api/dicts')
    const fetchMock = stubOk([
      { id: 120000, upId: 0, code: '120000', name: '天津市', level: 1 },
    ])
    const [first, second, third] = await Promise.all([
      listCityDicts({ upId: 120000 }),
      listCityDicts({ upId: 120000 }),
      listCityDicts({ upId: 120000 }),
    ])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(first).toBe(second)
    expect(second).toBe(third)
    expect(lastCall(fetchMock).url).toBe('/api/v1/system/dicts/city/list?upId=120000')
  })

  it('createCatal posts /system/dicts/catal and unwraps the new id', async () => {
    const fetchMock = stubOk(2)
    const body = { name: '超声检查状态' }
    const data = await createCatal(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/dicts/catal')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(2)
  })

  it('updateCatal puts /system/dicts/catal/{id} and deleteCatal deletes it', async () => {
    const fetchMock = stubOk(true)
    expect(await updateCatal(2, { name: '改名' })).toBe(true)
    expect(lastCall(fetchMock).url).toBe('/api/v1/system/dicts/catal/2')
    expect(lastCall(fetchMock).init.method).toBe('PUT')
    expect(await deleteCatal(2)).toBe(true)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/system/dicts/catal/2')
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe('DELETE')
  })

  it('listDetails GETs /system/dicts/detail/list?caId= and unwraps a plain sort-asc array', async () => {
    const fetchMock = stubOk([
      { id: 1, enable: 1, caId: 1000, code: 1, value: '启用', tag: null, flag: null, color: null, css: null, sort: 1, remark: null, createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00' },
      { id: 2, enable: 1, caId: 1000, code: 0, value: '停用', tag: null, flag: null, color: null, css: null, sort: 2, remark: null, createBy: null, createTime: '2026-11-04 09:00:00', updateBy: null, updateTime: '2026-11-04 09:00:00' },
    ])
    const data = await listDetails(1000)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/dicts/detail/list?caId=1000')
    expect(init.method).toBe('GET')
    expect(Array.isArray(data)).toBe(true)
    // V9: value is the single text field (label dropped).
    expect(data.map((detail) => detail.value)).toEqual(['启用', '停用'])
  })

  it('createDetail posts /system/dicts/detail and updateDetail/deleteDetail round-trip', async () => {
    const fetchMock = stubOkSequence([3, true, true])
    const body = { caId: 1000, value: '维护中', sort: 3 }
    expect(await createDetail(body)).toBe(3)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/dicts/detail')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(await updateDetail(3, { value: '维护中2' })).toBe(true)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/system/dicts/detail/3')
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe('PUT')
    expect(await deleteDetail(3)).toBe(true)
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/system/dicts/detail/3')
    expect((fetchMock.mock.calls[2][1] as RequestInit).method).toBe('DELETE')
  })
})

describe('logs client (task 4.3)', () => {
  it('listLogs GETs /system/logs with the page/pageSize/type/uid/dateYm query and unwraps the IPage shape', async () => {
    const fetchMock = stubOk({
      records: [
        { id: 3, dateYm: 202611, title: '用户管理', descr: '重置用户密码', type: '重置密码', uid: 1, account: 'admin', actionTime: '2026-11-03 16:20:11', spent: 34, method: 'PUT', name: 'resetPassword', pack: 'org.birdbro.lumen.system.controller.UserInfoController', url: '/api/v1/system/users/2/password', session: null, mobile: false, ip: '127.0.0.1', os: 'Mac OS X', browser: 'Chrome', engine: 'Blink', record: 0, param: '{"newPassword":"******"}', result: 'true', exception: null, app: 'lumen-system', createTime: '2026-11-03 16:20:11' },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })
    const page = await listLogs({ page: 1, pageSize: 20, type: '重置密码', uid: 1, dateYm: 202611 })
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe(
      '/api/v1/system/logs?page=1&pageSize=20&type=%E9%87%8D%E7%BD%AE%E5%AF%86%E7%A0%81&uid=1&dateYm=202611',
    )
    expect(init.method).toBe('GET')
    expect(page.records).toHaveLength(1)
    expect(page.records[0].actionTime).toBe('2026-11-03 16:20:11')
    expect(page.total).toBe(1)
  })

  it('listLogs sends only the paging keys when the optional filters are omitted', async () => {
    const fetchMock = stubOk({ records: [], total: 0, size: 20, current: 1, pages: 0 })
    await listLogs({ page: 1, pageSize: 20 })
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/logs?page=1&pageSize=20')
  })
})

describe('orgs client profile fields (org-management-redesign-web 1.1)', () => {
  /** Backend V17 org_info row shape (the /tree nodes are full entities). */
  const CAMPUS_NODE = {
    id: 274, upId: 1, level: 2, type: 1, name: '总院区',
    code: 'CAMPUS-1', shortName: null, status: 1,
    provinceCode: '310000', cityCode: '310100', districtCode: '310101',
    address: '浦东新区测试路1号', contact: '院区前台', phone: '021-60000000',
    email: 'campus@example.org', logoUrl: null, sort: 0, isDefault: 1,
    createBy: null, createTime: '2026-11-06 10:00:00', updateBy: null, updateTime: '2026-11-06 10:00:00',
    children: null,
  }

  it('orgTree surfaces the hospital/campus profile fields on each node', async () => {
    const fetchMock = stubOk([CAMPUS_NODE])
    const data = await orgTree()
    expect(lastCall(fetchMock).url).toBe('/api/v1/system/orgs/tree')
    expect(data[0].code).toBe('CAMPUS-1')
    expect(data[0].status).toBe(1)
    expect(data[0].isDefault).toBe(1)
    expect(data[0].address).toBe('浦东新区测试路1号')
    expect(data[0].shortName).toBeNull()
  })

  it('createOrg posts the campus profile body and unwraps the new id', async () => {
    const fetchMock = stubOk(275)
    const body = {
      upId: 1,
      code: 'CAMPUS-EAST',
      name: '东院区',
      shortName: '东院',
      status: 1,
      provinceCode: '310000',
      address: '东区测试路2号',
      sort: 2,
    }
    const data = await createOrg(body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(275)
  })

  it('updateOrg carries profile fields and status (type/parent stay out of the body)', async () => {
    const fetchMock = stubOk(true)
    const body = {
      code: 'CAMPUS-1',
      name: '总院区改名',
      status: 1,
      contact: '新的联系人',
      isDefault: 1,
    }
    const data = await updateOrg(274, body)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/orgs/274')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(true)
  })
})

describe('org-deps client page query (org-management-redesign-web 1.2)', () => {
  const DEP_ROW = {
    id: 1, orgId: 274, code: 'radiology', name: '放射科', status: 1,
    createBy: null, createTime: '2026-11-06 10:00:00', updateBy: null, updateTime: '2026-11-06 10:00:00',
  }

  it('pageDep GETs /system/org-deps/page with the full filter set and unwraps the IPage shape', async () => {
    const fetchMock = stubOk({ records: [DEP_ROW], total: 1, size: 20, current: 1, pages: 1 })
    const page = await pageDep({ orgId: 274, keyword: '放射', status: 1, pageNum: 1, pageSize: 20 })
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps/page?pageNum=1&pageSize=20&orgId=274&keyword=%E6%94%BE%E5%B0%84&status=1')
    expect(init.method).toBe('GET')
    expect(page.records).toHaveLength(1)
    expect(page.records[0].status).toBe(1)
    expect(page.total).toBe(1)
  })

  it('pageDep falls back to the paging defaults and omits absent filters', async () => {
    const fetchMock = stubOk({ records: [], total: 0, size: 20, current: 1, pages: 0 })
    await pageDep()
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/org-deps/page?pageNum=1&pageSize=20')
  })

  it('createDep carries the department status field', async () => {
    const fetchMock = stubOk(5)
    const body = { orgId: 274, code: 'pathology', name: '病理科', status: 1 }
    const data = await createDep(body)
    const { init } = lastCall(fetchMock)
    expect(init.body).toBe(JSON.stringify(body))
    expect(data).toBe(5)
  })

  it('updateDep carries status but never the code', async () => {
    const fetchMock = stubOk(true)
    await updateDep(5, { name: '病理科2', status: 0 })
    const { init } = lastCall(fetchMock)
    expect(init.body).toBe(JSON.stringify({ name: '病理科2', status: 0 }))
  })
})

describe('sign-logs client (sign-log-query b4, task 4.1)', () => {
  it('listSignLogs GETs /system/logs/sign with the page/pageSize/account/uid/dateYm query and unwraps the IPage shape', async () => {
    const fetchMock = stubOk({
      records: [
        { id: 1, dateYm: 202611, msg: '登录成功', descr: '登录成功', type: 1, uid: 1, account: 'admin', actionTime: '2026-11-04 10:00:00', mobile: false, ip: '127.0.0.1', os: 'Mac OS X', browser: 'Chrome', engine: 'Blink', session: null, app: 'lumen-system', deleted: 0, createTime: '2026-11-04 10:00:02' },
      ],
      total: 1,
      size: 20,
      current: 1,
      pages: 1,
    })
    const page = await listSignLogs({ page: 1, pageSize: 20, account: 'admin', uid: 1, dateYm: 202611 })
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/logs/sign?page=1&pageSize=20&account=admin&uid=1&dateYm=202611')
    expect(init.method).toBe('GET')
    expect(page.records).toHaveLength(1)
    expect(page.records[0].actionTime).toBe('2026-11-04 10:00:00')
    // Design D7: credential material never serializes to the console.
    expect(page.records[0]).not.toHaveProperty('token')
    expect(page.total).toBe(1)
  })

  it('listSignLogs sends only the paging keys when the optional filters are omitted', async () => {
    const fetchMock = stubOk({ records: [], total: 0, size: 20, current: 1, pages: 0 })
    await listSignLogs({ page: 1, pageSize: 20 })
    const { url } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/logs/sign?page=1&pageSize=20')
  })
})

describe('multipart upload transport (org-logo-upload-web 1.1)', () => {
  const PNG = new File([new Uint8Array([137, 80, 78, 71])], 'logo.png', { type: 'image/png' })

  afterEach(() => {
    // Restore the module-level token/expiry slots the other suites rely on.
    configureApiClient({ getToken: () => null, onSessionExpired: () => undefined })
  })

  it('apiUpload posts the FormData part with Bearer and NO Content-Type, unwraps {path}', async () => {
    configureApiClient({ getToken: () => 'jwt-logo' })
    const fetchMock = stubOk({ path: '/profile/avatar/2026/11/06/x.png' })
    const data = await apiUpload<{ path: string }>('/system/common/upload/image', PNG)
    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/system/common/upload/image')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect((init.body as FormData).get('file')).toBe(PNG)
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer jwt-logo')
    // The browser must own the multipart boundary - the transport never sets Content-Type.
    expect(headers.get('Content-Type')).toBeNull()
    expect(data.path).toBe('/profile/avatar/2026/11/06/x.png')
  })

  it('apiUpload without a token sends no Authorization header', async () => {
    const fetchMock = stubOk({ path: '/profile/avatar/x.png' })
    await apiUpload('/system/common/upload/image', PNG)
    expect(new Headers(lastCall(fetchMock).init.headers).get('Authorization')).toBeNull()
  })

  it('shares the JSON transport envelope semantics: 203 fires onSessionExpired then throws', async () => {
    const onSessionExpired = vi.fn()
    configureApiClient({ onSessionExpired })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        json: async () => ({ timestamp: '', code: '203', msg: '请先登录', data: null }),
      })),
    )
    await expect(apiUpload('/system/common/upload/image', PNG)).rejects.toMatchObject({ code: '203' })
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('shares the JSON transport envelope semantics: A0400 throws ApiError with code+msg', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        json: async () => ({ timestamp: '', code: 'A0400', msg: '仅支持 png/jpg/jpeg/webp 图片', data: null }),
      })),
    )
    await expect(apiUpload('/system/common/upload/image', PNG)).rejects.toMatchObject({
      code: 'A0400',
      msg: '仅支持 png/jpg/jpeg/webp 图片',
    })
  })

  it('apiRequest JSON regression pin: Content-Type stays application/json with a stringified body', async () => {
    const fetchMock = stubOk(true)
    await updateOrg(2, { name: '改名' })
    const { init } = lastCall(fetchMock)
    expect(init.body).toBe(JSON.stringify({ name: '改名' }))
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })
})
