// pages/appointment/appointment.ts
import { getClinics, getDepartments, getDoctors, getDoctorSlots, createAppointment } from '../../utils/api'
import type { Clinic, Department, Doctor, TimeSlot } from '../../types'

Page({
  data: {
    clinics: [] as Clinic[],
    departments: [] as Department[],
    doctors: [] as Doctor[],
    timeSlots: [] as TimeSlot[],

    // 当前选择
    selectedClinicId: null as number | null,
    selectedDepartmentId: null as number | null,
    selectedDoctorId: null as number | null,
    selectedSlot: null as TimeSlot | null,

    // 步骤：1-选门诊 2-选科室 3-选医生 4-选时段 5-确认
    step: 1,

    loading: false,

    // 用于 WXML 显示的选中项信息
    selectedClinicName: '',
    selectedDepartmentName: '',
    selectedDoctorName: ''
  },

  onLoad(options: any) {
    if (options.clinicId) {
      this.setData({ selectedClinicId: Number(options.clinicId) })
    }
    this.loadClinics()
    // 后端 /departments 要求 clinicId 必填，仅在有选中门诊时加载
    if (this.data.selectedClinicId) {
      this.loadDepartments()
    }
  },

  // 加载门店列表
  async loadClinics() {
    try {
      const result = await getClinics()
      if (result.code === 0 && result.data) {
        this.setData({ clinics: result.data })
        if (this.data.selectedClinicId) {
          this.loadDoctors()
        }
      }
    } catch (error) {
      console.error('加载门店失败:', error)
      wx.showToast({ title: '加载门店失败', icon: 'none' })
    }
  },

  // 加载科室列表
  async loadDepartments() {
    try {
      const result = await getDepartments()
      if (result.code === 0 && result.data) {
        this.setData({ departments: result.data })
      }
    } catch (error) {
      console.error('加载科室失败:', error)
    }
  },

  // 加载医生列表
  async loadDoctors() {
    try {
      const { selectedClinicId, selectedDepartmentId } = this.data
      const result = await getDoctors(selectedClinicId || undefined, selectedDepartmentId || undefined)
      if (result.code === 0 && result.data) {
        this.setData({ doctors: result.data })
      }
    } catch (error) {
      console.error('加载医生失败:', error)
      wx.showToast({ title: '加载医生失败', icon: 'none' })
    }
  },

  // 加载医生时段
  async loadTimeSlots(doctorId: number) {
    try {
      const today = new Date()
      const slots: TimeSlot[] = []

      for (let i = 0; i < 7; i++) {
        const date = new Date(today)
        date.setDate(today.getDate() + i)
        const dateStr = date.toISOString().split('T')[0]

        const result = await getDoctorSlots(doctorId, dateStr)
        if (result.code === 0 && Array.isArray(result.data)) {
          // 后端返回 remaining/maxAppointments，映射为本地 TimeSlot
          slots.push(...result.data.map((s: any) => ({
            date: dateStr,
            startTime: s.startTime,
            endTime: s.endTime,
            available: (s.remaining || 0) > 0
          })))
        }
      }

      this.setData({ timeSlots: slots })
    } catch (error) {
      console.error('加载时段失败:', error)
      wx.showToast({ title: '加载时段失败', icon: 'none' })
    }
  },

  // 选择门诊
  onSelectClinic(e: any) {
    const clinicId = Number(e.currentTarget.dataset.id)
    const clinic = this.data.clinics.find(c => c.id === clinicId)
    this.setData({
      selectedClinicId: clinicId,
      selectedClinicName: clinic?.name || '',
      step: 2
    })
    this.loadDepartments()
    this.loadDoctors()
  },

  // 选择科室
  onSelectDepartment(e: any) {
    const departmentId = Number(e.currentTarget.dataset.id)
    const department = this.data.departments.find(d => d.id === departmentId)
    this.setData({
      selectedDepartmentId: departmentId,
      selectedDepartmentName: department?.name || '',
      step: 3
    })
    this.loadDoctors()
  },

  // 选择医生
  async onSelectDoctor(e: any) {
    const doctorId = Number(e.currentTarget.dataset.id)
    const doctor = this.data.doctors.find(d => d.id === doctorId)
    this.setData({
      selectedDoctorId: doctorId,
      selectedDoctorName: doctor?.name || '',
      step: 4,
      loading: true
    })
    await this.loadTimeSlots(doctorId)
    this.setData({ loading: false })
  },

  // 选择时段
  onSelectSlot(e: any) {
    const index = Number(e.currentTarget.dataset.index)
    const slot = this.data.timeSlots[index]
    this.setData({
      selectedSlot: { ...slot, timeRange: `${slot.startTime}-${slot.endTime}` } as any,
      step: 5
    })
  },

  // 提交预约
  async submitAppointment() {
    const { selectedClinicId, selectedDoctorId, selectedDepartmentId, selectedSlot } = this.data

    if (!selectedClinicId || !selectedDoctorId || !selectedDepartmentId || !selectedSlot) {
      wx.showToast({ title: '请完整选择预约信息', icon: 'none' })
      return
    }

    this.setData({ loading: true })

    try {
      const result = await createAppointment({
        clinicId: selectedClinicId,
        doctorId: selectedDoctorId,
        departmentId: selectedDepartmentId,
        appointmentDate: selectedSlot.date,
        startTime: selectedSlot.startTime
      })

      if (result.code === 0) {
        wx.showToast({ title: '预约成功', icon: 'success' })
        setTimeout(() => {
          wx.navigateBack()
        }, 1500)
      } else {
        wx.showToast({ title: result.message || '预约失败', icon: 'none' })
      }
    } catch (error: any) {
      console.error('预约失败:', error)
      wx.showToast({ title: error.message || '预约失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 上一步
  goBack() {
    const currentStep = this.data.step
    if (currentStep > 1) {
      this.setData({ step: currentStep - 1 })
    }
  },

  // 获取步骤标题
  getStepTitle(): string {
    const titles: Record<number, string> = {
      1: '选择门诊',
      2: '选择科室',
      3: '选择医生',
      4: '选择时段',
      5: '确认预约'
    }
    return titles[this.data.step] || ''
  }
})
