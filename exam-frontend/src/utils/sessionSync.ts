const CHANNEL_NAME = 'exam-session-sync'

let channel: BroadcastChannel | null = null

function getChannel(): BroadcastChannel {
  if (!channel) {
    channel = new BroadcastChannel(CHANNEL_NAME)
  }
  return channel
}

/** 登录成功后广播，通知同浏览器的其他标签页（同一账号会被顶掉） */
export function broadcastLogin(userId: number) {
  try {
    getChannel().postMessage({ type: 'login', userId, time: Date.now() })
  } catch {
    // BroadcastChannel 不支持时静默失败
  }
}

/** 监听其他标签页的登录事件，当同一账号被顶时执行回调 */
export function onKicked(handler: (userId: number) => void) {
  try {
    getChannel().addEventListener('message', (event) => {
      if (event.data?.type === 'login') {
        handler(event.data.userId as number)
      }
    })
  } catch {
    // BroadcastChannel 不支持时静默失败
  }
}

export function closeChannel() {
  if (channel) {
    channel.close()
    channel = null
  }
}
