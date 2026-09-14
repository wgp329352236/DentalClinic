/// <reference path="./types/index.d.ts" />

import type { UserInfo } from '../miniprogram/types'

declare global {
  interface IAppOption {
    globalData: {
      userInfo: UserInfo | null
      token: string
      clinics: any[]
      /** 首页点门店后中转给预约页的门诊 id；预约页 onShow 消费后立即置空 */
      pendingClinicId: number | null
    }
    setUserInfo(userInfo: UserInfo): void
    setToken(token: string): void
    /** 预约页是 tabBar 页，switchTab 不能带 query，故用全局态中转门诊 id */
    setPendingClinicId(clinicId: number | null): void
    logout(): void
  }
}

export {}
