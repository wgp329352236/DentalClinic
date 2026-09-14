// API 服务层 - 封装所有后端接口调用
import { get, post, put } from './request'

// ==================== 认证相关 ====================

export interface WechatLoginParams {
  code: string
  // 微信手机号授权返回的动态令牌（open-type="getPhoneNumber" 的 e.detail.code）
  phoneCode?: string
  // 可选：微信用户资料（wx.getUserProfile 授权后返回，用于完善资料）
  userInfo?: {
    nickName: string
    avatarUrl: string
    gender?: number
    country?: string
    province?: string
    city?: string
  }
  encryptedData?: string
  iv?: string
  signature?: string
  rawData?: string
}

export interface WechatLoginResponse {
  userId: number
  accessToken: string
  refreshToken: string
  isNewUser: boolean
}

/**
 * 微信登录
 */
export function wechatLogin(params: WechatLoginParams) {
  return post<WechatLoginResponse>('/auth/wechat-login', params, false)
}

/**
 * 刷新 Token
 */
export function refreshToken(refreshToken: string) {
  return post<{ accessToken: string }>('/auth/refresh', { refreshToken }, false)
}

// ==================== 用户资料 ====================

export interface UserProfileResponse {
  userId: number
  openid: string
  phone: string
  nickname: string
  realName: string
  gender: number
  age: number
  avatar: string
  primaryClinicId: number
  balance: number
  bonusBalance: number
  // 会员等级（需求 3.7.1 等级卡片）
  levelCode: number
  levelName: string
  /** 折扣率，如 0.95 表示 9.5 折；无折扣为 1 */
  discountRate: number
  benefits: string[]
  /** 累计消费金额，用于计算升级进度 */
  accumulatedConsumption: number
  /** 距下一级还需消费的金额；已是最高等级时为 null */
  nextLevelCost: number | null
}

export interface UpdateProfileParams {
  realName?: string
  phone?: string
  gender?: number
  age?: number
  avatar?: string
  nickname?: string
}

/**
 * 获取用户资料
 */
export function getUserProfile() {
  return get<UserProfileResponse>('/profile/me')
}

/**
 * 更新用户资料
 *
 * 后端只回 code/message，不回更新后的资料体，因此 data 声明为 null；
 * 需要最新资料时由调用方重新 getUserProfile()。
 */
export function updateUserProfile(params: UpdateProfileParams) {
  return put<null>('/profile/me', params)
}

// ==================== 门诊/科室/医生 ====================

export interface Clinic {
  id: number
  name: string
  address: string
  phone: string
  businessHours: string
  distance?: number
  image: string
}

export interface Department {
  id: number
  name: string
  description: string
}

export interface Doctor {
  id: number
  name: string
  title: string
  clinicIds: number[]
  specialties: string[]
  introduction: string
  avatar: string
}

export interface TimeSlot {
  date: string
  startTime: string
  endTime: string
  available: boolean
}

/**
 * 获取门诊列表
 */
export function getClinics() {
  return get<Clinic[]>('/clinics', undefined, false)
}

/**
 * 获取科室列表
 */
export function getDepartments(clinicId?: number) {
  const params = clinicId ? { clinicId } : undefined
  return get<Department[]>('/departments', params, false)
}

/**
 * 获取医生列表
 */
export function getDoctors(clinicId?: number, departmentId?: number) {
  const params: any = {}
  if (clinicId) params.clinicId = clinicId
  if (departmentId) params.departmentId = departmentId
  return get<Doctor[]>('/doctors', params, false)
}

/**
 * 获取医生可预约时段
 */
export function getDoctorSlots(doctorId: number, date: string) {
  return get<TimeSlot[]>('/appointments/slots', { doctorId, date }, false)
}

// ==================== 预约 ====================

export interface CreateAppointmentParams {
  clinicId: number
  doctorId: number
  departmentId: number
  appointmentDate: string
  startTime: string
  remark?: string
}

