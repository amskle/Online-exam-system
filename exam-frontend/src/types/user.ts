
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
    token?: string
    role?: number
    roleName?: string
}
/**
 * 用户注册参数
 */
export interface UserRegisterDTO {
    account?: string
    password?: string
    username?: string
    role?: number
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
    loginStatus?: boolean
    role?: number
}