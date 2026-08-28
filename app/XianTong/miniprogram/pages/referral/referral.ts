// pages/referral/referral.ts
import { get } from '../../utils/request'
import type { ReferralInfo } from '../../types'

Page({
  data: {
    referralInfo: null as ReferralInfo | null,
    loading: false
  },

  onLoad() {
    this.loadReferralInfo()
  },

  async loadReferralInfo() {
    this.setData({ loading: true })
    try {
      const result = await get<ReferralInfo>('/referral')
      if (result.code === 0 && result.data) {
        this.setData({ referralInfo: result.data })
      }
    } catch (error) {
      console.error('加载推荐信息失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  },

  // 复制链接
  copyLink() {
    const link = this.data.referralInfo?.referralLink
    if (link) {
      wx.setClipboardData({
        data: link,
        success: () => {
          wx.showToast({ title: '链接已复制', icon: 'success' })
        }
      })
    }
  },

  // 分享小程序
  onShareAppMessage() {
    const referralCode = this.data.referralInfo?.referralCode
    return {
      title: '牙科门诊，专业口腔护理，注册送100元券！',
      path: `/pages/index/index?ref=${referralCode}`,
      imageUrl: '/images/share-cover.jpg'
    }
  }
})
