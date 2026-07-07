import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getRole, getToken, RoleEnum } from '@/utils/localStorage'

declare module 'vue-router' {
    interface RouteMeta {
        title?: string
        requiresAuth?: boolean
        roles?: RoleEnum[]
    }
}

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'Home',
        component: () => import('@/views/Login.vue'),
        meta: {
            title: '登录首页',
            requiresAuth: false
        }
    },
    {
        path: '/401',
        name: 'Unauthorized',
        component: () => import('@/views/error/401.vue'),
        meta: {
            title: '未授权访问',
            requiresAuth: false
        }
    },
    {
        path: '/admin-home',
        component: () => import('@/layouts/AdminLayout.vue'),
        redirect: () => {
            const role = getRole()
            return role === RoleEnum.ADMIN ? '/admin-home/dashboards' : '/admin-home/questions'
        },
        meta: {
            title: '后台管理',
            requiresAuth: true,
            roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
        },
        children: [
            {
                path: 'dashboards',
                name: 'AdminDashboard',
                component: () => import('@/views/admin/Dashboard.vue'),
                meta: {
                    title: '仪表盘',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN]
                }
            },
            {
                path: 'users',
                name: 'AdminUsers',
                component: () => import('@/views/admin/Users.vue'),
                meta: {
                    title: '用户管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN]
                }
            },
            {
                path: 'admins',
                name: 'AdminManagement',
                component: () => import('@/views/admin/AdminManagement.vue'),
                meta: {
                    title: '管理员管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN]
                }
            },
            {
                path: 'subjects',
                name: 'SubjectManagement',
                component: () => import('@/views/admin/SubjectManagement.vue'),
                meta: {
                    title: '科目管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN]
                }
            },
            {
                path: 'questions',
                name: 'QuestionManagement',
                component: () => import('@/views/admin/QuestionManagement.vue'),
                meta: {
                    title: '题目管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
                }
            },
            {
                path: 'examPapers',
                name: 'ExamPaperManagement',
                component: () => import('@/views/admin/ExamPaperManagement.vue'),
                meta: {
                    title: '试卷管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
                }
            },
            {
                path: 'examRecords',
                name: 'AdminExamRecords',
                component: () => import('@/views/admin/AdminExamRecords.vue'),
                meta: {
                    title: '考试记录管理',
                    requiresAuth: true,
                    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
                }
            }
        ]
    },
    {
        path: '/user_home',
        name: 'user_home',
        redirect: '/user-home/dashboards',
        meta: {
            title: '用户首页',
            requiresAuth: true,
            roles: [RoleEnum.STUDENT]
        }
    },
    {
        path: '/user-home',
        component: () => import('@/layouts/StudentLayout.vue'),
        redirect: '/user-home/dashboards',
        meta: {
            title: '学生考试中心',
            requiresAuth: true,
            roles: [RoleEnum.STUDENT]
        },
        children: [
            {
                path: 'dashboards',
                name: 'StudentDashboards',
                component: () => import('@/views/user/Dashboards.vue'),
                meta: {
                    title: '考试列表',
                    requiresAuth: true,
                    roles: [RoleEnum.STUDENT]
                }
            },
            {
                path: 'records',
                name: 'StudentExamRecords',
                component: () => import('@/views/user/ExamRecords.vue'),
                meta: {
                    title: '考试记录',
                    requiresAuth: true,
                    roles: [RoleEnum.STUDENT]
                }
            },
            {
                path: 'wrong-questions',
                name: 'StudentWrongQuestions',
                component: () => import('@/views/user/WrongQuestions.vue'),
                meta: {
                    title: '错题集',
                    requiresAuth: true,
                    roles: [RoleEnum.STUDENT]
                }
            }
        ]
    },
    {
        path: '/exam/:id',
        name: 'StudentExam',
        component: () => import('@/views/exam/Exam.vue'),
        meta: {
            title: '在线考试',
            requiresAuth: true,
            roles: [RoleEnum.STUDENT]
        }
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/error/404.vue'),
        meta: {
            title: '页面不存在',
            requiresAuth: false
        }
    }
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

router.beforeEach((to, _from, next) => {
    const token = getToken()
    const role = getRole()

    if (to.meta.requiresAuth && !token) {
        next('/')
        return
    }

    const roleMatchedRoute = [...to.matched].reverse().find((record) => record.meta.roles?.length)
    const allowedRoles = roleMatchedRoute?.meta.roles ?? []

    if (token && allowedRoles.length && (!role || !allowedRoles.includes(role))) {
        next('/401')
        return
    }

    next()
})

export default router
