/** 智能学习伙伴 (ai-tutor) 前端 API */
import axios from 'axios'
import { getToken } from '@/utils/localStorage'

const aiClient = axios.create({
  baseURL: import.meta.env.VITE_AI_BASE_URL || '/ai',
  timeout: 60000,
})

aiClient.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：错误码当异常抛，正常时解包到内层 data
aiClient.interceptors.response.use(
  (res) => {
    if (res.data.code !== 200) {
      return Promise.reject(res.data)
    }
    return res.data
  },
  (err) => Promise.reject(err)
)

// ═══════════════════════════════════════════════════
//  类型定义（对应 ai-tutor 返回的内层 data）
// ═══════════════════════════════════════════════════

// ── 教师智能体 ──

export interface SubjectItem {
  id: number
  name: string
}

export interface TeacherRecommendData {
  message: string
  suggestion: {
    subject_name: string
    recommended_count: number
  }
}

export interface TeacherGenerateParams {
  subjectId: number
  subjectName: string
  questionType: number
  difficulty: number
  count: number
  extraRequirement?: string
}

export interface GeneratedQuestion {
  content: string
  options: string | null
  answer: string
  analysis: string
  score: number
}

export interface TeacherGenerateData {
  questions: GeneratedQuestion[]
  saved_ids: number[]
  failed_questions: { index: number; content_preview: string; reason: string }[]
  warnings: string[]
}

export const teacherApi = {
  /** 获取所有科目列表（用于下拉框，走 ai-tutor 代理避免 CORS） */
  async subjects() {
    const r: any = await aiClient.get('/teacher/subjects')
    return (r.data || r) as SubjectItem[]
  },

  async recommend(subjectName?: string) {
    const r: any = await aiClient.post('/teacher/recommend', {
      subject_name: subjectName || null,
    })
    return r.data as TeacherRecommendData
  },

  async generate(params: TeacherGenerateParams) {
    const r: any = await aiClient.post('/teacher/generate', {
      subject_id: params.subjectId,
      subject_name: params.subjectName,
      question_type: params.questionType,
      difficulty: params.difficulty,
      count: params.count,
      extra_requirement: params.extraRequirement || null,
    })
    return r.data as TeacherGenerateData
  },

  async upload(file: File, subjectName: string) {
    const form = new FormData()
    form.append('file', file)
    form.append('subject_name', subjectName)
    const r: any = await aiClient.post('/teacher/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return r.data
  },
}

// ── 学生智能体 ──

export interface StudentStatusData {
  available: boolean
  reason: string | null
}

export interface StudentRecommendData {
  message: string
  weak_points: { concept: string; error_count: number }[]
}

export interface StudentAskParams {
  questionId?: number
  questionContent: string
  studentAnswer?: string
  message: string
  sessionId?: string
}

export interface StudentAskData {
  reply: string
  hints: string[]
  related_concepts: string[]
  session_id: string
}

export const studentApi = {
  async status() {
    const r: any = await aiClient.get('/student/status')
    return r.data as StudentStatusData
  },

  async recommend() {
    const r: any = await aiClient.post('/student/recommend')
    return r.data as StudentRecommendData
  },

  async ask(params: StudentAskParams) {
    const r: any = await aiClient.post('/student/ask', {
      question_id: params.questionId || null,
      question_content: params.questionContent,
      student_answer: params.studentAnswer || null,
      message: params.message,
      session_id: params.sessionId || null,
    })
    return r.data as StudentAskData
  },

  async clearSession(sessionId: string) {
    const r: any = await aiClient.post('/student/session/clear', { session_id: sessionId })
    return r.data
  },
}
