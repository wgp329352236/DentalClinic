// pages/profile-edit/profile-edit.ts
import {
  bindPhone,
  claimProfileCompleteReward,
  getUserProfile,
  updateUserProfile
} from '../../utils/api'
import type { UpdateProfileParams } from '../../utils/api'
import { checkLogin } from '../../utils/auth'

Page({
  data: {
    nickname: '',
    realName: '',
    phone: '',
    gender: null as number | null, // 1=男 2=女；未选择时不提交，避免误判资料已完善
    age: '' as number | string,
    // 头像只存本地：本地头像 > 微信登录头像 > 服务端历史头像 > 默认图
    avatar: '',
    // 未设置过本地头像时展示「选择头像」入口；设置后锁定只读
    canPickAvatar: false,
    submitting: false
  },

  async onLoad() {
    if (!checkLogin()) return
    await this.loadProfile()
  },

  async loadProfile() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const d = result.data
        const app = getApp<IAppOption>()

        // 手机号优先取微信登录时拿到的手机号（只读），其次用后端资料里的 phone
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
          avatar: app.getAvatarUrl(d.avatar || ''),
          canPickAvatar: !app.globalData.localAvatarPath
        })
      }
    } catch (error) {
      console.error('[ProfileEdit] 加载资料失败:', error)
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  /** 昵称默认规则：手机号后 4 位；无手机号时回退微信用户名 */
  defaultNickname(phone: string): string {
    const trimmed = (phone || '').trim()
    if (/^1\d{10}$/.test(trimmed)) return trimmed.slice(-4)
    return ''
  },

  /**
   * 微信「头像选择能力」：仅在未设置过本地头像时可用。
   *
   * 选中的临时文件保存到小程序本地文件系统，路径写入本地缓存，**不上传服务器**；
   * 设置后头像行变为只读，不允许再次修改。
   */
  async onChooseAvatar(e: any) {
    const app = getApp<IAppOption>()
    if (app.globalData.localAvatarPath) return
    const tempPath = e?.detail?.avatarUrl
    if (!tempPath) return

    try {
      const savedPath = await this.saveAvatarToLocal(tempPath)
      app.setLocalAvatarPath(savedPath)
      // 同步全局用户信息，首页/我的页返回时立即显示新头像
      const current = app.globalData.userInfo
      if (current) app.setUserInfo({ ...current, avatar: savedPath })
      this.setData({ avatar: savedPath, canPickAvatar: false })
      wx.showToast({ title: '头像已设置', icon: 'success' })
    } catch (error: any) {
      // 用户在选图面板点取消也会走到这里，不弹错误提示
      if (error?.errMsg?.includes('cancel')) return
      console.error('[ProfileEdit] 头像保存失败:', error)
      wx.showToast({ title: '头像设置失败，请重试', icon: 'none' })
    }
  },

  /** 把 chooseAvatar 返回的临时文件持久化到小程序本地目录（不经过服务器） */
  saveAvatarToLocal(tempPath: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const filePath = `${wx.env.USER_DATA_PATH}/avatar_${Date.now()}.jpg`
      wx.getFileSystemManager().saveFile({
        tempFilePath: tempPath,
        filePath,
        success: (res: any) => resolve(res.savedFilePath || filePath),
        fail: reject
      })
    })
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

  /**
   * 保存后回读校验（仅开发版/体验版执行）：
   * 重新拉取服务端资料，比对本次提交的昵称是否真正落库。
   * 头像只存本地、不上传服务器，故不参与回读比对。
   * 用于验证「授权 → 上报 → 落库」闭环，不一致只打日志告警，不影响已成功的保存结果。
   */
  async verifyProfileSaved(submitted: UpdateProfileParams) {
    try {
      const { data } = await getUserProfile()
      if (submitted.nickname && data.nickname !== submitted.nickname) {
        console.error(
          `[ProfileEdit] 资料回读不一致：昵称 提交=${submitted.nickname} 服务端=${data.nickname ?? 'null'}`
        )
      } else {
        console.log('[ProfileEdit] 资料回读一致：', { nickname: data.nickname })
      }
    } catch (error) {
      // 回读失败只说明校验没做成，不能反推保存失败
      console.warn('[ProfileEdit] 资料回读校验失败：', error)
    }
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
      // 头像只存本地，提交时不再携带 avatar
      const payload: UpdateProfileParams = {
        nickname: this.data.nickname.trim() || undefined,
        realName: this.data.realName.trim() || undefined,
        age: age === '' ? undefined : Number(age)
      }
      if (this.data.gender !== null) payload.gender = this.data.gender

      // request.ts 已把 code !== 0 统一 reject，这里只需处理成功路径
      await updateUserProfile(payload)

      // 非线上版做一次回读校验：确认昵称已真正落库（线上版跳过，避免多余请求）
      if (wx.getAccountInfoSync().miniProgram.envVersion !== 'release') {
        await this.verifyProfileSaved(payload)
      }

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
