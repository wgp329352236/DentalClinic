<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, DescriptionsItem } from 'ant-design-vue';

import { getAuditLogDetail } from '#/api/config';

const detail = ref<any>(null);
const loading = ref(false);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      if (data?.id) {
        loading.value = true;
        try { detail.value = await getAuditLogDetail(data.id); } catch (_) {}
        loading.value = false;
      }
    }
  },
});
</script>

<template>
  <Modal title="审计日志详情" :show-confirm-button="false">
    <div v-if="loading" class="py-4 text-center text-gray-400">加载中...</div>
    <div v-else-if="detail">
      <Descriptions :column="2" bordered size="small">
        <DescriptionsItem label="操作人">{{ detail.username }}</DescriptionsItem>
        <DescriptionsItem label="操作类型">{{ detail.operationType }}</DescriptionsItem>
        <DescriptionsItem label="资源">{{ detail.targetResource }}</DescriptionsItem>
        <DescriptionsItem label="目标 ID">{{ detail.targetId }}</DescriptionsItem>
        <DescriptionsItem label="响应码">{{ detail.responseCode }}</DescriptionsItem>
        <DescriptionsItem label="IP">{{ detail.ipAddress }}</DescriptionsItem>
        <DescriptionsItem label="User-Agent" :span="2">{{ detail.userAgent || '-' }}</DescriptionsItem>
        <DescriptionsItem label="时间" :span="2">{{ detail.createdAt }}</DescriptionsItem>
        <DescriptionsItem label="请求参数" :span="2">
          <pre class="max-h-48 overflow-auto rounded p-2 font-mono text-xs dark:bg-[var(--vben-bg-color)]">{{ detail.requestParams ? JSON.stringify(JSON.parse(detail.requestParams), null, 2) : '-' }}</pre>
        </DescriptionsItem>
      </Descriptions>
    </div>
  </Modal>
</template>
