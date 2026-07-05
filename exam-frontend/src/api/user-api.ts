import type { Result } from "@/types/result"
import type {
    BaseUserVO,
    UserLoginDTO,
    UserLoginResponseVO
} from "@/types/user"
import request from "@/utils/request"
// 用户登录
export const loginApi = (params: UserLoginDTO) => {
    return request.post<Result<UserLoginResponseVO>>("/user/login", params)
}
// Token认证
export const userTokenAuthApi = (token: string) => {
    return request.get<Result<BaseUserVO>>(`/user/${token}/auth`, { token })
}
