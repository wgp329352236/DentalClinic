/// <reference path="./types/index.d.ts" />

import type { UserInfo } from '../miniprogram/types'

declare global {
  interface IAppOption {
    globalData: {
      userInfo: UserInfo | null
      token: string
      clinics: any[]
      /** 微信登录时拿到的手机号（open-type="getPhoneNumber" 授权得到），编辑资料页只读使用 */
      wechatPhone: string
      /** 微信登录时拿到的昵称，用于上报服务端并作为昵称默认值 */
      wechatNickname: string
      /** 微信登录时取到的头像 URL（仅本地缓存，不上传服务器） */
      wechatAvatar: string
      /** 用户首次选择的头像文件路径（保存在小程序本地，之后只读） */
      localAvatarPath: string
      /** 首页点门店后中转给预约页的门诊 id；预约页 onShow 消费后立即置空 */
      pendingClinicId: number | null
      /** 预约记录页点改约后中转给预约页的预约 id；预约页 onShow 消费后立即置空 */
      pendingRescheduleId: number | null
      /** 分享链接/扫码进入携带的待绑定推荐码；登录成功后消费并清空 */
      pendingReferralCode: string | null
      /** 扫码进入携带的推广券模板 id（promo 参数）；优惠券页消费领 2 张券后清空 */
      pendingPromoTemplateId: number | null
      /** 优惠券页「去使用」后中转给预约页的券码 */
      pendingCouponCode: string | null
    }
    setUserInfo(userInfo: UserInfo): void
    setToken(token: string): void
    /** 设置微信登录手机号（更新全局态） */
    setWechatPhone(phone: string): void
    /** 设置微信登录昵称（更新全局态） */
    setWechatNickname(nickname: string): void
    /** 缓存微信登录头像 URL（只存本地，不上传服务器） */
    setWechatAvatar(avatar: string): void
    /** 记录/清除本地头像文件路径（已保存到小程序本地文件） */
    setLocalAvatarPath(path: string): void
    /** 本地头像文件是否仍可访问 */
    isLocalAvatarUsable(path: string): boolean
    /** 头像展示地址：本地头像 → 微信头像 → 服务端旧头像 → 包内默认图 */
    getAvatarUrl(serverAvatar?: string): string
    /** 预约页是 tabBar 页，switchTab 不能带 query，故用全局态中转门诊 id */
    setPendingClinicId(clinicId: number | null): void
    /** 预约页是 tabBar 页，switchTab 不能带 query，故用全局态中转待改约预约 id */
    setPendingRescheduleId(appointmentId: number | null): void
    /** 暂存/清除待绑定推荐码（同时同步本地 storage） */
    setPendingReferralCode(code: string | null): void
    /** 暂存/清除待领取推广券模板 id（同时同步本地 storage） */
    setPendingPromo(promo: string | null): void
    /** 设置待使用的优惠券码（tabBar 页面间中转） */
    setPendingCouponCode(couponCode: string | null): void
    logout(): void
  }
}

export {}
