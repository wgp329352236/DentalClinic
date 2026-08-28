<script lang="ts" setup>
/**
 * BUG-15.1.C1 fix: 使用 useVbenModal 替代 Modal.confirm
 * BUG-15.1.U1 fix: 提供输入框让用户输入游戏代码确认删除
 */
import { ref, computed } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Input, message } from 'ant-design-vue';

import { deleteGame } from '#/api/game';

const emit = defineEmits<{ success: [] }>();
const gameCode = ref('');
const gameName = ref('');
const confirmInput = ref('');

const canConfirm = computed(() => confirmInput.value === gameCode.value);

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ code: string; name: string }>();
      gameCode.value = data?.code || '';
      gameName.value = data?.name || '';
      confirmInput.value = '';
    }
  },
  async onConfirm() {
    if (!canConfirm.value) return;
    await deleteGame(gameCode.value);
    message.success('删除成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="删除游戏" confirm-text="确认删除" :confirm-disabled="!canConfirm">
    <div class="space-y-4">
      <Alert
        type="error"
        show-icon
        :message="`确定删除游戏「${gameName}」(${gameCode})？`"
        description="此操作将移除所有关联配置，不可恢复！"
      />
      <div>
        <p class="mb-2 text-sm">
          请输入游戏代码 <code class="rounded bg-gray-100 px-1 py-0.5 font-mono dark:bg-gray-700">{{ gameCode }}</code> 确认删除：
        </p>
        <Input
          v-model:value="confirmInput"
          :placeholder="`请输入 ${gameCode}`"
          :status="confirmInput && !canConfirm ? 'error' : ''"
        />
      </div>
    </div>
  </Modal>
</template>
