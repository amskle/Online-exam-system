import type { Result } from "@/types/result"
import type {
    BaseUserVO,
    UserLoginDTO,
    UserLoginResponseVO,
    UserUpdatePasswordDTO
} from "@/types/user"
import request from "@/utils/request"
// 用户登录
export const loginApi = (params: UserLoginDTO) => {
    return request.post<Result<UserLoginResponseVO>>("/user/login", params)
}
// Token认证
export const userTokenAuthApi = (token: string) => {
    return request.get<Result<BaseUserVO>>(`/user/${token}/auth`)
}
// 用户修改密码
export const updatePasswordApi = (id: number, params: UserUpdatePasswordDTO) => {
    return request.put<Result<void>>(`/user/${id}/updatePassword`, params)
}
