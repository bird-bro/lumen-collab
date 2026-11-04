import { defineMock } from 'vite-plugin-mock-dev-server'

import {
  fail,
  guard,
  rowStamp,
  ok,
  pageOf,
  seedDictCatal,
  seedDictDetails,
  state,
  type MockDictCatal,
  type MockDictDetail,
} from './_fixtures'

/** Shared dev-session state (globalThis bucket - see _fixtures.ts for the plugin-convention note). */
const catal = () =>
  state<Record<string, MockDictCatal[]>>('dictCatal', () => ({ list: seedDictCatal() })).list
const details = () =>
  state<Record<string, MockDictDetail[]>>('dictDetails', () => ({ list: seedDictDetails() })).list

/**
 * Dictionary endpoints (DictCatalController + DictDetailController). Catalog list is
 * paginated (IPage shape, `pageNum`/`pageSize` from the copied PageSizeConfig, id desc,
 * name/cls filters); detail list is a plain sort-asc array by `caCode`. Duplicate live
 * catalog code / detail (caCode, value) reject with A0714; unknown update targets reject
 * B0031; mutations return the new id (POST) or Boolean.TRUE (PUT/DELETE).
 */
export default defineMock([
  // --- catalog (/api/v1/system/dicts/catal) ---
  {
    url: '/api/v1/system/dicts/catal/list',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const pageNum = Number(request.query?.pageNum ?? 1)
      const pageSize = Number(request.query?.pageSize ?? 20)
      const name = request.query?.name as string | undefined
      const cls = request.query?.cls != null ? Number(request.query.cls) : null
      const rows = catal()
        .filter(c => (name == null || c.name.includes(name)) && (cls == null || c.cls === cls))
        .sort((a, b) => b.id - a.id) // id desc (the copied getPage convention)
      return ok(pageOf(rows.slice((pageNum - 1) * pageSize, pageNum * pageSize), pageNum, pageSize))
    },
  },
  {
    url: '/api/v1/system/dicts/catal',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as Partial<MockDictCatal>
      if (!body.code) return fail('A0400', 'code must not be blank')
      if (!body.name) return fail('A0400', 'name must not be blank')
      if (catal().some(c => c.code === body.code)) {
        return fail('A0714', `数据重复：dict catalog code already exists: ${body.code}`)
      }
      const id = Math.max(...catal().map(c => c.id), 0) + 1
      catal().push({
        id,
        enable: body.enable ?? 1,
        code: body.code,
        name: body.name,
        sort: body.sort ?? 0,
        remark: body.remark ?? null,
        cls: body.cls ?? null,
        initial: body.initial ?? null,
        createBy: null,
        createTime: rowStamp(),
        updateBy: null,
        updateTime: rowStamp(),
      })
      return ok(id)
    },
  },
  {
    url: '/api/v1/system/dicts/catal/:id',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const row = catal().find(c => c.id === id)
      if (!row) return fail('B0031', `dict catalog id ${id} does not exist`)
      const body = (request.body ?? {}) as Partial<MockDictCatal>
      if (body.code != null) {
        if (catal().some(c => c.code === body.code && c.id !== id)) {
          return fail('A0714', `数据重复：dict catalog code already exists: ${body.code}`)
        }
        row.code = body.code
      }
      if (body.name != null) row.name = body.name
      if (body.enable != null) row.enable = body.enable
      if (body.sort != null) row.sort = body.sort
      if (body.remark !== undefined) row.remark = body.remark ?? null
      if (body.cls !== undefined) row.cls = body.cls ?? null
      if (body.initial !== undefined) row.initial = body.initial ?? null
      row.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/dicts/catal/:id',
    method: 'DELETE',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Bare removeById semantics: the backend always returns TRUE.
      const id = Number(request.params?.id)
      const index = catal().findIndex(c => c.id === id)
      if (index >= 0) catal().splice(index, 1)
      return ok(true)
    },
  },
  // --- detail (/api/v1/system/dicts/detail) ---
  {
    url: '/api/v1/system/dicts/detail/list',
    method: 'GET',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // @RequestParam("caCode") is required - a missing param renders A0410
      // (GlobalExceptionHandler.handleMissingParam).
      const caCode = request.query?.caCode as string | undefined
      if (caCode == null) return fail('A0410', '请求必填参数为空')
      const rows = details()
        .filter(d => d.caCode === caCode)
        .sort((a, b) => a.sort - b.sort) // sort asc
      return ok(rows)
    },
  },
  {
    url: '/api/v1/system/dicts/detail',
    method: 'POST',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const body = (request.body ?? {}) as Partial<MockDictDetail>
      if (body.caId == null || !body.label) {
        return fail('A0400', 'dict detail caId and label are required')
      }
      const parent = catal().find(c => c.id === body.caId)
      if (!parent) return fail('A0400', `dict catalog does not exist: caId ${body.caId}`)
      if (body.value != null && details().some(d => d.caCode === parent.code && d.value === body.value)) {
        return fail('A0714', `数据重复：dict detail value already exists under caCode ${parent.code}: ${body.value}`)
      }
      const id = Math.max(...details().map(d => d.id), 0) + 1
      details().push({
        id,
        enable: body.enable ?? 1,
        caId: parent.id,
        caCode: parent.code, // server-resolved anchor (double-anchor consistency)
        code: body.code ?? null,
        value: body.value ?? null,
        label: body.label,
        color: body.color ?? null,
        css: body.css ?? null,
        sort: body.sort ?? 0,
        back1: body.back1 ?? null,
        back2: body.back2 ?? null,
        remark: body.remark ?? null,
        initial: body.initial ?? null,
        createBy: null,
        createTime: rowStamp(),
        updateBy: null,
        updateTime: rowStamp(),
      })
      return ok(id)
    },
  },
  {
    url: '/api/v1/system/dicts/detail/:id',
    method: 'PUT',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      const id = Number(request.params?.id)
      const row = details().find(d => d.id === id)
      if (!row) return fail('B0031', `dict detail id ${id} does not exist`)
      const body = (request.body ?? {}) as Partial<MockDictDetail>
      // A re-anchor must resolve to a live catalog; caCode follows it.
      const targetCaId = body.caId ?? row.caId
      const parent = catal().find(c => c.id === targetCaId)
      if (!parent) return fail('A0400', `dict catalog does not exist: caId ${targetCaId}`)
      const targetValue = body.value ?? row.value
      if (
        targetValue != null
        && details().some(d => d.caCode === parent.code && d.value === targetValue && d.id !== id)
      ) {
        return fail('A0714', `数据重复：dict detail value already exists under caCode ${parent.code}: ${targetValue}`)
      }
      if (body.caId != null) {
        row.caId = parent.id
        row.caCode = parent.code
      }
      if (body.code !== undefined) row.code = body.code ?? null
      if (body.value !== undefined) row.value = body.value ?? null
      if (body.label != null) row.label = body.label
      if (body.sort != null) row.sort = body.sort
      if (body.enable != null) row.enable = body.enable
      if (body.color !== undefined) row.color = body.color ?? null
      if (body.css !== undefined) row.css = body.css ?? null
      if (body.remark !== undefined) row.remark = body.remark ?? null
      if (body.back1 !== undefined) row.back1 = body.back1 ?? null
      if (body.back2 !== undefined) row.back2 = body.back2 ?? null
      row.updateTime = rowStamp()
      return ok(true)
    },
  },
  {
    url: '/api/v1/system/dicts/detail/:id',
    method: 'DELETE',
    body: (request) => {
      const denied = guard(request)
      if (denied) return denied
      // Bare removeById semantics: the backend always returns TRUE.
      const id = Number(request.params?.id)
      const index = details().findIndex(d => d.id === id)
      if (index >= 0) details().splice(index, 1)
      return ok(true)
    },
  },
])
