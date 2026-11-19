// 登录态校验工具
/**
 * 校验登录态：未登录时回到首页引导登录，返回 false
 * 用于需要认证的页面在发起请求前拦截，避免未捕获的 "未登录" 异常（MiniProgramError）
 */
export function checkLogin(): boolean {
  const app = getApp<IAppOption>()
  if (app && app.globalData && app.globalData.token) return true
  wx.reLaunch({ url: '/pages/index/index' })
  return false
}
