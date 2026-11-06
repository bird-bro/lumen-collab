import { Form, Select, theme } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { listCityDicts } from '@/api/dicts'
import type { CityDict } from '@/api/types'

/**
 * 地区 cascade - the OPS org-form region row (one 地区 label, three inline
 * filterable selects: 请选择省份 / 请选择市 / 请选择区县), rebuilt on AntD 5
 * for the org profile dialogs (user direction 2026-11-06: the three free-text
 * 省份编码/城市编码/区县编码 inputs were wrong - divisions are picked, not typed).
 *
 * Options stream from the real read-only divisions dict (GET
 * /system/dicts/city/list, spec `system-data` § City Reference Query - no mock
 * layer): level-1 provinces on mount, then each level fetches by its parent's
 * dict id (dict_city lineage: `up_id` chains the levels). A parent change
 * clears its descendants (form values AND option lists); a stale fetch is
 * dropped by comparing against the request's parent code, so fast
 * re-selection can never resurrect another branch's options. The form binds
 * the GB division codes (OrgNode.provinceCode/cityCode/districtCode strings;
 * dict_city id == code on levels 1-3, but the id is always resolved through
 * the loaded parent list rather than assumed).
 *
 * Must render inside the org <Form>: the instance is resolved with
 * Form.useFormInstance, and edit backfill reads the values the dialog preset
 * before mount (drawer destroyOnHidden remounts this row per dialog open).
 */

/** Select options for one level: label = display name, value = the stored GB code. */
function toOptions(rows: CityDict[]) {
  return rows.map((row) => ({ value: row.code, label: row.name }))
}

/** The dict id of a stored division code - null when absent or unknown to the dict. */
function parentIdOf(rows: CityDict[], code: string | null | undefined): number | null {
  if (!code) return null
  return rows.find((row) => row.code === code)?.id ?? null
}

export function RegionSelects() {
  const { token } = theme.useToken()
  const form = Form.useFormInstance()
  const [provinces, setProvinces] = useState<CityDict[]>([])
  const [cities, setCities] = useState<CityDict[]>([])
  const [districts, setDistricts] = useState<CityDict[]>([])
  // Stale-response guards: only the fetch issued for the CURRENTLY selected
  // parent code may land (see module doc).
  const provinceRequest = useRef<string | null>(null)
  const cityRequest = useRef<string | null>(null)

  const provinceCode: string | undefined = Form.useWatch('provinceCode', form)
  const cityCode: string | undefined = Form.useWatch('cityCode', form)

  // Mount: provinces always; on edit, chain-load the stored province's cities
  // and the stored city's districts so the lower selects resolve their labels.
  useEffect(() => {
    let cancelled = false
    void (async () => {
      const provinceRows = (await listCityDicts({ level: 1 })) ?? []
      if (cancelled) return
      setProvinces(provinceRows)
      const storedProvince = form.getFieldValue('provinceCode') as string | null | undefined
      if (!storedProvince) return
      provinceRequest.current = storedProvince
      const provinceId = parentIdOf(provinceRows, storedProvince)
      if (provinceId === null) return
      const cityRows = (await listCityDicts({ upId: provinceId })) ?? []
      if (cancelled || provinceRequest.current !== storedProvince) return
      setCities(cityRows)
      const storedCity = form.getFieldValue('cityCode') as string | null | undefined
      if (!storedCity) return
      cityRequest.current = storedCity
      const cityId = parentIdOf(cityRows, storedCity)
      if (cityId === null) return
      const districtRows = (await listCityDicts({ upId: cityId })) ?? []
      if (cancelled || cityRequest.current !== storedCity) return
      setDistricts(districtRows)
    })()
    return () => {
      cancelled = true
    }
  }, [form])

  const onProvinceChange = (code: string | undefined) => {
    form.setFieldsValue({ cityCode: undefined, districtCode: undefined })
    setCities([])
    setDistricts([])
    provinceRequest.current = code ?? null
    const parentId = parentIdOf(provinces, code)
    if (parentId === null) return
    void listCityDicts({ upId: parentId }).then((rows) => {
      if (provinceRequest.current === code) setCities(rows ?? [])
    })
  }

  const onCityChange = (code: string | undefined) => {
    form.setFieldsValue({ districtCode: undefined })
    setDistricts([])
    cityRequest.current = code ?? null
    const parentId = parentIdOf(cities, code)
    if (parentId === null) return
    void listCityDicts({ upId: parentId }).then((rows) => {
      if (cityRequest.current === code) setDistricts(rows ?? [])
    })
  }

  const selectStyle = { flex: 1, minWidth: 0 }

  return (
    <div style={{ display: 'flex', gap: token.marginXS }}>
      <Form.Item name="provinceCode" noStyle>
        <Select
          aria-label="省份"
          style={selectStyle}
          placeholder="请选择省份"
          showSearch
          optionFilterProp="label"
          allowClear
          options={toOptions(provinces)}
          onChange={onProvinceChange}
        />
      </Form.Item>
      <Form.Item name="cityCode" noStyle>
        <Select
          aria-label="城市"
          style={selectStyle}
          placeholder="请选择市"
          showSearch
          optionFilterProp="label"
          allowClear
          disabled={!provinceCode}
          options={toOptions(cities)}
          onChange={onCityChange}
        />
      </Form.Item>
      <Form.Item name="districtCode" noStyle>
        <Select
          aria-label="区县"
          style={selectStyle}
          placeholder="请选择区县"
          showSearch
          optionFilterProp="label"
          allowClear
          disabled={!cityCode}
          options={toOptions(districts)}
        />
      </Form.Item>
    </div>
  )
}
