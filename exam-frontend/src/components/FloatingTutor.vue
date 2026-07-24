<template>
  <!-- 悬浮智能伙伴 — 仅在指定页面显示 -->
  <div v-if="visible" class="floating-tutor">
    <!-- 收起态：小图标 -->
    <div v-if="!dialogOpen" class="tutor-bubble" @click="openDialog">
      <div class="tutor-icon">
        <span>{{ agentMode === 'teacher' ? '👨‍🏫' : '🎓' }}</span>
      </div>
      <div class="tutor-pulse"></div>
    </div>

    <!-- 展开态：聊天对话框 -->
    <Transition name="dialog-fade">
      <div v-if="dialogOpen" class="tutor-dialog">
        <div class="dialog-header">
          <div class="header-left">
            <span class="header-avatar">{{ agentMode === 'teacher' ? '👨‍🏫' : '🎓' }}</span>
            <div>
              <strong>{{ agentMode === 'teacher' ? '出题助手' : '学习伙伴' }}</strong>
              <small>{{ agentMode === 'teacher' ? '帮您高效出题' : '引导式答疑解惑' }}</small>
            </div>
          </div>
          <div class="header-actions">
            <el-button text size="small" @click="openHistory" title="历史记录">📋</el-button>
            <el-button :icon="Delete" text size="small" @click="clearChat" title="清空对话">清空</el-button>
            <el-button :icon="Close" text size="small" @click="dialogOpen = false" title="最小化" />
          </div>
        </div>

        <!-- 聊天模式 -->
        <template v-if="!showHistory">
          <!-- 消息列表 -->
          <div class="dialog-body" ref="bodyRef">
            <!-- 推荐卡片 -->
            <div v-if="showRecommend" class="recommend-card">
              <div class="recommend-header">
                <span>💡 {{ agentMode === 'teacher' ? '推荐出题任务' : '复习建议' }}</span>
                <el-button text size="small" type="primary" @click="showRecommend = false">关闭</el-button>
              </div>
              <p class="recommend-text">{{ recommendMessage }}</p>
              <div v-if="agentMode === 'teacher' && recommendSuggestion" class="recommend-action">
                <el-button size="small" type="primary" @click="quickGenerate">🚀 快速生成</el-button>
              </div>
            </div>

            <!-- 对话消息 -->
            <div v-for="(msg, i) in messages" :key="i" class="chat-message" :class="msg.role">
              <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : (agentMode === 'teacher' ? '👨‍🏫' : '🎓') }}</div>
              <div class="msg-bubble" v-html="renderContent(msg.content)"></div>
            </div>

            <!-- 加载中 -->
            <div v-if="loading" class="chat-message assistant">
              <div class="msg-avatar">{{ agentMode === 'teacher' ? '👨‍🏫' : '🎓' }}</div>
              <div class="msg-bubble loading-dots">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>

          <!-- 输入区 -->
          <div class="dialog-footer">
            <!-- 教师模式：快捷出题面板 -->
            <div v-if="agentMode === 'teacher'" class="quick-panel">
              <el-select v-model="genSubjectId" placeholder="科目" size="small" style="width:120px" @change="onSubjectChange" popper-class="tutor-select-popper">
                <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
              <el-select v-model="genType" size="small" style="width:100px" popper-class="tutor-select-popper">
                <el-option :value="1" label="单选题" />
                <el-option :value="2" label="多选题" />
                <el-option :value="3" label="判断题" />
                <el-option :value="4" label="主观题" />
              </el-select>
              <el-select v-model="genDifficulty" size="small" style="width:90px" popper-class="tutor-select-popper">
                <el-option :value="1" label="简单" />
                <el-option :value="2" label="中等" />
                <el-option :value="3" label="困难" />
              </el-select>
              <el-input-number v-model="genCount" :min="1" :max="20" size="small" style="width:80px" />
              <el-input v-model="genExtra" placeholder="额外要求（可选）" size="small" style="width:140px" />
              <div class="upload-row">
                <input ref="fileInputRef" type="file" accept=".pdf,.txt" hidden @change="onFileSelected" />
                <el-button size="small" :icon="Upload" @click="openFilePicker" :loading="uploading">
                  {{ uploading ? '上传中…' : '📄 上传知识库' }}
                </el-button>
                <el-button size="small" type="danger" @click="clearKnowledge" :disabled="uploading">
                  🗑️ 清空知识库
                </el-button>
                <el-button size="small" type="warning" @click="doGenerate" :loading="loading" :disabled="!genSubjectId">
                  🧠 生成题目
                </el-button>
              </div>
            </div>
            <div class="input-row">
              <el-input
                v-model="inputText"
                :placeholder="agentMode === 'teacher' ? '输入问题查询知识库…' : '输入你的疑问...'"
                @keyup.enter="sendMessage"
                :disabled="loading || !available"
              />
              <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading" :disabled="!available">
                {{ agentMode === 'teacher' ? '💬 提问' : '发送' }}
              </el-button>
            </div>
            <div v-if="!available" class="unavailable-hint">
              ⛔ {{ unavailableReason }}
            </div>
          </div>
        </template>

        <!-- 历史记录模式 -->
        <template v-else>
          <div class="dialog-body history-panel" ref="bodyRef">
            <!-- 加载中 -->
            <div v-if="loadingSessions" class="history-loading">
              <span>加载中…</span>
            </div>
            <!-- 空态 -->
            <div v-else-if="sessions.length === 0" class="history-empty">
              <div class="empty-icon">📭</div>
              <p>暂无历史记录</p>
            </div>
            <!-- 会话列表 -->
            <div v-else class="session-list">
              <div
                v-for="s in sessions"
                :key="s.session_id"
                class="session-card"
                @click="loadSession(s.session_id)"
              >
                <div class="session-card-header">
                  <span class="session-title">{{ s.title || '未命名对话' }}</span>
                  <span class="session-time">{{ formatTime(s.updated_at) }}</span>
                </div>
                <div class="session-preview">{{ s.preview || '（无内容）' }}</div>
                <div class="session-actions">
                  <el-button text size="small" type="danger" @click.stop="confirmDeleteSession(s)">
                    删除
                  </el-button>
                </div>
              </div>
            </div>
          </div>
          <div class="dialog-footer history-footer">
            <el-button size="small" @click="showHistory = false">← 返回对话</el-button>
          </div>
        </template>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick, inject } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Delete, Promotion, Upload } from '@element-plus/icons-vue'
