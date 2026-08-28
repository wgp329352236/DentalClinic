// pages/appointment/appointment.ts
import { get, post } from '../../utils/request'
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

    loading: false
  },

  onLoad(options: any) {
    if (options.clinicId) {
      this.setData({ selectedClinicId: Number(options.clinicId) })
    }
    this.loadClinics()
    this.loadDepartments()
  },

  // 加载门店列表
  async loadClinics() {
    const result = await get<Clinic[]>('/clinics')
    if (result.code === 0 && result.data) {
      this.setData({ clinics: result.data })
      // 如果已选中门诊，加载该门诊的医生
      if (this.data.selectedClinicId) {
        this.loadDoctors()
      }
    }
  },

  // 加载科室列表
  async loadDepartments() {
    const result = await get<Department[]>('/departments')
    if (result.code === 0 && result.data) {
      this.setData({ departments: result.data })
    }
  },

  // 加载医生列表
  async loadDoctors() {
    const { selectedClinicId, selectedDepartmentId } = this.data
    const params: any = {}
    if (selectedClinicId) params.clinicId = selectedClinicId
    if (selectedDepartmentId) params.departmentId = selectedDepartmentId

    const result = await get<Doctor[]>('/doctors', params)
    if (result.code === 0 && result.data) {
      this.setData({ doctors: result.data })
    }
  },

  // 加载医生时段
  async loadTimeSlots(doctorId: number) {
    const result = await get<TimeSlot[]>(`/doctors/${doctorId}/slots`)
    if (result.code === 0 && result.data) {
      this.setData({ timeSlots: result.data })
    }
  },

  // 选择门诊
  onSelectClinic(e: any) {
    const clinicId = Number(e.currentTarget.dataset.id)
    this.setData({
      selectedClinicId: clinicId,
      step: 2
    })
    this.loadDoctors()
  },

  // 选择科室
  onSelectDepartment(e: any) {
    const departmentId = Number(e.currentTarget.dataset.id)
    this.setData({
      selectedDepartmentId: departmentId,
      step: 3
    })
    this.loadDoctors()
  },

  // 选择医生
  async onSelectDoctor(e: any) {
    const doctorId = Number(e.currentTarget.dataset.id)
    this.setData({
      selectedDoctorId: doctorId,
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
      selectedSlot: slot,
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
      const result = await post('/appointments', {
        clinicId: selectedClinicId,
        doctorId: selectedDoctorId,
        departmentId: selectedDepartmentId,
        date: selectedSlot.date,
        timeRange: selectedSlot.timeRange
      })

      if (result.code === 0) {
        wx.showToast({ title: '预约成功', icon: 'success' })
        setTimeout(() => {
          wx.navigateBack()
        }, 1500)
      } else {
        wx.showToast({ title: result.message || '预约失败', icon: 'none' })
      }
    } catch (error) {
      console.error('预约失败:', error)
      wx.showToast({ title: '预约失败', icon: 'none' })
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
