<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Spin } from 'ant-design-vue';
import { toDataURL } from 'qrcode';

import { getClinicCheckinCode } from '#/api/clinic';

interface ClinicRow {
  id: number;
  name: string;
  address?: string;
}

const clinic = ref<ClinicRow>({ id: 0, name: '' });
/** 码内容明文（仅展示，方便核对），实际写入二维码的同一字符串 */
const payload = ref('');
const qrDataUrl = ref('');
const generating = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (!isOpen) return;

    const data = modalApi.getData<{ row: ClinicRow }>();
    const row = data?.row;
    if (!row?.id) {
      message.error('门店信息缺失，无法生成签到码');
      modalApi.close();
      return;
    }

    clinic.value = row;
    payload.value = '';
    qrDataUrl.value = '';
    modalApi.setState({ confirmText: '关闭', showCancelButton: false });

    generating.value = true;
    try {
      // 码由后端签发：dc1.<clinicId>.<HMAC-SHA256签名>，前端只负责展示/打印
      const data = await getClinicCheckinCode(row.id);
      payload.value = data.code;
      qrDataUrl.value = await toDataURL(payload.value, {
        errorCorrectionLevel: 'M',
        margin: 1,
        width: 320,
      });
    } catch (error: any) {
      message.error(error?.message || '签到码获取失败，请重试');
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
  a.download = `门店签到码-${clinic.value.name || clinic.value.id}.png`;
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
<head><meta charset="utf-8" /><title>门店签到码 - ${clinic.value.name}</title>
<style>
  body { font-family: -apple-system, "PingFang SC", sans-serif; text-align: center; padding: 40px; }
  h2 { margin: 0 0 8px; }
  p { color: #666; margin: 4px 0; }
  img { width: 320px; height: 320px; margin: 20px 0; }
</style></head>
<body>
  <h2>${clinic.value.name} · 到店签到码</h2>
  <p>${clinic.value.address || ''}</p>
  <img src="${qrDataUrl.value}" alt="checkin qrcode" onload="window.focus(); window.print();" />
  <p>请使用小程序「我的预约 - 扫码签到」扫描此码</p>
</body></html>`);
  win.document.close();
}
</script>

<template>
  <Modal title="门店签到码" class="w-96">
    <div class="flex flex-col items-center py-2">
      <Spin :spinning="generating" tip="生成中...">
        <img
          v-if="qrDataUrl"
          :src="qrDataUrl"
          alt="门店签到码"
          class="h-64 w-64 border border-solid border-gray-200"
        />
        <div v-else class="h-64 w-64"></div>
      </Spin>

      <div class="mt-4 text-base font-medium">{{ clinic.name }}</div>
      <div v-if="clinic.address" class="mt-1 text-center text-sm text-gray-500">
        {{ clinic.address }}
      </div>
      <div class="mt-3 text-xs leading-5 text-gray-400">
        打印张贴于门店前台，到店客户用小程序「我的预约 - 扫码签到」扫码完成签到
      </div>
      <div class="mt-1 break-all px-2 text-center font-mono text-xs text-gray-300">
        {{ payload }}
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
