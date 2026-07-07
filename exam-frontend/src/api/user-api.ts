import type { Result } from "@/types/result"
import type {
    BaseUserUpdateDTO,
    BaseUserVO,
    UserLoginDTO,
    UserLoginResponseVO,
    UserRegisterDTO,
    UserUpdatePasswordDTO,
} from "@/types/user"
import request from "@/utils/request"
// 用户登录
export const loginApi = (params: UserLoginDTO) => {
    return request.post<Result<UserLoginResponseVO>>("/user/login", params)
}
// 用户注册
export const registerApi = (params: UserRegisterDTO) => {
    return request.post<Result<string>>("/user/register", params)
}
// Token认证
export const userTokenAuthApi = (token: string) => {
    return request.get<Result<BaseUserVO>>(`/user/${token}/auth`)
}
// 用户修改密码
export const updatePasswordApi = (id: number, params: UserUpdatePasswordDTO) => {
    return request.put<Result<void>>(`/user/${id}/updatePassword`, params)
}
// 用户修改个人信息
export const userUpdateInfoApi = (params: BaseUserUpdateDTO) => {
    return request.put<Result<void>>(`/user`, params)
}

// 上传头像
export const uploadAvatarApi = (params: BaseUserUpdateDTO) => {
    return request.put<Result<void>>(`/user/uploadAvatar`, params)
}
