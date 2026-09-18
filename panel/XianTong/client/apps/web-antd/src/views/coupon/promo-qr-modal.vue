<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Spin } from 'ant-design-vue';
import { toDataURL } from 'qrcode';

interface CouponRow {
  id: number;
  name: string;
  faceValue?: number;
}

const coupon = ref<CouponRow>({ id: 0, name: '' });
/** 写入二维码的推广参数（promo=<templateId>），小程序扫码后按此领券 */
const promoParam = ref('');
const qrDataUrl = ref('');
const generating = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (!isOpen) return;

    const data = modalApi.getData<{ row: CouponRow }>();
    const row = data?.row;
    if (!row?.id) {
      message.error('优惠券信息缺失，无法生成推广码');
      modalApi.close();
      return;
    }

    coupon.value = row;
    promoParam.value = '';
    qrDataUrl.value = '';
    modalApi.setState({ confirmText: '关闭', showCancelButton: false });

    generating.value = true;
    try {
      // 二维码承载场景参数 promo=<templateId>：微信扫码进入小程序后，
      // 由小程序解析 scene/query 中 promo 值并调用推广领券接口（每人限领 2 张）
      promoParam.value = `promo=${row.id}`;
      qrDataUrl.value = await toDataURL(promoParam.value, {
        errorCorrectionLevel: 'M',
        margin: 1,
        width: 320,
      });
    } catch (error: any) {
      message.error(error?.message || '推广码生成失败，请重试');
      modalApi.close();
    } finally {
      generating.value = false;
    }
  },
  onConfirm() {
    modalApi.close();
  },
});

function downloadQr() {
  if (!qrDataUrl.value) return;
  const a = document.createElement('a');
  a.href = qrDataUrl.value;
  a.download = `推广码-${coupon.value.name || coupon.value.id}.png`;
  document.body.append(a);
  a.click();
  a.remove();
}

function printQr() {
  if (!qrDataUrl.value) return;
  const win = window.open('', '_blank');
  if (!win) {
    message.warning('浏览器拦截了打印窗口，请允许弹窗后重试');
    return;
  }
  // 图片带 onload：确保二维码加载完成后再唤起打印，避免打出空白页
  win.document.write(`<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8" /><title>推广码 - ${coupon.value.name}</title>
<style>
  body { font-family: -apple-system, "PingFang SC", sans-serif; text-align: center; padding: 40px; }
  h2 { margin: 0 0 8px; }
  p { color: #666; margin: 4px 0; }
  img { width: 320px; height: 320px; margin: 20px 0; }
</style></head>
<body>
  <h2>${coupon.value.name} · 推广领券</h2>
  <img src="${qrDataUrl.value}" alt="promo qrcode" onload="window.focus(); window.print();" />
  <p>微信扫一扫进入小程序，自动领取 2 张该优惠券（每人限领一次）</p>
</body></html>`);
  win.document.close();
}
</script>

<template>
  <Modal title="生成推广码" class="w-96">
    <div class="flex flex-col items-center py-2">
      <Spin :spinning="generating" tip="生成中...">
        <img
          v-if="qrDataUrl"
          :src="qrDataUrl"
          alt="推广码"
          class="h-64 w-64 border border-solid border-gray-200"
        />
        <div v-else class="h-64 w-64"></div>
      </Spin>

      <div class="mt-4 text-base font-medium">{{ coupon.name }}</div>
      <div class="mt-1 text-sm text-gray-500">
        面额 ¥{{ coupon.faceValue }} · 扫码领 2 张
      </div>
      <div class="mt-3 text-xs leading-5 text-gray-400">
        打印张贴或线上投放，客户微信扫一扫进入小程序，自动领取 2 张该优惠券（每人限领一次）
      </div>
      <div class="mt-1 break-all px-2 text-center font-mono text-xs text-gray-300">
        {{ promoParam }}
      </div>

      <div class="mt-4 flex gap-3">
        <Button :disabled="!qrDataUrl" @click="downloadQr">下载 PNG</Button>
        <Button type="primary" :disabled="!qrDataUrl" @click="printQr">
          打印
        </Button>
      </div>
    </div>
  </Modal>
</template>
