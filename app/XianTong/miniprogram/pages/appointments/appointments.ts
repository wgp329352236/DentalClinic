// pages/appointments/appointments.ts
import { getMyAppointments, cancelAppointment } from '../../utils/api'
import type { Appointment } from '../../types'

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
    appointments: [] as (Appointment & { statusText: string; statusClass: string })[],
    loading: false,
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
        const appointments = rawList.map((appt: any) => ({
          id: appt.id,
          clinicId: appt.clinicId || 0,
          clinicName: appt.clinicName,
          doctorId: appt.doctorId || 0,
          doctorName: appt.doctorName,
          departmentId: appt.departmentId || 0,
          departmentName: appt.departmentName || '',
          appointmentTime: appt.appointmentTime || `${appt.appointmentDate || ''} ${appt.startTime || ''}`,
          status: appt.status,
          createdAt: appt.createdAt,
          statusText: statusMap[appt.status] || appt.status,
          statusClass: statusClassMap[appt.status] || 'badge-default'
        }))
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
  }
})
