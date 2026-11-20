<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message, Select, Tag } from 'ant-design-vue';

import { getCustomerDetail, updateCustomerRemark } from '#/api/customer';
import {
  getCustomerTags,
  setUserTags,
  type CustomerTag,
} from '#/api/customerTag';
import { getReferralTree, type ReferralTreeNode } from '#/api/referral';

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };

const user = ref<any>({});
const wallet = ref<any>({});
const memberLevel = ref<any>({});
const summary = ref<any>({});
const appointments = ref<any[]>([]);
const consumptions = ref<any[]>([]);
const pointsRecords = ref<any[]>([]);
const remark = ref('');
const savingRemark = ref(false);

const appointmentStatusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'orange', text: '待确认' },
  confirmed: { color: 'blue', text: '已确认' },
  arrived: { color: 'cyan', text: '已到店' },
  completed: { color: 'green', text: '已完成' },
  cancelled: { color: 'default', text: '已取消' },
  no_show: { color: 'red', text: '爽约' },
};

function money(value: unknown) {
  return Number(value || 0).toFixed(2);
}

// 推荐链路：把树拍平成带缩进层级的列表，扁平展示避免引入递归组件
const referralRows = ref<
  { node: ReferralTreeNode; level: number }[]
>([]);

/** 将推荐树先序遍历拍平为带层级的列表 */
function flattenTree(
  node: ReferralTreeNode | null,
  level = 0,
): { node: ReferralTreeNode; level: number }[] {
  if (!node) return [];
  return [
    { node, level },
    ...(node.children || []).flatMap((child) => flattenTree(child, level + 1)),
  ];
}

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
      const [res, tags, tree] = await Promise.all([
        getCustomerDetail(customerId),
        getCustomerTags(),
        getReferralTree(customerId).catch(() => null),
      ]);
      user.value = res.user || {};
      wallet.value = res.wallet || {};
      memberLevel.value = res.memberLevel || {};
      summary.value = res.summary || {};
      appointments.value = res.appointments || [];
      consumptions.value = res.consumptions || [];
      pointsRecords.value = res.pointsRecords || [];
      remark.value = res.user?.remark || '';
      allTags.value = tags || [];
      tagOptions.value = (tags || []).map((tag: CustomerTag) => ({
        label: tag.tagName,
        value: tag.id,
      }));
      selectedTagIds.value = (res.user?.tags || []).map((tag: any) => tag.id);
      referralRows.value = flattenTree(tree || null);
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

