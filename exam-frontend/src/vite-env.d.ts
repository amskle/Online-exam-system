/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_ENV: string
    readonly VITE_API_BASE_URL: string
    readonly VITE_DEBUG: string

    readonly BASE_URL: string
    readonly MODE: string
    readonly DEV: boolean
    readonly PROD: boolean
    readonly SSR: boolean
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}