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
  session_id?: string
}

export interface TeacherChatData {
  reply: string
  session_id: string
  sources: { source_file: string; question_index: number; preview: string }[]
}

// ── 会话历史 ──

export interface SessionItem {
  session_id: string
  agent_mode: string
  title: string
  created_at: number
  updated_at: number
  preview: string
}

export interface SessionDetail {
  session_id: string
  agent_mode: string
  title: string
  created_at: number
  updated_at: number
  messages: { role: string; content: string }[]
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

  /** 知识库对话 — 基于已上传文档自由问答 */
  async chat(message: string, subjectName?: string, sessionId?: string) {
    const r: any = await aiClient.post('/teacher/chat', {
      message,
      subject_name: subjectName || null,
      session_id: sessionId || null,
    })
    return r.data as TeacherChatData
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

  /** 获取出题历史会话列表 */
  async getSessions() {
    const r: any = await aiClient.get('/teacher/sessions')
    return r.data as SessionItem[]
  },

  /** 获取单个出题会话详情（含消息） */
  async getSession(sessionId: string) {
    const r: any = await aiClient.get(`/teacher/sessions/${sessionId}`)
    return r.data as SessionDetail
  },

  /** 删除出题历史会话 */
  async deleteSession(sessionId: string) {
    const r: any = await aiClient.delete(`/teacher/sessions/${sessionId}`)
    return r.data
  },

  /** 清空知识库（教师库 + 学生库） */
  async clearKnowledge() {
    const r: any = await aiClient.delete('/teacher/knowledge')
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

/** SSE 流式答疑的事件回调 */
export interface StreamCallbacks {
  onStatus?: (text: string) => void
  onToken?: (text: string) => void
  onRewrite?: (text: string) => void
  onFinal?: (data: StudentAskData & { contains_answer?: boolean }) => void
  onError?: (message: string) => void
  onDone?: () => void
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

  /** SSE 流式答疑 — 使用 fetch ReadableStream 实时接收回复 */
  async streamAsk(params: StudentAskParams, callbacks: StreamCallbacks): Promise<void> {
    const base = import.meta.env.VITE_AI_BASE_URL || '/ai'
    const token = getToken()
    const resp = await fetch(`${base}/student/ask/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        question_id: params.questionId || null,
        question_content: params.questionContent,
        student_answer: params.studentAnswer || null,
        message: params.message,
        session_id: params.sessionId || null,
      }),
    })

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({ detail: resp.statusText }))
      callbacks.onError?.(err.detail || `请求失败 (${resp.status})`)
      return
    }

    const reader = resp.body?.getReader()
    if (!reader) {
      callbacks.onError?.('浏览器不支持流式读取')
      return
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''  // 保留不完整的行

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data: ')) continue
        const data = trimmed.slice(6)

        if (data === '[DONE]') {
          callbacks.onDone?.()
          return
        }

        try {
          const event = JSON.parse(data)
          switch (event.type) {
            case 'status':
              callbacks.onStatus?.(event.text)
              break
            case 'token':
              callbacks.onToken?.(event.text)
              break
            case 'rewrite':
              callbacks.onRewrite?.(event.text)
              break
            case 'final':
              callbacks.onFinal?.(event)
              break
            case 'error':
              callbacks.onError?.(event.message)
              break
          }
        } catch {
          // 忽略无法解析的行
        }
      }
    }
  },

  async clearSession(sessionId: string) {
    const r: any = await aiClient.post('/student/session/clear', { session_id: sessionId })
    return r.data
  },

  /** 获取答疑历史会话列表 */
  async getSessions() {
    const r: any = await aiClient.get('/student/sessions')
    return r.data as SessionItem[]
  },

  /** 获取单个答疑会话详情（含消息） */
  async getSession(sessionId: string) {
    const r: any = await aiClient.get(`/student/sessions/${sessionId}`)
    return r.data as SessionDetail
  },

  /** 删除答疑历史会话 */
  async deleteSession(sessionId: string) {
    const r: any = await aiClient.delete(`/student/sessions/${sessionId}`)
    return r.data
  },
}
