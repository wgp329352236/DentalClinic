// pages/wallet/wallet.ts
import {
  getWalletDetails,
  getConsumptionStatistics,
  getConsumptionRecords,
  getUnpaidConsumptions,
  payConsumption,
  getConsumptionPaymentStatus
} from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import { requestSubscribeMessage } from '../../utils/util'
import type { Wallet, RechargeRecord } from '../../types'
import type { ConsumptionRecord, UnpaidConsumption, WxPayParams } from '../../utils/api'

/** 与充值页保持一致：回调通常秒级到达，后端每次查单也会主动向微信对账 */
const POLL_MAX_ATTEMPTS = 6
const POLL_INTERVAL_MS = 1500

Page({
  data: {
    wallet: null as Wallet | null,
    rechargeRecords: [] as RechargeRecord[],
    consumptionRecords: [] as ConsumptionRecord[],
    unpaidConsumptions: [] as UnpaidConsumption[],
    unpaidTotal: 0,
    activeTab: 'recharge', // unpaid | recharge | consumption
    loading: false,
    loadedOnce: false,
    payingId: 0
  },

  onLoad() {
    if (!this.checkLogin()) return
  },

  onShow() {
    // 每次回到钱包页都刷新，确保充值/尾款支付后余额、待支付和记录均为最新状态
    if (!this.checkLogin()) return
    this.loadWallet()
  },

  // 校验登录态：未登录时回首页引导登录（避免请求未认证接口触发 MiniProgramError）
  checkLogin(): boolean {
    return checkLogin()
  },

  // 并行加载钱包详情、消费统计、消费记录和待支付尾款
  async loadWallet() {
    if (this.data.loading) return
    this.setData({ loading: true })

    try {
      const [walletResult, statsResult, recordsResult, unpaidResult] = await Promise.all([
        getWalletDetails(),
        getConsumptionStatistics().catch((error: any) => {
          console.warn('[Wallet] 加载消费统计失败:', error?.errMsg || error?.message || error)
          return null
        }),
        getConsumptionRecords({ page: 1, pageSize: 20 }).catch((error: any) => {
          console.warn('[Wallet] 加载消费记录失败:', error?.errMsg || error?.message || error)
          return null
        }),
        getUnpaidConsumptions().catch((error: any) => {
          console.warn('[Wallet] 加载待支付消费失败:', error?.errMsg || error?.message || error)
          return null
        })
      ])

      if (walletResult.code !== 0 || !walletResult.data) return

      const detail = walletResult.data
      const stats = statsResult?.code === 0 ? statsResult.data : null
      const unpaid = unpaidResult?.code === 0 && Array.isArray(unpaidResult.data)
        ? unpaidResult.data
        : this.data.unpaidConsumptions
      const unpaidTotal = Number(
        unpaid.reduce((sum: number, item: UnpaidConsumption) => sum + Number(item.wechatPaid || 0), 0).toFixed(2)
      )

      // 首次进入且存在待支付尾款时直接定位到待支付页签；之后刷新不打断用户当前选择
      const activeTab = !this.data.loadedOnce && unpaid.length > 0
        ? 'unpaid'
        : this.data.activeTab

      this.setData({
        wallet: {
          balance: detail.balance,
          giftedBalance: detail.bonusBalance,
          monthlySpent: stats?.monthAmount ?? this.data.wallet?.monthlySpent ?? 0,
          totalSpent: stats?.totalAmount ?? this.data.wallet?.totalSpent ?? 0
        },
        rechargeRecords: (detail.rechargeHistory || []).map((r: any) => ({
          id: r.id,
          amount: r.amount,
          giftedAmount: r.bonusAmount,
          payMethod: 'wechat',
          status: r.status,
          createdAt: r.createdAt
        })),
        consumptionRecords: recordsResult?.code === 0 && recordsResult.data
          ? recordsResult.data.list || []
          : this.data.consumptionRecords,
        unpaidConsumptions: unpaid,
        unpaidTotal,
        activeTab,
        loadedOnce: true
      })
    } catch (error: any) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Wallet] 加载钱包失败:', error?.errMsg || error?.message || error)
    } finally {
      this.setData({ loading: false })
    }
  },

  onTabChange(e: any) {
    const tab = String(e.currentTarget.dataset.tab || '')
    if (tab === 'unpaid' || tab === 'recharge' || tab === 'consumption') {
      this.setData({ activeTab: tab })
    }
  },

  showUnpaidTab() {
    this.setData({ activeTab: 'unpaid' })
  },

  // 去充值
  goToRecharge() {
    wx.navigateTo({
      url: '/pages/recharge/recharge'
    })
  },

  // 支付余额不足产生的消费尾款
  async handlePayUnpaid(e: any) {
    const id = Number(e.currentTarget.dataset.id)
    if (!Number.isFinite(id) || id <= 0 || this.data.payingId) return

    // 必须在点击事件的同步路径内申请订阅授权，不能放到 await 之后
    requestSubscribeMessage(['consumption_success', 'level_change'])

    this.setData({ payingId: id })
    wx.showLoading({ title: '下单中...' })
    let loadingVisible = true
    const hideLoading = () => {
      if (!loadingVisible) return
      loadingVisible = false
      wx.hideLoading()
    }

    try {
      const result = await payConsumption(id)
      const payment = result.data
      if (result.code !== 0 || !payment) {
        hideLoading()
        wx.showToast({ title: result.message || '发起支付失败', icon: 'none' })
        return
      }

      // 本地未配置真实微信支付时，后端已完成模拟结算
      if (payment.status === 'success') {
        hideLoading()
        await this.onConsumptionPaymentSuccess()
        return
      }

      if (payment.status !== 'pending' || !payment.payParams) {
        hideLoading()
        wx.showToast({ title: '支付订单状态异常，请稍后重试', icon: 'none' })
        return
      }

      hideLoading()
      await this.invokeWxPayment(id, payment.payParams)
    } catch (error: any) {
      console.error('[Wallet] 发起消费尾款支付失败:', error)
      hideLoading()
      wx.showToast({
        title: error?.message || error?.errMsg || '支付失败，请稍后重试',
        icon: 'none'
      })
    } finally {
      hideLoading()
      this.setData({ payingId: 0 })
    }
  },

  // 调起微信收银台，并在收银台返回成功后继续向服务端确认最终结算状态
  async invokeWxPayment(consumptionId: number, payParams: WxPayParams) {
    let payCancelled = false
    try {
      await new Promise<void>((resolve, reject) => {
        wx.requestPayment({
          timeStamp: payParams.timeStamp,
          nonceStr: payParams.nonceStr,
          package: payParams.package,
          signType: payParams.signType as 'RSA',
          paySign: payParams.paySign,
          success: () => resolve(),
          fail: (err) => {
            if (err?.errMsg?.includes('cancel')) {
              payCancelled = true
              resolve()
            } else {
              reject(err)
            }
          }
        })
      })
    } catch (error) {
      console.error('[Wallet] 微信支付调起失败:', error)
      wx.showToast({ title: '支付失败，请稍后重试', icon: 'none' })
      return
    }

    if (payCancelled) {
      wx.showToast({ title: '已取消支付', icon: 'none' })
      return
    }

    wx.showLoading({ title: '确认支付结果...' })
    const finalStatus = await this.pollConsumptionPaymentStatus(consumptionId)
    wx.hideLoading()

    if (finalStatus === 'success') {
      await this.onConsumptionPaymentSuccess()
      return
    }

    wx.showModal({
      title: finalStatus === 'failed' ? '支付未完成' : '支付结果确认中',
      content: finalStatus === 'failed'
        ? '本次支付未成功，请重新发起支付。'
        : '暂未收到支付成功通知。若已扣款，到账可能有短暂延迟，请稍后在消费记录中查看，切勿重复支付。',
      showCancel: false,
      confirmText: '我知道了'
    })
  },

  // 每次查询都会让后端在 pending 时主动向微信查单，避免异步回调延迟导致误判
  async pollConsumptionPaymentStatus(consumptionId: number): Promise<'success' | 'failed' | 'timeout'> {
    for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
      try {
        const result = await getConsumptionPaymentStatus(consumptionId)
        if (result.code === 0 && result.data) {
          if (result.data.status === 'success') return 'success'
          if (result.data.status !== 'pending') return 'failed'
        }
      } catch (error: any) {
        console.warn(`[Wallet] 第${attempt + 1}次查询尾款状态失败:`, error?.errMsg || error?.message || error)
      }
    }
    return 'timeout'
  },

  // 尾款结算成功：刷新钱包、统计、消费记录，并将用户带到已结算的消费记录页签
  async onConsumptionPaymentSuccess() {
    wx.showToast({ title: '支付成功', icon: 'success' })
    this.setData({ activeTab: 'consumption' })
    await this.loadWallet()
  }
})
