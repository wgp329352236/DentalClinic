// 模拟数据服务 - 提供所有 API 的模拟数据
import type {
  UserInfo, MemberLevel, Clinic, Department, Doctor, TimeSlot,
  Appointment, Wallet, RechargeRecord, ConsumptionRecord,
  PointsInfo, PointsRecord, ReferralInfo, ReferralReward,
  Coupon, Message
} from '../types'

// ==================== 基础数据 ====================

const memberLevels: Record<number, MemberLevel> = {
  1: { level: 1, name: '普通会员', discount: 1.0, benefits: ['预约优先', '生日祝福'], nextLevelCost: 3000 },
  2: { level: 2, name: '银卡会员', discount: 0.95, benefits: ['专属客服', '优先排队'], nextLevelCost: 7000 },
  3: { level: 3, name: '金卡会员', discount: 0.9, benefits: ['免费年度口腔检查1次', '洁牙优惠'], nextLevelCost: 20000 },
  4: { level: 4, name: '钻石会员', discount: 0.85, benefits: ['免费年度口腔检查2次', '家属共享折扣', 'VIP专属活动'], nextLevelCost: 0 }
}

const clinics: Clinic[] = [
  {
    id: 1,
    name: '牙科门诊（总店）',
    address: '北京市朝阳区建国路88号',
    phone: '010-12345678',
    businessHours: '09:00-18:00',
    distance: 1.2,
    image: '/images/clinic1.jpg'
  },
  {
    id: 2,
    name: '牙科门诊（分店A）',
    address: '北京市海淀区中关村大街100号',
    phone: '010-87654321',
    businessHours: '09:00-18:00',
    distance: 3.5,
    image: '/images/clinic2.jpg'
  }
]

const departments: Department[] = [
  { id: 1, name: '口腔检查', description: '全面口腔健康检查' },
  { id: 2, name: '洗牙', description: '超声波洁牙服务' },
  { id: 3, name: '补牙', description: '龋齿修复治疗' },
  { id: 4, name: '正畸', description: '牙齿矫正治疗' },
  { id: 5, name: '种植', description: '种植牙手术' }
]

const doctors: Doctor[] = [
  {
    id: 1,
    name: '张医生',
    title: '主治医师',
    clinicIds: [1],
    specialties: ['口腔检查', '补牙'],
    introduction: '从事口腔临床工作15年，擅长龋齿修复和牙周治疗',
    avatar: '/images/doctor1.jpg'
  },
  {
    id: 2,
    name: '李医生',
    title: '副主任医师',
    clinicIds: [1, 2],
    specialties: ['正畸', '种植'],
    introduction: '口腔正畸专科医师，10年正畸经验',
    avatar: '/images/doctor2.jpg'
  },
  {
    id: 3,
    name: '王医生',
    title: '主任医师',
    clinicIds: [2],
    specialties: ['种植', '口腔检查'],
    introduction: '种植牙专家，完成种植手术2000+例',
    avatar: '/images/doctor3.jpg'
  }
]

// 生成未来7天的时段
function generateTimeSlots(doctorId: number): TimeSlot[] {
  const slots: TimeSlot[] = []
  const today = new Date()

  for (let i = 0; i < 7; i++) {
    const date = new Date(today)
    date.setDate(today.getDate() + i)
    const dateStr = date.toISOString().split('T')[0]

    const morningSlots = ['09:00-09:30', '09:30-10:00', '10:00-10:30', '10:30-11:00', '11:00-11:30', '11:30-12:00']
    const afternoonSlots = ['14:00-14:30', '14:30-15:00', '15:00-15:30', '15:30-16:00', '16:00-16:30', '16:30-17:00']

    const allSlots = morningSlots.concat(afternoonSlots)
    allSlots.forEach(function(timeRange) {
      const parts = timeRange.split('-')
      slots.push({
        date: dateStr,
        startTime: parts[0],
        endTime: parts[1],
        available: Math.random() > 0.3
      })
    })
  }

  return slots
}

// ==================== 用户数据 ====================

let currentUser: UserInfo = {
  id: 1,
  name: '张三',
  phone: '138****8888',
  gender: 'male',
  age: 30,
  avatar: '/images/avatar.jpg',
  clinicId: 1,
  memberLevel: memberLevels[2],
  registerSource: 'self',
  totalSpent: 8500
}

let wallet = {
  balance: 2500,
  giftedBalance: 450,
  monthlySpent: 800,
  totalSpent: 8500
}

