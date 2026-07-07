import type { Result } from '@/types/result'
import type { AdminUser, AutoGeneratePaper, DashboardOverview, ExamPaper, ExamRecord, PageQuery, PageVO, Question, Subject, TrendStats } from '@/types/admin'
import request from '@/utils/request'

export const adminUserListApi = (params: PageQuery & Partial<AdminUser>) =>
  request.get<Result<PageVO<AdminUser>>>('/admin/users/listPage', params)

export const adminUserAddApi = (params: AdminUser) =>
  request.post<Result<void>>('/admin/users', params)

export const adminUserUpdateApi = (params: AdminUser) =>
  request.put<Result<void>>('/admin/users', params)

export const adminUserStatusApi = (id: number, loginStatus: boolean) =>
  request.put<Result<void>>(`/admin/users/${id}/status?loginStatus=${loginStatus}`)

export const adminUserDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/admin/users/${id}`)

export const subjectListApi = () =>
  request.get<Result<Subject[]>>('/subject/list')

export const subjectListPageApi = (params: PageQuery & Partial<Subject>) =>
  request.get<Result<PageVO<Subject>>>('/subject/listPage', params)

export const subjectAddApi = (params: Subject) =>
  request.post<Result<void>>('/subject', params)

export const subjectUpdateApi = (params: Subject) =>
  request.put<Result<void>>('/subject', params)

export const subjectDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/subject/${id}`)

export const questionListPageApi = (params: PageQuery & Partial<Question>) =>
  request.get<Result<PageVO<Question>>>('/question/listPage', params)

export const questionAddApi = (params: Question) =>
  request.post<Result<void>>('/question', params)

export const questionUpdateApi = (params: Question) =>
  request.put<Result<void>>('/question', params)

export const questionDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/question/${id}`)

export const examPaperListPageApi = (params: PageQuery & Partial<ExamPaper>) =>
  request.get<Result<PageVO<ExamPaper>>>('/examPaper/listPage', params)

export const examPaperDetailApi = (id: number) =>
  request.get<Result<ExamPaper>>(`/examPaper/${id}/detail`)

export const examPaperAddApi = (params: ExamPaper) =>
  request.post<Result<void>>('/examPaper/add', params)

export const examPaperUpdateApi = (params: ExamPaper) =>
  request.put<Result<void>>('/examPaper/update', params)

export const examPaperAutoGenerateApi = (params: AutoGeneratePaper) =>
  request.post<Result<void>>('/examPaper/autoGenerate', params)

export const examPaperDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/examPaper/${id}`)

export const examRecordListPageApi = (params: PageQuery & Partial<ExamRecord>) =>
  request.get<Result<PageVO<ExamRecord>>>('/examRecord/listPage', params)

export const examRecordDetailApi = (id: number) =>
  request.get<Result<ExamRecord>>(`/examRecord/${id}/detail`)

export const examRecordGradeApi = (params: { recordId?: number; answers: Array<{ answerId?: number; score?: number; judgement?: string }> }) =>
  request.post<Result<void>>('/examRecord/grade', params)

export const examRecordDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/examRecord/${id}`)

export const dashboardOverviewApi = () =>
  request.get<Result<DashboardOverview>>('/admin/dashboard/overview')

export const dashboardTrendApi = (days: number) =>
  request.get<Result<TrendStats[]>>('/admin/dashboard/trends', { days })
