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
      /** 分享链接/扫码进入携带的待绑定推荐码；登录成功后消费并清空 */
      pendingReferralCode: string | null
    }
    setUserInfo(userInfo: UserInfo): void
    setToken(token: string): void
    /** 预约页是 tabBar 页，switchTab 不能带 query，故用全局态中转门诊 id */
    setPendingClinicId(clinicId: number | null): void
    /** 暂存/清除待绑定推荐码（同时同步本地 storage） */
    setPendingReferralCode(code: string | null): void
    logout(): void
  }
}

export {}
