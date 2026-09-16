<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Select, Tag } from 'ant-design-vue';

import { getCustomerDetail } from '#/api/customer';
import {
  getCustomerTags,
  setUserTags,
  type CustomerTag,
} from '#/api/customerTag';

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };

const user = ref<any>({});
const wallet = ref<any>({});
const memberLevel = ref<any>({});

// 贴标签：全量标签候选项 + 当前客户已选 + 保存状态
const allTags = ref<CustomerTag[]>([]);
const selectedTagIds = ref<number[]>([]);
const savingTags = ref(false);

const tagOptions = ref<any[]>([]);

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      const customerId = data?.id ?? 0;
      const [res, tags] = await Promise.all([
        getCustomerDetail(customerId),
        getCustomerTags(),
      ]);
      user.value = res.user || {};
      wallet.value = res.wallet || {};
      memberLevel.value = res.memberLevel || {};
      allTags.value = tags || [];
      tagOptions.value = (tags || []).map((tag: CustomerTag) => ({
        label: tag.tagName,
        value: tag.id,
      }));
      selectedTagIds.value = (res.user?.tags || []).map((tag: any) => tag.id);
    }
  },
});

async function onSaveTags() {
  savingTags.value = true;
  try {
    await setUserTags(user.value.id, selectedTagIds.value);
    message.success('标签已保存');
  } finally {
    savingTags.value = false;
  }
}
</script>

<template>
  <Modal title="客户详情" :footer="false">
    <div class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <div>
        <span class="opacity-60">姓名：</span>
        {{ user.realName || user.nickname || '-' }}
      </div>
      <div>
        <span class="opacity-60">手机号：</span>
        {{ user.phone || '-' }}
      </div>
      <div>
        <span class="opacity-60">性别：</span>
        {{ genderMap[user.gender] ?? '未知' }}
      </div>
      <div>
        <span class="opacity-60">年龄：</span>
        {{ user.age ?? '-' }}
      </div>
      <div>
        <span class="opacity-60">余额：</span>
        ￥{{ wallet.balance ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">赠送余额：</span>
        ￥{{ wallet.bonusBalance ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">会员等级：</span>
        {{ memberLevel.levelName || '-' }}
      </div>
      <div>
        <span class="opacity-60">等级折扣：</span>
        {{ memberLevel.discount ?? '-' }}
      </div>
      <div>
        <span class="opacity-60">升级门槛：</span>
        ￥{{ memberLevel.minConsumption ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">注册时间：</span>
        {{ user.createdAt || '-' }}
      </div>
    </div>

    <!-- 客户标签：展示 + 直接编辑（全量覆盖保存） -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium">客户标签</span>
        <Button
          type="link"
          size="small"
          :loading="savingTags"
          @click="onSaveTags"
        >
          保存标签
        </Button>
      </div>
      <Select
        v-model:value="selectedTagIds"
        allow-clear
        mode="multiple"
        :options="tagOptions"
        placeholder="选择标签（可多选）"
        style="width: 100%"
      />
      <div v-if="selectedTagIds.length" class="mt-2">
        <Tag
          v-for="tagId in selectedTagIds"
          :key="tagId"
          :color="allTags.find((tag) => tag.id === tagId)?.color || 'blue'"
        >
          {{ allTags.find((tag) => tag.id === tagId)?.tagName || tagId }}
        </Tag>
      </div>
    </div>
  </Modal>
</template>
