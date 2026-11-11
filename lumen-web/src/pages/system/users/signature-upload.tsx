import { PlusOutlined } from '@ant-design/icons'
import { Upload, message, theme } from 'antd'
import type { UploadProps } from 'antd'
import { ApiError } from '@/api/client'
import { uploadImage } from '@/api/common'

/**
 * The report-signature image's upload control (user-edit-delete-profile-web
 * task 2.1): a CONTROLLED FORM CONTROL composed after the `logo-upload.tsx`
 * pattern - the wrapping `<Form.Item name="signature">` injects
 * `value`/`onChange`, the dialog prefill presets it, and the submitted body
 * carries the stored path verbatim. The transport is the common image upload
 * endpoint; the control adds NO storage or bookkeeping of its own.
 *
 * Validation is client-side BEFORE any request (the spec: mirrors the logo
 * control), with the RCP page's narrower whitelist: jpg/png/jpeg ONLY (the
 * signature is a report-embedded image; webp is out). `accept` stays
 * `image/*` ON PURPOSE - rc-upload filters picks against `accept` before
 * `beforeUpload` runs, so a narrow accept would SILENTLY drop a wrong pick
 * with no warning; the logo control's precedent keeps the deny reachable and
 * the whitelist's warning the user-visible contract. Rejections warn and
 * return `Upload.LIST_IGNORE` so no request fires and the tile stays
 * untouched; a valid pick flows through `customRequest` -> `uploadImage()` ->
 * `onChange(returned path)` ONLY on success, so a failed upload keeps the
 * previously stored value with the backend msg surfaced.
 *
 * Deliberately NO `maxCount` (the logo control's recorded reason applies
 * verbatim: the internal list would block the re-pick-replaces-tile flow);
 * `showUploadList={false}` - the injected `value` is the model.
 */
/** The 2MB business cap - the same rule the backend enforces (org-logo-upload D4). */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024

/** The narrower report-signature whitelist (D3): jpg/png/jpeg, NOT webp. */
const ALLOWED_EXTENSIONS = ['.png', '.jpg', '.jpeg']

interface SignatureUploadFieldProps {
  /** The stored signature path - injected by the wrapping `<Form.Item name="signature">`. */
  value?: string | null;
  /** The form's setter - called with the returned `data.path` on upload success. */
  onChange?: (value: string) => void;
  /** The Form.Item-assigned control id - forwarded to the trigger div so the
   *  field label associates (getByLabelText / a11y). */
  id?: string;
}

export function SignatureUploadField({ value, onChange, id }: SignatureUploadFieldProps) {
  const { token } = theme.useToken()

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const dot = file.name.lastIndexOf('.')
    const extension = dot >= 0 ? file.name.slice(dot).toLowerCase() : ''
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      message.warning('签名仅支持 jpg/png/jpeg 图片')
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
        message.error(error instanceof ApiError ? error.msg : '签名上传失败')
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
          alt="签名预览"
          src={value}
          style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        />
      ) : (
        <div id={id} aria-label="上传签名" style={{ color: token.colorTextSecondary }}>
          <PlusOutlined />
          <div style={{ marginTop: 8 }}>上传</div>
        </div>
      )}
    </Upload>
  )
}
