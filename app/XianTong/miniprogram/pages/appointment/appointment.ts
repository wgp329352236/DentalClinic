// pages/appointment/appointment.ts
import {
  createAppointment,
  getClinics,
  getCoupons,
  getDepartments,
  getDoctorSlots,
  getDoctors,
  getMyAppointments,
  rescheduleAppointment
} from '../../utils/api'
import {
  formatBusinessDays,
  formatDistance,
  getBusinessStatus,
  getCurrentLocation,
  getDistanceMeters,
  isValidCoordinate,
  requestSubscribeMessage,
  resolveFileUrl
} from '../../utils/util'
import type { Clinic, Coupon, Department, Doctor, TimeSlot } from '../../types'

interface SelectableSlot extends TimeSlot {
  timeRange: string
}

interface CouponOption {
  couponCode: string
  name: string
  amount: number
  minAmount: number
  scope: string
  validTo: string
  isOriginal?: boolean
}

/** 使用本地时区格式化日期，避免 toISOString() 在 UTC+8 把凌晨日期减一天。 */
function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

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
    selectedSlot: null as SelectableSlot | null,

    // 步骤：1-选门诊 2-选科室 3-选医生 4-选时段 5-确认
    step: 1,

    loading: false,

    // 改约模式：沿用原预约的门店/科室/医生，只允许重新选择日期和时段
    rescheduleAppointmentId: null as number | null,
    isReschedule: false,
    originalAppointmentText: '',
    originalCouponCode: '',

    // 优惠券选择：空字符串明确表示本次不使用/释放原券
    availableCoupons: [] as CouponOption[],
    selectedCouponCode: '',
    selectedCouponName: '',
    selectedCouponFaceValue: 0,
    couponsLoading: false,

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

  /**
   * 消费首页中转过来的门诊 id。
   *
   * 本页是 tabBar 页面，首页只能用 switchTab 跳转而它不支持 query，
   * 因此门诊 id 走 app.globalData.pendingClinicId 中转。读到后立即置空，
   * 否则用户下次自己点 tabBar 进来还会被强行切回上次那家门诊。
   */
  onShow() {
    const app = getApp<IAppOption>()
    const pendingCouponCode = app?.globalData?.pendingCouponCode
    if (pendingCouponCode) {
      app.setPendingCouponCode(null)
      this.setData({ selectedCouponCode: pendingCouponCode })
      if (this.data.step === 5) {
        this.loadAvailableCoupons()
      }
    }
    const pendingRescheduleId = app?.globalData?.pendingRescheduleId
    if (pendingRescheduleId) {
      app.setPendingRescheduleId(null)
      // 改约入口优先于首页门店入口，避免同时残留两个中转值时误切到新预约流程。
      app.setPendingClinicId(null)
      this.loadRescheduleAppointment(pendingRescheduleId)
      return
    }

    const pending = app?.globalData?.pendingClinicId
    if (!pending) return
    app.setPendingClinicId(null)
    // onLoad 可能已经跑完（页面被缓存），这里要重新拉一次该门诊下的科室
    this.setData({ selectedClinicId: pending })
    this.loadDepartments(pending)
    this.loadDoctors(pending)
  },

  /** 从预约记录页进入改约：读取原预约并直接进入选时段步骤。 */
  async loadRescheduleAppointment(appointmentId: number) {
    this.setData({ loading: true })
    wx.showLoading({ title: '加载预约中...', mask: true })
    try {
      const result = await getMyAppointments(undefined, 1, 100)
      const list = Array.isArray(result.data)
        ? result.data
        : ((result.data as any)?.list || [])
      const appointment = list.find((item: any) => Number(item.id) === appointmentId)

      if (!appointment) throw new Error('预约不存在或已失效')
      if (appointment.status !== 'pending' && appointment.status !== 'confirmed') {
        throw new Error('当前预约状态不支持改约')
      }
      if (appointment.cancelRequestStatus === 'pending') {
        throw new Error('取消申请审核中，暂不能改约')
      }

      const endText = appointment.endTime ? `-${appointment.endTime}` : ''
      this.setData({
        rescheduleAppointmentId: appointmentId,
        isReschedule: true,
        selectedClinicId: appointment.clinicId,
        selectedClinicName: appointment.clinicName || '',
        selectedDepartmentId: appointment.departmentId || null,
        selectedDepartmentName: appointment.departmentName || '',
        selectedDoctorId: appointment.doctorId,
        selectedDoctorName: appointment.doctorName || '',
        originalCouponCode: appointment.couponCode || '',
        selectedCouponCode: appointment.couponCode || '',
        selectedCouponName: appointment.couponName || '',
        selectedCouponFaceValue: Number(appointment.couponFaceValue || 0),
        selectedSlot: null,
        originalAppointmentText:
          `${appointment.appointmentDate || ''} ${appointment.startTime || ''}${endText}`.trim(),
        step: 4
      })
      await this.loadTimeSlots(appointment.doctorId)
    } catch (error: any) {
      console.error('加载改约预约失败:', error)
      wx.showToast({ title: error?.message || '加载改约信息失败', icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ loading: false })
    }
  },

  // 加载门店列表
  async loadClinics() {
    try {
      // 与首页一致：每次进入预约页最多获取一次定位，失败时静默降级。
      const [result, location] = await Promise.all([getClinics(), getCurrentLocation()])
      if (result.code === 0 && result.data) {
        const clinics: Clinic[] = result.data.map((item: any) => {
          const latitude = item.latitude == null ? null : Number(item.latitude)
          const longitude = item.longitude == null ? null : Number(item.longitude)
          const businessDays = item.businessDays || '1,2,3,4,5,6,7'
          const fallbackDistance = item.distance == null ? undefined : Number(item.distance)
          let distance = Number.isFinite(fallbackDistance) ? fallbackDistance : undefined
          // 定位与门店坐标都有效时才计算距离：开发者工具默认定位为 (0,0)，
          // 直接参与计算会得出上万公里的离谱距离
          if (location && isValidCoordinate(latitude, longitude)) {
            distance = getDistanceMeters(location, { latitude: latitude!, longitude: longitude! })
          }
          return {
            ...item,
            businessHours:
              item.businessHours ||
              (item.openTime && item.closeTime ? `${item.openTime}-${item.closeTime}` : ''),
            businessDays,
            latitude,
            longitude,
            distance,
            distanceText: distance == null ? '' : formatDistance(distance),
            businessDayText: formatBusinessDays(businessDays),
            openStatusText: getBusinessStatus(
              businessDays,
              item.openTime,
              item.closeTime
            ),
            image: resolveFileUrl(item.image || item.coverImage || '')
          }
        })
        clinics.sort((left, right) => {
          const leftDistance = left.distance == null ? Number.POSITIVE_INFINITY : left.distance
          const rightDistance = right.distance == null ? Number.POSITIVE_INFINITY : right.distance
          return leftDistance - rightDistance
        })
        this.setData({ clinics })
      }
    } catch (error) {
      console.error('加载门店失败:', error)
      wx.showToast({ title: '加载门店失败', icon: 'none' })
    }
  },

  // 加载科室列表
  async loadDepartments(clinicId?: number | null) {
    const targetClinicId = clinicId ?? this.data.selectedClinicId
    if (!targetClinicId) {
      this.setData({ departments: [], doctors: [] })
      return
    }
    try {
      const result = await getDepartments(targetClinicId)
      if (result.code === 0 && result.data) {
        this.setData({ departments: result.data })
      }
    } catch (error) {
      console.error('加载科室失败:', error)
    }
  },

  // 加载医生列表
  async loadDoctors(clinicId?: number | null, departmentId?: number | null) {
    const targetClinicId = clinicId ?? this.data.selectedClinicId
    const targetDepartmentId = departmentId ?? this.data.selectedDepartmentId
    if (!targetClinicId) {
      this.setData({ doctors: [] })
      return
    }
    try {
      const result = await getDoctors(targetClinicId, targetDepartmentId || undefined)
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
      this.setData({ timeSlots: [] })

      for (let i = 0; i < 7; i++) {
        const date = new Date(today)
        date.setDate(today.getDate() + i)
        const dateStr = formatLocalDate(date)

        // 传 clinicId 让后端按该门店的营业日/营业时间过滤时段，
        // 否则闭店日仍会显示可约时段
        const result = await getDoctorSlots(
          doctorId,
          dateStr,
          this.data.selectedClinicId || undefined
        )
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
      selectedDepartmentId: null,
      selectedDepartmentName: '',
      selectedDoctorId: null,
      selectedDoctorName: '',
      selectedSlot: null,
      departments: [],
      doctors: [],
      timeSlots: [],
      step: 2
    })
    this.loadDepartments(clinicId)
  },

  // 选择科室
  onSelectDepartment(e: any) {
    const departmentId = Number(e.currentTarget.dataset.id)
    const department = this.data.departments.find(d => d.id === departmentId)
    this.setData({
      selectedDepartmentId: departmentId,
      selectedDepartmentName: department?.name || '',
      selectedDoctorId: null,
      selectedDoctorName: '',
      selectedSlot: null,
      doctors: [],
      timeSlots: [],
      step: 3
    })
    this.loadDoctors(this.data.selectedClinicId, departmentId)
  },

  // 选择医生
  async onSelectDoctor(e: any) {
    const doctorId = Number(e.currentTarget.dataset.id)
    const doctor = this.data.doctors.find(d => d.id === doctorId)
    this.setData({
      selectedDoctorId: doctorId,
      selectedDoctorName: doctor?.name || '',
      selectedSlot: null,
      step: 4,
      loading: true
    })
    await this.loadTimeSlots(doctorId)
    this.setData({ loading: false })
  },

  // 选择时段
  async onSelectSlot(e: any) {
    const index = Number(e.currentTarget.dataset.index)
    const slot = this.data.timeSlots[index]
    if (!slot?.available) return
    // 微信单次最多申请 3 个模板：选时段时先申请两次就诊提醒，
    // 提交页再把剩余两个名额留给“已提交”和“已确认”。
    requestSubscribeMessage([
      'appointment_remind_evening',
      'appointment_remind_morning'
    ])
    this.setData({
      selectedSlot: { ...slot, timeRange: `${slot.startTime}-${slot.endTime}` },
      step: 5
    })
    await this.loadAvailableCoupons()
  },

  async loadAvailableCoupons() {
    this.setData({ couponsLoading: true })
    try {
      const result = await getCoupons('unused')
      const coupons: CouponOption[] = result.code === 0 && Array.isArray(result.data)
        ? result.data.map((coupon: Coupon) => ({
            couponCode: coupon.couponCode,
            name: coupon.name,
            amount: coupon.amount,
            minAmount: coupon.minAmount,
            scope: coupon.scope,
            validTo: coupon.validTo
          }))
        : []

      // 改约时原券已被预约占用，不会出现在未使用列表中，需要单独回显供“保留原券”选择。
      const originalCode = this.data.originalCouponCode
      if (originalCode && !coupons.some(item => item.couponCode === originalCode)) {
        coupons.unshift({
          couponCode: originalCode,
          name: this.data.selectedCouponCode === originalCode
            ? (this.data.selectedCouponName || '原预约优惠券')
            : '原预约优惠券',
          amount: this.data.selectedCouponCode === originalCode ? this.data.selectedCouponFaceValue : 0,
          minAmount: 0,
          scope: '',
          validTo: '',
          isOriginal: true
        })
      }

      const preferredCode = this.data.selectedCouponCode
      const selected = preferredCode ? coupons.find(item => item.couponCode === preferredCode) : undefined
      this.setData({
        availableCoupons: coupons,
        selectedCouponCode: selected?.couponCode || '',
        selectedCouponName: selected?.name || '',
        selectedCouponFaceValue: selected?.amount || 0
      })
    } catch (error) {
      console.warn('[Appointment] 加载可用优惠券失败:', error)
      // 改约请求中 null 表示保留原券、空串表示释放原券。加载失败时不能让
      // 网络异常替用户做“释放原券”的决定，因此仍把原券作为可选项保留。
      const originalCode = this.data.originalCouponCode
      this.setData({
        availableCoupons: originalCode ? [{
          couponCode: originalCode,
          name: this.data.selectedCouponName || '原预约优惠券',
          amount: this.data.selectedCouponFaceValue,
          minAmount: 0,
          scope: '',
          validTo: '',
          isOriginal: true
        }] : [],
        selectedCouponCode: originalCode || this.data.selectedCouponCode,
        selectedCouponName: originalCode ? (this.data.selectedCouponName || '原预约优惠券') : this.data.selectedCouponName,
        selectedCouponFaceValue: originalCode ? this.data.selectedCouponFaceValue : 0
      })
    } finally {
      this.setData({ couponsLoading: false })
    }
  },

  onSelectCoupon(e: any) {
    const couponCode = String(e.currentTarget.dataset.code || '')
    const coupon = this.data.availableCoupons.find(item => item.couponCode === couponCode)
    if (!coupon) return
    this.setData({
      selectedCouponCode: coupon.couponCode,
      selectedCouponName: coupon.name,
      selectedCouponFaceValue: coupon.amount
    })
  },

  onSelectNoCoupon() {
    this.setData({
      selectedCouponCode: '',
      selectedCouponName: '',
      selectedCouponFaceValue: 0
    })
  },

  // 提交预约
  async submitAppointment() {
    // 订阅消息授权引导：必须在点击事件的同步链上调用，不能放到 await 之后
    requestSubscribeMessage([
      'appointment_submitted',
      'appointment_confirmed'
    ])

    const {
      selectedClinicId,
      selectedDoctorId,
      selectedDepartmentId,
      selectedSlot,
      rescheduleAppointmentId,
      selectedCouponCode
    } = this.data

    if (!selectedClinicId || !selectedDoctorId || !selectedDepartmentId || !selectedSlot) {
      wx.showToast({ title: '请完整选择预约信息', icon: 'none' })
      return
    }

    this.setData({ loading: true })

    try {
      const result = rescheduleAppointmentId
        ? await rescheduleAppointment(rescheduleAppointmentId, {
            appointmentDate: selectedSlot.date,
            startTime: selectedSlot.startTime,
            couponCode: selectedCouponCode || ''
          })
        : await createAppointment({
            clinicId: selectedClinicId,
            doctorId: selectedDoctorId,
            departmentId: selectedDepartmentId,
            appointmentDate: selectedSlot.date,
            startTime: selectedSlot.startTime,
            couponCode: selectedCouponCode || ''
          })

      if (result.code === 0) {
        wx.showToast({ title: result.message || (rescheduleAppointmentId ? '改约成功' : '预约成功'), icon: 'success' })
        // tabBar 页面会被复用：提交成功后清理改约上下文和选择，避免下次进入仍停留在确认页。
        this.setData({
          step: 1,
          rescheduleAppointmentId: null,
          isReschedule: false,
          originalAppointmentText: '',
          originalCouponCode: '',
          availableCoupons: [],
          selectedCouponCode: '',
          selectedCouponName: '',
          selectedCouponFaceValue: 0,
          selectedClinicId: null,
          selectedDepartmentId: null,
          selectedDoctorId: null,
          selectedSlot: null,
          selectedClinicName: '',
          selectedDepartmentName: '',
          selectedDoctorName: '',
          departments: [],
          doctors: [],
          timeSlots: []
        })
        setTimeout(() => {
          wx.navigateTo({ url: '/pages/appointments/appointments' })
        }, 1500)
      } else {
        wx.showToast({ title: result.message || (rescheduleAppointmentId ? '改约失败' : '预约失败'), icon: 'none' })
      }
    } catch (error: any) {
      console.error(rescheduleAppointmentId ? '改约失败:' : '预约失败:', error)
      wx.showToast({ title: error?.message || (rescheduleAppointmentId ? '改约失败' : '预约失败'), icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 上一步
  goBack() {
    if (this.data.isReschedule) return
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
      4: this.data.isReschedule ? '选择新时段' : '选择时段',
      5: this.data.isReschedule ? '确认改约' : '确认预约'
    }
    return titles[this.data.step] || ''
  }
})
