/**
 * Exam-item typed clients (exam-setting-web task 1.1). The page/detail responses are
 * DISPLAY-RESOLVED server-side (bodyName/groupName/methodNames - D2: no client-side
 * joins); the method selection travels as an ID ARRAY and the server rewrites the
 * binding set inside the item's transaction.
 */
import { apiRequest } from './client';
import type {
  CreateExamItemBody,
  ExamItem,
  ExamItemPageQuery,
  Page,
  UpdateExamItemBody,
} from './types';

/**
 * GET /setting/exam-items/page - the 检查项目 table's server-side query; the
 * 部位分组 filter derives through the body join server-side.
 */
export function pageExamItems(query: ExamItemPageQuery): Promise<Page<ExamItem>> {
  return apiRequest<Page<ExamItem>>('/setting/exam-items/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      modality: query.modality,
      groupId: query.groupId,
      keyword: query.keyword,
      isCommon: query.isCommon,
    },
  });
}

/** GET /setting/exam-items/{id} - the edit drawer's echo (display names resolved). */
export function getExamItem(id: number): Promise<ExamItem> {
  return apiRequest<ExamItem>(`/setting/exam-items/${id}`, { method: 'GET' });
}

/** POST /setting/exam-items - returns the new item id. */
export function createExamItem(body: CreateExamItemBody): Promise<number> {
  return apiRequest<number>('/setting/exam-items', { method: 'POST', body });
}

/** PUT /setting/exam-items/{id} - full-replace + binding rewrite in one transaction. */
export function updateExamItem(id: number, body: UpdateExamItemBody): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-items/${id}`, { method: 'PUT', body });
}

/** DELETE /setting/exam-items/{id} - logical delete server side. */
export function deleteExamItem(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-items/${id}`, { method: 'DELETE' });
}
