/**
 * Body-group typed clients (exam-setting-web task 1.1; paths verbatim from the
 * exam-setting endpoint deltas, the OrgDeviceController verb style). The group table
 * carries no code column - the NAME is the operational key, unique within the
 * modality among live rows; duplicate names / blocked deletes reject A0400
 * server-side and surface through `ApiError`.
 */
import { apiRequest } from './client';
import type {
  CreateBodyGroupBody,
  ExamBodyGroup,
  UpdateBodyGroupBody,
} from './types';

/**
 * GET /setting/exam-body-groups/list?modality= - the 检查部位 page's left-column feed
 * and the 检查项目 drawer's cascade filter source, sort-ordered server-side.
 */
export function listBodyGroups(modality: string): Promise<ExamBodyGroup[]> {
  return apiRequest<ExamBodyGroup[]>('/setting/exam-body-groups/list', {
    method: 'GET',
    query: { modality },
  });
}

/** POST /setting/exam-body-groups - returns the new group id. */
export function createBodyGroup(body: CreateBodyGroupBody): Promise<number> {
  return apiRequest<number>('/setting/exam-body-groups', { method: 'POST', body });
}

/** PUT /setting/exam-body-groups/{id} - full-replace (D9 null-aware server side). */
export function updateBodyGroup(id: number, body: UpdateBodyGroupBody): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-body-groups/${id}`, { method: 'PUT', body });
}

/** DELETE /setting/exam-body-groups/{id} - guarded by live bodies server side. */
export function deleteBodyGroup(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-body-groups/${id}`, { method: 'DELETE' });
}
