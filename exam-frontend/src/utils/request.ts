import axios, { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from './localStorage'

// 创建 axios 实例
const instance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 15000,
})
// 请求拦截器
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})
// 响应拦截器
instance.interceptors.response.use(
    (response: AxiosResponse) => {
        if (response.data.code !== 200) {
            return Promise.reject(response.data)
        }
        return response.data
    },
    (error) => {
        ElMessage.error(error.message || '请求失败，请稍后再试')
        return Promise.reject(error)
    }
)
// 封装请求方法
const request = {
    post: <T = any>(url: string, data?: any): Promise<T> => {
        return instance.post(url, data)
    },
    get: <T = any>(url: string, params?: any): Promise<T> => {
        return instance.get(url, { params })
    },
    put: <T = any>(url: string, data?: any): Promise<T> => {
        return instance.put(url, data)
    },
    delete: <T = any>(url: string, params?: any): Promise<T> => {
        return instance.delete(url, { params })
    }
}

export default request
