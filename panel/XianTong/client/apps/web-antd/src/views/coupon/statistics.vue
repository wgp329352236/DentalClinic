<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Col,
  Empty,
  Row,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import { getCouponStatistics, getCouponTemplates } from '#/api/coupon';
import type { CouponStatistics, CouponTemplate } from '#/api/coupon';

const couponTypeMap: Record<string, string> = {
  cash: '现金券',
  discount: '折扣券',
  service: '服务券',
};

const loading = ref(false);
const statistics = ref<CouponStatistics>({
  totalRemaining: 0,
  totalIssued: 0,
  totalTemplates: 0,
  totalUsed: 0,
  usageRate: 0,
});
const templates = ref<CouponTemplate[]>([]);

const columns = [
  { dataIndex: 'name', key: 'name', title: '券名称', width: 180 },
  { dataIndex: 'couponType', key: 'couponType', title: '类型', width: 100 },
  {
    dataIndex: 'totalQuantity',
    key: 'totalQuantity',
    title: '发放总量',
    width: 100,
  },
  {
    dataIndex: 'issuedQuantity',
    key: 'issuedQuantity',
    title: '已发放',
    width: 90,
  },
  {
    dataIndex: 'usedQuantity',
    key: 'usedQuantity',
    title: '已核销',
    width: 90,
  },
  { key: 'remaining', title: '剩余库存', width: 100 },
  { key: 'usageRate', title: '核销率', width: 100 },
  { dataIndex: 'validTo', key: 'validTo', title: '有效期至', width: 120 },
  { dataIndex: 'status', key: 'status', title: '状态', width: 90 },
];

function remaining(row: Record<string, any>) {
  return Math.max(
    0,
    Number(row.totalQuantity ?? 0) - Number(row.issuedQuantity ?? 0),
  );
}

function usageRate(row: Record<string, any>) {
  const issued = Number(row.issuedQuantity ?? 0);
  if (issued <= 0) return 0;
  return Number(((Number(row.usedQuantity ?? 0) / issued) * 100).toFixed(2));
}

async function loadData() {
  loading.value = true;
  try {
    const [stats, templateList] = await Promise.all([
      getCouponStatistics(),
      getCouponTemplates(),
    ]);
    statistics.value = stats;
    templates.value = templateList;
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <Page
    description="汇总优惠券发放、核销、库存余量与各模板使用情况"
    title="优惠券统计"
  >
    <template #extra>
      <Button :loading="loading" @click="loadData">刷新</Button>
    </template>

    <Row :gutter="[16, 16]">
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="statistics.totalTemplates"
            suffix="个"
            title="优惠券模板"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="statistics.totalIssued"
            suffix="张"
            title="累计发放"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="statistics.totalUsed"
            suffix="张"
            title="累计核销"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="statistics.totalRemaining"
            suffix="张"
            title="剩余可发库存"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :precision="2"
            :value="statistics.usageRate"
            suffix="%"
            title="整体核销率"
          />
        </Card>
      </Col>
    </Row>

    <Card class="mt-4" title="各模板使用情况">
      <Empty
        v-if="!loading && templates.length === 0"
        description="暂无优惠券模板"
      />
      <Table
        v-else
        :columns="columns"
        :data-source="templates"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 980 }"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'couponType'">
            <Tag color="blue">
              {{ couponTypeMap[record.couponType] || record.couponType }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'remaining'">
            {{ remaining(record) }}
          </template>
          <template v-else-if="column.key === 'usageRate'">
            {{ usageRate(record) }}%
          </template>
          <template v-else-if="column.key === 'validTo'">
            {{ record.validTo?.slice(0, 10) || '-' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </Tag>
          </template>
        </template>
      </Table>
    </Card>
  </Page>
</template>
