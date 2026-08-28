<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, DescriptionsItem, Tag } from 'ant-design-vue';

import { getTaskDetail } from '#/api/task';

const detail = ref<any>(null);
const loading = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ taskId: string }>();
      if (data?.taskId) {
        loading.value = true;
        try {
          detail.value = await getTaskDetail(data.taskId);
        } catch (_) {}
        loading.value = false;
      }
    }
  },
});
</script>

<template>
  <Modal title="任务详情" :show-confirm-button="false">
    <div v-if="loading" class="py-4 text-center text-gray-400">加载中...</div>
    <div v-else-if="detail">
      <Descriptions :column="2" bordered size="small">
        <DescriptionsItem label="任务 ID" :span="2">{{ detail.taskId }}</DescriptionsItem>
        <DescriptionsItem label="类型">{{ detail.taskType }}</DescriptionsItem>
        <DescriptionsItem label="游戏">{{ detail.gameCode }}</DescriptionsItem>
        <DescriptionsItem label="状态">
          <Tag :color="detail.status === 'success' ? 'success' : detail.status === 'failed' ? 'error' : 'processing'">
            {{ detail.status }}
          </Tag>
        </DescriptionsItem>
        <DescriptionsItem label="耗时">{{ detail.duration || '-' }}</DescriptionsItem>
        <DescriptionsItem label="开始时间">{{ detail.startTime }}</DescriptionsItem>
        <DescriptionsItem label="结束时间">{{ detail.endTime || '-' }}</DescriptionsItem>
        <DescriptionsItem label="触发方式" :span="2">{{ detail.triggerType || '手动' }}</DescriptionsItem>
      </Descriptions>
      <!-- 错误堆栈 -->
      <div v-if="detail.errorStack" class="mt-4">
        <div class="mb-2 font-medium text-red-500">错误堆栈</div>
        <pre class="max-h-64 overflow-auto rounded border p-3 font-mono text-xs dark:border-gray-600 dark:bg-[var(--vben-bg-color)]">{{ detail.errorStack }}</pre>
      </div>
    </div>
  </Modal>
</template>