import { teacherApi, studentApi } from '@/api/tutor-api'
import type { GeneratedQuestion, SubjectItem, SessionItem, SessionDetail } from '@/api/tutor-api'

const route = useRoute()

// ── 可见性判断 ──
const agentMode = computed<'teacher' | 'student' | null>(() => {
  if (route.path === '/admin-home/questions') return 'teacher'
  if (route.path === '/user-home/wrong-questions') return 'student'
  return null
})

const visible = computed(() => agentMode.value !== null)

// ── 对话框状态 ──
const dialogOpen = ref(false)
const loading = ref(false)
const available = ref(true)
const unavailableReason = ref('')
const sessionId = ref('')

// ── 学生答疑上下文（由 StudentLayout provide，WrongQuestions 设置）──
interface TutorCtx {
  questionId: number | null
  questionContent: string
  studentAnswer: string
  triggerOpen: number
}
const tutorCtx = inject<TutorCtx | null>('tutorContext', null)

// ── 对话 ──
interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMsg[]>([])
const inputText = ref('')
const bodyRef = ref<HTMLElement>()

// ── 推荐 ──
const showRecommend = ref(false)
const recommendMessage = ref('')
const recommendSuggestion = ref<any>(null)

// ── 教师快捷出题 ──
const subjects = ref<SubjectItem[]>([])
const genSubjectId = ref<number | null>(null)
const genSubjectName = ref('')
const genType = ref(1)
const genDifficulty = ref(2)
const genCount = ref(5)
const genExtra = ref('')

function onSubjectChange(val: number | null) {
  const s = subjects.value.find((it) => it.id === val)
  genSubjectName.value = s?.name ?? ''
}

