<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import { getCustomers } from '#/api/customer';
import { getCustomerTags } from '#/api/customerTag';
import { getMemberLevels } from '#/api/member';

import CustomerAdjustModal from './adjust-modal.vue';
import CustomerDetailModal from './detail-modal.vue';

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: CustomerDetailModal,
  destroyOnClose: true,
});

const [AdjustModal, adjustModalApi] = useVbenModal({
  connectedComponent: CustomerAdjustModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '姓名/昵称/手机号' },
        fieldName: 'keyword',
        label: '关键词',
      },
      {
        component: 'ApiSelect',
        componentProps: {
          allowClear: true,
          api: getClinics,
          labelField: 'name',
          placeholder: '全部门诊',
          style: 'width: 100%',
          valueField: 'id',
        },
        fieldName: 'clinicId',
        label: '就诊门店',
      },
      {
        component: 'ApiSelect',
        componentProps: {
          allowClear: true,
          api: getCustomerTags,
          labelField: 'tagName',
          placeholder: '全部标签',
          style: 'width: 100%',
          valueField: 'id',
        },
        fieldName: 'tagId',
        label: '标签',
      },
      {
        component: 'ApiSelect',
        componentProps: {
          allowClear: true,
          api: getMemberLevels,
          labelField: 'levelName',
          placeholder: '全部等级',
          style: 'width: 100%',
          valueField: 'levelCode',
        },
        fieldName: 'memberLevel',
        label: '会员等级',
      },
      {
        component: 'RangePicker',
        componentProps: {
          allowClear: true,
          placeholder: ['注册开始日期', '注册结束日期'],
          style: 'width: 100%',
          valueFormat: 'YYYY-MM-DD',
        },
        fieldName: 'registrationRange',
        label: '注册时间',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'realName',
        title: '姓名',
        minWidth: 100,
        slots: { default: 'name' },
      },
      { field: 'phone', title: '手机号', width: 130 },
      {
        field: 'gender',
        title: '性别',
        width: 70,
        slots: { default: 'gender' },
      },
      { field: 'age', title: '年龄', width: 70 },
      { field: 'balance', title: '余额', width: 100 },
      { field: 'bonusBalance', title: '赠送余额', width: 100 },
      {
        field: 'levelName',
        title: '会员等级',
        width: 110,
        slots: { default: 'level' },
      },
      { field: 'totalConsumption', title: '累计消费', width: 110 },
      {
        field: 'tags',
        title: '标签',
        minWidth: 140,
        slots: { default: 'tags' },
      },
      {
        field: 'action',
        title: '操作',
        width: 210,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const { registrationRange, ...params } = formValues ?? {};
          const res = await getCustomers({
            ...params,
            startDate: registrationRange?.[0],
            endDate: registrationRange?.[1],
            page: page.currentPage,
            pageSize: page.pageSize,
          });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});

function onDetail(row: any) {
  detailModalApi.setData({ id: row.id }).open();
}

function onAdjust(row: any) {
  adjustModalApi.setData({ row }).open();
}
</script>

<template>
  <Page auto-content-height>
    <DetailModal />
    <AdjustModal @success="gridApi.query()" />
    <Grid table-title="客户管理">
      <template #name="{ row }">
        {{ row.realName || row.nickname || '-' }}
      </template>
      <template #gender="{ row }">
        {{ genderMap[row.gender] ?? '未知' }}
      </template>
      <template #level="{ row }">
        <Tag v-if="row.levelName" color="gold">{{ row.levelName }}</Tag>
        <span v-else>-</span>
      </template>
      <template #tags="{ row }">
        <template v-if="row.tags?.length">
          <Tag
            v-for="tag in row.tags"
            :key="tag.id"
            :color="tag.color"
            class="mb-0.5"
          >
            {{ tag.tagName }}
          </Tag>
        </template>
        <span v-else>-</span>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onDetail(row)">详情</Button>
        <Button type="link" size="small" @click="onAdjust(row)">调整</Button>
      </template>
    </Grid>
  </Page>
</template>
