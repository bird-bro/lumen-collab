/**
 * Exam-method typed clients (exam-setting-web task 1.1). The page collapses the
 * console's 名称/编码 query types into one keyword matched against both columns
 * server-side; the list is the 检查项目 form's picker feed (sort-ordered).
 */
import { apiRequest } from './client';
import type {
  CreateExamMethodBody,
  ExamMethod,
  ExamMethodPageQuery,
  Page,
  UpdateExamMethodBody,
} from './types';

/**
 * GET /setting/exam-methods/page - the 检查方法 table's server-side query; falls
 * back to the paging defaults (pageNum=1, pageSize=20).
 */
export function pageExamMethods(query: ExamMethodPageQuery): Promise<Page<ExamMethod>> {
  return apiRequest<Page<ExamMethod>>('/setting/exam-methods/page', {
    method: 'GET',
    query: {
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      modality: query.modality,
      keyword: query.keyword,
    },
  });
}

/** GET /setting/exam-methods/list?modality=[&keyword=] - the item form's picker feed. */
export function listExamMethods(modality: string, keyword?: string): Promise<ExamMethod[]> {
  return apiRequest<ExamMethod[]>('/setting/exam-methods/list', {
    method: 'GET',
    query: { modality, keyword },
  });
}

/** POST /setting/exam-methods - returns the new method id. */
export function createExamMethod(body: CreateExamMethodBody): Promise<number> {
  return apiRequest<number>('/setting/exam-methods', { method: 'POST', body });
}

/** PUT /setting/exam-methods/{id} - full-replace (D9 null-aware server side). */
export function updateExamMethod(id: number, body: UpdateExamMethodBody): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-methods/${id}`, { method: 'PUT', body });
}

/** DELETE /setting/exam-methods/{id} - guarded by live item bindings server side. */
export function deleteExamMethod(id: number): Promise<boolean> {
  return apiRequest<boolean>(`/setting/exam-methods/${id}`, { method: 'DELETE' });
}
