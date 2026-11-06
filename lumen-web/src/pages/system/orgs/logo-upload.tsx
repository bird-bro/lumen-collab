import { PlusOutlined } from '@ant-design/icons'
import { Upload, message, theme } from 'antd'
import type { UploadProps } from 'antd'
import { ApiError } from '@/api/client'
import { uploadImage } from '@/api/common'

/** The 2MB business cap - the same rule the backend enforces (org-logo-upload D4). */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024

/** Extension whitelist mirroring the server gate; decided on the file NAME (case-insensitive). */
const ALLOWED_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.webp']

interface LogoUploadFieldProps {
  /** The stored logo path - injected by the wrapping `<Form.Item name="logoUrl">`. */
  value?: string | null;
  /** The form's setter - called with the returned `data.path` on upload success. */
  onChange?: (value: string) => void;
}

/**
 * The org dialog's Logo field (org-logo-upload-web, spec `web-system-console` §
 * Organization Logo Upload Control): an AntD picture-card upload as a CONTROLLED FORM
 * CONTROL - the wrapping `<Form.Item name="logoUrl">` injects `value`/`onChange`, so
 * the dialog backfill (`openDialog`'s setFieldsValue) presets it and `submitDialog`'s
 * `validateFields` keeps it in the submitted org body (the wire contract keeps
 * `logoUrl`; a bare string is not natively bindable to `Upload`, hence the control).
 *
 * Validation is client-side BEFORE any request (delta scenario 2): extension whitelist
 * + 2MB cap, rejections warn and return `Upload.LIST_IGNORE` so no request fires and
 * the tile stays untouched. Valid picks flow through `customRequest` ->
 * `uploadImage()` -> `onChange(returned path)` ONLY on success, so a failed upload
 * keeps the previously stored value (delta scenario 4) with the backend msg surfaced.
 *
 * Display: `value` renders as the tile image as-is - a relative `/profile/...` stored
 * path resolves same-origin through the static read-back segment, a legacy absolute
 * `https://...` value renders unchanged (delta scenario 3).
 *
 * Deliberately NO `maxCount`: the internal file list would cap at 1 and BLOCK the
 * re-pick-replaces-tile flow; the list is hidden (`showUploadList={false}`), the
 * drawer remounts per dialog open (`destroyOnHidden`), and the injected `value` - not
 * the list - is the model. `beforeUpload` returning `true` lets `customRequest` drive
 * the upload; the default action poster is never used.
 */
export function LogoUploadField({ value, onChange }: LogoUploadFieldProps) {
  const { token } = theme.useToken()

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const dot = file.name.lastIndexOf('.')
    const extension = dot >= 0 ? file.name.slice(dot).toLowerCase() : ''
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      message.warning('仅支持 png/jpg/jpeg/webp 图片')
      return Upload.LIST_IGNORE
    }
    if (file.size > MAX_IMAGE_BYTES) {
      message.warning('图片大小不能超过 2MB')
      return Upload.LIST_IGNORE
    }
    return true
  }

  const customRequest: UploadProps['customRequest'] = (options) => {
    void uploadImage(options.file as File)
      .then((uploaded) => {
        onChange?.(uploaded.path)
        options.onSuccess?.(uploaded)
      })
      .catch((error: unknown) => {
        message.error(error instanceof ApiError ? error.msg : 'Logo 上传失败')
        options.onError?.(error instanceof Error ? error : new Error(String(error)))
      })
  }

  return (
    <Upload
      accept="image/*"
      listType="picture-card"
      showUploadList={false}
      beforeUpload={beforeUpload}
      customRequest={customRequest}
    >
      {value ? (
        <img
          alt="Logo 预览"
          src={value}
          style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        />
      ) : (
        <div aria-label="上传 Logo" style={{ color: token.colorTextSecondary }}>
          <PlusOutlined />
          <div style={{ marginTop: 8 }}>上传</div>
        </div>
      )}
    </Upload>
  )
}
