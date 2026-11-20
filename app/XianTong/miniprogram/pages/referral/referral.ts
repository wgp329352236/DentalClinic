// pages/referral/referral.ts
import {
  getReferralCode,
  getReferralQrCode,
  getReferralRecords
} from '../../utils/api'
import type { ReferralInfo, ReferralReward } from '../../types'

function rewardPoints(value?: number | null) {
  return typeof value === 'number' && value > 0 ? value : undefined
}

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
      const [codeRes, recordsRes, qrRes] = await Promise.all([
        getReferralCode(),
        getReferralRecords(),
        getReferralQrCode().catch(() => null)
      ])

      if (codeRes.code === 0 && codeRes.data) {
        const records = recordsRes.code === 0 && recordsRes.data ? recordsRes.data : null
        const rewards: ReferralReward[] = []
        for (const record of records?.list || []) {
          for (const reward of record.rewards || []) {
            rewards.push({
              id: reward.id,
              type: reward.type,
              rewardType: reward.rewardType,
              rewardValue: reward.rewardValue,
              points:
                reward.rewardType === 'points'
                  ? rewardPoints(reward.rewardValue)
                  : undefined,
              balance:
                reward.rewardType === 'balance'
                  ? rewardPoints(reward.rewardValue)
                  : undefined,
              status: reward.status,
              referredPerson: record.refereeName,
              createdAt: reward.createdAt
            })
          }
        }
        for (const reward of records?.tierRewards || []) {
          rewards.push({
            id: reward.id,
            type: 'tier',
            rewardType: reward.rewardType,
            rewardValue: reward.rewardValue,
            points:
              reward.rewardType === 'points'
                ? rewardPoints(reward.rewardValue)
                : undefined,
            balance:
              reward.rewardType === 'balance'
                ? rewardPoints(reward.rewardValue)
                : undefined,
            status: reward.status,
            createdAt: reward.createdAt
          })
        }
        const referralInfo: ReferralInfo = {
          referralCode: codeRes.data.referralCode,
          referralLink: codeRes.data.shareLink,
          totalReferrals: records?.total || 0,
          monthlyReferrals: records?.monthlyCount || 0,
          pointsGranted: records?.summary?.pointsGranted || 0,
          balanceGranted: records?.summary?.balanceGranted || 0,
          pendingRewards: records?.summary?.pendingCount || 0,
          qrcodeImage:
            qrRes?.code === 0 && qrRes.data?.available
              ? qrRes.data.qrcodeImageBase64
              : undefined,
          qrcodeTip:
            qrRes?.code === 0 && qrRes.data?.available
              ? undefined
              : qrRes?.data?.message || '当前环境暂无法生成小程序码',
          rewards
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