// ── 知识库文档上传 ──
const fileInputRef = ref<HTMLInputElement>()
const uploading = ref(false)

// ── 历史记录面板 ──
const showHistory = ref(false)
const sessions = ref<SessionItem[]>([])
const loadingSessions = ref(false)

function openFilePicker() {
  fileInputRef.value?.click()
}

async function clearKnowledge() {
  try {
    await ElMessageBox.confirm(
      '确定要清空全部知识库内容吗？此操作不可恢复。清空后需重新上传文档。',
      '确认清空',
      { confirmButtonText: '确定清空', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const result: any = await teacherApi.clearKnowledge()
    ElMessage.success(`知识库已清空（教师库 ${result?.teacher_deleted ?? '?'} 条，学生库 ${result?.student_deleted ?? '?'} 条）`)
  } catch (err: any) {
    ElMessage.error(err?.message || '清空失败')
  }
}

async function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 校验格式
  if (!file.name.toLowerCase().endsWith('.pdf') && !file.name.toLowerCase().endsWith('.txt')) {
    ElMessage.warning('仅支持 PDF 和 TXT 格式')
    input.value = ''
    return
  }
  // 校验大小 (≤ 16MB)
  if (file.size > 16 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 16MB')
    input.value = ''
    return
  }

  uploading.value = true
  try {
    const subjectName = genSubjectName.value || '未知科目'
    const result: any = await teacherApi.upload(file, subjectName)
    ElMessage.success(`「${file.name}」上传成功，共入库 ${result?.chunk_count ?? '?'} 个知识条目`)
    // 刷新推荐以体现新知识
    await fetchRecommend()
  } catch (err: any) {
    ElMessage.error(err?.message || `「${file.name}」上传失败`)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

// ── 方法 ──

function openDialog() {
  dialogOpen.value = true
  nextTick(() => scrollToBottom())
}

function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
}

function renderContent(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\n/g, '<br>')
}

async function clearChat() {
  if (agentMode.value === 'student' && sessionId.value) {
    try { await studentApi.clearSession(sessionId.value) } catch { /* 静默 */ }
  }
  sessionId.value = ''
  messages.value = []
}

// ── 历史记录 ──

function formatTime(ts: number): string {
  const d = new Date(ts * 1000)
  const now = Date.now()
  const diff = now - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + ' ' +
         d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function openHistory() {
  showHistory.value = true
  loadingSessions.value = true
  try {
    if (agentMode.value === 'teacher') {
      sessions.value = await teacherApi.getSessions()
    } else {
      sessions.value = await studentApi.getSessions()
    }
  } catch {
    ElMessage.warning('加载历史记录失败')
  } finally {
    loadingSessions.value = false
  }
}

async function loadSession(targetSessionId: string) {
  loading.value = true
  try {
    let detail: SessionDetail
    if (agentMode.value === 'teacher') {
      detail = await teacherApi.getSession(targetSessionId)
    } else {
      detail = await studentApi.getSession(targetSessionId)
    }
    messages.value = detail.messages.map(m => ({
      role: m.role as 'user' | 'assistant',
      content: m.content,
    }))
    sessionId.value = detail.session_id
    showHistory.value = false
    nextTick(() => scrollToBottom())
  } catch {
    ElMessage.error('加载会话失败')
  } finally {
    loading.value = false
  }
}

async function confirmDeleteSession(s: SessionItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${s.title || '未命名对话'}」吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  try {
    if (agentMode.value === 'teacher') {
      await teacherApi.deleteSession(s.session_id)
    } else {
      await studentApi.deleteSession(s.session_id)
    }
    // 如果删的是当前活跃会话，重置聊天
    if (sessionId.value === s.session_id) {
      sessionId.value = ''
      messages.value = []
    }
    // 刷新列表
    sessions.value = sessions.value.filter(it => it.session_id !== s.session_id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

async function fetchRecommend() {
  if (agentMode.value === 'teacher') {
    try {
      const data = await teacherApi.recommend(genSubjectName.value || undefined)
      recommendMessage.value = data.message
      recommendSuggestion.value = data.suggestion
      showRecommend.value = true
    } catch {
      // 静默失败
    }
  } else {
    try {
      const data = await studentApi.recommend()
      recommendMessage.value = data.message
      showRecommend.value = true
    } catch {
      // 静默失败
    }
  }
}

async function checkAvailability() {
  if (agentMode.value === 'student') {
    try {
      const data = await studentApi.status()
      available.value = data.available
      unavailableReason.value = data.reason || ''
    } catch {
      available.value = true
    }
  } else {
    available.value = true
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value || !available.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    if (agentMode.value === 'teacher') {
      // 教师模式：走 /teacher/chat 知识库对话
      const data = await teacherApi.chat(
        text,
        genSubjectName.value || undefined,
        sessionId.value || undefined,
      )
      sessionId.value = data.session_id || ''

      let reply = data.reply
      if (data.sources && data.sources.length > 0) {
        reply += '\n\n📚 参考来源：'
        const seen = new Set<string>()
        data.sources.forEach(s => {
          const key = `${s.source_file}#${s.question_index}`
          if (!seen.has(key)) {
            seen.add(key)
            reply += `\n  - ${s.source_file} 第${s.question_index}题`
          }
        })
      }
      messages.value.push({ role: 'assistant', content: reply })
    } else {
      // 学生模式：流式 SSE 答疑
      const qId = tutorCtx?.questionId ?? undefined
      const qContent = tutorCtx?.questionContent || text
      const sAnswer = tutorCtx?.studentAnswer || undefined
      // 先插入占位消息用于流式更新
      const streamMsgIdx = messages.value.length
      messages.value.push({ role: 'assistant', content: '' })
      scrollToBottom()

      await studentApi.streamAsk(
        {
          questionId: qId,
          questionContent: qContent,
          studentAnswer: sAnswer,
          message: text,
          sessionId: sessionId.value || undefined,
        },
        {
          onStatus: (statusText) => {
            messages.value[streamMsgIdx].content = `⏳ ${statusText}`
          },
          onToken: (token) => {
            // 清除状态前缀
            const cur = messages.value[streamMsgIdx].content
            const clean = cur.startsWith('⏳ ') ? '' : cur
            messages.value[streamMsgIdx].content = clean + token
            scrollToBottom()
          },
          onRewrite: (newText) => {
            messages.value[streamMsgIdx].content = newText
            scrollToBottom()
          },
          onFinal: (data) => {
            let reply = data.reply
            if (data.hints && data.hints.length > 0) {
              reply += '\n\n💡 思考提示：\n' + data.hints.map((h: string, i: number) => `${i + 1}. ${h}`).join('\n')
            }
            messages.value[streamMsgIdx].content = reply
            sessionId.value = data.session_id || ''
            scrollToBottom()
          },
          onError: (errMsg) => {
            messages.value[streamMsgIdx].content = `❌ 出错了：${errMsg}`
          },
          onDone: () => {
            if (!messages.value[streamMsgIdx].content || messages.value[streamMsgIdx].content.startsWith('⏳ ')) {
              messages.value[streamMsgIdx].content = '抱歉，我暂时无法回答这个问题。请稍后再试。'
            }
          },
        },
      )
    }
  } catch (err: any) {
    messages.value.push({
      role: 'assistant',
      content: `❌ 出错了：${err?.message || '请求失败，请稍后重试'}`,
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function doGenerate() {
  if (!genSubjectId.value || loading.value || !available.value) return

  loading.value = true
  try {
    const data = await teacherApi.generate({
      subjectId: genSubjectId.value,
      subjectName: genSubjectName.value,
      questionType: genType.value,
      difficulty: genDifficulty.value,
      count: genCount.value,
      extraRequirement: genExtra.value || undefined,
    })

    sessionId.value = data.session_id || ''

    const qList = data.questions || []
    const ids = data.saved_ids || []
    const failed = data.failed_questions || []
    const warnings = data.warnings || []

    let reply = `✅ 已生成并入库 ${ids.length} 道题目！\n\n`
    qList.forEach((q: GeneratedQuestion, i: number) => {
      reply += `📝 **第${i + 1}题** (ID:${ids[i] || '待定'})\n${q.content}\n`
      if (q.options) {
        try {
          const opts = JSON.parse(q.options)
          if (Array.isArray(opts) && opts.length > 0) {
            opts.forEach((o: string, j: number) => {
              reply += `  ${String.fromCharCode(65 + j)}. ${o}\n`
            })
          }
        } catch {
          reply += `  选项: ${q.options}\n`
        }
      }
      reply += `  答案: ${q.answer}\n  解析: ${q.analysis}\n\n`
    })
    if (failed.length > 0) {
      reply += `\n⚠️ ${failed.length} 道题入库失败:\n`
      failed.forEach((f: any) => { reply += `  - 第${f.index + 1}题: ${f.reason}\n` })
    }
    if (warnings.length > 0) {
      reply += `\n💡 提示：\n${warnings.map((w: string) => `  - ${w}`).join('\n')}`
    }
    messages.value.push({ role: 'assistant', content: reply })
  } catch (err: any) {
    messages.value.push({
      role: 'assistant',
      content: `❌ 生成失败：${err?.message || '请求失败，请稍后重试'}`,
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function quickGenerate() {
  if (!genSubjectId.value) return
  showRecommend.value = false
  doGenerate()
}

// ── 加载科目列表 ──
async function loadSubjects() {
  try {
    subjects.value = await teacherApi.subjects()
    // 默认选第一个
    if (subjects.value.length > 0 && !genSubjectId.value) {
      genSubjectId.value = subjects.value[0].id
      genSubjectName.value = subjects.value[0].name
    }
  } catch (err: any) {
    console.error('[FloatingTutor] 加载科目列表失败:', err?.message || err)
  }
}

// ── 生命周期 ──
watch(agentMode, async (mode) => {
  if (mode) {
    sessionId.value = ''
    if (mode === 'teacher') await loadSubjects()
    await checkAvailability()
    await fetchRecommend()
  } else {
    dialogOpen.value = false
  }
})

// 学生模式：监听来自 WrongQuestions 的 AI 答疑触发
watch(() => tutorCtx?.triggerOpen ?? 0, async (val) => {
  if (!val || agentMode.value !== 'student') return
  // 打开对话框并自动发送引导消息
  dialogOpen.value = true
  showRecommend.value = false
  const qContent = tutorCtx?.questionContent || ''
  const preview = qContent.length > 60 ? qContent.slice(0, 60) + '…' : qContent
  const autoMsg = preview ? `请帮我分析这道错题：${preview}` : '请帮我分析这道错题'
  messages.value = []
  inputText.value = ''
  await checkAvailability()
  // 自动发送（流式）
  messages.value.push({ role: 'user', content: autoMsg })
  loading.value = true
  scrollToBottom()
  const streamMsgIdx = messages.value.length
  messages.value.push({ role: 'assistant', content: '' })
  await studentApi.streamAsk(
    {
      questionId: tutorCtx?.questionId ?? undefined,
      questionContent: tutorCtx?.questionContent || autoMsg,
      studentAnswer: tutorCtx?.studentAnswer || undefined,
      message: autoMsg,
      sessionId: undefined,
    },
    {
      onStatus: (statusText) => {
        messages.value[streamMsgIdx].content = `⏳ ${statusText}`
      },
      onToken: (token) => {
        const cur = messages.value[streamMsgIdx].content
        const clean = cur.startsWith('⏳ ') ? '' : cur
        messages.value[streamMsgIdx].content = clean + token
        scrollToBottom()
      },
      onRewrite: (newText) => {
        messages.value[streamMsgIdx].content = newText
        scrollToBottom()
      },
      onFinal: (data) => {
        let reply = data.reply
        if (data.hints && data.hints.length > 0) {
          reply += '\n\n💡 思考提示：\n' + data.hints.map((h: string, i: number) => `${i + 1}. ${h}`).join('\n')
        }
        messages.value[streamMsgIdx].content = reply
        sessionId.value = data.session_id || ''
        scrollToBottom()
      },
      onError: (errMsg) => {
        messages.value[streamMsgIdx].content = `❌ 出错了：${errMsg}`
      },
      onDone: () => {
        if (!messages.value[streamMsgIdx].content || messages.value[streamMsgIdx].content.startsWith('⏳ ')) {
          messages.value[streamMsgIdx].content = '抱歉，我暂时无法回答这个问题。请稍后再试。'
        }
      },
    },
  )
  loading.value = false
  scrollToBottom()
})

onMounted(() => {
  if (agentMode.value) {
    if (agentMode.value === 'teacher') loadSubjects()
    checkAvailability()
    fetchRecommend()
  }
})
</script>

<style scoped>
.floating-tutor {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9999;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ── 悬浮气泡 ── */
.tutor-bubble {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.45);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: transform 0.2s, box-shadow 0.2s;
  animation: float 3s ease-in-out infinite;
}

.tutor-bubble:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 28px rgba(102, 126, 234, 0.6);
}

.tutor-icon {
  font-size: 28px;
  z-index: 1;
}

.tutor-pulse {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid rgba(102, 126, 234, 0.3);
  animation: pulse 2s ease-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.5); opacity: 0; }
}

/* ── 对话框 ── */
.tutor-dialog {
  width: 420px;
  height: 560px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: all 0.3s ease;
}
.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

/* ── 对话框头部 ── */
.dialog-header {
  height: 60px;
  padding: 0 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-avatar {
  font-size: 24px;
}

.header-left strong {
  font-size: 15px;
  display: block;
}

.header-left small {
  font-size: 11px;
  opacity: 0.8;
}

.header-actions .el-button {
  color: #fff;
}

/* ── 对话框主体 ── */
.dialog-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  background: #f8f9fc;
}

/* ── 推荐卡片 ── */
.recommend-card {
  background: linear-gradient(135deg, #fff8e1, #fff3cd);
  border: 1px solid #ffe082;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.recommend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
}

.recommend-text {
  font-size: 13px;
  color: #555;
  margin: 0 0 8px;
  line-height: 1.6;
}

/* ── 消息 ── */
.chat-message {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e8ecf4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message.assistant .msg-bubble {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-top-left-radius: 4px;
  color: #333;
}

.chat-message.user .msg-bubble {
  background: #667eea;
  color: #fff;
  border-top-right-radius: 4px;
}

/* ── 加载动画 ── */
.loading-dots {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 8px 0;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: dot-bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(2) { animation-delay: 0.16s; }
.loading-dots span:nth-child(3) { animation-delay: 0.32s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* ── 输入区 ── */
.dialog-footer {
  padding: 10px 16px 14px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.quick-panel {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.upload-row {
  width: 100%;
  padding-top: 4px;
  border-top: 1px dashed #e0e0e0;
  margin-top: 2px;
}

.input-row {
  display: flex;
  gap: 8px;
}

.unavailable-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #e74c3c;
  text-align: center;
}

/* ── 历史面板 ── */
.history-panel {
  background: #f8f9fc;
}

.history-loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
  color: #999;
  font-size: 14px;
}

.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #999;
}

.history-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.history-empty p {
  margin: 0;
  font-size: 14px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.session-card:hover {
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.12);
}

.session-card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}

.session-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.session-time {
  font-size: 11px;
  color: #999;
  flex-shrink: 0;
  margin-left: 8px;
}

.session-preview {
  font-size: 12px;
  color: #777;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 6px;
}

.session-actions {
  display: flex;
  justify-content: flex-end;
}

.history-footer {
  display: flex;
  justify-content: center;
  padding: 10px 16px;
}

/* ── 响应式 ── */
@media (max-width: 480px) {
  .tutor-dialog {
    width: calc(100vw - 32px);
    height: calc(100vh - 80px);
    right: 0;
    bottom: 0;
    border-radius: 16px 16px 0 0;
  }
}
</style>

<style>
/* 全局样式 — el-select popper 提升 z-index（必须放在非 scoped 块中） */
.tutor-select-popper {
  z-index: 99999 !important;
}
</style>
