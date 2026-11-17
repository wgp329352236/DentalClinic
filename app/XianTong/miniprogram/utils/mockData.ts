// 模拟数据服务 - 提供所有 API 的模拟数据（离线开发/演示用）
//
// 字段口径：
// 1. 以 utils/api.ts 的接口类型为准（如 /points/balance 返回 totalBalance）；
// 2. 列表类接口同时给出页面实际读取的字段名（积分明细的 changeAmount、优惠券的
//    templateName、推荐记录的 refereeName 等），页面无需再做兼容判断；
// 3. 业务失败统一返回 { code, message }，由 request.ts 的 mockRequest 转成 reject，
//    与真实接口的失败语义保持一致。
//
// 生效方式：把 utils/request.ts 的 USE_MOCK 置为 true。
import type {
  UserInfo, MemberLevel, Clinic, Department, Doctor, TimeSlot,
  MedicalRecord, MedicalRecordDetail, Banner, Notice, ActivityDetail
} from '../types'

// ==================== 通用工具 ====================

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

/** 本地时区日期 yyyy-MM-dd（与页面 getToday() 口径一致，保证「当天签到」演示可用） */
function formatDate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function formatDateTime(date: Date): string {
  return `${formatDate(date)} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

/** 相对今天偏移 days 天的日期；days 为负表示过去 */
function dateOffset(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatDate(date)
}

/** 相对今天偏移 days 天的日期时间，time 形如 "09:30:00" */
function datetimeAt(days: number, time: string): string {
  return `${dateOffset(days)} ${time}`
}

function now(): string {
  return formatDateTime(new Date())
}

/** 新增记录的自增 id */
function nextId(list: { id: number }[]): number {
  return list.reduce((max, item) => (item.id > max ? item.id : max), 0) + 1
}

function paginate<T>(list: T[], page?: any, pageSize?: any) {
  const p = Number(page) > 0 ? Number(page) : 1
  const size = Number(pageSize) > 0 ? Number(pageSize) : 20
  const start = (p - 1) * size
  return {
    list: list.slice(start, start + size),
    total: list.length,
    page: p,
    pageSize: size
  }
}

/** 稳定哈希：让同一个（医生 + 日期 + 时段）的余量在多次请求间保持不变，便于反复测试 */
function hashString(str: string): number {
  let hash = 7
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) % 1000003
  }
  return hash
}

// ==================== 会员等级 ====================

interface LevelMeta {
  level: number
  name: string
  discount: number
  benefits: string[]
  /** 进入该等级的累计消费门槛 */
  threshold: number
}

const levelMetaList: LevelMeta[] = [
  { level: 1, name: '普通会员', discount: 1, benefits: ['预约优先', '生日祝福'], threshold: 0 },
  { level: 2, name: '银卡会员', discount: 0.95, benefits: ['专属客服', '优先排队', '生日专属礼'], threshold: 3000 },
  { level: 3, name: '金卡会员', discount: 0.9, benefits: ['免费年度口腔检查1次', '洁牙8折', '家属共享折扣'], threshold: 10000 },
  { level: 4, name: '钻石会员', discount: 0.85, benefits: ['免费年度口腔检查2次', '种植/正畸专属折扣', 'VIP专属活动'], threshold: 30000 }
]

function levelOf(totalSpent: number): LevelMeta {
  let matched = levelMetaList[0]
  levelMetaList.forEach(item => {
    if (totalSpent >= item.threshold) matched = item
  })
  return matched
}

/** 距下一等级还需消费的金额；已是最高等级返回 null（页面据此隐藏进度条） */
function nextLevelCostOf(totalSpent: number): number | null {
  const current = levelOf(totalSpent)
  const next = levelMetaList.find(item => item.level === current.level + 1)
  if (!next) return null
  return Math.max(0, next.threshold - totalSpent)
}

function buildMemberLevel(totalSpent: number): MemberLevel {
  const meta = levelOf(totalSpent)
  return {
    level: meta.level,
    name: meta.name,
    discount: meta.discount,
    benefits: meta.benefits,
    nextLevelCost: nextLevelCostOf(totalSpent)
  }
}

// ==================== 基础数据（门诊 / 科室 / 医生） ====================

/** 首页会兼容读取 openTime/closeTime/coverImage（后端门店表字段） */
interface MockClinic extends Clinic {
  openTime: string
  closeTime: string
  coverImage: string
}

/** 预约页按 clinicId + departmentId 过滤医生，故额外维护 departmentIds */
interface MockDoctor extends Doctor {
  departmentIds: number[]
}

const clinics: MockClinic[] = [
  {
    id: 1,
    name: '牙科门诊（总店）',
    address: '北京市朝阳区建国路88号',
    phone: '010-12345678',
    businessHours: '09:00-18:00',
    openTime: '09:00',
    closeTime: '18:00',
    distance: 1.2,
    image: '/images/clinic1.jpg',
    coverImage: '/images/clinic1.jpg'
  },
  {
    id: 2,
    name: '牙科门诊（分店A）',
    address: '北京市海淀区中关村大街100号',
    phone: '010-87654321',
    businessHours: '09:00-18:00',
    openTime: '09:00',
    closeTime: '18:00',
    distance: 3.5,
    image: '/images/clinic2.jpg',
    coverImage: '/images/clinic2.jpg'
  },
  {
    id: 3,
    name: '牙科门诊（分店B）',
    address: '北京市西城区金融街7号长安中心1层',
    phone: '010-55667788',
    businessHours: '09:00-19:00',
    openTime: '09:00',
    closeTime: '19:00',
    distance: 6.4,
    image: '/images/clinic3.jpg',
    coverImage: '/images/clinic3.jpg'
  },
  {
    id: 4,
    name: '儿童齿科中心（望京店）',
    address: '北京市朝阳区望京SOHO T1 座 3 层',
    phone: '010-66778899',
    businessHours: '09:00-17:30',
    openTime: '09:00',
    closeTime: '17:30',
    distance: 5.1,
    image: '/images/clinic4.jpg',
    coverImage: '/images/clinic4.jpg'
  }
]

const departments: Department[] = [
  { id: 1, name: '口腔检查', description: '全面口腔健康检查，含口内检查与拍片建议' },
  { id: 2, name: '洗牙', description: '超声波洁牙 + 抛光，清除牙结石与色素' },
  { id: 3, name: '补牙', description: '龋齿充填修复，树脂/玻璃离子可选' },
  { id: 4, name: '正畸', description: '青少年及成人矫正，金属/隐形矫治' },
  { id: 5, name: '种植', description: '单颗/多颗种植修复，含植骨方案' },
  { id: 6, name: '牙周治疗', description: '牙龈炎、牙周炎系统治疗与维护' },
  { id: 7, name: '儿童齿科', description: '涂氟、窝沟封闭、乳牙治疗' }
]

const doctors: MockDoctor[] = [
  {
    id: 1,
    name: '张伟',
    title: '主治医师',
    clinicIds: [1],
    departmentIds: [1, 2, 3],
    specialties: ['口腔检查', '补牙', '洗牙'],
    introduction: '从事口腔临床工作15年，擅长龋齿充填与牙周基础治疗，年接诊2000+人次',
    avatar: '/images/doctor1.jpg'
  },
  {
    id: 2,
    name: '李静',
    title: '副主任医师',
    clinicIds: [1, 2],
    departmentIds: [4, 5],
    specialties: ['正畸', '种植'],
    introduction: '口腔正畸专科医师，10年正畸经验，完成隐形矫正病例800余例',
    avatar: '/images/doctor2.jpg'
  },
  {
    id: 3,
    name: '王强',
    title: '主任医师',
    clinicIds: [2, 3],
    departmentIds: [1, 5],
    specialties: ['种植', '口腔检查'],
    introduction: '种植牙专家，完成各类种植手术2000+例，擅长上颌窦提升与即刻种植',
    avatar: '/images/doctor3.jpg'
  },
  {
    id: 4,
    name: '陈晓',
    title: '医师',
    clinicIds: [1, 4],
    departmentIds: [2, 7],
    specialties: ['儿童齿科', '洗牙'],
    introduction: '儿童口腔医学硕士，擅长儿童行为诱导、涂氟与窝沟封闭',
    avatar: '/images/doctor4.jpg'
  },
  {
    id: 5,
    name: '刘敏',
    title: '主治医师',
    clinicIds: [2],
    departmentIds: [3, 6],
    specialties: ['补牙', '牙周治疗'],
    introduction: '牙周病学硕士，擅长牙周系统治疗与美学树脂修复',
    avatar: '/images/doctor5.jpg'
  },
  {
    id: 6,
    name: '赵磊',
    title: '副主任医师',
    clinicIds: [1, 3],
    departmentIds: [4, 6],
    specialties: ['正畸', '牙周治疗'],
    introduction: '正畸与牙周联合治疗方向，擅长成人复杂病例的跨学科方案设计',
    avatar: '/images/doctor6.jpg'
  },
  {
    id: 7,
    name: '孙悦',
    title: '医师',
    clinicIds: [4],
    departmentIds: [2, 7],
    specialties: ['儿童齿科', '洗牙'],
    introduction: '专注儿童口腔预防与早期矫治，善于安抚低龄儿童就诊情绪',
    avatar: '/images/doctor7.jpg'
  },
  {
    id: 8,
    name: '周涛',
    title: '主任医师',
    clinicIds: [1, 2, 3],
    departmentIds: [3, 5],
    specialties: ['种植', '补牙'],
    introduction: '口腔修复与种植双学科背景，专注咬合重建与复杂种植修复',
    avatar: '/images/doctor8.jpg'
  }
]

// 可预约时段（与门店营业时间一致）
const MORNING_SLOTS = ['09:00-09:30', '09:30-10:00', '10:00-10:30', '10:30-11:00', '11:00-11:30', '11:30-12:00']
const AFTERNOON_SLOTS = ['14:00-14:30', '14:30-15:00', '15:00-15:30', '15:30-16:00', '16:00-16:30', '16:30-17:00']
const MAX_PER_SLOT = 3

interface MockTimeSlot extends TimeSlot {
  /** 剩余可约名额，页面据此判断 available */
  remaining: number
  maxAppointments: number
}

function isPastSlot(date: string, startTime: string): boolean {
  return `${date} ${startTime}` < now().substring(0, 16)
}

function remainingOf(doctorId: number, date: string, startTime: string): number {
  // 已过号时段不可约
  if (isPastSlot(date, startTime)) return 0
  const booked = hashString(`${doctorId}|${date}|${startTime}`) % 4
  return Math.max(0, MAX_PER_SLOT - booked)
}

// ==================== 用户 / 钱包 ====================

interface MockWallet {
  balance: number
  bonusBalance: number
}

let wallet: MockWallet = {
  balance: 2500,
  bonusBalance: 450
}

let currentUser: UserInfo = {
  id: 1,
  name: '张三',
  nickname: '小张',
  phone: '13800138000',
  gender: 'male',
  age: 30,
  avatar: '/images/avatar.jpg',
  clinicId: 1,
  memberLevel: buildMemberLevel(8500),
  registerSource: 'self',
  totalSpent: 8500,
  balance: wallet.balance,
  bonusBalance: wallet.bonusBalance
}

/** 充值赠送规则：达到门槛即按对应比例赠送 */
const RECHARGE_RULES = [
  { min: 10000, rate: 0.25 },
  { min: 5000, rate: 0.2 },
  { min: 3000, rate: 0.15 },
  { min: 1000, rate: 0.1 },
  { min: 500, rate: 0.05 }
]

function calcBonus(amount: number): number {
  const rule = RECHARGE_RULES.find(item => amount >= item.min)
  return rule ? Math.round(amount * rule.rate) : 0
}

interface MockRechargeRecord {
  id: number
  amount: number
  bonusAmount: number
  status: 'success' | 'pending' | 'failed' | 'refunded'
  paidAt: string
  createdAt: string
  pointsEarned: number
}

const rechargeRecords: MockRechargeRecord[] = [
  { id: 1, amount: 3000, bonusAmount: 450, status: 'success', paidAt: datetimeAt(-30, '10:30:05'), createdAt: datetimeAt(-30, '10:30:00'), pointsEarned: 30 },
  { id: 2, amount: 1000, bonusAmount: 100, status: 'success', paidAt: datetimeAt(-60, '14:20:10'), createdAt: datetimeAt(-60, '14:20:00'), pointsEarned: 10 },
  { id: 3, amount: 500, bonusAmount: 25, status: 'success', paidAt: datetimeAt(-100, '09:05:30'), createdAt: datetimeAt(-100, '09:05:00'), pointsEarned: 5 },
  { id: 4, amount: 2000, bonusAmount: 300, status: 'success', paidAt: datetimeAt(-150, '16:40:20'), createdAt: datetimeAt(-150, '16:40:00'), pointsEarned: 20 },
  { id: 5, amount: 1000, bonusAmount: 100, status: 'pending', paidAt: '', createdAt: datetimeAt(-1, '20:12:00'), pointsEarned: 0 }
]

// ==================== 积分 ====================

interface MockPointsRecord {
  id: number
  /** 带符号的积分变动，正数获得、负数消耗 */
  changeAmount: number
  sourceType: string
  description: string
  expiresAt: string
  createdAt: string
}

const pointsRecords: MockPointsRecord[] = [
  { id: 1, changeAmount: 1000, sourceType: '注册赠送', description: '新用户注册礼包', expiresAt: dateOffset(365), createdAt: datetimeAt(-120, '11:20:00') },
  { id: 2, changeAmount: 38, sourceType: '消费积分', description: '种植消费3800元', expiresAt: dateOffset(365), createdAt: datetimeAt(-45, '12:10:00') },
  { id: 3, changeAmount: 6, sourceType: '消费积分', description: '补牙消费600元', expiresAt: dateOffset(365), createdAt: datetimeAt(-20, '16:05:00') },
  { id: 4, changeAmount: 1, sourceType: '消费积分', description: '口腔检查消费100元', expiresAt: dateOffset(365), createdAt: datetimeAt(-6, '09:40:00') },
  { id: 5, changeAmount: 2, sourceType: '消费积分', description: '儿童涂氟消费200元', expiresAt: dateOffset(365), createdAt: datetimeAt(-70, '14:50:00') },
  { id: 6, changeAmount: 30, sourceType: '充值积分', description: '充值3000元', expiresAt: dateOffset(365), createdAt: datetimeAt(-30, '10:30:00') },
  { id: 7, changeAmount: 10, sourceType: '充值积分', description: '充值1000元', expiresAt: dateOffset(365), createdAt: datetimeAt(-60, '14:20:00') },
  { id: 8, changeAmount: 100, sourceType: '推荐奖励', description: '推荐好友王**首诊消费', expiresAt: dateOffset(365), createdAt: datetimeAt(-12, '18:00:00') },
  { id: 9, changeAmount: 20, sourceType: '完善资料', description: '完善个人资料奖励', expiresAt: dateOffset(365), createdAt: datetimeAt(-90, '20:15:00') },
  { id: 10, changeAmount: 2, sourceType: '签到积分', description: '每日签到', expiresAt: dateOffset(365), createdAt: datetimeAt(-1, '08:05:00') },
  { id: 11, changeAmount: -100, sourceType: '积分抵扣', description: '抵扣诊疗费1.00元', expiresAt: '', createdAt: datetimeAt(-25, '15:20:00') },
  { id: 12, changeAmount: -500, sourceType: '积分兑换', description: '兑换超声波洁牙券', expiresAt: '', createdAt: datetimeAt(-8, '20:30:00') },
  { id: 13, changeAmount: -400, sourceType: '积分兑换', description: '兑换儿童涂氟券', expiresAt: '', createdAt: datetimeAt(-40, '19:10:00') }
]

let pointsBalance = {
  totalBalance: pointsRecords.reduce((sum, record) => sum + record.changeAmount, 0),
  updatedAt: now()
}

let lastCheckinDate = ''
let profileRewardClaimed = true // 演示用户已领过完善资料奖励

interface MockPointsGoods {
  id: number
  name: string
  goodsType: 'service' | 'item'
  pointsPrice: number
  coverImage: string | null
  description: string | null
  /** -1 表示不限量 */
  remainingStock: number
  sortOrder: number
}

const pointsGoods: MockPointsGoods[] = [
  { id: 1, name: '超声波洁牙券', goodsType: 'service', pointsPrice: 500, coverImage: null, description: '到店出示核销码即可享受一次超声波洁牙', remainingStock: -1, sortOrder: 1 },
  { id: 2, name: '口腔检查套餐', goodsType: 'service', pointsPrice: 200, coverImage: null, description: '含口内检查 + 全口曲面断层片一次', remainingStock: -1, sortOrder: 2 },
  { id: 3, name: '儿童涂氟券', goodsType: 'service', pointsPrice: 400, coverImage: null, description: '12岁以下儿童全口涂氟一次', remainingStock: -1, sortOrder: 3 },
  { id: 4, name: '成人护龈牙刷套装', goodsType: 'item', pointsPrice: 300, coverImage: '/images/goods-toothbrush.jpg', description: '软毛牙刷3支装，到店自提', remainingStock: 8, sortOrder: 4 },
  { id: 5, name: '声波电动牙刷', goodsType: 'item', pointsPrice: 1200, coverImage: '/images/goods-electric.jpg', description: '含3个刷头，到店自提', remainingStock: 3, sortOrder: 5 }
]

interface MockExchangeOrder {
  id: number
  goodsId: number
  goodsName: string
  pointsCost: number
  redeemCode: string
  status: 'unused' | 'used'
  redeemedAt: string | null
  createdAt: string
}

const exchangeOrders: MockExchangeOrder[] = [
  { id: 1, goodsId: 1, goodsName: '超声波洁牙券', pointsCost: 500, redeemCode: 'PE2026080112345678', status: 'unused', redeemedAt: null, createdAt: datetimeAt(-8, '20:30:00') },
  { id: 2, goodsId: 3, goodsName: '儿童涂氟券', pointsCost: 400, redeemCode: 'PE2026071012345671', status: 'used', redeemedAt: datetimeAt(-35, '10:15:00'), createdAt: datetimeAt(-40, '19:10:00') }
]

// ==================== 预约 ====================

type AppointmentStatus = 'pending' | 'confirmed' | 'arrived' | 'completed' | 'cancelled' | 'no_show'

interface MockAppointment {
  id: number
  clinicId: number
  clinicName: string
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  appointmentDate: string
  startTime: string
  endTime: string
  status: AppointmentStatus
  remark?: string
  createdAt: string
  arrivedAt?: string
  completedAt?: string
}

let appointments: MockAppointment[] = [
  {
    id: 1,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张伟',
    departmentId: 2,
    departmentName: '洗牙',
    appointmentDate: dateOffset(0),
    startTime: '09:30',
    endTime: '10:00',
    status: 'confirmed',
    remark: '牙龈易出血，请安排温和洁牙',
    createdAt: datetimeAt(-3, '11:20:00')
  },
  {
    id: 2,
    clinicId: 2,
    clinicName: '牙科门诊（分店A）',
    doctorId: 2,
    doctorName: '李静',
    departmentId: 4,
    departmentName: '正畸',
    appointmentDate: dateOffset(1),
    startTime: '10:00',
    endTime: '10:30',
    status: 'pending',
    createdAt: datetimeAt(-1, '09:10:00')
  },
  {
    id: 3,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张伟',
    departmentId: 3,
    departmentName: '补牙',
    appointmentDate: dateOffset(2),
    startTime: '14:30',
    endTime: '15:30',
    status: 'confirmed',
    createdAt: datetimeAt(-2, '15:40:00')
  },
  {
    id: 4,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 8,
    doctorName: '周涛',
    departmentId: 5,
    departmentName: '种植',
    appointmentDate: dateOffset(0),
    startTime: '08:30',
    endTime: '09:00',
    status: 'arrived',
    createdAt: datetimeAt(-5, '10:00:00'),
    arrivedAt: datetimeAt(0, '08:22:00')
  },
  {
    id: 5,
    clinicId: 3,
    clinicName: '牙科门诊（分店B）',
    doctorId: 6,
    doctorName: '赵磊',
    departmentId: 6,
    departmentName: '牙周治疗',
    appointmentDate: dateOffset(4),
    startTime: '16:00',
    endTime: '16:30',
    status: 'pending',
    createdAt: datetimeAt(-1, '20:05:00')
  },
  {
    id: 6,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张伟',
    departmentId: 1,
    departmentName: '口腔检查',
    appointmentDate: dateOffset(-6),
    startTime: '09:00',
    endTime: '09:30',
    status: 'completed',
    createdAt: datetimeAt(-9, '14:00:00'),
    arrivedAt: datetimeAt(-6, '08:52:00'),
    completedAt: datetimeAt(-6, '09:40:00')
  },
  {
    id: 7,
    clinicId: 2,
    clinicName: '牙科门诊（分店A）',
    doctorId: 5,
    doctorName: '刘敏',
    departmentId: 3,
    departmentName: '补牙',
    appointmentDate: dateOffset(-20),
    startTime: '15:00',
    endTime: '16:00',
    status: 'completed',
    createdAt: datetimeAt(-25, '10:30:00'),
    arrivedAt: datetimeAt(-20, '14:50:00'),
    completedAt: datetimeAt(-20, '16:05:00')
  },
  {
    id: 8,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 8,
    doctorName: '周涛',
    departmentId: 5,
    departmentName: '种植',
    appointmentDate: dateOffset(-45),
    startTime: '10:30',
    endTime: '12:00',
    status: 'completed',
    createdAt: datetimeAt(-60, '09:20:00'),
    arrivedAt: datetimeAt(-45, '10:15:00'),
    completedAt: datetimeAt(-45, '12:10:00')
  },
  {
    id: 9,
    clinicId: 4,
    clinicName: '儿童齿科中心（望京店）',
    doctorId: 4,
    doctorName: '陈晓',
    departmentId: 7,
    departmentName: '儿童齿科',
    appointmentDate: dateOffset(-70),
    startTime: '14:00',
    endTime: '14:40',
    status: 'completed',
    createdAt: datetimeAt(-75, '16:30:00'),
    arrivedAt: datetimeAt(-70, '13:50:00'),
    completedAt: datetimeAt(-70, '14:50:00')
  },
  {
    id: 10,
    clinicId: 2,
    clinicName: '牙科门诊（分店A）',
    doctorId: 3,
    doctorName: '王强',
    departmentId: 1,
    departmentName: '口腔检查',
    appointmentDate: dateOffset(-10),
    startTime: '11:00',
    endTime: '11:30',
    status: 'cancelled',
    createdAt: datetimeAt(-13, '09:00:00')
  },
  {
    id: 11,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 1,
    doctorName: '张伟',
    departmentId: 2,
    departmentName: '洗牙',
    appointmentDate: dateOffset(-15),
    startTime: '09:00',
    endTime: '09:30',
    status: 'no_show',
    createdAt: datetimeAt(-18, '15:00:00')
  },
  {
    id: 12,
    clinicId: 3,
    clinicName: '牙科门诊（分店B）',
    doctorId: 6,
    doctorName: '赵磊',
    departmentId: 6,
    departmentName: '牙周治疗',
    appointmentDate: dateOffset(-33),
    startTime: '10:00',
    endTime: '11:00',
    status: 'completed',
    createdAt: datetimeAt(-36, '11:10:00'),
    arrivedAt: datetimeAt(-33, '09:50:00'),
    completedAt: datetimeAt(-33, '11:05:00')
  },
  {
    id: 13,
    clinicId: 4,
    clinicName: '儿童齿科中心（望京店）',
    doctorId: 7,
    doctorName: '孙悦',
    departmentId: 7,
    departmentName: '儿童齿科',
    appointmentDate: dateOffset(0),
    startTime: '15:00',
    endTime: '15:30',
    status: 'confirmed',
    remark: '孩子首次涂氟，请提前告知注意事项',
    createdAt: datetimeAt(-2, '18:20:00')
  },
  {
    id: 14,
    clinicId: 1,
    clinicName: '牙科门诊（总店）',
    doctorId: 4,
    doctorName: '陈晓',
    departmentId: 2,
    departmentName: '洗牙',
    appointmentDate: dateOffset(3),
    startTime: '16:30',
    endTime: '17:00',
    status: 'pending',
    createdAt: datetimeAt(-1, '21:05:00')
  }
]

const ACTIVE_STATUS: AppointmentStatus[] = ['pending', 'confirmed', 'arrived']

/** 待就诊（含已到店）按时间正序在前，历史记录按时间倒序在后 */
function sortAppointments(list: MockAppointment[]): MockAppointment[] {
  return list.slice().sort((a, b) => {
    const aActive = ACTIVE_STATUS.indexOf(a.status) >= 0
    const bActive = ACTIVE_STATUS.indexOf(b.status) >= 0
    if (aActive !== bActive) return aActive ? -1 : 1
    const ak = `${a.appointmentDate} ${a.startTime}`
    const bk = `${b.appointmentDate} ${b.startTime}`
    return aActive ? ak.localeCompare(bk) : bk.localeCompare(ak)
  })
}

// ==================== 就诊记录 ====================

const medicalRecordDetails: MedicalRecordDetail[] = [
  {
    id: 6,
    appointmentDate: dateOffset(-6),
    startTime: '09:00',
    endTime: '09:30',
    clinicName: '牙科门诊（总店）',
    clinicAddress: '北京市朝阳区建国路88号',
    doctorName: '张伟',
    doctorTitle: '主治医师',
    doctorIntroduction: '从事口腔临床工作15年，擅长龋齿充填与牙周基础治疗',
    departmentName: '口腔检查',
    diagnosis: '牙列基本整齐，下颌前牙区牙结石Ⅰ度，牙龈轻度炎症',
    treatmentPlan: '建议每半年洁牙一次，日常使用牙线并采用巴氏刷牙法',
    remark: '患者对冷刺激略敏感，已告知观察',
    status: 'completed',
    arrivedAt: datetimeAt(-6, '08:52:00'),
    completedAt: datetimeAt(-6, '09:40:00'),
    consumptionAmount: 100,
    totalAmount: 100,
    consumptionDetails: [
      { id: 61, amount: 100, balanceUsed: 100, wechatPaid: 0, pointsEarned: 1, description: '口腔检查费', createdAt: datetimeAt(-6, '09:40:00') }
    ]
  },
  {
    id: 7,
    appointmentDate: dateOffset(-20),
    startTime: '15:00',
    endTime: '16:00',
    clinicName: '牙科门诊（分店A）',
    clinicAddress: '北京市海淀区中关村大街100号',
    doctorName: '刘敏',
    doctorTitle: '主治医师',
    doctorIntroduction: '牙周病学硕士，擅长牙周系统治疗与美学树脂修复',
    departmentName: '补牙',
    diagnosis: '左下第一磨牙深龋，牙髓活力正常',
    treatmentPlan: '去腐后树脂充填，1个月后复查；若出现自发痛需根管治疗',
    status: 'completed',
    arrivedAt: datetimeAt(-20, '14:50:00'),
    completedAt: datetimeAt(-20, '16:05:00'),
    consumptionAmount: 600,
    totalAmount: 600,
    consumptionDetails: [
      { id: 71, amount: 600, balanceUsed: 500, wechatPaid: 100, pointsEarned: 6, description: '树脂充填（左下第一磨牙）', createdAt: datetimeAt(-20, '16:05:00') }
    ]
  },
  {
    id: 8,
    appointmentDate: dateOffset(-45),
    startTime: '10:30',
    endTime: '12:00',
    clinicName: '牙科门诊（总店）',
    clinicAddress: '北京市朝阳区建国路88号',
    doctorName: '周涛',
    doctorTitle: '主任医师',
    doctorIntroduction: '口腔修复与种植双学科背景，专注咬合重建与复杂种植修复',
    departmentName: '种植',
    diagnosis: '右下第一磨牙缺失3个月，牙槽骨高度可，骨密度尚可',
    treatmentPlan: '一期植入种植体，3个月后行二期基台连接，再2周后冠修复',
    remark: '术后24小时内避免漱口与患侧咀嚼',
    status: 'completed',
    arrivedAt: datetimeAt(-45, '10:15:00'),
    completedAt: datetimeAt(-45, '12:10:00'),
    consumptionAmount: 3800,
    totalAmount: 3800,
    consumptionDetails: [
      { id: 81, amount: 3000, balanceUsed: 2700, wechatPaid: 300, pointsEarned: 30, description: '种植体植入术', createdAt: datetimeAt(-45, '12:10:00') },
      { id: 82, amount: 800, balanceUsed: 800, wechatPaid: 0, pointsEarned: 8, description: '愈合基台及耗材', createdAt: datetimeAt(-45, '12:10:00') }
    ]
  },
  {
    id: 9,
    appointmentDate: dateOffset(-70),
    startTime: '14:00',
    endTime: '14:40',
    clinicName: '儿童齿科中心（望京店）',
    clinicAddress: '北京市朝阳区望京SOHO T1 座 3 层',
    doctorName: '陈晓',
    doctorTitle: '医师',
    doctorIntroduction: '儿童口腔医学硕士，擅长儿童行为诱导、涂氟与窝沟封闭',
    departmentName: '儿童齿科',
    diagnosis: '混合牙列期，六龄齿窝沟较深，未见明显龋坏',
    treatmentPlan: '全口涂氟 + 六龄齿窝沟封闭，半年复查',
    status: 'completed',
    arrivedAt: datetimeAt(-70, '13:50:00'),
    completedAt: datetimeAt(-70, '14:50:00'),
    consumptionAmount: 200,
    totalAmount: 200,
    consumptionDetails: [
      { id: 91, amount: 200, balanceUsed: 200, wechatPaid: 0, pointsEarned: 2, description: '全口涂氟', createdAt: datetimeAt(-70, '14:50:00') }
    ]
  },
  {
    id: 12,
    appointmentDate: dateOffset(-33),
    startTime: '10:00',
    endTime: '11:00',
    clinicName: '牙科门诊（分店B）',
    clinicAddress: '北京市西城区金融街7号长安中心1层',
    doctorName: '赵磊',
    doctorTitle: '副主任医师',
    doctorIntroduction: '正畸与牙周联合治疗方向，擅长成人复杂病例的跨学科方案设计',
    departmentName: '牙周治疗',
    diagnosis: '全口牙龈红肿，探诊出血（BOP）阳性，下前牙区牙周袋深4-5mm',
    treatmentPlan: '分两次完成全口龈下刮治，1个月后复查牙周袋深度与出血情况',
    remark: '刮治后24小时内避免进食过冷过热食物',
    status: 'completed',
    arrivedAt: datetimeAt(-33, '09:50:00'),
    completedAt: datetimeAt(-33, '11:05:00'),
    consumptionAmount: 800,
    totalAmount: 800,
    consumptionDetails: [
      { id: 121, amount: 800, balanceUsed: 500, wechatPaid: 300, pointsEarned: 8, description: '全口龈下刮治（首次）', createdAt: datetimeAt(-33, '11:05:00') }
    ]
  }
]

/** 列表项由详情派生，保证列表与详情的数据一致 */
const medicalRecords: MedicalRecord[] = medicalRecordDetails.map(item => ({
  id: item.id,
  appointmentDate: item.appointmentDate,
  startTime: item.startTime,
  endTime: item.endTime,
  clinicName: item.clinicName,
  doctorName: item.doctorName,
  doctorTitle: item.doctorTitle,
  departmentName: item.departmentName,
  diagnosis: item.diagnosis,
  remark: item.remark,
  consumptionAmount: item.consumptionAmount,
  completedAt: item.completedAt
}))

// ==================== 消费记录 ====================

interface MockConsumptionRecord {
  id: number
  amount: number
  balanceUsed: number
  wechatPaid: number
  pointsEarned: number
  pointsUsed: number
  pointsDeduct: number
  description: string
  clinicName: string
  appointmentDate: string
  doctorName: string
  departmentName: string
  createdAt: string
}

/** 就诊消费明细自动转成消费记录，另补几条无就诊记录的日常消费，保证钱包统计与就诊记录口径一致 */
const consumptionRecords: MockConsumptionRecord[] = [
  ...medicalRecordDetails.flatMap(record =>
    record.consumptionDetails.map(detail => ({
      id: detail.id,
      amount: detail.amount,
      balanceUsed: detail.balanceUsed,
      wechatPaid: detail.wechatPaid,
      pointsEarned: detail.pointsEarned,
      pointsUsed: 0,
      pointsDeduct: 0,
      description: detail.description || record.departmentName || '',
      clinicName: record.clinicName,
      appointmentDate: record.appointmentDate,
      doctorName: record.doctorName,
      departmentName: record.departmentName || '',
      createdAt: detail.createdAt
    }))
  ),
  { id: 1001, amount: 298, balanceUsed: 298, wechatPaid: 0, pointsEarned: 2, pointsUsed: 0, pointsDeduct: 0, description: '洁牙套餐（含抛光）', clinicName: '牙科门诊（总店）', appointmentDate: dateOffset(-2), doctorName: '陈晓', departmentName: '洗牙', createdAt: datetimeAt(-2, '10:35:00') },
  { id: 1002, amount: 600, balanceUsed: 400, wechatPaid: 200, pointsEarned: 6, pointsUsed: 0, pointsDeduct: 0, description: '树脂充填（右上第一磨牙）', clinicName: '牙科门诊（分店A）', appointmentDate: dateOffset(-9), doctorName: '刘敏', departmentName: '补牙', createdAt: datetimeAt(-9, '15:20:00') },
  { id: 1003, amount: 200, balanceUsed: 200, wechatPaid: 0, pointsEarned: 2, pointsUsed: 0, pointsDeduct: 0, description: '儿童全口涂氟', clinicName: '儿童齿科中心（望京店）', appointmentDate: dateOffset(-16), doctorName: '孙悦', departmentName: '儿童齿科', createdAt: datetimeAt(-16, '11:10:00') },
  { id: 1004, amount: 300, balanceUsed: 300, wechatPaid: 0, pointsEarned: 3, pointsUsed: 0, pointsDeduct: 0, description: '正畸复诊调整', clinicName: '牙科门诊（总店）', appointmentDate: dateOffset(-25), doctorName: '李静', departmentName: '正畸', createdAt: datetimeAt(-25, '16:45:00') }
]

// ==================== 推荐 ====================

const referralCode = 'REF123456'
const shareLink = `https://mp.dental-clinic.example.com/invite?code=${referralCode}`

interface MockReferralRecord {
  id: number
  /** 页面读取的字段名 */
  refereeName: string
  /** api.ts 声明的字段名，两者同时下发以便联调比对 */
  referredPersonName: string
  registeredAt: string
  firstConsumptionAt?: string
  /** full=奖励已全额发放，partial=仅发放注册奖励，none=待发放 */
  rewardStatus: 'full' | 'partial' | 'none'
}

let referralRecords: MockReferralRecord[] = [
  { id: 1, refereeName: '李**', referredPersonName: '李**', registeredAt: datetimeAt(-5, '10:12:00'), firstConsumptionAt: datetimeAt(-3, '15:30:00'), rewardStatus: 'full' },
  { id: 2, refereeName: '王**', referredPersonName: '王**', registeredAt: datetimeAt(-12, '09:40:00'), firstConsumptionAt: datetimeAt(-11, '16:20:00'), rewardStatus: 'full' },
  { id: 3, refereeName: '赵**', referredPersonName: '赵**', registeredAt: datetimeAt(-18, '20:05:00'), firstConsumptionAt: datetimeAt(-16, '11:00:00'), rewardStatus: 'full' },
  { id: 4, refereeName: '孙**', referredPersonName: '孙**', registeredAt: datetimeAt(-8, '13:25:00'), rewardStatus: 'partial' },
  { id: 5, refereeName: '周**', referredPersonName: '周**', registeredAt: datetimeAt(-2, '19:48:00'), rewardStatus: 'partial' },
  { id: 6, refereeName: '吴**', referredPersonName: '吴**', registeredAt: datetimeAt(-1, '08:30:00'), rewardStatus: 'none' }
]

// ==================== 优惠券 ====================

interface MockCoupon {
  id: number
  couponCode: string
  /** 页面读取的字段名 */
  templateName: string
  faceValue: number
  minConsumption: number
  couponType: string
  validFrom: string
  validTo: string
  status: 'unused' | 'used' | 'expired'
}

const coupons: MockCoupon[] = [
  { id: 1, couponCode: 'NEW100', templateName: '新人券', faceValue: 100, minConsumption: 200, couponType: '全项目通用', validFrom: dateOffset(-30), validTo: dateOffset(45), status: 'unused' },
  { id: 2, couponCode: 'REF50', templateName: '推荐奖励券', faceValue: 50, minConsumption: 100, couponType: '洗牙、补牙', validFrom: dateOffset(-20), validTo: dateOffset(60), status: 'unused' },
  { id: 3, couponCode: 'BIRTH80', templateName: '生日券', faceValue: 80, minConsumption: 300, couponType: '全项目通用', validFrom: dateOffset(0), validTo: dateOffset(30), status: 'unused' },
  { id: 4, couponCode: 'ACT200', templateName: '暑期活动券', faceValue: 200, minConsumption: 500, couponType: '正畸、种植', validFrom: dateOffset(-60), validTo: dateOffset(-5), status: 'expired' },
  { id: 5, couponCode: 'VIP300', templateName: '会员日券', faceValue: 300, minConsumption: 800, couponType: '全项目通用', validFrom: dateOffset(-10), validTo: dateOffset(20), status: 'used' }
]

/** 推广领券模板：与后端「优惠券模板 - 推广码」对应，一次发放 2 张 */
const promoClaimTemplates = [
  { id: 1, name: '暑期推广券', prefix: 'PROMO1-', faceValue: 100, minConsumption: 300, couponType: '全项目通用', validDays: 30 },
  { id: 2, name: '正畸推广券', prefix: 'PROMO2-', faceValue: 500, minConsumption: 2000, couponType: '正畸', validDays: 45 }
]

/** 已领取过的推广模板（模拟服务端幂等：每人每模板限领一次） */
const claimedPromoTemplates = new Set<number>()

// ==================== 消息 ====================

interface MockMessage {
  id: number
  msgType: 'system' | 'consumption' | 'activity'
  title: string
  content: string
  isRead: boolean
  createdAt: string
}

let messages: MockMessage[] = [
  { id: 1, msgType: 'system', title: '预约提醒', content: '您今天09:30在牙科门诊（总店）有洗牙预约，到店后请在前台扫码签到', isRead: false, createdAt: datetimeAt(0, '08:00:00') },
  { id: 2, msgType: 'activity', title: '活动通知', content: '暑期正畸专场：隐形矫正立减2000元，推荐好友再得100积分', isRead: false, createdAt: datetimeAt(-2, '10:00:00') },
  { id: 3, msgType: 'system', title: '签到提醒', content: '每日签到可领2积分，连续签到奖励更丰富', isRead: false, createdAt: datetimeAt(-1, '09:00:00') },
  { id: 4, msgType: 'consumption', title: '消费通知', content: `您于${dateOffset(-6)} 09:40消费100元，余额支付100元，获得积分1分`, isRead: true, createdAt: datetimeAt(-6, '09:40:00') },
  { id: 5, msgType: 'consumption', title: '积分兑换', content: '您使用500积分兑换了超声波洁牙券，请到店出示核销码', isRead: true, createdAt: datetimeAt(-8, '20:30:00') },
  { id: 6, msgType: 'system', title: '预约提醒', content: '您在牙科门诊（分店A）的口腔检查预约已取消，如有需要可重新预约', isRead: true, createdAt: datetimeAt(-10, '11:30:00') },
  { id: 7, msgType: 'consumption', title: '充值到账', content: '充值3000元已到账，赠送450元，赠送金额可用于全项目消费', isRead: true, createdAt: datetimeAt(-30, '10:31:00') },
  { id: 8, msgType: 'activity', title: '会员日', content: '每月8日会员日，洁牙8折并赠送口腔检查一次', isRead: true, createdAt: datetimeAt(-38, '09:00:00') }
]

// ==================== 首页内容 ====================

const banners: Banner[] = [
  {
    id: 1,
    title: '暑期正畸特惠 · 隐形矫正立减2000元',
    imageUrl: '/images/banner-ortho.jpg',
    activityId: 1,
    linkType: 'none',
    linkTarget: ''
  },
  {
    id: 2,
    title: '会员充值 · 最高赠送25%',
    imageUrl: '/images/banner-recharge.jpg',
    activityId: 2,
    linkType: 'none',
    linkTarget: ''
  },
  {
    id: 3,
    title: '新客专享100元口腔检查券',
    imageUrl: '/images/banner-newcomer.jpg',
    activityId: 3,
    linkType: 'none',
    linkTarget: ''
  }
]

/** 活动详情：与 banners 的 activityId 一一对应 */
const activities: ActivityDetail[] = [
  {
    id: 1,
    title: '暑期正畸特惠 · 隐形矫正立减2000元',
    summary: '暑期矫正专场，隐形矫正立减 2000 元，含免费拍片与方案设计',
    content: '活动时间：7 月 1 日 - 8 月 31 日\n\n适用项目：隐形矫正、金属自锁托槽\n\n到院可享免费口腔检查与拍片，方案设计不收费。\n\n本活动不与门店其他优惠同享。',
    startAt: datetimeAt(-30, '00:00:00'),
    endAt: datetimeAt(30, '23:59:59')
  },
  {
    id: 2,
    title: '会员充值 · 最高赠送25%',
    summary: '充值 3000 元赠 750 元，余额可用于全项目消费',
    content: '充值档位：\n1000 元赠 150 元\n3000 元赠 750 元\n5000 元赠 1250 元\n\n赠送金额与本金合并使用，不可提现。',
    startAt: datetimeAt(-60, '00:00:00'),
    endAt: datetimeAt(60, '23:59:59')
  },
  {
    id: 3,
    title: '新客专享100元口腔检查券',
    summary: '首次到院的新客可领取口腔检查券一张',
    content: '凭券可享一次全面口腔检查，含内窥镜拍照与医生面诊。\n\n每位新客限领一次，需提前预约。',
    startAt: datetimeAt(-10, '00:00:00'),
    endAt: datetimeAt(90, '23:59:59')
  }
]

const notices: Notice[] = [
  { id: 1, content: '温馨提示：就诊当天请在前台扫码签到，可显著减少等待时间', linkType: 'none', linkTarget: '' },
  { id: 2, content: '暑期正畸专场开启，免费拍片 + 方案设计，点击立即预约', linkType: 'page', linkTarget: '/pages/appointment/appointment' },
  { id: 3, content: '推荐好友注册，双方各得100积分，多推多得', linkType: 'page', linkTarget: '/pages/referral/referral' }
]

/** 订阅消息模板：mock 环境下 wx.requestSubscribeMessage 不会真的弹出，仅用于验证下发链路 */
const subscribeTemplates = [
  { templateKey: 'appointment_remind_evening', templateId: 'MOCK_TEMPLATE_ID_APPOINTMENT_EVENING' },
  { templateKey: 'appointment_remind_morning', templateId: 'MOCK_TEMPLATE_ID_APPOINTMENT_MORNING' }
]

// ==================== 状态变更辅助 ====================

function addPoints(changeAmount: number, sourceType: string, description: string, expiresAt = dateOffset(365)) {
  pointsRecords.unshift({
    id: nextId(pointsRecords),
    changeAmount,
    sourceType,
    description,
    expiresAt,
    createdAt: now()
  })
  pointsBalance.totalBalance += changeAmount
  pointsBalance.updatedAt = now()
}

function addMessage(msgType: MockMessage['msgType'], title: string, content: string) {
  messages.unshift({
    id: nextId(messages),
    msgType,
    title,
    content,
    isRead: false,
    createdAt: now()
  })
}

// ==================== API 服务类 ====================

class MockApiService {
  async call(path: string, method: string, data: any = {}): Promise<any> {
    // ---------- 认证 ----------
    if (path === '/auth/wechat-login' && method === 'POST') {
      return this.wechatLogin(data)
    }
    if (path === '/auth/refresh' && method === 'POST') {
      return { accessToken: `mock_jwt_token_${Date.now()}` }
    }

    // ---------- 用户资料 ----------
    if (path === '/profile/me' && method === 'GET') {
      return this.getProfile()
    }
    if (path === '/profile/me' && method === 'PUT') {
      return this.updateProfile(data)
    }
    if (path === '/profile/export' && method === 'GET') {
      return this.exportProfile()
    }
    if (path === '/profile/me' && method === 'DELETE') {
      return null
    }

    // ---------- 首页内容 ----------
    if (path === '/content/home' && method === 'GET') {
      return { banners, notices }
    }
    if (path.startsWith('/content/activities/') && method === 'GET') {
      const id = Number(path.split('/').pop())
      return activities.find((item) => item.id === id) || null
    }
    if (path === '/content/subscribe-templates' && method === 'GET') {
      return subscribeTemplates
    }

    // ---------- 门诊 / 科室 / 医生 / 时段 ----------
    if (path === '/clinics' && method === 'GET') {
      return clinics
    }
    if (path === '/departments' && method === 'GET') {
      return departments
    }
    if (path === '/doctors' && method === 'GET') {
      return this.getDoctors(data)
    }
    if (path === '/appointments/slots' && method === 'GET') {
      return this.getSlots(data)
    }

    // ---------- 预约 ----------
    if ((path === '/appointments' || path === '/appointments/my') && method === 'GET') {
      const list = data.status
        ? sortAppointments(appointments).filter(item => item.status === data.status)
        : sortAppointments(appointments)
      return paginate(list, data.page, data.pageSize)
    }
    if (path === '/appointments' && method === 'POST') {
      return this.createAppointment(data)
    }

    let matched = path.match(/^\/appointments\/(\d+)\/cancel$/)
    if (matched && method === 'PUT') {
      return this.cancelAppointment(Number(matched[1]))
    }

    matched = path.match(/^\/appointments\/(\d+)\/reschedule$/)
    if (matched && method === 'PUT') {
      return this.rescheduleAppointment(Number(matched[1]), data)
    }

    matched = path.match(/^\/appointments\/(\d+)\/checkin$/)
    if (matched && method === 'POST') {
      return this.checkinAppointment(Number(matched[1]), data)
    }

    // ---------- 钱包 / 充值 ----------
    if (path === '/wallet/details' && method === 'GET') {
      return {
        balance: wallet.balance,
        bonusBalance: wallet.bonusBalance,
        totalBalance: wallet.balance + wallet.bonusBalance,
        version: 1,
        rechargeHistory: rechargeRecords
      }
    }
    if (path === '/recharge/tiers' && method === 'GET') {
      return RECHARGE_RULES.slice().reverse().map(rule => ({
        amount: rule.min,
        bonus: Math.round(rule.min * rule.rate),
        rate: `${rule.rate * 100}%`
      }))
    }
    if (path === '/recharge/create' && method === 'POST') {
      return this.createRecharge(data)
    }

    matched = path.match(/^\/recharge\/(\d+)\/status$/)
    if (matched && method === 'GET') {
      return this.getRechargeStatus(Number(matched[1]))
    }

    // ---------- 消费记录 ----------
    if (path === '/consumption/records' && method === 'GET') {
      return this.getConsumptionRecords(data)
    }
    if (path === '/consumption/statistics' && method === 'GET') {
      return this.getConsumptionStatistics()
    }

    // ---------- 积分 ----------
    if (path === '/points/balance' && method === 'GET') {
      return { totalBalance: pointsBalance.totalBalance, updatedAt: pointsBalance.updatedAt }
    }
    if (path === '/points/summary' && method === 'GET') {
      return this.getPointsSummary()
    }
    if (path === '/points/records' && method === 'GET') {
      return paginate(pointsRecords, data.page, data.pageSize)
    }
    if (path === '/points/checkin' && method === 'POST') {
      return this.checkin()
    }
    if (path === '/points/profile-complete' && method === 'POST') {
      return this.claimProfileCompleteReward()
    }

    // ---------- 积分商城 ----------
    if (path === '/points/mall/goods' && method === 'GET') {
      return pointsGoods.slice().sort((a, b) => a.sortOrder - b.sortOrder)
    }
    if (path === '/points/mall/exchange' && method === 'POST') {
      return this.exchangeGoods(data)
    }
    if (path === '/points/mall/orders' && method === 'GET') {
      return exchangeOrders
    }

    // ---------- 推荐 ----------
    if (path === '/referral/code' && method === 'GET') {
      return { referralCode, shareLink }
    }
    if (path === '/referral/records' && method === 'GET') {
      return paginate(referralRecords, data.page, data.pageSize)
    }
    if (path === '/referral/register' && method === 'POST') {
      return this.registerWithReferral(data)
    }

    // ---------- 优惠券 ----------
    if (path === '/coupons' && method === 'GET') {
      const list = data.status ? coupons.filter(item => item.status === data.status) : coupons
      return list
    }
    if (path === '/coupons/redeem' && method === 'POST') {
      return this.redeemCoupon(data)
    }
    if (path === '/coupons/promo-claim' && method === 'POST') {
      return this.claimPromoCoupons(data)
    }

    // ---------- 消息 ----------
    if (path === '/messages' && method === 'GET') {
      return this.getMessages(data)
    }
    if (path === '/messages/unread-count' && method === 'GET') {
      return { count: messages.filter(item => !item.isRead).length }
    }

    matched = path.match(/^\/messages\/(\d+)\/read$/)
    const readMatched = matched
    if (readMatched && method === 'PUT') {
      const message = messages.find(item => item.id === Number(readMatched[1]))
      if (message) message.isRead = true
      return { success: true }
    }
    if (path === '/messages/read-all' && method === 'PUT') {
      messages.forEach(item => { item.isRead = true })
      return { success: true }
    }

    // ---------- 就诊记录 ----------
    if (path === '/medical-records' && method === 'GET') {
      const keyword = String(data.keyword || '').trim()
      const list = keyword
        ? medicalRecords.filter(item =>
            [item.clinicName, item.doctorName, item.departmentName, item.diagnosis]
              .some(field => (field || '').includes(keyword)))
        : medicalRecords
      return paginate(list, data.page, data.pageSize)
    }

    matched = path.match(/^\/medical-records\/(\d+)$/)
    if (matched && method === 'GET') {
      const record = medicalRecordDetails.find(item => item.id === Number(matched[1]))
      if (!record) return { code: 4044, message: '就诊记录不存在' }
      return record
    }

    throw new Error(`未实现的API: ${method} ${path}`)
  }

  // ---------------- 认证 / 资料 ----------------

  private wechatLogin(data: any) {
    // 携带手机号授权时视为新用户，便于演示 /profile/me 的自动拉取
    const isNewUser = !!data?.phoneCode
    // 手机号授权登录后回带微信手机号；未授权则不回带（页面手机号置空、按兜底逻辑展示）
    const phone = data?.phoneCode ? currentUser.phone : ''
    return {
      userId: currentUser.id,
      accessToken: `mock_jwt_token_${Date.now()}`,
      refreshToken: `mock_refresh_token_${Date.now()}`,
      isNewUser,
      phone
    }
  }

  private getProfile() {
    const totalSpent = currentUser.totalSpent || 0
    const level = levelOf(totalSpent)
    return {
      userId: currentUser.id,
      openid: 'mock_openid_0001',
      phone: currentUser.phone,
      nickname: currentUser.nickname || currentUser.name,
      realName: currentUser.name,
      gender: currentUser.gender === 'female' ? 2 : 1,
      age: currentUser.age,
      avatar: currentUser.avatar,
      primaryClinicId: currentUser.clinicId,
      balance: wallet.balance,
      bonusBalance: wallet.bonusBalance,
      levelCode: level.level,
      levelName: level.name,
      discountRate: level.discount,
      benefits: level.benefits,
      accumulatedConsumption: totalSpent,
      nextLevelCost: nextLevelCostOf(totalSpent)
    }
  }

  private updateProfile(data: any) {
    if (data.realName !== undefined) currentUser.name = data.realName
    if (data.nickname !== undefined) currentUser.nickname = data.nickname
    // 手机号绑定微信登录手机号，编辑资料页只读，这里即便收到也忽略，避免被手动覆盖
    if (data.gender !== undefined) currentUser.gender = Number(data.gender) === 2 ? 'female' : 'male'
    if (data.age !== undefined) currentUser.age = Number(data.age)
    if (data.avatar !== undefined) currentUser.avatar = data.avatar
    // 后端只回 code/message，页面需重新 GET /profile/me 获取最新资料
    return null
  }

  private exportProfile() {
    const totalSpent = currentUser.totalSpent || 0
    return {
      userId: currentUser.id,
      nickname: currentUser.nickname,
      realName: currentUser.name,
      phone: currentUser.phone,
      gender: currentUser.gender === 'female' ? 2 : 1,
      age: currentUser.age,
      avatar: currentUser.avatar,
      sourceType: currentUser.registerSource || 'self',
      registeredAt: datetimeAt(-180, '10:00:00'),
      balance: wallet.balance,
      bonusBalance: wallet.bonusBalance,
      levelName: levelOf(totalSpent).name,
      accumulatedConsumption: totalSpent,
      totalPoints: pointsBalance.totalBalance,
      exportedAt: now()
    }
  }

  // ---------------- 门诊 / 医生 / 时段 ----------------

  private getDoctors(data: any) {
    let list: MockDoctor[] = doctors
    if (data?.clinicId) {
      list = list.filter(item => item.clinicIds.includes(Number(data.clinicId)))
    }
    if (data?.departmentId) {
      list = list.filter(item => item.departmentIds.includes(Number(data.departmentId)))
    }
    return list
  }

  private getSlots(data: any): MockTimeSlot[] {
    const doctorId = Number(data?.doctorId || 0)
    const dates: string[] = []
    if (data?.date) {
      dates.push(String(data.date))
    } else {
      for (let i = 0; i < 7; i++) dates.push(dateOffset(i))
    }

    const slots: MockTimeSlot[] = []
    dates.forEach(date => {
      MORNING_SLOTS.concat(AFTERNOON_SLOTS).forEach(range => {
        const parts = range.split('-')
        const remaining = remainingOf(doctorId, date, parts[0])
        slots.push({
          date,
          startTime: parts[0],
          endTime: parts[1],
          remaining,
          maxAppointments: MAX_PER_SLOT,
          available: remaining > 0
        })
      })
    })
    return slots
  }

  // ---------------- 预约 ----------------

  private createAppointment(data: any) {
    const clinicId = Number(data?.clinicId)
    const doctorId = Number(data?.doctorId)
    const departmentId = Number(data?.departmentId)
    const appointmentDate = String(data?.appointmentDate || '')
    const startTime = String(data?.startTime || '')

    if (!clinicId || !doctorId || !departmentId || !appointmentDate || !startTime) {
      return { code: 4001, message: '请选择门诊、医生、科室与就诊时段' }
    }
    if (remainingOf(doctorId, appointmentDate, startTime) <= 0) {
      return { code: 4002, message: '该时段已约满，请选择其他时间' }
    }

    const appointment: MockAppointment = {
      id: nextId(appointments),
      clinicId,
      clinicName: clinics.find(item => item.id === clinicId)?.name || '',
      doctorId,
      doctorName: doctors.find(item => item.id === doctorId)?.name || '',
      departmentId,
      departmentName: departments.find(item => item.id === departmentId)?.name || '',
      appointmentDate,
      startTime,
      endTime: startTime,
      status: 'pending',
      remark: data?.remark || '',
      createdAt: now()
    }
    appointments.unshift(appointment)
    addMessage('system', '预约提交成功', `您已预约${appointment.appointmentDate} ${startTime} ${appointment.departmentName}，请等待门店确认`)
    return appointment
  }

  private cancelAppointment(id: number) {
    const appointment = appointments.find(item => item.id === id)
    if (!appointment) return { code: 4041, message: '预约不存在' }
    if (appointment.status !== 'pending' && appointment.status !== 'confirmed') {
      return { code: 4003, message: '当前状态不可取消' }
    }
    appointment.status = 'cancelled'
    return { success: true, status: 'cancelled' }
  }

  private rescheduleAppointment(id: number, data: any) {
    const appointment = appointments.find(item => item.id === id)
    if (!appointment) return { code: 4041, message: '预约不存在' }
    if (appointment.status !== 'pending' && appointment.status !== 'confirmed') {
      return { code: 4004, message: '当前状态不可改约' }
    }
    const appointmentDate = String(data?.appointmentDate || '')
    const startTime = String(data?.startTime || '')
    if (!appointmentDate || !startTime) return { code: 4005, message: '请选择新的就诊时间' }
    if (remainingOf(appointment.doctorId, appointmentDate, startTime) <= 0) {
      return { code: 4002, message: '该时段已约满，请选择其他时间' }
    }
    appointment.appointmentDate = appointmentDate
    appointment.startTime = startTime
    return appointment
  }

  private checkinAppointment(id: number, data: any) {
    const appointment = appointments.find(item => item.id === id)
    if (!appointment) return { code: 4041, message: '预约不存在' }
    if (appointment.status !== 'pending' && appointment.status !== 'confirmed') {
      return { code: 4006, message: '当前状态无需签到' }
    }
    if (appointment.appointmentDate !== dateOffset(0)) {
      return { code: 4007, message: '仅就诊当天可扫码签到' }
    }

    // 门店签到码格式：dc1.<clinicId>.<签名>
    const code = String(data?.code || '').trim()
    const matched = /^dc1\.(\d+)\.[A-Za-z0-9]+$/.exec(code)
    if (!matched) {
      return { code: 4008, message: '无效的门店码，请扫描门店前台二维码' }
    }
    if (Number(matched[1]) !== appointment.clinicId) {
      return { code: 4009, message: '扫码门店与预约门店不一致' }
    }

    appointment.status = 'arrived'
    appointment.arrivedAt = now()
    addMessage('system', '签到成功', `您已在${appointment.clinicName}完成签到，请等待叫号`)
    return { success: true, status: 'arrived' }
  }

  // ---------------- 钱包 / 充值 ----------------

  private createRecharge(data: any) {
    const amount = Number(data?.amount)
    if (!amount || amount < 100) {
      return { code: 4001, message: '最低充值100元' }
    }

    const bonusAmount = calcBonus(amount)
    const pointsEarned = Math.floor(amount / 100)
    const record: MockRechargeRecord = {
      id: nextId(rechargeRecords),
      amount,
      bonusAmount,
      status: 'success',
      paidAt: now(),
      createdAt: now(),
      pointsEarned
    }
    rechargeRecords.unshift(record)

    wallet.balance += amount
    wallet.bonusBalance += bonusAmount
    currentUser.balance = wallet.balance
    currentUser.bonusBalance = wallet.bonusBalance

    if (pointsEarned > 0) {
      addPoints(pointsEarned, '充值积分', `充值${amount}元`)
    }
    addMessage('consumption', '充值到账', `充值${amount}元已到账，赠送${bonusAmount}元，获得${pointsEarned}积分`)

    // mock 模式下没有真实微信支付：下单即到账（status=success），页面直接走成功分支
    return {
      recordId: record.id,
      status: 'success',
      mock: true,
      amount,
      bonusAmount,
      totalAdded: amount + bonusAmount,
      pointsEarned,
      paidAt: record.paidAt,
      payParams: null
    }
  }

  private getRechargeStatus(recordId: number) {
    const record = rechargeRecords.find(item => item.id === recordId)
    if (!record) return { code: 4042, message: '充值订单不存在' }
    return {
      recordId: record.id,
      status: record.status,
      amount: record.amount,
      bonusAmount: record.bonusAmount,
      totalAdded: record.status === 'success' ? record.amount + record.bonusAmount : null,
      pointsEarned: record.pointsEarned,
      paidAt: record.paidAt
    }
  }

  // ---------------- 消费记录 ----------------

  private getConsumptionRecords(data: any) {
    const startDate = String(data?.startDate || '')
    const endDate = String(data?.endDate || '')
    const list = consumptionRecords
      .filter(item => (!startDate || item.createdAt >= startDate)
        && (!endDate || item.createdAt <= `${endDate} 23:59:59`))
      .slice()
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return paginate(list, data?.page, data?.pageSize)
  }

  private getConsumptionStatistics() {
    const monthPrefix = dateOffset(0).substring(0, 7)
    const monthList = consumptionRecords.filter(item => item.createdAt.startsWith(monthPrefix))
    const sum = (list: MockConsumptionRecord[]) => list.reduce((total, item) => total + item.amount, 0)
    return {
      totalAmount: sum(consumptionRecords),
      totalRecords: consumptionRecords.length,
      monthAmount: sum(monthList),
      monthRecords: monthList.length
    }
  }

  // ---------------- 积分 ----------------

  private checkin() {
    const today = dateOffset(0)
    if (lastCheckinDate === today) {
      return { code: 4010, message: '今日已签到，明天再来' }
    }
    lastCheckinDate = today
    addPoints(2, '签到积分', '每日签到')
    return { pointsEarned: 2 }
  }

  private claimProfileCompleteReward() {
    if (profileRewardClaimed) {
      return { code: 4011, message: '完善资料奖励已领取' }
    }
    profileRewardClaimed = true
    addPoints(20, '完善资料', '完善个人资料奖励')
    return { pointsEarned: 20 }
  }

  private getPointsSummary() {
    const monthPrefix = dateOffset(0).substring(0, 7)
    let monthlyEarned = 0
    let monthlySpent = 0
    const expiryMap = new Map<string, number>()
    pointsRecords.forEach(item => {
      if (item.createdAt.startsWith(monthPrefix)) {
        if (item.changeAmount > 0) monthlyEarned += item.changeAmount
        else monthlySpent += Math.abs(item.changeAmount)
      }
      // 有效期分布只统计仍有效的获得积分，按过期月份升序
      if (item.changeAmount > 0 && item.expiresAt) {
        const expireMonth = item.expiresAt.substring(0, 7)
        if (expireMonth >= monthPrefix) {
          expiryMap.set(expireMonth, (expiryMap.get(expireMonth) || 0) + item.changeAmount)
        }
      }
    })
    const expiryGroups = Array.from(expiryMap.entries())
      .map(([expireMonth, points]) => ({ expireMonth, points }))
      .sort((a, b) => a.expireMonth.localeCompare(b.expireMonth))
    return { totalBalance: pointsBalance.totalBalance, monthlyEarned, monthlySpent, expiryGroups }
  }

  private exchangeGoods(data: any) {
    const goodsId = Number(data?.goodsId)
    const goods = pointsGoods.find(item => item.id === goodsId)
    if (!goods) return { code: 4043, message: '商品不存在或已下架' }
    if (goods.remainingStock === 0) return { code: 4012, message: '该商品已兑换完' }
    if (pointsBalance.totalBalance < goods.pointsPrice) return { code: 4013, message: '积分不足' }

    if (goods.remainingStock > 0) goods.remainingStock -= 1
    addPoints(-goods.pointsPrice, '积分兑换', `兑换${goods.name}`)

    const order: MockExchangeOrder = {
      id: nextId(exchangeOrders),
      goodsId: goods.id,
      goodsName: goods.name,
      pointsCost: goods.pointsPrice,
      redeemCode: `PE${Date.now()}`,
      status: 'unused',
      redeemedAt: null,
      createdAt: now()
    }
    exchangeOrders.unshift(order)
    addMessage('consumption', '积分兑换成功', `您使用${goods.pointsPrice}积分兑换了${goods.name}，核销码 ${order.redeemCode}`)
    return order
  }

  // ---------------- 推荐 / 优惠券 ----------------

  private registerWithReferral(data: any) {
    const code = String(data?.referralCode || '').trim()
    if (!code) return { code: 4001, message: '推荐码不能为空' }
    if (code === referralCode) return { code: 4014, message: '不能使用自己的推荐码' }
    if (!/^REF\d{6}$/.test(code)) return { code: 4015, message: '推荐码无效' }
    return { success: true }
  }

  private redeemCoupon(data: any) {
    const code = String(data?.couponCode || '').trim()
    const coupon = coupons.find(item => item.couponCode === code)
    if (!coupon) return { code: 4044, message: '券码不存在' }
    if (coupon.status === 'used') return { code: 4016, message: '该券已使用' }
    if (coupon.status === 'expired') return { code: 4017, message: '该券已过期' }
    coupon.status = 'used'
    return { success: true, couponId: coupon.id, faceValue: coupon.faceValue }
  }

  /** 推广领券：一次发 2 张，同一模板重复扫码返回「已领取」 */
  private claimPromoCoupons(data: any) {
    const templateId = Number(data?.templateId)
    const template = promoClaimTemplates.find(item => item.id === templateId)
    if (!template) return { code: 4045, message: '推广活动不存在或已结束' }
    if (claimedPromoTemplates.has(templateId)) {
      return { code: 4018, message: '该活动券已领取，请勿重复扫码' }
    }

    claimedPromoTemplates.add(templateId)
    const claimed = [0, 1].map(index => {
      const coupon: MockCoupon = {
        id: nextId(coupons),
        couponCode: `${template.prefix}${Date.now()}${index + 1}`,
        templateName: template.name,
        faceValue: template.faceValue,
        minConsumption: template.minConsumption,
        couponType: template.couponType,
        validFrom: dateOffset(0),
        validTo: dateOffset(template.validDays),
        status: 'unused'
      }
      coupons.unshift(coupon)
      return {
        couponCode: coupon.couponCode,
        couponName: coupon.templateName,
        faceValue: coupon.faceValue,
        validTo: coupon.validTo
      }
    })
    addMessage('activity', '推广券到账', `您已领取「${template.name}」2张，请在有效期内使用`)
    return { coupons: claimed }
  }

  // ---------------- 消息 ----------------

  private getMessages(data: any) {
    let list = messages.slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    if (data?.msgType) {
      list = list.filter(item => item.msgType === data.msgType)
    }
    if (data?.isRead !== undefined && data?.isRead !== '') {
      list = list.filter(item => String(item.isRead) === String(data.isRead))
    }
    return paginate(list, data?.page, data?.pageSize)
  }
}

export const mockApiService = new MockApiService()
