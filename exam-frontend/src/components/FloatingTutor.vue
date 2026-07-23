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
            <el-button :icon="Delete" text size="small" @click="clearChat" title="清空对话">清空</el-button>
            <el-button :icon="Close" text size="small" @click="dialogOpen = false" title="最小化" />
          </div>
        </div>

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
          </div>
          <div class="input-row">
            <el-input
              v-model="inputText"
              :placeholder="agentMode === 'teacher' ? '描述出题需求，或点击上方快速生成...' : '输入你的疑问...'"
              @keyup.enter="sendMessage"
              :disabled="loading || !available"
            />
            <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading" :disabled="!available">
              发送
            </el-button>
          </div>
          <div v-if="!available" class="unavailable-hint">
            ⛔ {{ unavailableReason }}
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { Close, Delete, Promotion } from '@element-plus/icons-vue'
import { teacherApi, studentApi } from '@/api/tutor-api'
import type { GeneratedQuestion, SubjectItem } from '@/api/tutor-api'

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
      if (!genSubjectId.value) {
        messages.value.push({ role: 'assistant', content: '⚠️ 请先选择一个科目' })
        loading.value = false
        return
      }
      const data = await teacherApi.generate({
        subjectId: genSubjectId.value,
        subjectName: genSubjectName.value,
        questionType: genType.value,
        difficulty: genDifficulty.value,
        count: genCount.value,
        extraRequirement: text || genExtra.value || undefined,
      })

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
    } else {
      const data = await studentApi.ask({
        questionContent: text,
        message: text,
        sessionId: sessionId.value || undefined,
      })

      sessionId.value = data.session_id || ''
      let reply = data.reply
      if (data.hints && data.hints.length > 0) {
        reply += '\n\n💡 思考提示：\n' + data.hints.map((h: string, i: number) => `${i + 1}. ${h}`).join('\n')
      }
      messages.value.push({ role: 'assistant', content: reply })
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

function quickGenerate() {
  if (!genSubjectId.value) return
  inputText.value = `请为${genSubjectName.value}生成${genCount.value}道${['', '单选题', '多选题', '判断题', '主观题'][genType.value]}`
  showRecommend.value = false
  sendMessage()
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
