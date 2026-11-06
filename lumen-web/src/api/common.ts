/**
 * Common (system-wide) resource clients. Typed against
 * `openspec/specs/api/spec.md` § Common Image Upload Endpoint - no mock layer,
 * every request goes through the transport to the real backend.
 */
import { apiUpload } from './client';
import type { UploadedImage } from './types';

/**
 * POST /system/common/upload/image - the common image transport (OPS
 * `CommonController.uploadImage` 1:1). The backend answers the unified envelope
 * whose `data` is `{ path }` with the stored value
 * (`/profile/avatar/<yyyy/MM/dd>/<uuid>.<ext>`); the server validates
 * png/jpg/jpeg/webp ≤2MB (A0400 rejections surface through the shared envelope).
 */
export function uploadImage(file: File): Promise<UploadedImage> {
  return apiUpload<UploadedImage>('/system/common/upload/image', file);
}
