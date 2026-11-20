import { Col, Form, Input, Row, Select, Switch } from 'antd'
import { theme } from 'antd'
import { FormInstance } from 'antd'

/**
 * PatientDemographicsForm (us-station-registration-web task 1.2; spec delta
 * `web-us-station` § Patient Resolution Flow, decision #5): the SHARED patient
 * demographics field group - composed by the US workstation registration drawer
 * today, radiology/endo registration drawers later. Renders INSIDE the host's
 * AntD Form context (Form.Items with fixed names: idType/idNo/cardNo/name/gender/
 * phone/age/patientSource/admissionNumber/bedNumber/height/weight) so the host
 * reads the whole create body from one form instance.
 *
 * The 急诊占位 (PROVISIONAL) toggle (decision #3): an unnamed placeholder archive -
 * 姓名输入禁用（无名氏，系统侧落占位行），影像号照发. The toggle is a form field
 * `placeholder` (boolean) the host submits as the 占位 marker; the switch-row
 * layout follows the exam-item 常用项目 precedent (user ruling 2026-11-19: a lone
 * boolean switch never gets a standalone Form.Item row - label left, switch right).
 */
export const DEMOGRAPHICS_FIELDS = [
  'idType',
  'idNo',
  'cardNo',
  'name',
  'gender',
  'phone',
  'age',
  'patientSource',
  'admissionNumber',
  'bedNumber',
  'height',
  'weight',
  'placeholder',
] as const;

export function PatientDemographicsForm() {
  const { token } = theme.useToken()
  const placeholder = Form.useWatch('placeholder')
  return (
    <>
      {/* the 急诊占位 switch-row (lone boolean: label left, switch right) */}
      <div
        data-item-common-row
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: token.marginLG,
        }}
      >
        <label htmlFor="us-placeholder">急诊占位</label>
        <Form.Item name="placeholder" valuePropName="checked" noStyle>
          <Switch id="us-placeholder" aria-label="急诊占位" />
        </Form.Item>
      </div>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item
            name="name"
            label="姓名"
            rules={[{ required: true, message: '姓名不能为空' }]}
          >
            <Input
              aria-label="姓名"
              disabled={Boolean(placeholder)}
              placeholder={placeholder ? '无名氏（占位）' : undefined}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="gender" label="性别">
            <Select
              aria-label="性别"
              allowClear
              options={[
                { value: 1, label: '男' },
                { value: 2, label: '女' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="idType" label="证件类型">
            <Select
              aria-label="证件类型"
              allowClear
              placeholder="身份证 / 就诊卡 …"
              options={[
                { value: '身份证', label: '身份证' },
                { value: '就诊卡', label: '就诊卡' },
                { value: '医保卡', label: '医保卡' },
              ]}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="idNo" label="证件号">
            <Input aria-label="证件号" placeholder="解析链第一键" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="cardNo" label="就诊卡号">
            <Input aria-label="就诊卡号" placeholder="解析链第二键" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="phone" label="手机号">
            <Input aria-label="手机号" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="age" label="年龄">
            <Input aria-label="年龄" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="patientSource" label="患者来源">
            <Select
              aria-label="患者来源"
              allowClear
              options={[
                { value: '门诊', label: '门诊' },
                { value: '住院', label: '住院' },
                { value: '急诊', label: '急诊' },
                { value: '体检', label: '体检' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="admissionNumber" label="住院号">
            <Input aria-label="住院号" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="bedNumber" label="床号">
            <Input aria-label="床号" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={12}>
        <Col span={12}>
          <Form.Item name="height" label="身高(cm)">
            <Input aria-label="身高" type="number" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="weight" label="体重(kg)">
            <Input aria-label="体重" type="number" />
          </Form.Item>
        </Col>
      </Row>
    </>
  )
}

/** Convenience: the identity echo fills the demographics fields on a resolved pick. */
export function demographicsFromIdentity(identity: Record<string, unknown>): Record<string, unknown> {
  const {
    patUid: _patUid,
    patId: _patId,
    state: _state,
    birthDate: _birthDate,
    ...demographics
  } = identity
  return demographics
}

export type { FormInstance }
