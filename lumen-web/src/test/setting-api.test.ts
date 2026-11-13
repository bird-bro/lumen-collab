import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  createBodyGroup,
  deleteBodyGroup,
  listBodyGroups,
  updateBodyGroup,
} from '@/api/exam-body-group'
import {
  createExamBody,
  deleteExamBody,
  getExamBody,
  listExamBodies,
  updateExamBody,
} from '@/api/exam-body'
import {
  createExamMethod,
  deleteExamMethod,
  listExamMethods,
  pageExamMethods,
  updateExamMethod,
} from '@/api/exam-method'
import {
  createExamItem,
  deleteExamItem,
  getExamItem,
  pageExamItems,
  updateExamItem,
} from '@/api/exam-item'
import {
  createRepTemp,
  deleteRepTemp,
  pageRepTemps,
  repTempTree,
  updateRepTemp,
} from '@/api/rep-temp'

/**
 * 检查设置 typed clients (exam-setting-web task 1.1; paths verbatim from the
 * exam-setting endpoint deltas). Fetch is stubbed at the transport seam; each
 * client is asserted on method + path + query/body (the OrgDeviceController
 * verb style: kebab plural resources, GET /page|/list|/{id}, POST, PUT /{id},
 * DELETE /{id}) AND on the unwrapped payload shape (plain arrays vs IPage
 * `Page<T>`, JSON-number ids). DTO truth = the exam-setting spec deltas.
 */


