# 图标资源说明

由于 tabBar 需要图标文件，以下是需要的图标列表（建议使用 81x81 px PNG 格式）：

## TabBar 图标

| 文件名 | 说明 | 颜色 |
|---|---|---|
| tab-home.png | 首页图标（未选中） | #999999 |
| tab-home-active.png | 首页图标（选中） | #1890ff |
| tab-appointment.png | 预约图标（未选中） | #999999 |
| tab-appointment-active.png | 预约图标（选中） | #1890ff |
| tab-wallet.png | 钱包图标（未选中） | #999999 |
| tab-wallet-active.png | 钱包图标（选中） | #1890ff |
| tab-profile.png | 我的图标（未选中） | #999999 |
| tab-profile-active.png | 我的图标（选中） | #1890ff |

## 其他图片

| 文件名 | 说明 | 建议尺寸 |
|---|---|---|
| banner1.jpg | 轮播图1 | 750x300 px |
| banner2.jpg | 轮播图2 | 750x300 px |
| banner3.jpg | 轮播图3 | 750x300 px |
| icon-appointment.png | 快捷操作-预约图标 | 100x100 px |
| icon-recharge.png | 快捷操作-充值图标 | 100x100 px |
| icon-points.png | 快捷操作-积分图标 | 100x100 px |
| icon-referral.png | 快捷操作-推荐图标 | 100x100 px |
| avatar.jpg | 默认头像 | 200x200 px |
| doctor1.jpg | 医生头像1 | 200x200 px |
| doctor2.jpg | 医生头像2 | 200x200 px |
| doctor3.jpg | 医生头像3 | 200x200 px |
| clinic1.jpg | 门诊图片1 | 400x300 px |
| clinic2.jpg | 门诊图片2 | 400x300 px |
| share-cover.jpg | 分享封面 | 500x400 px |

## 临时解决方案

在开发阶段，可以修改 `app.json` 中的 tabBar 配置，移除 `iconPath` 和 `selectedIconPath`，只保留文字标签。微信小程序会自动隐藏图标。

或者使用在线工具生成简单的占位图标：
- https://www.favicon-generator.org/
- https://realfavicongenerator.com/