export interface Appointment {
  id: number
  clinicId: number
  clinicName: string
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  appointmentDate: string
  startTime: string
  status: 'pending' | 'confirmed' | 'arrived' | 'completed' | 'cancelled' | 'no_show'
  remark?: string
  createdAt: string
}

export interface AppointmentsResponse {
  list: Appointment[]
  total: number
  page: number
  pageSize: number
}

/**
 * 创建预约
 */
export function createAppointment(params: CreateAppointmentParams) {
  return post<Appointment>('/appointments', params)
}

/**
 * 获取我的预约列表
 */
export function getMyAppointments(status?: string, page = 1, pageSize = 20) {
  const params: any = { page, pageSize }
  if (status) params.status = status
  return get<AppointmentsResponse>('/appointments/my', params)
}

/**
 * 取消预约
 */
export function cancelAppointment(id: number) {
  return put(`/appointments/${id}/cancel`)
}

/**
 * 改约
 */
export interface RescheduleParams {
  appointmentDate: string
  startTime: string
}

export function rescheduleAppointment(id: number, params: RescheduleParams) {
  return put<Appointment>(`/appointments/${id}/reschedule`, params)
}

// ==================== 钱包/充值 ====================

export interface WalletDetails {
  balance: number
  bonusBalance: number
  totalBalance: number
  version: number
  rechargeHistory: RechargeRecord[]
}

export interface RechargeRecord {
  id: number
  amount: number
  bonusAmount: number
  status: string
  paidAt: string
  createdAt: string
}

export interface RechargeTier {
  amount: number
  bonus: number
  rate: string
}

/**
 * 获取钱包详情
 */
export function getWalletDetails() {
  return get<WalletDetails>('/wallet/details')
}

/**
 * 获取充值档位
 */
export function getRechargeTiers() {
  return get<RechargeTier[]>('/recharge/tiers', undefined, false)
}

/**
 * 创建充值订单
 */
export interface CreateRechargeParams {
  amount: number
  clinicId: number
}

export function createRecharge(params: CreateRechargeParams) {
  return post<{ orderId: number; amount: number }>('/recharge/create', params)
}

// ==================== 积分 ====================

export interface PointsBalance {
  totalBalance: number
  updatedAt?: string
}

export interface PointsRecord {
  id: number
  type: 'earn' | 'spend'
  source: string
  points: number
  relatedInfo?: string
  expireAt?: string
  createdAt: string
}

export interface PointsRecordsResponse {
  list: PointsRecord[]
  total: number
  page: number
  pageSize: number
}

/**
 * 获取积分余额
 */
export function getPointsBalance() {
  return get<PointsBalance>('/points/balance')
}

/**
 * 获取积分记录
 */
export function getPointsRecords(page = 1, pageSize = 20) {
  return get<PointsRecordsResponse>('/points/records', { page, pageSize })
}

/**
 * 每日签到
 */
export function checkin() {
  return post<{ pointsEarned: number }>('/points/checkin')
}

/**
 * 完善资料领取积分
 */
export function claimProfileCompleteReward() {
  return post<{ pointsEarned: number }>('/points/profile-complete')
}

// ==================== 推荐 ====================

export interface ReferralCodeResponse {
  referralCode: string
  shareLink: string
}

export interface ReferralRecord {
  id: number
  referredPersonName: string
  registeredAt: string
  firstConsumptionAt?: string
  rewardStatus: string
}

export interface ReferralRecordsResponse {
  list: ReferralRecord[]
  total: number
  page: number
  pageSize: number
}

/**
 * 获取推荐码
 */
export function getReferralCode() {
  return get<ReferralCodeResponse>('/referral/code')
}

/**
 * 获取推荐记录
 */
export function getReferralRecords(page = 1, pageSize = 20) {
  return get<ReferralRecordsResponse>('/referral/records', { page, pageSize })
}

/**
 * 使用推荐码注册
 */
export function registerWithReferral(referralCode: string) {
  return post('/referral/register', { referralCode })
}

