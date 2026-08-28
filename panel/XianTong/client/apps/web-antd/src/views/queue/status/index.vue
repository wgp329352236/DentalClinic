<script lang="ts" setup>
import { ref, onMounted, onUnmounted } from 'vue';

import { Page } from '@vben/common-ui';

import { Card, Col, Progress, Row, Statistic, Tag } from 'ant-design-vue';

import { getQueueStatus } from '#/api/queue';

const queues = ref<any[]>([]);
const loading = ref(true);
let timer: any = null;

async function fetchStatus() {
  try {
    const data = await getQueueStatus();
    // 后端返回 { queues: [...], workers: [...] }，使用 workers 数组
    queues.value = data?.workers || [];
  } catch (_) {}
  loading.value = false;
}

onMounted(() => {
  fetchStatus();
  // 10 秒自动刷新
  timer = setInterval(fetchStatus, 10000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});

function getPoolColor(active: number, total: number): string {
  const ratio = active / total;
  if (ratio >= 0.9) return '#ff4d4f';
  if (ratio >= 0.6) return '#faad14';
  return '#52c41a';
}
</script>

<template>
  <Page title="队列状态" description="线程池实时状态，每 10 秒自动刷新">
    <Row :gutter="[16, 16]">
      <Col v-for="q in queues" :key="q.name" :xs="24" :sm="12" :lg="8">
        <Card :loading="loading">
          <template #title>
            <span class="text-base font-medium">{{ q.poolName }}</span>
            <Tag :color="q.activeThreads > 0 ? 'processing' : 'default'" class="ml-2">
              {{ q.activeThreads > 0 ? '运行中' : '空闲' }}
            </Tag>
          </template>
          <div class="space-y-3">
            <div class="flex justify-between">
              <Statistic title="活跃线程" :value="q.activeThreads" />
              <Statistic title="总线程" :value="q.totalThreads" />
              <Statistic title="排队任务" :value="q.queuedTasks" />
            </div>
            <Progress
              :percent="Math.round((q.activeThreads / q.totalThreads) * 100)"
              :stroke-color="getPoolColor(q.activeThreads, q.totalThreads)"
              :format="(p: number) => `${q.activeThreads}/${q.totalThreads}`"
            />
          </div>
        </Card>
      </Col>
    </Row>
    <div v-if="!loading && queues.length === 0" class="py-12 text-center text-gray-400">
      暂无队列数据
    </div>
  </Page>
</template>
