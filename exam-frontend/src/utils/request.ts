import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
    return config
})

request.interceptors.response.use(
    (response) => {
        return response.data
    },
    (error) => {
        ElMessage.error(error.message || '请求失败，请稍后再试')
        return Promise.reject(error)
    }
)

export default request