// pages/referral/referral.ts
import { getReferralCode, getReferralRecords } from '../../utils/api'
import type { ReferralInfo } from '../../types'

Page({
  data: {
    referralInfo: null as ReferralInfo | null,
    loading: false
  },

  onLoad() {
    this.loadReferralInfo()
  },

  // 加载推荐信息（GET /referral/code + GET /referral/records，映射后端字段）
  async loadReferralInfo() {
    this.setData({ loading: true })
    try {
      const [codeRes, recordsRes] = await Promise.all([
        getReferralCode(),
        getReferralRecords()
      ])

      if (codeRes.code === 0 && codeRes.data) {
        const records = recordsRes.code === 0 && recordsRes.data ? recordsRes.data : null
        const referralInfo: ReferralInfo = {
          referralCode: codeRes.data.referralCode,
          referralLink: codeRes.data.shareLink,
          totalReferrals: records?.total || 0,
          monthlyReferrals: 0, // 后端暂无本月推荐统计
          rewards: (records?.list || []).map((r: any) => ({
            id: r.id,
            type: 'register',
            status: r.rewardStatus === 'full' ? 'issued' : 'pending',
            referredPerson: r.refereeName,
            createdAt: r.registeredAt
          }))
        }
        this.setData({ referralInfo })
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
