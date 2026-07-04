import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: {
            title: '登录首页',
            requiresAuth: false

        }
    },
    {
        path: '/user_home',
        name: 'user_home',
        component: () => import('@/views/UserHome.vue'),
        meta: {
            title: '用户首页',
            requiresAuth: false

        }
    }

]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

export default router