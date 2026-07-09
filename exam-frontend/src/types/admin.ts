export interface PageVO<T> {
  records: T[]
  total: number
}

export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

export interface AdminUser {
  id?: number
  account?: string
  password?: string
  username?: string
  avatar?: string
  gender?: number
  phone?: string
  loginStatus?: boolean
  role?: number
  createTime?: string
}

export interface Subject {
  id?: number
  name?: string
  description?: string
  createTime?: string
}

export interface Question {
  id?: number
  subjectId?: number
  subjectName?: string
  type?: number
  difficulty?: number
  content?: string
  options?: string
  answer?: string
  analysis?: string
  score?: number
  createTime?: string
}

export interface ExamPaperQuestion {
  questionId?: number
  paperScore?: number
}

export interface ExamPaper {
  id?: number
  title?: string
  subjectId?: number
  subjectName?: string
  totalScore?: number
  duration?: number
  maxAttempts?: number
  status?: number
  startTime?: string
  endTime?: string
  createTime?: string
  questions?: (Question & { paperScore?: number })[]
}

export interface AutoGeneratePaper {
  title?: string
  subjectId?: number
  subjectName?: string
  totalScore?: number
  duration?: number
  maxAttempts?: number
  typeConfigs?: Array<{
    type: number
    count: number
    scorePerQuestion: number
    difficultyDist: Record<number, number>
  }>
}

export interface ExamRecord {
  id?: number
  userId?: number
  username?: string
  paperId?: number
  paperTitle?: string
  score?: number
  totalScore?: number
  passScore?: number
  attemptCount?: number
  status?: number
  startTime?: string
  submitTime?: string
  createTime?: string
  answers?: ExamRecordAnswer[]
}

export interface ExamRecordAnswer {
  id?: number
  recordId?: number
  questionId?: number
  type?: number
  questionContent?: string
  options?: string
  userAnswer?: string
  correctAnswer?: string
  fullScore?: number
  score?: number
  judgement?: string
}

export interface NameValue {
  name: string
  value: number
}

export interface DashboardOverview {
  userCount: number
  adminCount: number
  paperCount: number
  recordCount: number
  questionCount: number
  subjectCount: number
  questionTypeStats: NameValue[]
  subjectQuestionStats: NameValue[]
  trendStats?: TrendStats[]
}

export interface StudentScoreStats {
  recordCount: number
  averageScoreRate: number
  highestScoreRate: number
  lowestScoreRate: number
  passCount: number
  passRate: number
  scoreDistribution: NameValue[]
  topStudentScores: NameValue[]
}

export interface TrendStats {
  date: string
  users: number
  exams: number
  questions: number
}

export interface WrongQuestion {
  id?: number
  userId?: number
  questionId?: number
  subjectId?: number
  subjectName?: string
  type?: number
  content?: string
  options?: string
  userAnswer?: string
  correctAnswer?: string
  analysis?: string
  wrongCount?: number
  mastered?: boolean
  lastWrongTime?: string
  createTime?: string
}
