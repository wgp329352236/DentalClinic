<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

const statusMap: Record<string, string> = {
  failed: '失败',
  refunded: '已退款',
  success: '已完成',
};

const record = ref<any>({});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ row?: any }>();
      record.value = data?.row ?? {};
    }
  },
});
</script>

<template>
  <Modal title="消费详情" :footer="false">
    <div class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <div>
        <span class="opacity-60">客户：</span>
        {{ record.customerName || '-' }}
      </div>
      <div>
        <span class="opacity-60">手机号：</span>
        {{ record.phone || '-' }}
      </div>
      <div>
        <span class="opacity-60">门诊：</span>
        {{ record.clinicName || '-' }}
      </div>
      <div>
        <span class="opacity-60">预约ID：</span>
        {{ record.appointmentId ?? '-' }}
      </div>
      <div>
        <span class="opacity-60">项目原价：</span>
        ￥{{ record.originalAmount ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">等级折扣：</span>
        -￥{{ record.discountAmount ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">优惠券抵扣：</span>
        -￥{{ record.couponDeduct ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">应付金额：</span>
        ￥{{ record.amount ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">实付金额：</span>
        ￥{{ record.payAmount ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">余额支付：</span>
        ￥{{ record.balanceUsed ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">微信支付：</span>
        ￥{{ record.wechatPaid ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">获得积分：</span>
        {{ record.pointsEarned ?? 0 }}
      </div>
      <div>
        <span class="opacity-60">状态：</span>
        {{ statusMap[record.status] ?? record.status ?? '-' }}
      </div>
      <div>
        <span class="opacity-60">操作员：</span>
        {{ record.operatorName || '-' }}
      </div>
      <div>
        <span class="opacity-60">消费时间：</span>
        {{ record.createdAt || '-' }}
      </div>
      <div class="col-span-2">
        <span class="opacity-60">备注：</span>
        {{ record.description || '-' }}
      </div>
    </div>
  </Modal>
</template>
