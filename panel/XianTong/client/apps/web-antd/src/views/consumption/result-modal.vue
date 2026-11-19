<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

const stageMap: Record<string, string> = {
  first_consumption: '首消奖励',
  none: '无推荐奖励',
  ongoing: '持续奖励',
};

const settlement = ref<any>({});

const referral = computed(() => settlement.value.referralReward ?? null);
const hasReferral = computed(
  () => !!referral.value && referral.value.stage !== 'none',
);

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ settlement?: any }>();
      settlement.value = data?.settlement ?? {};
    }
  },
});
</script>

<template>
  <Modal title="录入成功 · 本次结算明细" :footer="false">
    <div class="space-y-4 text-sm">
      <!-- 金额结算 -->
      <div>
        <div class="mb-2 font-medium opacity-80">金额结算</div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-2">
          <div>
            <span class="opacity-60">项目原价：</span>
            ￥{{ settlement.originalAmount ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">等级折扣：</span>
            <span class="text-red-500">
              -￥{{ settlement.discountAmount ?? 0 }}
            </span>
          </div>
          <div>
            <span class="opacity-60">优惠券：</span>
            {{ settlement.couponName || '未使用' }}
          </div>
          <div>
            <span class="opacity-60">券抵扣：</span>
            <span class="text-red-500">
              -￥{{ settlement.couponDeduct ?? 0 }}
            </span>
          </div>
          <div v-if="(settlement.pointsDeduct ?? 0) > 0">
            <span class="opacity-60">积分抵扣：</span>
            <span class="text-red-500">
              -￥{{ settlement.pointsDeduct ?? 0 }}
            </span>
          </div>
          <div v-if="(settlement.pointsDeduct ?? 0) > 0">
            <span class="opacity-60">消耗积分：</span>
            <span class="text-red-500">
              -{{ settlement.pointsUsed ?? 0 }}
            </span>
          </div>
          <div>
            <span class="opacity-60">折后应付：</span>
            ￥{{ settlement.amount ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">实付金额：</span>
            <span class="font-medium text-red-500">
              ￥{{ settlement.payAmount ?? 0 }}
            </span>
          </div>
        </div>
      </div>

      <!-- 支付拆分 -->
      <div>
        <div class="mb-2 font-medium opacity-80">支付拆分</div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-2">
          <div>
            <span class="opacity-60">余额支付：</span>
            ￥{{ settlement.balanceUsed ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">微信支付：</span>
            ￥{{ settlement.wechatPaid ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">支付后余额：</span>
            ￥{{ settlement.balanceAfter ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">支付后赠送余额：</span>
            ￥{{ settlement.bonusBalanceAfter ?? 0 }}
          </div>
        </div>
      </div>

      <!-- 积分与等级 -->
      <div>
        <div class="mb-2 font-medium opacity-80">积分与等级</div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-2">
          <div>
            <span class="opacity-60">本次获得积分：</span>
            <span class="text-green-600">
              +{{ settlement.pointsEarned ?? 0 }}
            </span>
          </div>
          <div>
            <span class="opacity-60">积分余额：</span>
            {{ settlement.pointsBalanceAfter ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">累计消费：</span>
            ￥{{ settlement.accumulatedConsumption ?? 0 }}
          </div>
          <div>
            <span class="opacity-60">当前等级：</span>
            {{ settlement.levelName || '-' }}
            <Tag v-if="settlement.levelUpgraded" color="gold">已升级</Tag>
          </div>
        </div>
      </div>

      <!-- 推荐奖励 -->
      <div v-if="hasReferral">
        <div class="mb-2 font-medium opacity-80">
          推荐奖励
          <Tag color="blue">
            {{ stageMap[referral.stage] ?? referral.stage }}
          </Tag>
        </div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-2">
          <div>
            <span class="opacity-60">推荐人ID：</span>
            {{ referral.referrerId ?? '-' }}
          </div>
          <div>
            <span class="opacity-60">奖励积分：</span>
            <span class="text-green-600">+{{ referral.points ?? 0 }}</span>
          </div>
          <div>
            <span class="opacity-60">奖励赠送余额：</span>
            ￥{{ referral.bonusBalance ?? 0 }}
          </div>
          <div v-if="referral.newcomerCouponValue">
            <span class="opacity-60">新客券面值：</span>
            ￥{{ referral.newcomerCouponValue }}
          </div>
          <div v-if="referral.remark" class="col-span-2">
            <span class="opacity-60">备注：</span>
            {{ referral.remark }}
          </div>
        </div>
      </div>

      <div class="text-xs opacity-50">
        流水号 #{{ settlement.recordId ?? '-' }} ·
        {{ settlement.createdAt || '' }}
      </div>
    </div>
  </Modal>
</template>
