
// 通用接口返回结果类型
export interface Result<T> {
    code: number
    message: string
    data?: T
    count?: number
    timestamp?: number
}