// pages/medical-records/medical-records.ts
import { getMedicalRecords } from '../../utils/api'
import type { MedicalRecord } from '../../types'
import { checkLogin } from '../../utils/auth'

const PAGE_SIZE = 20

Page({
  data: {
    records: [] as MedicalRecord[],
    keyword: '',
    page: 1,
    total: 0,
    loading: false,
    // 已取满 total 条后不再触发下一页请求，避免 onReachBottom 反复空转
    hasMore: true
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadRecords(true)
  },

  onShow() {
    // 从详情页返回时不重新拉取：列表数据不会因查看详情而变化
  },

  onPullDownRefresh() {
    this.loadRecords(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadRecords(false)
    }
  },

  onKeywordInput(e: any) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.loadRecords(true)
  },

  /**
   * @param reset true=从第一页重新加载（搜索/下拉刷新），false=追加下一页
   */
  async loadRecords(reset: boolean) {
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    try {
      const result = await getMedicalRecords(page, PAGE_SIZE, this.data.keyword.trim() || undefined)
      if (result.code === 0 && result.data) {
        const list = result.data.list || []
        const records = reset ? list : this.data.records.concat(list)
        this.setData({
          records,
          page: result.data.page || page,
          total: result.data.total || 0,
          hasMore: records.length < (result.data.total || 0)
        })
      }
    } catch (error) {
      console.error('[MedicalRecords] 加载失败:', error)
      wx.showToast({ title: '加载失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onRecordTap(e: any) {
    const id = Number(e.currentTarget.dataset.id)
    wx.navigateTo({ url: `/pages/medical-record-detail/medical-record-detail?id=${id}` })
  }
})
