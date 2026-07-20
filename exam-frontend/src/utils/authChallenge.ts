export type AuthPurpose = 'LOGIN' | 'REGISTER'

export interface AuthChallengeState {
  challengeId: string
  purpose: AuthPurpose
  maskedEmail?: string
  emailRequired: boolean
  expiresAt: number
}

const AUTH_CHALLENGE_KEY = 'AUTH_CHALLENGE'

export function saveAuthChallenge(challenge: AuthChallengeState) {
  sessionStorage.setItem(AUTH_CHALLENGE_KEY, JSON.stringify(challenge))
}

export function getAuthChallenge(): AuthChallengeState | null {
  const raw = sessionStorage.getItem(AUTH_CHALLENGE_KEY)
  if (!raw) return null
  try {
    const challenge = JSON.parse(raw) as AuthChallengeState
    if (!challenge.challengeId || challenge.expiresAt <= Date.now()) {
      clearAuthChallenge()
      return null
    }
    return challenge
  } catch {
    clearAuthChallenge()
    return null
  }
}

export function clearAuthChallenge() {
  sessionStorage.removeItem(AUTH_CHALLENGE_KEY)
}
