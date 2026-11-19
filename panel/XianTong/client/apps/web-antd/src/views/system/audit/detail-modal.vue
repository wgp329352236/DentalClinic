<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Descriptions, DescriptionsItem } from 'ant-design-vue';

import { getAuditLogDetail } from '#/api/config';

const detail = ref<any>(null);
const loading = ref(false);

/**
 * 请求参数是后端存下的 JSON 字符串，格式化放在 script 里做。
 *
 * 两个原因：一是模板里直接 JSON.parse 碰上脏数据会让整个弹窗渲染报错；
 * 二是 <pre> 会原样渲染空白，内容必须紧贴标签写，而带长 class 的 <pre>
 * 会被 oxfmt 拆成把 > 挂在行尾的写法，与 vue/html-closing-bracket-newline 冲突。
 * 样式改由外层 div 承担（font-mono / text-xs 会被 pre 继承），<pre> 就能单行写完。
 */
const requestParamsText = computed(() => {
  const raw = detail.value?.requestParams;
  if (!raw) return '-';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    // 不是合法 JSON 就原样展示，方便排查到底是哪条脏数据
    return String(raw);
  }
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      if (data?.id) {
        loading.value = true;
        try {
          detail.value = await getAuditLogDetail(data.id);
        } catch {}
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
        <DescriptionsItem label="操作人">
          {{ detail.username }}
        </DescriptionsItem>
        <DescriptionsItem label="操作类型">
          {{ detail.operationType }}
        </DescriptionsItem>
        <DescriptionsItem label="资源">
          {{ detail.targetResource }}
        </DescriptionsItem>
        <DescriptionsItem label="目标 ID">
          {{ detail.targetId }}
        </DescriptionsItem>
        <DescriptionsItem label="响应码">
          {{ detail.responseCode }}
        </DescriptionsItem>
        <DescriptionsItem label="IP">{{ detail.ipAddress }}</DescriptionsItem>
        <DescriptionsItem label="User-Agent" :span="2">
          {{ detail.userAgent || '-' }}
        </DescriptionsItem>
        <DescriptionsItem label="时间" :span="2">
          {{ detail.createdAt }}
        </DescriptionsItem>
        <DescriptionsItem label="请求参数" :span="2">
          <div
            class="max-h-48 overflow-auto rounded p-2 font-mono text-xs dark:bg-[var(--vben-bg-color)]"
          >
            <pre>{{ requestParamsText }}</pre>
          </div>
        </DescriptionsItem>
      </Descriptions>
    </div>
  </Modal>
</template>