async function onSaveRemark() {
  savingRemark.value = true;
  try {
    await updateCustomerRemark(user.value.id, remark.value.trim());
    user.value.remark = remark.value.trim();
    message.success('客户备注已保存');
  } finally {
    savingRemark.value = false;
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

    <!-- 客户完整画像摘要 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 text-sm font-medium">客户画像</div>
      <div class="grid grid-cols-4 gap-2 text-sm">
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">累计消费</div>
          <div class="font-medium">￥{{ money(summary.totalConsumption) }}</div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">累计实付</div>
          <div class="font-medium">￥{{ money(summary.totalPaidAmount) }}</div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">消费笔数</div>
          <div class="font-medium">{{ summary.consumptionCount ?? 0 }}</div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">积分余额</div>
          <div class="font-medium">{{ summary.pointsBalance ?? 0 }}</div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">预约总数</div>
          <div class="font-medium">{{ summary.appointmentTotal ?? 0 }}</div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">已完成</div>
          <div class="font-medium">
            {{ summary.appointmentCompleted ?? 0 }}
          </div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">已取消</div>
          <div class="font-medium">
            {{ summary.appointmentCancelled ?? 0 }}
          </div>
        </div>
        <div class="rounded bg-gray-50 p-2">
          <div class="text-xs opacity-60">最近到店</div>
          <div class="truncate font-medium" :title="summary.recentVisitAt">
            {{ summary.recentVisitAt || '-' }}
          </div>
        </div>
      </div>
      <div class="mt-2 text-xs opacity-60">
        最近消费：{{ summary.recentConsumptionAt || '暂无消费记录' }}
      </div>
    </div>

    <!-- 客户备注 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium">客户备注</span>
        <Button
          type="link"
          size="small"
          :loading="savingRemark"
          @click="onSaveRemark"
        >
          保存备注
        </Button>
      </div>
      <textarea
        v-model="remark"
        class="min-h-20 w-full rounded border border-gray-300 p-2 text-sm outline-none focus:border-blue-400"
        maxlength="2000"
        placeholder="记录就诊偏好、沟通注意事项等"
      ></textarea>
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

    <!-- 最近预约 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 text-sm font-medium">最近预约（最多 20 条）</div>
      <div
        v-for="item in appointments"
        :key="item.id"
        class="border-b border-gray-100 py-2 text-sm last:border-0"
      >
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium">{{ item.appointmentDate }} {{ item.startTime }}</span>
          <Tag :color="appointmentStatusMap[item.status]?.color || 'default'">
            {{ appointmentStatusMap[item.status]?.text || item.status }}
          </Tag>
          <span class="opacity-70">{{ item.clinicName }}</span>
          <span class="opacity-70">{{ item.doctorName }}</span>
          <span v-if="item.departmentName" class="opacity-60">
            {{ item.departmentName }}
          </span>
        </div>
        <div v-if="item.remark" class="mt-1 text-xs opacity-60">
          备注：{{ item.remark }}
        </div>
      </div>
      <div v-if="!appointments.length" class="py-2 text-sm text-gray-400">
        暂无预约记录
      </div>
    </div>

    <!-- 最近消费 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 text-sm font-medium">最近消费（最多 20 条）</div>
      <div
        v-for="item in consumptions"
        :key="item.id"
        class="border-b border-gray-100 py-2 text-sm last:border-0"
      >
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium">￥{{ money(item.payAmount) }}</span>
          <span class="opacity-60">折后 ￥{{ money(item.amount) }}</span>
          <span v-if="Number(item.couponDeduct)" class="opacity-60">
            优惠券 -￥{{ money(item.couponDeduct) }}
          </span>
          <Tag v-if="item.status !== 'success'" color="red">
            {{ item.status === 'refunded' ? '已退款' : item.status }}
          </Tag>
          <Tag v-else-if="item.paymentStatus !== 'success'" color="orange">
            待支付
          </Tag>
          <span class="ml-auto text-xs opacity-50">{{ item.createdAt }}</span>
        </div>
        <div class="mt-1 text-xs opacity-60">
          {{ item.clinicName }}
          <span v-if="item.pointsEarned"> · 获得 {{ item.pointsEarned }} 积分</span>
          <span v-if="item.description"> · {{ item.description }}</span>
        </div>
      </div>
      <div v-if="!consumptions.length" class="py-2 text-sm text-gray-400">
        暂无消费记录
      </div>
    </div>

    <!-- 积分流水 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 text-sm font-medium">积分流水（最多 20 条）</div>
      <div
        v-for="item in pointsRecords"
        :key="item.id"
        class="flex items-start gap-3 border-b border-gray-100 py-2 text-sm last:border-0"
      >
        <span
          class="w-16 text-right font-medium"
          :class="item.changeAmount >= 0 ? 'text-green-600' : 'text-red-500'"
        >
          {{ item.changeAmount >= 0 ? '+' : '' }}{{ item.changeAmount }}
        </span>
        <div class="min-w-0 flex-1">
          <div>{{ item.description || item.sourceType }}</div>
          <div class="text-xs opacity-50">
            余额 {{ item.balanceAfter }} · 有效期至 {{ item.expiresAt }}
          </div>
        </div>
        <span class="text-xs opacity-50">{{ item.createdAt }}</span>
      </div>
      <div v-if="!pointsRecords.length" class="py-2 text-sm text-gray-400">
        暂无积分流水
      </div>
    </div>

    <!-- 推荐链路图（需求 4.4 谁推荐了谁）：扁平缩进展示多层推荐关系 -->
    <div class="mt-4 border-t pt-3">
      <div class="mb-2 text-sm font-medium">推荐链路</div>
      <div
        v-for="(row, idx) in referralRows"
        :key="row.node.userId + '-' + idx"
        class="flex items-center gap-2 py-1 text-sm"
        :style="{ paddingLeft: row.level * 20 + 'px' }"
      >
        <span v-if="row.level > 0" class="text-gray-300">└</span>
        <span class="font-medium">{{ row.node.name }}</span>
        <span v-if="row.node.phone" class="text-gray-400">
          {{ row.node.phone }}
        </span>
        <Tag v-if="row.level > 0" :color="row.node.converted ? 'green' : 'default'">
          {{ row.node.converted ? '已首消' : '未首消' }}
        </Tag>
        <span v-if="row.level === 0" class="text-gray-400">（本人）</span>
      </div>
      <div
        v-if="referralRows.length <= 1"
        class="py-2 text-sm text-gray-400"
      >
        暂无下级推荐人
      </div>
    </div>
  </Modal>
</template>
