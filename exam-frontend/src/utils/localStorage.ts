const ROLE_KEY = 'ROLE'
const ROLE_NAME_KEY = 'ROLE_NAME'
const TOKEN_KEY = 'TOKEN'
const REMEMBER_KEY = 'remember_login'

// ── 会话级存储（sessionStorage）──
// 每个标签页独立，关闭标签页自动清除，同浏览器多标签页不同账号互不干扰
const sessionStore = {
  get(key: string) { return sessionStorage.getItem(key) },
  set(key: string, value: string) { sessionStorage.setItem(key, value) },
  remove(key: string) { sessionStorage.removeItem(key) }
}

// ── 持久存储（localStorage）──
// 仅用于"记住账号"等跨标签页共享的非敏感偏好

interface RememberedLogin {
  account: string
}

export function saveRememberedLogin(account: string) {
  const payload: RememberedLogin = { account }
  localStorage.setItem(REMEMBER_KEY, JSON.stringify(payload))
}

export function getRememberedLogin(): RememberedLogin | null {
  const raw = localStorage.getItem(REMEMBER_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return { account: parsed.account }
  } catch {
    return null
  }
}

export function clearRememberedLogin() {
  localStorage.removeItem(REMEMBER_KEY)
}

export enum RoleEnum {
  STUDENT = 1,
  TEACHER = 2,
  ADMIN = 3,
}

export const setRole = (role: RoleEnum) => {
  sessionStore.set(ROLE_KEY, String(role))
}

export const getRole = (): RoleEnum | null => {
  const role = sessionStore.get(ROLE_KEY)
  return role ? (Number(role) as RoleEnum) : null
}

export const setRoleName = (roleName: string) => {
  sessionStore.set(ROLE_NAME_KEY, roleName)
}

export const getRoleName = (): string | null => {
  return sessionStore.get(ROLE_NAME_KEY)
}

export const setToken = (token: string) => {
  sessionStore.set(TOKEN_KEY, token)
}

export const getToken = () => {
  return sessionStore.get(TOKEN_KEY)
}

export const clearRole = () => {
  sessionStore.remove(ROLE_KEY)
}

export const clearRoleName = () => {
  sessionStore.remove(ROLE_NAME_KEY)
}

export const clearToken = () => {
  sessionStore.remove(TOKEN_KEY)
}

export const clearAllAuth = () => {
  sessionStore.remove(ROLE_KEY)
  sessionStore.remove(ROLE_NAME_KEY)
  sessionStore.remove(TOKEN_KEY)
}
