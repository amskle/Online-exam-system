
/**
 * 用户登录参数接受类
 */
export interface UserLoginDTO {
    account?: string
    password?: string
}
/**
 * 登录响应数据返回类
 */
export interface UserLoginResponseVO {
    status?: 'AUTHENTICATED' | 'EMAIL_REQUIRED' | 'EMAIL_VERIFICATION_REQUIRED'
    token?: string
    role?: number
    roleName?: string
    challengeId?: string
    maskedEmail?: string
    expiresIn?: number
}
/**
 * 用户注册参数
 */
export interface UserRegisterDTO {
    account?: string
    password?: string
    username?: string
    role?: number
    email?: string
}

export interface EmailSendDTO {
    challengeId: string
    email?: string
}

export interface EmailVerifyDTO {
    challengeId: string
    code: string
    trustDevice: boolean
}

/**
 * 修改密码参数类型
 */
export interface UserUpdatePasswordDTO {
    password?: string
}
/**
 * 基础用户信息
 */
export interface BaseUserVO {
    id?: number
    account?: string
    username?: string
    avatar?: string
    gender?: number
    phone?: string
    email?: string
    emailVerifyTime?: string
    loginStatus?: boolean
    role?: number
}
/**
 * 基础用户信息修改
 */
export interface BaseUserUpdateDTO {
    id?: number
    username?: string
    avatar?: string
    gender?: number
    phone?: string
    email?: string
    loginStatus?: boolean
    role?: number
}
