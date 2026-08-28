<script lang="ts" setup>
/**
 * BUG-15.2.C12 fix: Sheet 映射使用 useVbenModal 展示结构化表格
 * (备用组件，当前 detail/index.vue 使用内联 Modal)
 */
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

const mappingData = ref<Record<string, Array<{ sheetId: string; sheetName: string }>>>({});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<any>();
      mappingData.value = data || {};
    }
  },
});
</script>

<template>
  <Modal title="飞书 Sheet 映射" :show-confirm-button="false">
    <div class="max-h-96 overflow-auto">
      <div v-for="(items, platform) in mappingData" :key="platform" class="mb-4">
        <h4 class="mb-2 font-medium">{{ platform }}</h4>
        <table class="w-full border-collapse text-sm">
          <thead>
            <tr class="border-b">
              <th class="p-2 text-left">Sheet 名称</th>
              <th class="p-2 text-left">Sheet ID</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.sheetId" class="border-b">
              <td class="p-2">{{ item.sheetName }}</td>
              <td class="p-2 font-mono text-xs">{{ item.sheetId }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="Object.keys(mappingData).length === 0" class="py-4 text-center text-gray-400">
        无映射数据
      </div>
    </div>
  </Modal>
</template>