// ==================== 优惠券 ====================

export interface Coupon {
  id: number
  couponCode: string
  name: string
  amount: number
  minAmount: number
  scope: string
  validFrom: string
  validTo: string
  status: 'unused' | 'used' | 'expired'
}

/**
 * 获取优惠券列表
 */
export function getCoupons(status?: string) {
  const params = status ? { status } : undefined
  return get<Coupon[]>('/coupons', params)
}

/**
 * 兑换优惠券
 */
export function redeemCoupon(couponCode: string, appointmentId?: number) {
  return post('/coupons/redeem', { couponCode, appointmentId })
}

// ==================== 消息通知 ====================

export interface Message {
  id: number
  msgType: string
  title: string
  content: string
  isRead: boolean
  createdAt: string
}

export interface MessagesResponse {
  list: Message[]
  total: number
  page: number
  pageSize: number
}

/**
 * 获取消息列表
 */
export function getMessages(page = 1, pageSize = 20, msgType?: string, isRead?: boolean) {
  const params: any = { page, pageSize }
  if (msgType) params.msgType = msgType
  if (isRead !== undefined) params.isRead = isRead
  return get<MessagesResponse>('/messages', params)
}

/**
 * 获取未读消息数
 */
export function getUnreadCount() {
  return get<{ count: number }>('/messages/unread-count')
}

/**
 * 标记消息为已读
 */
export function markMessageRead(id: number) {
  return put(`/messages/${id}/read`)
}

/**
 * 全部标记为已读
 */
export function markAllRead() {
  return put('/messages/read-all')
}

// ==================== 就诊记录 ====================

export interface MedicalRecord {
  /** 就诊记录以预约为载体，此 id 即预约 id */
  id: number
  appointmentDate: string
  startTime?: string
  endTime?: string
  clinicName: string
  doctorName: string
  doctorTitle?: string
  departmentName?: string
  diagnosis?: string
  remark?: string
  consumptionAmount: number
  completedAt?: string
}

export interface ConsumptionDetail {
  id: number
  amount: number
  balanceUsed: number
  wechatPaid: number
  pointsEarned: number
  description?: string
  createdAt: string
}

export interface MedicalRecordDetail extends MedicalRecord {
  clinicAddress?: string
  doctorIntroduction?: string
  status: string
  treatmentPlan?: string
  arrivedAt?: string
  totalAmount: number
  consumptionDetails: ConsumptionDetail[]
}

export interface MedicalRecordsResponse {
  list: MedicalRecord[]
  total: number
  page: number
  pageSize: number
}

/**
 * 获取就诊记录列表（已完成的预约）
 */
export function getMedicalRecords(page = 1, pageSize = 20, keyword?: string) {
  const params: any = { page, pageSize }
  if (keyword) params.keyword = keyword
  return get<MedicalRecordsResponse>('/medical-records', params)
}

/**
 * 获取就诊记录详情
 * @param id 预约 id
 */
export function getMedicalRecordDetail(id: number) {
  return get<MedicalRecordDetail>(`/medical-records/${id}`)
}

// ==================== 首页内容（轮播图/公告） ====================

/** 跳转类型：与管理端 mp_banner/mp_notice 的 link_type 取值一致 */
export type ContentLinkType = 'none' | 'page' | 'webview'

export interface Banner {
  id: number
  title: string
  imageUrl: string
  linkType: ContentLinkType
  linkTarget?: string
}

export interface Notice {
  id: number
  content: string
  linkType: ContentLinkType
  linkTarget?: string
}

export interface HomeContent {
  banners: Banner[]
  notices: Notice[]
}

/**
 * 获取首页内容（轮播图 + 公告）
 *
 * 公开接口，未登录也可调用：首页在登录前就要展示这些内容。
 * 停用与不在生效排期内的条目已由后端过滤。
 */
export function getHomeContent() {
  return get<HomeContent>('/content/home', undefined, false)
}
