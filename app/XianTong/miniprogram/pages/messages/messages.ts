// pages/messages/messages.ts
import { getMessages, markAllRead, markMessageRead } from '../../utils/api'
import type { Message } from '../../utils/api'
import { checkLogin } from '../../utils/auth'

const PAGE_SIZE = 20

// 后端 notification.msg_type 的实际取值，与 ConsumptionAdminService / ReferralRewardService 写入的一致
const TABS = [
  { key: '', label: '全部' },
  { key: 'system', label: '系统通知' },
  { key: 'consumption', label: '消费提醒' },
  { key: 'activity', label: '活动消息' }
]

const typeTextMap: Record<string, string> = {
  system: '系统通知',
  consumption: '消费提醒',
  activity: '活动消息'
}

Page({
  data: {
    tabs: TABS,
    activeTab: '',
    messages: [] as (Message & { typeText: string })[],
    unreadCount: 0,
    page: 1,
    total: 0,
    loading: false,
    hasMore: true
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadMessages(true)
  },

  onShow() {
    // 从别处（如消费完成）回到本页时刷新未读角标
    if (this.data.messages.length > 0) this.loadMessages(true)
  },

  onPullDownRefresh() {
    this.loadMessages(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadMessages(false)
    }
  },

  onTabChange(e: any) {
    const tab = e.currentTarget.dataset.tab as string
    if (tab === this.data.activeTab) return
    this.setData({ activeTab: tab })
    this.loadMessages(true)
  },

  /**
   * @param reset true=从第一页重新加载，false=追加下一页
   */
  async loadMessages(reset: boolean) {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      const result = await getMessages(
        page,
        PAGE_SIZE,
        this.data.activeTab || undefined
      )
      if (result.code === 0 && result.data) {
        const list = (result.data.list || []).map((m: any) => ({
          id: m.id,
          msgType: m.msgType,
          title: m.title,
          content: m.content,
          isRead: m.isRead,
          createdAt: m.createdAt,
          typeText: typeTextMap[m.msgType] || '消息'
        }))
        const messages = reset ? list : this.data.messages.concat(list)
        const total = result.data.total || 0
        this.setData({
          messages,
          page: result.data.page || page,
          total,
          hasMore: messages.length < total,
          // 全部标签下的未读数即角标数；切到分类标签时保留上一次的全局值，
          // 避免角标随筛选条件跳变
          unreadCount: this.data.activeTab ? this.data.unreadCount : messages.filter(m => !m.isRead).length
        })
      }
    } catch (error) {
      console.error('[Messages] 加载失败:', error)
      wx.showToast({ title: '加载失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 点击消息：展开正文并标记已读
  async onMessageTap(e: any) {
    const id = Number(e.currentTarget.dataset.id)
    const target = this.data.messages.find(m => m.id === id)
    if (!target || target.isRead) return
    try {
      await markMessageRead(id)
      this.setData({
        messages: this.data.messages.map(m => (m.id === id ? { ...m, isRead: true } : m)),
        unreadCount: Math.max(0, this.data.unreadCount - 1)
      })
    } catch (error) {
      // 标记已读失败不打断阅读：消息内容已经展示了，重试的价值不大
      console.warn('[Messages] 标记已读失败:', error)
    }
  },

  async onMarkAllRead() {
    if (this.data.unreadCount === 0) {
      wx.showToast({ title: '没有未读消息', icon: 'none' })
      return
    }
    try {
      await markAllRead()
      wx.showToast({ title: '已全部标记为已读', icon: 'success' })
      this.setData({ activeTab: '' })
      this.loadMessages(true)
    } catch (error: any) {
      wx.showToast({ title: error?.message || '操作失败', icon: 'none' })
    }
  }
})
