<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { getCustomerDetail } from '#/api/customer';

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };

const user = ref<any>({});
const wallet = ref<any>({});
const memberLevel = ref<any>({});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      const res = await getCustomerDetail(data?.id ?? 0);
      user.value = res.user || {};
      wallet.value = res.wallet || {};
      memberLevel.value = res.memberLevel || {};
    }
  },
});
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
  </Modal>
</template>
