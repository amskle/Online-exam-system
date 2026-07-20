import type { Result } from '@/types/result'
import type { ExamPaper, ExamRecord, PageQuery, PageVO, WrongQuestion } from '@/types/admin'
import request from '@/utils/request'

export const studentExamPaperListApi = (params: PageQuery & { title?: string }) =>
  request.get<Result<PageVO<ExamPaper>>>('/student/examPapers/listPage', params)

export const studentExamPaperDetailApi = (id: number) =>
  request.get<Result<ExamPaper>>(`/student/examPapers/${id}/detail`)

export const studentExamRecordStartApi = (paperId: number) =>
  request.post<Result<ExamRecord>>(`/student/examRecords/start?paperId=${paperId}`)

export const studentExamRecordSubmitApi = (params: { recordId?: number; paperId?: number; answers: Array<{ questionId?: number; userAnswer?: string }> }) =>
  request.post<Result<void>>('/student/examRecords/submit', params)

export const studentExamRecordWarnApi = (recordId: number) =>
  request.post<Result<void>>(`/student/examRecords/warn?recordId=${recordId}`)

export const studentExamRecordListApi = (params: PageQuery & { paperTitle?: string; status?: number }) =>
  request.get<Result<PageVO<ExamRecord>>>('/student/examRecords/listPage', params)

export const studentExamRecordDetailApi = (id: number) =>
  request.get<Result<ExamRecord>>(`/student/examRecords/${id}/detail`)

export const studentWrongQuestionListApi = (params: PageQuery & { subjectId?: number; mastered?: boolean }) =>
  request.get<Result<PageVO<WrongQuestion>>>('/student/wrongQuestions/listPage', params)

export const studentWrongQuestionMasteredApi = (id: number, mastered: boolean) =>
  request.put<Result<void>>(`/student/wrongQuestions/${id}/mastered?mastered=${mastered}`)

export const studentWrongQuestionDeleteApi = (id: number) =>
  request.delete<Result<void>>(`/student/wrongQuestions/${id}`)
