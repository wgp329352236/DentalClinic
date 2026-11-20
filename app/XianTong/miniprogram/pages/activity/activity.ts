// pages/activity/activity.ts
import type { ActivityDetail } from '../../types'
import { getActivityDetail } from '../../utils/api'
import { requestSubscribeMessage, resolveFileUrl } from '../../utils/util'

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
    /** 重试需要，onLoad 的 options 只在首次进入时可用 */
    activityId: 0,
    activity: null as ActivityDetail | null,
    cover: '',
    /** 详情纯文本按空行/换行切段，wxml 逐段渲染 */
    paragraphs: [] as string[],
    period: '',
    /** 断网/超时/服务异常的提示；活动已结束属于正常分支，不算错误 */
    errorMessage: '',
  },

  onLoad(options: Record<string, string | undefined>) {
    const id = Number(options.id)
    if (!id) {
      // 正常入口都会带 id；缺失时直接落到「已结束」态，不做无效请求
      this.setData({ loading: false })
      return
    }
    this.setData({ activityId: id })
    this.loadDetail(id)
  },

  async loadDetail(id: number) {
    this.setData({ loading: true, errorMessage: '' })
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
      // 请求层已把业务码挂在 error.code 上：404/400 说明活动真的不存在或已结束，
      // 其余（断网、超时、500）是加载失败，不能也提示「已结束」，否则会误导用户
      const code = (error as { code?: number }).code
      if (code === 404 || code === 400) {
        this.setData({ loading: false, activity: null })
        return
      }
      console.warn('[Activity] 加载活动详情失败:', error)
      this.setData({ loading: false, errorMessage: '活动加载失败' })
    }
  },

  onRetryTap() {
    if (this.data.activityId) this.loadDetail(this.data.activityId)
  },

  /** 封面地址失效时退回标题占位，避免整块空白 */
  onCoverError() {
    this.setData({ cover: '' })
  },

  onBookTap() {
    // 用户在活动页产生明确操作时申请一次活动上线通知授权，不阻塞预约跳转。
    requestSubscribeMessage(['activity_notice'])
    // 预约页是 tabBar 页，只能 switchTab
    wx.switchTab({ url: '/pages/appointment/appointment' })
  },
})
