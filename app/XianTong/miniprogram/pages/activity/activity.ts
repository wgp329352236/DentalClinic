// pages/activity/activity.ts
import type { ActivityDetail } from '../../types'
import { getActivityDetail } from '../../utils/api'
import { resolveFileUrl } from '../../utils/util'

/** 后端返回 "YYYY-MM-DD HH:mm:ss"，详情页只展示日期；单边为空时用「即日 / 长期」兜底 */
function formatPeriod(startAt?: string, endAt?: string): string {
  const start = startAt ? startAt.slice(0, 10) : ''
  const end = endAt ? endAt.slice(0, 10) : ''
  if (!start && !end) return '长期有效'
  return `${start || '即日'} 至 ${end || '长期'}`
}

Page({
  data: {
    loading: true,
    activity: null as ActivityDetail | null,
    cover: '',
    /** 详情纯文本按空行/换行切段，wxml 逐段渲染 */
    paragraphs: [] as string[],
    period: '',
  },

  onLoad(options: Record<string, string | undefined>) {
    const id = Number(options.id)
    if (!id) {
      // 正常入口都会带 id；缺失时直接落到「已结束」态，不做无效请求
      this.setData({ loading: false })
      return
    }
    this.loadDetail(id)
  },

  async loadDetail(id: number) {
    try {
      const result = await getActivityDetail(id)
      if (result.code !== 0 || !result.data) {
        // 404 是正常业务分支（活动下架/过期），不弹系统错误，只展示占位
        this.setData({ loading: false, activity: null })
        return
      }
      const activity = result.data
      this.setData({
        loading: false,
        activity,
        cover: activity.coverImage ? resolveFileUrl(activity.coverImage) : '',
        paragraphs: (activity.content || '')
          .split(/\n+/)
          .map((item) => item.trim())
          .filter(Boolean),
        period: formatPeriod(activity.startAt, activity.endAt),
      })
      wx.setNavigationBarTitle({ title: activity.title })
    } catch (error) {
      console.warn('[Activity] 加载活动详情失败:', error)
      this.setData({ loading: false })
    }
  },

  /** 封面地址失效时退回标题占位，避免整块空白 */
  onCoverError() {
    this.setData({ cover: '' })
  },

  onBookTap() {
    // 预约页是 tabBar 页，只能 switchTab
    wx.switchTab({ url: '/pages/appointment/appointment' })
  },
})
