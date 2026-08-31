// 用户相关类型
export interface UserInfo {
  id: number
  name: string
  phone: string
  gender: 'male' | 'female'
  age: number
  avatar: string
  clinicId: number
  memberLevel: MemberLevel
  registerSource: 'self' | 'referral' | 'store'
  totalSpent?: number // 累计消费金额
}

export interface MemberLevel {
  level: number
  name: string
  discount: number // 折扣，如 0.95 表示 9.5折
  benefits: string[]
  nextLevelCost: number // 距升级还需消费金额
}

// 门店相关类型
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
  date: string // YYYY-MM-DD
  startTime: string // 如 "09:00"
  endTime: string // 如 "09:30"
  available: boolean
}

// 预约相关类型
export interface Appointment {
  id: number
  clinicId: number
  clinicName: string
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  appointmentTime: string // YYYY-MM-DD HH:mm
  status: 'pending' | 'confirmed' | 'arrived' | 'completed' | 'cancelled' | 'no_show'
  createdAt: string
}

// 钱包相关类型
export interface Wallet {
  balance: number
  giftedBalance: number
  monthlySpent: number
  totalSpent: number
}

export interface RechargeRecord {
  id: number
  amount: number
  giftedAmount: number
  payMethod: 'wechat' | 'balance'
  status: 'success' | 'pending' | 'failed'
  createdAt: string
}

export interface ConsumptionRecord {
  id: number
  amount: number
  description: string
  clinicName: string
  createdAt: string
}

// 积分相关类型
export interface PointsInfo {
  balance: number
  monthlyEarned: number
  monthlySpent: number
}

export interface PointsRecord {
  id: number
  type: 'earn' | 'spend'
  source: string // 如 "消费积分"、"充值积分"、"签到"
  points: number
  relatedInfo?: string
  expireAt: string
  createdAt: string
}

// 推荐相关类型
export interface ReferralInfo {
  referralCode: string
  referralLink: string
  totalReferrals: number
  monthlyReferrals: number
  rewards: ReferralReward[]
}

export interface ReferralReward {
  id: number
  type: 'register' | 'first_consumption' | 'tier'
  points?: number
  balance?: number
  couponAmount?: number
  status: 'pending' | 'issued'
  referredPerson?: string // 脱敏姓名
  createdAt: string
}

// 优惠券相关类型
export interface Coupon {
  id: number
  name: string
  amount: number
  minAmount: number
  scope: string // 适用范围
  validFrom: string
  validTo: string
  status: 'unused' | 'used' | 'expired'
}

// 消息相关类型
export interface Message {
  id: number
  type: 'system' | 'consumption' | 'activity'
  title: string
  content: string
  isRead: boolean
  createdAt: string
}

// API 响应类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T = any> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