const rechargeRecords: RechargeRecord[] = [
  { id: 1, amount: 3000, giftedAmount: 450, payMethod: 'wechat', status: 'success', createdAt: '2026-08-20 10:30:00' },
  { id: 2, amount: 1000, giftedAmount: 100, payMethod: 'wechat', status: 'success', createdAt: '2026-07-15 14:20:00' }
]

const consumptionRecords: ConsumptionRecord[] = [
  { id: 1, amount: 200, description: '洗牙', clinicName: '牙科门诊（总店）', createdAt: '2026-08-22 11:00:00' },
  { id: 2, amount: 600, description: '补牙（左下磨牙）', clinicName: '牙科门诊（总店）', createdAt: '2026-08-10 15:30:00' }
]

let appointments: Appointment[] = [
  {
    id: 1,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张医生',
    departmentId: 2,
    departmentName: '洗牙',
    appointmentTime: '2026-08-30 09:30',
    status: 'confirmed',
    createdAt: '2026-08-25 16:00:00'
  },
  {
    id: 2,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张医生',
    departmentId: 1,
    departmentName: '口腔检查',
    appointmentTime: '2026-08-15 10:00',
    status: 'completed',
    createdAt: '2026-08-10 09:00:00'
  }
]

let pointsInfo: PointsInfo = {
  balance: 1250,
  monthlyEarned: 300,
  monthlySpent: 100
}

const pointsRecords: PointsRecord[] = [
  { id: 1, type: 'earn', source: '消费积分', points: 200, relatedInfo: '洗牙消费200元', expireAt: '2027-08-22', createdAt: '2026-08-22 11:00:00' },
  { id: 2, type: 'earn', source: '充值积分', points: 15, relatedInfo: '充值1000元', expireAt: '2027-07-15', createdAt: '2026-07-15 14:20:00' },
  { id: 3, type: 'spend', source: '抵扣诊疗费', points: -100, relatedInfo: '抵扣1元', expireAt: '', createdAt: '2026-08-20 10:00:00' },
  { id: 4, type: 'earn', source: '签到积分', points: 2, expireAt: '2027-08-28', createdAt: '2026-08-28 08:00:00' }
]

let referralInfo: ReferralInfo = {
  referralCode: 'REF123456',
  referralLink: 'https://example.com/ref/REF123456',
  totalReferrals: 8,
  monthlyReferrals: 3,
  rewards: [
    { id: 1, type: 'register', points: 20, status: 'issued', referredPerson: '李**', createdAt: '2026-08-25 10:00:00' },
    { id: 2, type: 'first_consumption', points: 100, balance: 50, status: 'issued', referredPerson: '王**', createdAt: '2026-08-20 14:30:00' },
    { id: 3, type: 'tier', points: 200, status: 'pending', referredPerson: '', createdAt: '2026-08-28 09:00:00' }
  ]
}

const coupons: Coupon[] = [
  { id: 1, name: '新人券', amount: 100, minAmount: 200, scope: '全项目通用', validFrom: '2026-08-01', validTo: '2026-09-30', status: 'unused' },
  { id: 2, name: '推荐奖励券', amount: 50, minAmount: 100, scope: '洗牙、补牙', validFrom: '2026-08-20', validTo: '2026-10-20', status: 'unused' },
  { id: 3, name: '活动券', amount: 200, minAmount: 500, scope: '正畸项目', validFrom: '2026-07-01', validTo: '2026-08-31', status: 'expired' }
]

let messages: Message[] = [
  { id: 1, type: 'system', title: '预约提醒', content: '您明天09:30在牙科门诊（总店）有洗牙预约，请准时就诊', isRead: false, createdAt: '2026-08-29 20:00:00' },
  { id: 2, type: 'consumption', title: '消费通知', content: '您于08-22 11:00消费200元，当前余额2300元，获得积分200分', isRead: true, createdAt: '2026-08-22 11:00:00' },
  { id: 3, type: 'activity', title: '活动通知', content: '本月推荐好友注册，双倍积分奖励！', isRead: false, createdAt: '2026-08-28 10:00:00' }
]

// ==================== API 服务类 ====================

