/**
 * Exam-body typed clients (exam-setting-web task 1.1). The list returns the
 * up_id-nested FOREST server-side (the item drawer's cascade tree select and the
 * 检查部位 page's table); group/parent/modality lockstep validations reject A0400.
 */
import { apiRequest } from './client';
import type {
  BodyTreeNode,
  CreateExamBodyBody,
  ExamBody,
  UpdateExamBodyBody,
} from './types';

/**
 * GET /setting/exam-bodies/list?modality=[&groupId=] - the modality's (optionally
 * group-scoped) live bodies as an up_id-nested forest.
 */
export function listExamBodies(modality: string, groupId?: number): Promise<BodyTreeNode[]> {
  return apiRequest<BodyTreeNode[]>('/setting/exam-bodies/list', {
    method: 'GET',
    query: { modality, groupId },
  });
}

/** GET /setting/exam-bodies/{id} - the edit drawer's echo. */
export function getExamBody(id: number): Promise<ExamBody> {
  return apiRequest<ExamBody>(`/setting/exam-bodies/${id}`, { method: 'GET' });
}

/** POST /setting/exam-bodies - returns the new body id. */
export function createExamBody(body: CreateExamBodyBody): Promise<number> {
  return apiRequest<number>('/setting/exam-bodies', { method: 'POST', body });
}

/** PUT /setting/exam-bodies/{id} - full-replace (D9 null-aware server side). */
export function updateExamBody(id: number, body: UpdateExamBodyBody): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-bodies/${id}`, { method: 'PUT', body });
}

/** DELETE /setting/exam-bodies/{id} - guarded by live children/items server side. */
export function deleteExamBody(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-bodies/${id}`, { method: 'DELETE' });
}