function stubOk(data: unknown): { mock: { calls: unknown[][] } } {
  const fetchMock = vi.fn(async () => ({
    json: async () => ({ timestamp: '2026-11-12 10:00:00.000', code: '200', msg: 'OK', data }),
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

describe('exam-body-group clients', () => {
  it('listBodyGroups GETs /setting/exam-body-groups/list with the modality query', async () => {
    const group = { id: 5, modality: 'CT', name: '胸部', sort: 1, enable: 1 }
    const fetchMock = stubOk([group])

    const rows = await listBodyGroups('CT')

    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/setting/exam-body-groups/list?modality=CT')
    expect(init.method).toBe('GET')
    expect(rows).toEqual([group])
  })

  it('createBodyGroup POSTs the body and returns the JSON-number id', async () => {
    const fetchMock = stubOk(7)

    const id = await createBodyGroup({ modality: 'CT', name: '头颅', sort: 2, enable: 1 })

    const { url, init } = lastCall(fetchMock)
    expect(url).toBe('/api/v1/setting/exam-body-groups')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({
      modality: 'CT', name: '头颅', sort: 2, enable: 1,
    })
    expect(id).toBe(7)
  })

  it('updateBodyGroup PUTs /{id}; deleteBodyGroup DELETEs /{id}', async () => {
    const putMock = stubOk(true)
    expect(await updateBodyGroup(5, { modality: 'CT', name: '胸部改', sort: null, enable: 1 })).toBe(true)
    expect(lastCall(putMock)).toMatchObject({
      url: '/api/v1/setting/exam-body-groups/5',
    })
    expect(lastCall(putMock).init.method).toBe('PUT')

    const deleteMock = stubOk(true)
    expect(await deleteBodyGroup(5)).toBe(true)
    expect(lastCall(deleteMock).url).toBe('/api/v1/setting/exam-body-groups/5')
    expect(lastCall(deleteMock).init.method).toBe('DELETE')
  })
})

describe('exam-body clients', () => {
  it('listExamBodies GETs /setting/exam-bodies/list, groupId optional', async () => {
    const tree = [
      {
        id: 9, modality: 'CT', groupId: 5, upId: null, name: '胸部', code: null,
        sort: 1, enable: 1, children: [
          { id: 10, modality: 'CT', groupId: 5, upId: 9, name: '肺', code: null, sort: 1, enable: 1, children: [] },
        ],
      },
    ]
    const fetchMock = stubOk(tree)

    const rows = await listExamBodies('CT', 5)

    expect(lastCall(fetchMock).url).toBe(
      '/api/v1/setting/exam-bodies/list?modality=CT&groupId=5')
    expect(rows[0]?.children[0]?.name).toBe('肺')

    const fetchMock2 = stubOk([])
    await listExamBodies('CT')
    expect(lastCall(fetchMock2).url).toBe('/api/v1/setting/exam-bodies/list?modality=CT')
  })

  it('getExamBody GETs /{id}; createExamBody POSTs; updateExamBody PUTs /{id}; deleteExamBody DELETEs /{id}', async () => {
    const body = { id: 9, modality: 'CT', groupId: 5, upId: null, name: '胸部', code: 'XB', sort: 1, enable: 1 }
    const getMock = stubOk(body)
    expect(await getExamBody(9)).toEqual(body)
    expect(lastCall(getMock).url).toBe('/api/v1/setting/exam-bodies/9')

    const postMock = stubOk(11)
    expect(await createExamBody({ modality: 'CT', groupId: 5, upId: null, name: '腹部', code: null, sort: 2, enable: 1 })).toBe(11)
    expect(lastCall(postMock).init.method).toBe('POST')

    const putMock = stubOk(true)
    await updateExamBody(9, { modality: 'CT', groupId: 5, upId: null, name: '胸部改', code: null, sort: null, enable: 1 })
    expect(lastCall(putMock).init.method).toBe('PUT')

    const deleteMock = stubOk(true)
    await deleteExamBody(9)
    expect(lastCall(deleteMock).init.method).toBe('DELETE')
  })
})

describe('exam-method clients', () => {
  it('pageExamMethods GETs /setting/exam-methods/page with filters', async () => {
    const page = { total: 1, records: [{ id: 3, modality: 'CT', name: '平扫', code: null, sort: 1, enable: 1 }] }
    const fetchMock = stubOk(page)

    const result = await pageExamMethods({ modality: 'CT', keyword: '平扫', pageNum: 2, pageSize: 10 })

    const { url } = lastCall(fetchMock)
    expect(url).toContain('/api/v1/setting/exam-methods/page?')
    expect(url).toContain('modality=CT')
    expect(url).toContain('keyword=')
    expect(url).toContain('pageNum=2')
    expect(url).toContain('pageSize=10')
    expect(result.records[0]?.name).toBe('平扫')
  })

  it('listExamMethods GETs /list; CRUD verbs round-trip', async () => {
    const listMock = stubOk([{ id: 3, modality: 'CT', name: '平扫', code: null, sort: 1, enable: 1 }])
    await listExamMethods('CT')
    expect(lastCall(listMock).url).toBe('/api/v1/setting/exam-methods/list?modality=CT')

    const postMock = stubOk(4)
    expect(await createExamMethod({ modality: 'CT', name: '增强', code: 'ZQ', sort: 2, enable: 1 })).toBe(4)
    expect(lastCall(postMock).init.method).toBe('POST')

    const putMock = stubOk(true)
    await updateExamMethod(3, { modality: 'CT', name: '平扫改', code: null, sort: null, enable: 1 })
    expect(lastCall(putMock).init.method).toBe('PUT')

    const deleteMock = stubOk(true)
    await deleteExamMethod(3)
    expect(lastCall(deleteMock).init.method).toBe('DELETE')
  })
})

describe('exam-item clients', () => {
  it('pageExamItems GETs /setting/exam-items/page with group/keyword/isCommon filters', async () => {
    const page = {
      total: 1,
      records: [{
        id: 21, modality: 'CT', exam: '胸部CT平扫', code: 'XBCT', bodyId: 9,
        price: 12.5, rate: 1, tag: 1, scoreRep: 7, scoreAud: 3, isCommon: true,
        sort: 1, enable: 1, remark: null, bodyName: '胸部', groupName: '胸部',
        methodNames: '平扫,增强',
      }],
    }
    const fetchMock = stubOk(page)

    const result = await pageExamItems({
      modality: 'CT', groupId: 5, keyword: '胸部', isCommon: true, pageNum: 1, pageSize: 20,
    })

    const { url } = lastCall(fetchMock)
    expect(url).toContain('/api/v1/setting/exam-items/page?')
    expect(url).toContain('groupId=5')
    expect(url).toContain('isCommon=true')
    expect(result.records[0]?.methodNames).toBe('平扫,增强')
    expect(result.records[0]?.bodyName).toBe('胸部')
  })

  it('getExamItem returns the display-resolved VO; CRUD verbs round-trip', async () => {
    const item = {
      id: 21, modality: 'CT', exam: '胸部CT平扫', code: 'XBCT', bodyId: 9,
      price: null, rate: null, tag: null, scoreRep: null, scoreAud: null,
      isCommon: false, sort: null, enable: 1, remark: null,
      bodyName: '胸部', groupName: '胸部', methodNames: '',
    }
    const getMock = stubOk(item)
    expect(await getExamItem(21)).toEqual(item)
    expect(lastCall(getMock).url).toBe('/api/v1/setting/exam-items/21')

    const postMock = stubOk(22)
    expect(await createExamItem({
      modality: 'CT', exam: '头颅CT', code: null, bodyId: 10,
      methodIds: [3, 4], price: 30, rate: null, tag: null,
      scoreRep: null, scoreAud: null, isCommon: false, sort: null, enable: 1, remark: null,
    })).toBe(22)
    const { init } = lastCall(postMock)
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body)).methodIds).toEqual([3, 4])

    const putMock = stubOk(true)
    await updateExamItem(21, {
      modality: 'CT', exam: '胸部CT平扫改', code: null, bodyId: 9,
      methodIds: [4], price: null, rate: null, tag: null,
      scoreRep: null, scoreAud: null, isCommon: true, sort: null, enable: 1, remark: null,
    })
    expect(lastCall(putMock).init.method).toBe('PUT')

    const deleteMock = stubOk(true)
    await deleteExamItem(21)
    expect(lastCall(deleteMock).init.method).toBe('DELETE')
  })
})

describe('rep-temp clients', () => {
  it('repTempTree GETs /setting/rep-temps/tree with modality+type', async () => {
    const tree = [
      { id: 31, name: ' folders', flag: 0, upId: null, children: [
        { id: 32, name: '结节模板', flag: 1, upId: 31, children: [] },
      ] },
    ]
    const fetchMock = stubOk(tree)

    const rows = await repTempTree('CT', 2)

    expect(lastCall(fetchMock).url).toBe('/api/v1/setting/rep-temps/tree?modality=CT&type=2')
    expect(rows[0]?.children[0]?.name).toBe('结节模板')
  })

  it('pageRepTemps GETs /page under one parent; CRUD verbs round-trip', async () => {
    const page = { total: 1, records: [{ id: 32, type: 1, ownerUid: null, modality: 'CT', flag: 1, upId: 31, name: '结节模板', finding: '表现', impression: '意见' }] }
    const pageMock = stubOk(page)
    const result = await pageRepTemps({ type: 1, modality: 'CT', upId: 31, name: '结节', pageNum: 1, pageSize: 20 })
    const { url } = lastCall(pageMock)
    expect(url).toContain('/api/v1/setting/rep-temps/page?')
    expect(url).toContain('upId=31')
    expect(result.records[0]?.finding).toBe('表现')

    const postMock = stubOk(33)
    expect(await createRepTemp({ type: 2, modality: 'CT', flag: 1, upId: 31, name: '私有模板', finding: 'F', impression: 'I' })).toBe(33)
    expect(lastCall(postMock).init.method).toBe('POST')

    const putMock = stubOk(true)
    await updateRepTemp(32, { modality: 'CT', flag: 1, upId: 31, name: '改', finding: null, impression: null })
    expect(lastCall(putMock).init.method).toBe('PUT')

    const deleteMock = stubOk(true)
    await deleteRepTemp(32)
    expect(lastCall(deleteMock).init.method).toBe('DELETE')
  })
})
