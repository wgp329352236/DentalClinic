// pages/medical-record-detail/medical-record-detail.ts
import { getMedicalRecordDetail } from '../../utils/api'
import type { MedicalRecordDetail } from '../../types'
import { checkLogin } from '../../utils/auth'

Page({
  data: {
    record: null as MedicalRecordDetail | null,
    loading: false
  },

  onLoad(options: Record<string, string | undefined>) {
    if (!checkLogin()) return
    const id = Number(options.id)
    // 非法 id 直接返回：发一次注定 400 的请求只会让用户看到无意义的错误提示
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 800)
      return
    }
    this.loadDetail(id)
  },

  async loadDetail(id: number) {
    this.setData({ loading: true })
    try {
      const result = await getMedicalRecordDetail(id)
      if (result.code === 0 && result.data) {
        this.setData({ record: result.data })
      }
    } catch (error: any) {
      console.error('[MedicalRecordDetail] 加载失败:', error)
      wx.showToast({ title: error?.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 再次预约。
   *
   * 预约页是 tabBar 页面，只能用 switchTab（navigateTo 跳 tabBar 会静默失败）。
   * 详情接口未下发门诊电话，故不提供「联系门诊」入口，避免点了没反应。
   */
  onRebook() {
    wx.switchTab({ url: '/pages/appointment/appointment' })
  }
})
