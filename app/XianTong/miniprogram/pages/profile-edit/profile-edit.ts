// pages/profile-edit/profile-edit.ts
import {
  bindPhone,
  claimProfileCompleteReward,
  getClinics,
  getUserProfile,
  updateUserProfile
} from '../../utils/api'
import type { Clinic, UpdateProfileParams } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import { uploadFile } from '../../utils/request'
import { resolveFileUrl } from '../../utils/util'

interface UploadedFile {
  url: string
  fileName: string
  size: number
}

Page({
  data: {
    nickname: '',
    realName: '',
    phone: '',
    gender: null as number | null, // 1=男 2=女；未选择时不提交，避免误判资料已完善
    age: '' as number | string,
    // 存的是可直接渲染的绝对地址；提交给后端时要还原成后端返回的相对地址
    avatar: '',
    avatarUploading: false,
    submitting: false,

    // 归属门店：首次设置后锁定，后续如需变更联系门店
    primaryClinicId: null as number | null,
    primaryClinicName: '',
    primaryClinicLocked: false,
    clinics: [] as Clinic[],
    clinicNames: [] as string[],
    clinicIndex: 0
  },

  // 后端返回的原始头像地址（相对路径），提交时若用户没换图就原样回传，
  // 避免把补全主机名后的绝对地址写进库，导致换域名后图片全失效
  originalAvatar: '',

  async onLoad() {
    if (!checkLogin()) return
    await this.loadProfile()
    await this.loadClinics()
  },

  async loadProfile() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const d = result.data
        this.originalAvatar = d.avatar || ''

        // 手机号优先取微信登录时拿到的手机号（只读），其次用后端资料里的 phone
        const app = getApp<IAppOption>()
        const wechatPhone = app?.globalData?.wechatPhone || ''
        const phone = wechatPhone || d.phone || ''

        // 昵称优先服务端值，其次微信登录昵称，最后才用手机号后 4 位兜底
        const wechatNickname = app?.globalData?.wechatNickname || ''
        const nickname = d.nickname || wechatNickname || this.defaultNickname(phone)

        this.setData({
          nickname,
          realName: d.realName || '',
          phone,
          gender: d.gender ?? null,
          age: d.age ?? '',
          avatar: resolveFileUrl(d.avatar || ''),
          primaryClinicId: d.primaryClinicId ?? null,
          primaryClinicLocked: d.primaryClinicId != null
        })
      }
    } catch (error) {
      console.error('[ProfileEdit] 加载资料失败:', error)
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  async loadClinics() {
    try {
      const result = await getClinics()
      if (result.code !== 0 || !Array.isArray(result.data)) return
      const clinics = result.data
      const clinicNames = clinics.map(item => item.name)
      const primaryClinicId = this.data.primaryClinicId
      const matchedIndex = primaryClinicId == null
        ? -1
        : clinics.findIndex(item => item.id === primaryClinicId)
      this.setData({
        clinics,
        clinicNames,
        clinicIndex: matchedIndex >= 0 ? matchedIndex : 0,
        primaryClinicName:
          matchedIndex >= 0
            ? clinics[matchedIndex]?.name || ''
            : primaryClinicId != null
              ? ''
              : ''
      })
    } catch (error) {
      console.error('[ProfileEdit] 加载门店失败:', error)
    }
  },

  /** 昵称默认规则：手机号后 4 位；无手机号时回退微信用户名 */
  defaultNickname(phone: string): string {
    const trimmed = (phone || '').trim()
    if (/^1\d{10}$/.test(trimmed)) return trimmed.slice(-4)
    return ''
  },

  /**
   * 微信「头像填写能力」：<button open-type="chooseAvatar"> 的回调，
   * e.detail.avatarUrl 为临时文件路径（支持从微信头像选择），上传到服务端后存相对地址。
   * 这是新版基础库获取头像的推荐方式；旧版 wx.getUserProfile 已不再返回真实头像。
   */
  async onChooseAvatar(e: any) {
    if (this.data.avatarUploading) return
    const tempPath = e?.detail?.avatarUrl
    if (!tempPath) return

    try {
      this.setData({ avatarUploading: true })
      const upload = await uploadFile<UploadedFile>('/files/upload', tempPath)
      if (upload.code === 0 && upload.data?.url) {
        this.originalAvatar = upload.data.url
        this.setData({ avatar: resolveFileUrl(upload.data.url) })
      } else {
        wx.showToast({ title: upload.message || '上传失败', icon: 'none' })
      }
    } catch (error: any) {
      // 用户在选图面板点取消也会走到这里，不弹错误提示
      if (error?.errMsg?.includes('cancel')) return
      console.error('[ProfileEdit] 头像上传失败:', error)
      wx.showToast({ title: error?.message || '上传失败', icon: 'none' })
    } finally {
      this.setData({ avatarUploading: false })
    }
  },

  /**
   * 重新授权手机号：open-type="getPhoneNumber" 回调，把 code 交后端换取并落库。
   * 成功后同步全局态 wechatPhone 与本页展示；失败/取消给出轻提示，不影响其他资料编辑。
   */
  async onReauthorizePhone(e: any) {
    if (e?.detail?.errMsg !== 'getPhoneNumber:ok' || !e?.detail?.code) {
      console.warn('[ProfileEdit] 手机号授权未完成:', e?.detail?.errMsg)
      return
    }
    try {
      const result = await bindPhone(e.detail.code)
      const phone = result.data?.phone || ''
      if (phone) {
        const app = getApp<IAppOption>()
        app?.setWechatPhone(phone)
        this.setData({ phone })
        wx.showToast({ title: '手机号已更新', icon: 'success' })
      }
    } catch (error: any) {
      console.error('[ProfileEdit] 重新授权手机号失败:', error)
      wx.showToast({ title: error?.message || '授权失败，请重试', icon: 'none' })
    }
  },

  onNicknameInput(e: any) {
    this.setData({ nickname: e.detail.value })
  },

  onRealNameInput(e: any) {
    this.setData({ realName: e.detail.value })
  },

  onAgeInput(e: any) {
    this.setData({ age: e.detail.value })
  },

  onGenderChange(e: any) {
    this.setData({ gender: Number(e.detail.value) })
  },

  onClinicChange(e: any) {
    if (this.data.primaryClinicLocked) return
    const index = Number(e.detail.value)
    const clinic = this.data.clinics[index]
    if (!clinic) return
    this.setData({
      clinicIndex: index,
      primaryClinicId: clinic.id,
      primaryClinicName: clinic.name
    })
  },

  // 提交保存
  async onSave() {
    if (this.data.submitting) return

    // 手机号取自微信登录，只读、不上传；这里仅校验昵称/姓名/年龄
    const age = this.data.age
    if (age !== '' && (Number(age) < 0 || Number(age) > 150)) {
      wx.showToast({ title: '年龄需在 0-150 之间', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const payload: UpdateProfileParams = {
        nickname: this.data.nickname.trim() || undefined,
        realName: this.data.realName.trim() || undefined,
        age: age === '' ? undefined : Number(age),
        avatar: this.originalAvatar || undefined
      }
      if (this.data.gender !== null) payload.gender = this.data.gender
      if (this.data.primaryClinicId != null) payload.primaryClinicId = this.data.primaryClinicId

      // request.ts 已把 code !== 0 统一 reject，这里只需处理成功路径
      await updateUserProfile(payload)

      // 资料保存已成功；积分领取失败（条件不全/网络异常）不能回滚或阻断保存结果。
      let pointsEarned = 0
      try {
        const reward = await claimProfileCompleteReward()
        pointsEarned = Number(reward.data?.pointsEarned || 0)
      } catch (rewardError) {
        console.warn('[ProfileEdit] 完善资料积分领取失败:', rewardError)
      }

      wx.showToast({
        title: pointsEarned > 0 ? `资料已保存，积分+${pointsEarned}` : '保存成功',
        icon: pointsEarned > 0 ? 'none' : 'success',
        duration: pointsEarned > 0 ? 2200 : 1200
      })
      setTimeout(() => wx.navigateBack(), pointsEarned > 0 ? 2200 : 1200)
    } catch (error: any) {
      // 唯一约束冲突（409）等已由 request.ts reject 成带后端文案的 Error，直接展示即可
      wx.showToast({ title: error?.message || '保存失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