class MockApiService {
  async call(path: string, method: string, data?: any): Promise<any> {
    // Auth
    if (path === '/auth/wechat-login' && method === 'POST') {
      return this.wechatLogin(data)
    }
    if (path === '/profile/me' && method === 'GET') {
      return {
        userId: currentUser.id,
        openid: 'mock_openid',
        phone: currentUser.phone,
        nickname: currentUser.name,
        realName: currentUser.name,
        gender: currentUser.gender === 'male' ? 1 : 2,
        age: currentUser.age,
        avatar: currentUser.avatar,
        primaryClinicId: currentUser.clinicId,
        balance: wallet.balance,
        bonusBalance: wallet.giftedBalance
      }
    }

    // Clinics & Departments & Doctors
    if (path === '/clinics' && method === 'GET') {
      return clinics
    }
    if (path === '/departments' && method === 'GET') {
      return departments
    }
    if (path === '/doctors' && method === 'GET') {
      let filtered = doctors
      if (data?.clinicId) {
        filtered = filtered.filter(d => d.clinicIds.includes(Number(data.clinicId)))
      }
      return filtered
    }
    if (path.match(/^\/appointments\/slots$/) && method === 'GET') {
      const doctorId = Number(data?.doctorId)
      return generateTimeSlots(doctorId)
    }

    // Appointments
    if (path === '/appointments' && method === 'GET') {
      return { list: appointments, total: appointments.length, page: 1, pageSize: 10 }
    }
    if (path === '/appointments' && method === 'POST') {
      const newAppointment: Appointment = {
        id: appointments.length + 1,
        clinicId: data.clinicId,
        clinicName: clinics.find(c => c.id === data.clinicId)?.name || '',
        doctorId: data.doctorId,
        doctorName: doctors.find(d => d.id === data.doctorId)?.name || '',
        departmentId: data.departmentId,
        departmentName: departments.find(d => d.id === data.departmentId)?.name || '',
        appointmentTime: `${data.appointmentDate} ${data.startTime}`,
        status: 'pending',
        createdAt: new Date().toISOString().replace('T', ' ').substring(0, 19)
      }
      appointments.unshift(newAppointment)
      return newAppointment
    }
    if (path.match(/^\/appointments\/\d+\/cancel$/) && method === 'PUT') {
      const id = Number(path.split('/')[2])
      const appt = appointments.find(a => a.id === id)
      if (appt) {
        appt.status = 'cancelled'
      }
      return { success: true }
    }

    // Wallet
    if (path === '/wallet/details' && method === 'GET') {
      return {
        balance: wallet.balance,
        bonusBalance: wallet.giftedBalance,
        totalBalance: wallet.balance + wallet.giftedBalance,
        version: 1,
        rechargeHistory: rechargeRecords
      }
    }
    if (path === '/recharge/tiers' && method === 'GET') {
      return [
        { amount: 1000, bonus: 100, rate: '10%' },
        { amount: 3000, bonus: 450, rate: '15%' },
        { amount: 5000, bonus: 1000, rate: '20%' },
        { amount: 10000, bonus: 2500, rate: '25%' }
      ]
    }
    if (path === '/recharge/create' && method === 'POST') {
      const plan = data
      const record: RechargeRecord = {
        id: rechargeRecords.length + 1,
        amount: plan.amount,
        giftedAmount: plan.amount * 0.1,
        payMethod: 'wechat',
        status: 'success',
        createdAt: new Date().toISOString().replace('T', ' ').substring(0, 19)
      }
      rechargeRecords.unshift(record)
      wallet.balance += plan.amount + record.giftedAmount
      wallet.giftedBalance += record.giftedAmount
      return { orderId: record.id, amount: plan.amount }
    }

    // Points
    if (path === '/points/balance' && method === 'GET') {
      return pointsInfo
    }
    if (path === '/points/records' && method === 'GET') {
      return { list: pointsRecords, total: pointsRecords.length, page: 1, pageSize: 10 }
    }
    if (path === '/points/checkin' && method === 'POST') {
      pointsInfo.balance += 2
      pointsInfo.monthlyEarned += 2
      return { pointsEarned: 2 }
    }

    // Referral
    if (path === '/referral/code' && method === 'GET') {
      return referralInfo
    }
    if (path === '/referral/records' && method === 'GET') {
      return { list: referralInfo.rewards, total: referralInfo.rewards.length, page: 1, pageSize: 10 }
    }

    // Coupons
    if (path === '/coupons' && method === 'GET') {
      return coupons
    }

    // Messages
    if (path === '/messages' && method === 'GET') {
      return { list: messages, total: messages.length, page: 1, pageSize: 10 }
    }
    if (path === '/messages/unread-count' && method === 'GET') {
      return { count: messages.filter(m => !m.isRead).length }
    }

    throw new Error(`未实现的API: ${method} ${path}`)
  }

  private wechatLogin(data: any) {
    return {
      userId: currentUser.id,
      accessToken: 'mock_jwt_token_' + Date.now(),
      refreshToken: 'mock_refresh_token_' + Date.now(),
      isNewUser: false
    }
  }
}

export const mockApiService = new MockApiService()
