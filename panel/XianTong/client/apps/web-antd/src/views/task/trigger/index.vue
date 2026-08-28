<script lang="ts" setup>
import { ref, onUnmounted } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, message, Modal, Progress, Statistic, Tag } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getGameList } from '#/api/game';
import { getBatchProgress, triggerTask } from '#/api/task';

const triggering = ref(false);
const taskResult = ref<any>(null);
const progressData = ref<any>(null);
let progressTimer: any = null;

const CHANNEL_OPTIONS = [
  { label: 'Google', value: 'google' },
  { label: 'Meta', value: 'meta' },
  { label: 'TikTok', value: 'tiktok' },
  { label: 'Apple', value: 'apple' },
  { label: 'Adjust', value: 'adjust' },
  { label: 'Applovin', value: 'applovin' },
  { label: 'Moloco', value: 'moloco' },
  { label: 'Unity', value: 'unity' },
  { label: 'Mintegral', value: 'mintegral' },
];

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Select',
      fieldName: 'type',
      label: '任务类型',
      rules: 'required',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '拉取数据 (Pull)', value: 'pull' },
          { label: '推送报表 (Push)', value: 'push' },
          { label: '导出 Excel (Export)', value: 'export' },
        ],
      },
    },
    {
      component: 'Select',
      fieldName: 'gameCode',
      label: '游戏',
      rules: 'required',
      componentProps: { placeholder: '选择游戏', showSearch: true, options: [], style: 'width: 100%' },
    },
    {
      component: 'RangePicker',
      fieldName: 'dateRange',
      label: '日期范围',
      rules: 'required',
      componentProps: { format: 'YYYY-MM-DD', valueFormat: 'YYYYMMDD' },
    },
    {
      component: 'Select',
      fieldName: 'channels',
      label: '渠道',
      componentProps: { mode: 'multiple', options: CHANNEL_OPTIONS, placeholder: '留空=全部渠道', style: 'width: 100%' },
    },
  ],
});

// 加载游戏列表
async function loadGames() {
  try {
    const res = await getGameList({ page: 1, pageSize: 100 });
    const options = (res.list || []).map((g: any) => ({ label: `${g.code} - ${g.name}`, value: g.code }));
    formApi.updateSchema([{ fieldName: 'gameCode', componentProps: { placeholder: '选择游戏', showSearch: true, options } }]);
  } catch (_) {}
}
loadGames();

async function onTrigger() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  const values = await formApi.getValues();
  const [startDate, endDate] = values.dateRange || [];

  // 超 30 天警告
  if (startDate && endDate) {
    const diff = (Number(endDate) - Number(startDate));
    if (diff > 300000) { // 粗略判断超 30 天
      await new Promise<void>((resolve, reject) => {
        Modal.confirm({
          title: '日期范围较大',
          content: '选择的日期范围超过 30 天，可能耗时较长。确定继续？',
          onOk: () => resolve(),
          onCancel: () => reject(),
        });
      });
    }
  }

  triggering.value = true;
  try {
    const result = await triggerTask(values.type, {
      gameCode: values.gameCode,
      startDate: Number(startDate),
      endDate: Number(endDate),
      channels: values.channels || [],
    });
    taskResult.value = result;
    message.success('任务已触发');

    // 开始轮询进度
    if (result?.taskId) {
      startProgressPolling(result.taskId);
    }
  } catch (e: any) {
    message.error(e.message || '触发失败');
  }
  triggering.value = false;
}

function startProgressPolling(taskId: string) {
  progressData.value = null;
  progressTimer = setInterval(async () => {
    try {
      const data = await getBatchProgress(taskId);
      progressData.value = data;
      // 完成或失败时停止轮询
      if (data?.status === 'completed' || data?.status === 'failed') {
        stopProgressPolling();
      }
      // 超 5 分钟无进展标记"可能卡住"
      if (data?.stalledMinutes && data.stalledMinutes >= 5) {
        data._stalled = true;
      }
    } catch (_) {}
  }, 5000);
}

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer);
    progressTimer = null;
  }
}

onUnmounted(stopProgressPolling);
</script>

<template>
  <Page title="手动触发任务" description="选择任务类型、游戏和日期范围后触发执行">
    <Card class="mb-4">
      <Form />
      <div class="mt-4 flex justify-end">
        <Button type="primary" :loading="triggering" @click="onTrigger">触发任务</Button>
      </div>
    </Card>

    <!-- 进度展示 -->
    <Card v-if="taskResult" title="执行进度">
      <div class="mb-2">
        <Tag color="blue">Task ID: {{ taskResult.taskId }}</Tag>
      </div>
      <div v-if="progressData" class="space-y-3">
        <Progress
          :percent="progressData.total > 0 ? Math.round((progressData.completed / progressData.total) * 100) : 0"
          :status="progressData.status === 'failed' ? 'exception' : progressData.status === 'completed' ? 'success' : 'active'"
        />
        <div class="flex gap-6">
          <Statistic title="总数" :value="progressData.total || 0" />
          <Statistic title="已完成" :value="progressData.completed || 0" />
          <Statistic title="失败" :value="progressData.failed || 0" />
          <Statistic title="耗时" :value="progressData.elapsed || '-'" />
        </div>
        <Tag v-if="progressData._stalled" color="warning">⚠️ 超过 5 分钟无进展，可能卡住</Tag>
      </div>
      <div v-else class="text-gray-400">等待进度数据...</div>
    </Card>
  </Page>
</template>
