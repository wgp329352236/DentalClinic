// pages/appointments/appointments.ts
import { getMyAppointments, cancelAppointment, checkinAppointment } from '../../utils/api'
import type { Appointment } from '../../types'

/**
 * 本地时区今天的日期（yyyy-MM-dd）。
 * 后端只允许就诊当天签到，按钮的显隐用它预判，最终以后端校验为准（防绕过/时区边界）。
 */
function getToday(): string {
  const now = new Date()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  const day = `${now.getDate()}`.padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

const statusMap: Record<string, string> = {
  pending: '待确认',
  confirmed: '已确认',
  arrived: '已到店',
  completed: '已完成',
  cancelled: '已取消',
  no_show: '已爽约'
}

const statusClassMap: Record<string, string> = {
  pending: 'badge-warning',
  confirmed: 'badge-primary',
  arrived: 'badge-success',
  completed: 'badge-default',
  cancelled: 'badge-default',
  no_show: 'badge-danger'
}

Page({
  data: {
    appointments: [] as (Appointment & {
      statusText: string
      statusClass: string
      /** 就诊日期 yyyy-MM-dd，用于判断当天才展示「扫码签到」 */
      appointmentDate: string
      /** pending/confirmed 且就诊日期为今天：展示扫码签到按钮 */
      canCheckin: boolean
    })[],
    loading: false,
    /** 扫码 + 签到请求进行中，防止重复调起/重复提交 */
    checkingIn: false,
    activeTab: 'upcoming' // upcoming | history
  },

  onLoad() {
    this.loadAppointments()
  },

  async loadAppointments() {
    this.setData({ loading: true })
    try {
      // GET /appointments/my，后端直接返回数组；兼容分页对象
      const result = await getMyAppointments()
      const rawList = Array.isArray(result.data)
        ? result.data
        : (result.data as any)?.list || []
      if (result.code === 0) {
        const today = getToday()
        const appointments = rawList.map((appt: any) => {
          const appointmentDate: string = appt.appointmentDate || ''
          const status: string = appt.status
          return {
            id: appt.id,
            clinicId: appt.clinicId || 0,
            clinicName: appt.clinicName,
            doctorId: appt.doctorId || 0,
            doctorName: appt.doctorName,
            departmentId: appt.departmentId || 0,
            departmentName: appt.departmentName || '',
            appointmentTime: appt.appointmentTime || `${appointmentDate} ${appt.startTime || ''}`.trim(),
            status,
            createdAt: appt.createdAt,
            appointmentDate,
            // 只有「待确认/已确认 + 就诊当天」可扫码签到；arrived/completed 等状态后端会拒
            canCheckin: (status === 'pending' || status === 'confirmed') && appointmentDate === today,
            statusText: statusMap[status] || status,
            statusClass: statusClassMap[status] || 'badge-default'
          }
        })
        this.setData({ appointments })
      }
    } catch (error) {
      console.error('加载预约失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  },

  // 切换标签
  onTabChange(e: any) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
  },

  // 取消预约
  async onCancelAppointment(e: any) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showModal({
      title: '提示',
      content: '确定要取消该预约吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            const result = await cancelAppointment(id)
            if (result.code === 0) {
              wx.showToast({ title: '已取消', icon: 'success' })
              this.loadAppointments()
            } else {
              wx.showToast({ title: result.message || '取消失败', icon: 'none' })
            }
          } catch (error) {
            console.error('取消预约失败:', error)
            wx.showToast({ title: '取消失败', icon: 'none' })
          }
        }
      }
    })
  },

  // 获取可取消的预约
  getCancellableAppointments() {
    return this.data.appointments.filter(appt =>
      appt.status === 'pending' || appt.status === 'confirmed'
    )
  },

  /**
   * 扫码到店签到（需求 3.2.2：pending/confirmed → arrived）。
   *
   * 流程：用户在门店扫前台「门店签到码」→ wx.scanCode 拿到码原文（后端签发的
   * HMAC 签名码 dc1.<clinicId>.<sig>）→ POST /appointments/{id}/checkin 原样上报，
   * 由后端验签并校验门店营业中且与预约门店一致，防止远程扫自制码签到；
   * 用户取消扫码不打扰，签到失败直接展示后端文案（如「扫码门店与预约门店不一致」）。
   */
  onScanCheckin(e: any) {
    if (this.data.checkingIn) return
    const id = Number(e.currentTarget.dataset.id)
    if (!id) return

    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: async (scanRes: any) => {
        if (this.data.checkingIn) return

        // 码原文整体上报，由后端验签解析门店；空串/非本店码由后端拒绝
        const code = String(scanRes?.result || '').trim()
        if (!code) {
          wx.showToast({ title: '无效的门店码，请扫描门店前台二维码', icon: 'none' })
          return
        }

        this.setData({ checkingIn: true })
        wx.showLoading({ title: '签到中...', mask: true })
        try {
          await checkinAppointment(id, code)
          wx.showToast({ title: '签到成功', icon: 'success' })
          this.loadAppointments()
        } catch (error: any) {
          console.error('扫码签到失败:', error)
          wx.showToast({ title: error?.message || '签到失败', icon: 'none' })
        } finally {
          wx.hideLoading()
          this.setData({ checkingIn: false })
        }
      },
      fail: (err: any) => {
        // errMsg 为 "scanCode:fail cancel" 表示用户主动取消，无需提示
        if (!/cancel/i.test(err?.errMsg || '')) {
          wx.showToast({ title: '扫码失败，请重试', icon: 'none' })
        }
      }
    })
  }
})
