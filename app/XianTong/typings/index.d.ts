/// <reference path="./types/index.d.ts" />

import type { UserInfo } from '../miniprogram/types'

declare global {
  interface IAppOption {
    globalData: {
      userInfo: UserInfo | null
      token: string
      clinics: any[]
    }
    setUserInfo(userInfo: UserInfo): void
    setToken(token: string): void
    logout(): void
  }
}

export {}
