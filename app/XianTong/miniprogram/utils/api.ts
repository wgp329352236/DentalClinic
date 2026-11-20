// API 服务层 - 封装所有后端接口调用
import type { ActivityDetail } from '../types'
import { get, post, put, del } from './request'

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
  /** 微信手机号授权登录后由后端回带（未授权手机号时为空串/缺失），编辑资料页只读使用 */
  phone?: string
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

/**
 * 已登录用户重新授权手机号并绑定到当前账号。
 *
 * 场景：登录时取消了手机号授权（走静默登录），或后续需要更换手机号。
 * 传 open-type="getPhoneNumber" 回调里的 e.detail.code，后端用 access_token 换取真实手机号后落库。
 * 需登录态（needAuth 默认 true）。
 */
export function bindPhone(phoneCode: string) {
  return post<{ phone: string }>('/auth/bind-phone', { phoneCode })
}

// ==================== 用户资料 ====================

export interface UserProfileResponse {
  userId: number
  openid: string
  phone: string | null
  nickname: string | null
  realName: string | null
  gender: number | null
  age: number | null
  avatar: string | null
  primaryClinicId: number | null
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
  /** 首次设置归属门店；已有归属门店时后端不允许自助修改 */
  primaryClinicId?: number
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

/** 个人数据导出（需求 5.2 数据可携带权） */
export interface UserDataExport {
  userId: number
  nickname?: string
  realName?: string
  phone?: string
  gender?: number
  age?: number
  avatar?: string
  sourceType: string
  registeredAt: string
  balance: number
  bonusBalance: number
  levelName?: string
  accumulatedConsumption: number
  totalPoints: number
  exportedAt: string
}

/**
 * 导出当前用户全部个人数据（JSON，供下载存档）
 */
export function exportProfile() {
  return get<UserDataExport>('/profile/export')
}

/**
 * 注销当前账户（软删除，注销后无法登录）
 */
export function deleteAccount() {
  return del<null>('/profile/me')
}

// ==================== 门诊/科室/医生 ====================

export interface Clinic {
  id: number
  name: string
  address: string
  phone: string
  businessHours: string
  businessDays: string
  latitude?: number | null
  longitude?: number | null
  distance?: number
  distanceText?: string
  businessDayText?: string
  openStatusText?: string
  image: string
  /** 后端门店表字段名，页面会兼容读取 */
  coverImage?: string
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
export function getDoctorSlots(doctorId: number, date: string, clinicId?: number) {
  return get<TimeSlot[]>('/appointments/slots', { doctorId, date, clinicId }, false)
}

// ==================== 预约 ====================

export interface CreateAppointmentParams {
  clinicId: number
  doctorId: number
  departmentId: number
  appointmentDate: string
  startTime: string
  remark?: string
  /** 空字符串表示不使用优惠券；不传则由后端按业务场景处理 */
  couponCode?: string
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
  endTime?: string | null
  status: 'pending' | 'confirmed' | 'arrived' | 'completed' | 'cancelled' | 'no_show'
  remark?: string
  /** 就诊前 2 小时内取消会转为门店审批，主状态在审批前保持不变 */
  cancelRequestStatus?: 'pending' | 'approved' | 'rejected' | null
  cancelRequestReason?: string | null
  cancelRequestedAt?: string | null
  createdAt: string
  couponCode?: string | null
  couponName?: string | null
  couponFaceValue?: number | null
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
  // 后端当前直接返回数组；page/pageSize 暂时保留用于未来兼容分页响应
  return get<Appointment[]>('/appointments/my', params)
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
  /** null/不传保留原券；空字符串释放原券；其他值更换优惠券 */
  couponCode?: string | null
}

export function rescheduleAppointment(id: number, params: RescheduleParams) {
  return put<Appointment>(`/appointments/${id}/reschedule`, params)
}

/**
 * 扫码到店签到
 *
 * POST /appointments/{id}/checkin，code 为门店前台张贴的 HMAC 签名码原文
 * （格式 dc1.<clinicId>.<signature>，由后端签发，扫码后原样上报，不在本地解析）。
 * 签名真伪、归属本人、门店营业状态、扫码门店与预约门店一致、状态、就诊当天均由后端校验；
 * 业务失败（伪造码/非本人/门店不一致/非当天等）统一 reject，message 为后端文案。
 */
export function checkinAppointment(id: number, code: string) {
  return post(`/appointments/${id}/checkin`, { code })
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

/** wx.requestPayment 调起参数（字段名与微信 API 保持一致） */
export interface WxPayParams {
  timeStamp: string
  nonceStr: string
  package: string
  signType: string
  paySign: string
}

export interface CreateRechargeResult {
  recordId: number
  /** pending=需继续调起微信支付（payParams 有值）；success=已到账（mock 模式） */
  status: 'pending' | 'success' | string
  /** true 表示后端未配置真实微信支付、本次为模拟入账，仅本地/演示环境出现 */
  mock: boolean
  amount: number
  bonusAmount: number
  totalAdded?: number | null
  pointsEarned?: number | null
  paidAt?: string | null
  payParams?: WxPayParams | null
}

export function createRecharge(params: CreateRechargeParams) {
  return post<CreateRechargeResult>('/recharge/create', params)
}

export interface RechargeStatusResult {
  recordId: number
  /** pending/success/failed/refunded */
  status: 'pending' | 'success' | 'failed' | 'refunded' | string
  amount: number
  bonusAmount: number
  totalAdded?: number | null
  pointsEarned?: number | null
  paidAt?: string | null
}

/**
 * 查询充值订单状态（调起支付后轮询；后端对 pending 单会主动向微信查单兜底）
 */
export function getRechargeStatus(recordId: number) {
  return get<RechargeStatusResult>(`/recharge/${recordId}/status`)
}

// ==================== 消费记录 ====================

export interface ConsumptionRecord {
  id: number
  amount: number
  balanceUsed: number
  wechatPaid: number
  /** 消费结算状态；pending 表示余额不足、微信尾款尚未支付 */
  paymentStatus?: 'pending' | 'success' | string
  pointsEarned: number
  pointsUsed: number
  pointsDeduct: number
  description?: string
  clinicName?: string
  appointmentDate?: string
  doctorName?: string
  departmentName?: string
  createdAt: string
}

export interface ConsumptionRecordsResponse {
  list: ConsumptionRecord[]
  total: number
  page: number
  pageSize: number
}

export interface ConsumptionStatistics {
  totalAmount: number
  totalRecords: number
  monthAmount: number
  monthRecords: number
}

/**
 * 获取消费记录列表
 */
export function getConsumptionRecords(params?: {
  page?: number
  pageSize?: number
  startDate?: string
  endDate?: string
  clinicId?: number
}) {
  return get<ConsumptionRecordsResponse>('/consumption/records', params)
}

/**
 * 获取消费统计
 */
export function getConsumptionStatistics() {
  return get<ConsumptionStatistics>('/consumption/statistics')
}

/** 余额不足、等待微信支付尾款的消费记录 */
export interface UnpaidConsumption {
  id: number
  /** 本次消费总金额 */
  amount: number
  /** 余额抵扣后仍需微信支付的尾款 */
  wechatPaid: number
  description?: string | null
  createdAt: string
}

export interface ConsumptionPaymentInitResult {
  consumptionId: number
  /** pending=需调起微信支付；success=本地 mock 已完成结算 */
  status: 'pending' | 'success' | string
  mock: boolean
  amount: number
  payParams?: WxPayParams | null
}

export interface ConsumptionPaymentStatusResult {
  consumptionId: number
  /** pending/success */
  status: 'pending' | 'success' | string
  amount: number
  wechatPaid: number
  pointsEarned: number
  paidAt?: string | null
}

/**
 * 获取当前用户的待支付消费
 */
export function getUnpaidConsumptions() {
  return get<UnpaidConsumption[]>('/consumption/unpaid')
}

/**
 * 发起消费尾款支付
 *
 * openid 由服务端按当前登录用户读取，客户端不再上报。
 */
export function payConsumption(id: number) {
  return post<ConsumptionPaymentInitResult>(`/consumption/pay/${id}`)
}

/**
 * 查询消费尾款支付状态（pending 时服务端会主动向微信查单）
 */
export function getConsumptionPaymentStatus(id: number) {
  return get<ConsumptionPaymentStatusResult>(`/consumption/pay/${id}/status`)
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

/** 积分有效期分布：某过期月份剩余未过期积分 */
export interface ExpiryGroup {
  expireMonth: string
  points: number
}

/** 积分汇总（本月获取/消耗 + 有效期分布，需求 3.4.3） */
export interface PointsSummary {
  totalBalance: number
  monthlyEarned: number
  monthlySpent: number
  expiryGroups: ExpiryGroup[]
}

/**
 * 获取积分汇总：本月获取/消耗 + 按过期月份分布的剩余积分
 */
export function getPointsSummary() {
  return get<PointsSummary>('/points/summary')
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

// ==================== 积分商城 ====================

export interface PointsGoods {
  id: number
  name: string
  /** service=到店服务项目，item=实物商品 */
  goodsType: 'service' | 'item' | string
  pointsPrice: number
  coverImage?: string | null
  description?: string | null
  /** -1 表示不限量 */
  remainingStock: number
  sortOrder: number
}

export interface ExchangeOrder {
  id: number
  goodsId: number
  goodsName: string
  pointsCost: number
  /** 到店核销码（PE 开头） */
  redeemCode: string
  /** unused=待核销，used=已核销 */
  status: 'unused' | 'used' | string
  redeemedAt?: string | null
  createdAt: string
}

/**
 * 积分商城：上架商品列表
 */
export function getPointsGoods() {
  return get<PointsGoods[]>('/points/mall/goods')
}

/**
 * 积分商城：兑换商品（扣积分 + 生成核销码）
 *
 * 业务失败（已下架/售罄/积分不足）reject，message 为后端文案。
 */
export function exchangePointsGoods(goodsId: number) {
  return post<ExchangeOrder>('/points/mall/exchange', { goodsId })
}

/**
 * 积分商城：我的兑换订单（含已核销，最新在前）
 */
export function getMyExchangeOrders() {
  return get<ExchangeOrder[]>('/points/mall/orders')
}

// ==================== 推荐 ====================

export interface ReferralCodeResponse {
  referralCode: string
  shareLink: string
}

export interface ReferralRecord {
  id: number
  refereeName: string
  registeredAt: string
  firstConsumedAt?: string
  rewardStatus: string
  rewards: ReferralRewardRecord[]
  rewardPoints: number
  rewardBonus: number
}

export interface ReferralRewardRecord {
  id: number
  type: 'registration' | 'first_consumption' | 'tier' | 'ongoing'
  status: 'pending' | 'issued'
  rewardType?: 'points' | 'balance' | 'points_balance' | null
  rewardValue?: number | null
  createdAt: string
}

export interface ReferralRewardSummary {
  pointsGranted: number
  balanceGranted: number
  pendingCount: number
}

export interface ReferralRecordsResponse {
  list: ReferralRecord[]
  total: number
  monthlyCount: number
  summary: ReferralRewardSummary
  tierRewards: ReferralRewardRecord[]
  page: number
  pageSize: number
}

export interface ReferralQrCodeResponse {
  available: boolean
  payload: string
  qrcodeImageBase64?: string
  message?: string
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

/** 获取真实微信小程序码；不可用时由页面降级为分享按钮。 */
export function getReferralQrCode() {
  return get<ReferralQrCodeResponse>('/referral/qrcode')
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
  usedAt?: string | null
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

/** 推广领券返回的单张券 */
export interface PromoClaimedCoupon {
  couponCode: string
  couponName: string
  faceValue: number
  validTo: string
}

/**
 * 推广领券：扫码进入小程序自动领取 2 张（幂等，每人每模板限领一次）。
 * POST /coupons/promo-claim
 */
export function claimPromoCoupons(templateId: number) {
  return post<{ coupons: PromoClaimedCoupon[] }>('/coupons/promo-claim', { templateId })
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
  /** 关联活动 ID：一张轮播图对应一个活动，点击进入活动详情页 */
  activityId: number
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

/**
 * 活动详情（首页轮播图点击落地）
 *
 * 公开接口；活动不存在、已停用或不在排期内时后端返回 code=404，
 * 由调用方按「活动已结束」处理。
 */
export function getActivityDetail(id: number) {
  return get<ActivityDetail>(`/content/activities/${id}`, undefined, false)
}

/** 订阅消息模板配置（管理端 message_templates 中已填微信 templateId 的启用模板） */
export interface SubscribeTemplate {
  /** 业务模板标识，如 appointment_remind_evening */
  templateKey: string
  /** 微信公众平台模板库中的模板 id */
  templateId: string
}

/**
 * 获取订阅消息模板列表（公开接口）
 *
 * 仅返回 status=1 且已配置微信 templateId 的模板；管理端占位（templateId 为空）
 * 的模板不会下发，小程序拿到空数组时静默跳过 wx.requestSubscribeMessage。
 */
export function getSubscribeTemplates() {
  return get<SubscribeTemplate[]>('/content/subscribe-templates', undefined, false)
}
