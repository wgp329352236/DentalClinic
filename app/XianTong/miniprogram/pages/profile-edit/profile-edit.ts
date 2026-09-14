// pages/profile-edit/profile-edit.ts
import { getUserProfile, updateUserProfile } from '../../utils/api'
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
    gender: 1, // 1=男 2=女
    age: '' as number | string,
    // 存的是可直接渲染的绝对地址；提交给后端时要还原成后端返回的相对地址
    avatar: '',
    avatarUploading: false,
    submitting: false
  },

  // 后端返回的原始头像地址（相对路径），提交时若用户没换图就原样回传，
  // 避免把补全主机名后的绝对地址写进库，导致换域名后图片全失效
  originalAvatar: '',

  onLoad() {
    if (!checkLogin()) return
    this.loadProfile()
  },

  async loadProfile() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const d = result.data
        this.originalAvatar = d.avatar || ''
        this.setData({
          nickname: d.nickname || '',
          realName: d.realName || '',
          phone: d.phone || '',
          gender: d.gender || 1,
          age: d.age ?? '',
          avatar: resolveFileUrl(d.avatar)
        })
      }
    } catch (error) {
      console.error('[ProfileEdit] 加载资料失败:', error)
      wx.showToast({ title: '加载失败', icon: 'none' })
    }
  },

  // 选择并上传头像
  async onChooseAvatar() {
    if (this.data.avatarUploading) return
    try {
      const res = await wx.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sizeType: ['compressed'],
        sourceType: ['album', 'camera']
      })
      const filePath = res.tempFiles[0]?.tempFilePath
      if (!filePath) return

      this.setData({ avatarUploading: true })
      const upload = await uploadFile<UploadedFile>('/files/upload', filePath)
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

  onNicknameInput(e: any) {
    this.setData({ nickname: e.detail.value })
  },

  onRealNameInput(e: any) {
    this.setData({ realName: e.detail.value })
  },

  onPhoneInput(e: any) {
    this.setData({ phone: e.detail.value })
  },

  onAgeInput(e: any) {
    this.setData({ age: e.detail.value })
  },

  onGenderChange(e: any) {
    this.setData({ gender: Number(e.detail.value) })
  },

  // 提交保存
  async onSave() {
    if (this.data.submitting) return

    const phone = this.data.phone.trim()
    // 前端先校验格式，省一次往返；手机号是否被占用由后端唯一索引判定并回 409 文案
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
      wx.showToast({ title: '手机号格式不正确', icon: 'none' })
      return
    }
    const age = this.data.age
    if (age !== '' && (Number(age) < 0 || Number(age) > 150)) {
      wx.showToast({ title: '年龄需在 0-150 之间', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      // request.ts 已把 code !== 0 统一 reject，这里只需处理成功路径
      await updateUserProfile({
        nickname: this.data.nickname.trim() || undefined,
        realName: this.data.realName.trim() || undefined,
        phone: phone || undefined,
        gender: this.data.gender,
        age: age === '' ? undefined : Number(age),
        avatar: this.originalAvatar || undefined
      })
      wx.showToast({ title: '保存成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (error: any) {
      // 唯一约束冲突（409）等已由 request.ts reject 成带后端文案的 Error，直接展示即可
      wx.showToast({ title: error?.message || '保存失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
