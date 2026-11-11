import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Form } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SignatureUploadField } from '@/pages/system/users/signature-upload'
import * as commonApi from '@/api/common'
import { ApiError } from '@/api/client'

/**
 * The signature upload control (user-edit-delete-profile-web task 2.1; spec
 * scenario "Signature upload reuses the common transport"): a controlled form
 * control over the common image upload endpoint with the RCP's narrower
 * whitelist (jpg/png/jpeg only - D3) and the 2MB cap. Every behavior below is
 * proven at the client seam (`vi.mock('@/api/common')`), never via page data.
 */
vi.mock('@/api/common')

/** The wrapper's injected onChange is Form's; the harness reads it back out. */
function renderControl() {
  const seen: string[] = []
  function Harness({ onChange }: { onChange: (v: string) => void }) {
    const [form] = Form.useForm()
    return (
      <Form form={form}>
        <Form.Item name="signature" label="签名">
          <SignatureUploadField />
        </Form.Item>
        {/* expose a hook the test can call to read the live form value */}
        <button type="button" onClick={() => onChange((form.getFieldValue('signature') ?? '') as string)}>
          read-value
        </button>
      </Form>
    )
  }
  render(<Harness onChange={(v) => seen.push(v)} />)
  return { seen }
}

function drawerlessFileInput(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]')
  expect(input).not.toBeNull()
  return input as HTMLInputElement
}

const PNG = new File([new Uint8Array([137, 80, 78, 71])], 'sig.png', { type: 'image/png' })

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(commonApi.uploadImage).mockResolvedValue({ path: '/profile/avatar/2026/11/11/sig.png' })
})

describe('signature upload control (user-edit-delete-profile-web 2.1)', () => {
  it('renders the stored path as the preview image without uploading', () => {
    render(
      <Form>
        <Form.Item name="signature" label="签名" initialValue="/profile/avatar/2026/11/11/kept.png">
          <SignatureUploadField />
        </Form.Item>
      </Form>,
    )
    expect(screen.getByAltText('签名预览')).toHaveAttribute('src', '/profile/avatar/2026/11/11/kept.png')
    expect(commonApi.uploadImage).not.toHaveBeenCalled()
  })

  it('uploads a picked png through the common endpoint and stores the returned path', async () => {
    const { seen } = renderControl()
    const user = userEvent.setup()
    await user.upload(drawerlessFileInput(), PNG)

    await waitFor(() => expect(commonApi.uploadImage).toHaveBeenCalledTimes(1))
    expect(commonApi.uploadImage).toHaveBeenCalledWith(PNG)
    await waitFor(() => expect(screen.getByAltText('签名预览')).toHaveAttribute(
      'src', '/profile/avatar/2026/11/11/sig.png'))
    await user.click(screen.getByRole('button', { name: 'read-value' }))
    expect(seen[seen.length - 1]).toBe('/profile/avatar/2026/11/11/sig.png')
  })

  it('rejects a .gif client-side with a warning and fires no request', async () => {
    renderControl()
    const user = userEvent.setup()
    const gif = new File([new Uint8Array([71, 73, 70])], 'sig.gif', { type: 'image/gif' })
    await user.upload(drawerlessFileInput(), gif)
    expect(await screen.findByText('签名仅支持 jpg/png/jpeg 图片')).toBeInTheDocument()
    expect(commonApi.uploadImage).not.toHaveBeenCalled()
  })

  it('rejects an over-2MB image client-side and fires no request', async () => {
    renderControl()
    const user = userEvent.setup()
    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' })
    Object.defineProperty(big, 'size', { value: 2 * 1024 * 1024 + 1 })
    await user.upload(drawerlessFileInput(), big)
    expect(await screen.findByText('图片大小不能超过 2MB')).toBeInTheDocument()
    expect(commonApi.uploadImage).not.toHaveBeenCalled()
  })

  it('keeps the previous value and surfaces the msg when the upload fails', async () => {
    // a UNIQUE msg text: antd's message portal is document-shared across tests,
    // and the earlier size-rejection test's span would collide on findByText.
    vi.mocked(commonApi.uploadImage).mockRejectedValueOnce(new ApiError('A0400', '签名存储失败测试'))
    render(
      <Form>
        <Form.Item name="signature" label="签名" initialValue="/profile/avatar/2026/11/11/kept.png">
          <SignatureUploadField />
        </Form.Item>
      </Form>,
    )
    const user = userEvent.setup()
    await user.upload(drawerlessFileInput(), PNG)
    expect(await screen.findByText('签名存储失败测试')).toBeInTheDocument()
    expect(screen.getByAltText('签名预览')).toHaveAttribute('src', '/profile/avatar/2026/11/11/kept.png')
  })
})
