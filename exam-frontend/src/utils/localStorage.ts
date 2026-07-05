const ROLE_KEY = 'ROLE'
const ROLE_NAME_KEY = 'ROLE_NAME'
const TOKEN_KEY = 'TOKEN'

export enum RoleEnum {
    STUDENT = 1,
    TEACHER = 2,
    ADMIN = 3,
}

export const setRole = (role: RoleEnum) => {
    localStorage.setItem(ROLE_KEY, String(role))
};

export const getRole = (): RoleEnum | null => {
    const role = localStorage.getItem(ROLE_KEY)
    return role ? (Number(role) as RoleEnum) : null
};

export const setRoleName = (roleName: string) => {
    localStorage.setItem(ROLE_NAME_KEY, roleName)
};

export const getRoleName = (): string | null => {
    return localStorage.getItem(ROLE_NAME_KEY)
};

export const setToken = (token: string) => {
    localStorage.setItem(TOKEN_KEY, token)
};

export const getToken = () => {
    return localStorage.getItem(TOKEN_KEY)
};

// 清除角色
export const clearRole = () => {
    localStorage.removeItem(ROLE_KEY)
};

// 清除角色名称
export const clearRoleName = () => {
    localStorage.removeItem(ROLE_NAME_KEY)
};

// 清除 Token
export const clearToken = () => {
    localStorage.removeItem(TOKEN_KEY)
};

// 清除所有登录信息（组合方法，最常用）
export const clearAllAuth = () => {
    localStorage.removeItem(ROLE_KEY)
    localStorage.removeItem(ROLE_NAME_KEY)
    localStorage.removeItem(TOKEN_KEY)
};
