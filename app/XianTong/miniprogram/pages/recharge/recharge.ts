// pages/recharge/recharge.ts
import { getRechargeTiers, createRecharge, getRechargeStatus } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import { requestSubscribeMessage } from '../../utils/util'
import type { WxPayParams } from '../../utils/api'

interface RechargePlan {
  amount: number
  giftedAmount: number
  giftRatio: number
}

/** 轮询支付结果的次数与间隔：回调通常秒级到达，后端查单也会兜底，共等待约 9 秒 */
const POLL_MAX_ATTEMPTS = 6
const POLL_INTERVAL_MS = 1500

Page({
  data: {
    plans: [] as RechargePlan[],
    selectedPlan: null as RechargePlan | null,
    customAmount: '',
    loading: false
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadPlans()
  },

  // 加载充值档位（GET /recharge/tiers，映射后端字段）
  async loadPlans() {
    try {
      const result = await getRechargeTiers()
      if (result.code === 0 && Array.isArray(result.data)) {
        const plans: RechargePlan[] = result.data.map((t: any) => ({
          amount: t.amount,
          giftedAmount: t.bonus,
          giftRatio: t.amount ? t.bonus / t.amount : 0
        }))
        this.setData({ plans })
      }
    } catch (error) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Recharge] 加载充值档位失败:', error)
    }
  },

  // 选择充值档位
  onSelectPlan(e: any) {
    const index = Number(e.currentTarget.dataset.index)
    const plan = this.data.plans[index]
    this.setData({ selectedPlan: plan, customAmount: '' })
  },

  // 自定义金额输入
  onCustomAmountInput(e: any) {
    this.setData({ customAmount: e.detail.value, selectedPlan: null })
  },

  // 确认充值
  async handleRecharge() {
    // 订阅消息授权引导：充值后会触发充值成功/消费成功/等级变动等通知
    requestSubscribeMessage([
      "recharge_success",
      "consumption_success",
      "level_change"
    ])

    const { selectedPlan, customAmount } = this.data

    let amount = 0

    if (selectedPlan) {
      amount = selectedPlan.amount
    } else if (customAmount) {
      amount = Number(customAmount)
      if (amount < 100) {
        wx.showToast({ title: '最低充值100元', icon: 'none' })
        return
      }
    } else {
      wx.showToast({ title: '请选择充值档位或输入金额', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认充值',
      content: `确认充值${amount}元？赠送金额由系统按档位规则计算。`,
      success: async (res) => {
        if (res.confirm) {
          this.doRecharge(amount)
        }
      }
    })
  },

  async doRecharge(amount: number) {
    if (this.data.loading) return
    this.setData({ loading: true })

    try {
      wx.showLoading({ title: '下单中...' })

      // 1. 创建充值订单（POST /recharge/create，赠送金额由后端按规则计算）
      // 用户属于全医院，无需选择门店；门店由后端按最近到店/默认营业门店解析
      const result = await createRecharge({ amount })
      wx.hideLoading()

      if (result.code !== 0 || !result.data) {
        wx.showToast({ title: result.message || '下单失败', icon: 'none' })
        return
      }

      const order = result.data

      // 2a. mock 模式（后端未配置真实支付）：下单即到账
      if (order.status === 'success') {
        this.onRechargeSuccess()
        return
      }

      // 2b. 真实支付：订单待支付，调起微信收银台
      if (order.status !== 'pending' || !order.payParams) {
        wx.showToast({ title: '订单状态异常，请重新充值', icon: 'none' })
        return
      }

      await this.invokeWxPayment(order.recordId, order.payParams)
    } catch (error) {
      wx.hideLoading()
      console.error('充值失败:', error)
      wx.showToast({ title: '充值失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  // 调起微信支付并处理结果
  async invokeWxPayment(recordId: number, payParams: WxPayParams) {
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
            // 用户取消收银台不算系统错误，单独提示
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
      console.error('[Recharge] 微信支付调起失败:', error)
      wx.showToast({ title: '支付失败，请稍后重试', icon: 'none' })
      return
    }

    if (payCancelled) {
      // 订单仍为 pending，不做跳转：用户可重新选择金额下单（旧单会由微信侧超时关闭）
      wx.showToast({ title: '已取消支付', icon: 'none' })
      return
    }

    // 收银台返回成功不代表服务端已入账：异步通知可能有延迟，轮询订单状态确认
    wx.showLoading({ title: '确认支付结果...' })
    const finalStatus = await this.pollPaymentStatus(recordId)
    wx.hideLoading()

    if (finalStatus === 'success') {
      this.onRechargeSuccess()
    } else if (finalStatus === 'failed') {
      wx.showToast({ title: '订单支付失败', icon: 'none' })
    } else {
      // 超时：钱可能已扣但回调/查单尚未确认，提示用户勿重复支付，后端会继续按回调入账
      wx.showModal({
        title: '支付结果确认中',
        content: '暂未收到支付成功通知，若已扣款，到账可能有几分钟延迟，可稍后在钱包页查看。',
        showCancel: false,
        confirmText: '我知道了'
      })
    }
  },

  // 轮询订单状态；后端每次查询都会主动向微信查单，兼具回调丢失时的对账兜底作用
  async pollPaymentStatus(recordId: number): Promise<'success' | 'failed' | 'timeout'> {
    for (let attempt = 0; attempt < POLL_MAX_ATTEMPTS; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
      try {
        const res = await getRechargeStatus(recordId)
        if (res.code === 0 && res.data) {
          if (res.data.status === 'success') return 'success'
          if (res.data.status === 'failed' || res.data.status === 'refunded') return 'failed'
        }
      } catch (error) {
        // 单次查询失败（网络抖动等）不致命，继续下一次轮询
        console.warn(`[Recharge] 第${attempt + 1}次查询支付状态失败:`, error)
      }
    }
    return 'timeout'
  },

  // 充值成功后的统一反馈：提示并返回上一页（钱包页 onShow 会重新拉余额）
  onRechargeSuccess() {
    wx.showToast({ title: '充值成功', icon: 'success' })
    setTimeout(() => {
      wx.navigateBack()
    }, 1500)
  }
})
