<script lang="ts" setup>
import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getAppointments } from '#/api/appointment';
import { getClinics } from '#/api/clinic';
import { createConsumption } from '#/api/consumption';
import { getCouponRecords } from '#/api/coupon';
import { getCustomers } from '#/api/customer';

// 结算结果交给列表页展示，这里只负责把后端返回的明细原样抛出
const emit = defineEmits<{ success: [any] }>();

const appointmentStatusMap: Record<string, string> = {
  cancelled: '已取消',
  completed: '已完成',
  confirmed: '已确认',
  pending: '待确认',
};

const couponTypeMap: Record<string, string> = {
  cash: '现金券',
  discount: '折扣券',
  service: '服务券',
};

/**
 * 预约ID → 预约归属。
 *
 * 在 afterFetch 里顺手缓存，使「选中预约后自动带出门诊」不需要额外发请求；
 * 同时缓存 userId 用于校验归属，详见下方 trigger 的说明。
 */
const appointmentMap = new Map<number, { clinicId: number; userId: number }>();

/**
 * 优惠券选项文案。
 *
 * 能否抵扣由后端校验（仅现金券、需达使用门槛、需在有效期内），把条件直接写进选项，
 * 避免操作员盲选提交后才收到 400。
 */
function couponLabel(item: any) {
  if (item.couponType !== 'cash') {
    const type = couponTypeMap[item.couponType] ?? '未知类型';
    return `${item.couponName} · ${type}（不可在此抵扣）`;
  }
  // 一次性构造再用 filter 去掉空项：unicorn/no-immediate-mutation 不允许对刚建好的数组就地 push
  const parts = [
    `${item.couponName} · 抵 ${item.faceValue ?? 0} 元`,
    Number(item.minConsumption) > 0
      ? `满 ${item.minConsumption} 可用`
      : '无门槛',
    item.validTo ? `${String(item.validTo).slice(0, 10)} 到期` : '',
  ].filter(Boolean);
  return parts.join(' · ');
}

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'userId',
      label: '客户',
      rules: 'selectRequired',
      componentProps: {
        allowClear: true,
        api: getCustomers,
        labelFn: (item: any) =>
          `${item.realName || item.nickname || '客户'}（${item.phone || '无手机号'} · ID:${item.id}）`,
        optionFilterProp: 'label',
        placeholder: '请选择客户，可搜索姓名或手机号',
        resultField: 'list',
        showSearch: true,
        style: 'width: 100%',
        valueField: 'id',
      },
      dependencies: {
        // 换客户后，上一位客户的预约与优惠券已不适用，必须清空，
        // 否则会提交出「券归属他人 / 预约不属于该客户」的数据被后端拒绝
        trigger(_values: any, _actions: any, controller: any) {
          controller.setFieldValue('appointmentId', undefined);
          controller.setFieldValue('userCouponId', undefined);
        },
        triggerFields: ['userId'],
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'clinicId',
      label: '消费门诊',
      rules: 'selectRequired',
      componentProps: {
        allowClear: true,
        api: getClinics,
        labelField: 'name',
        placeholder: '请选择门诊',
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'appointmentId',
      label: '关联预约',
      help: '选填。关联后会自动带出该预约的门诊，优惠券的适用科室也能被正确校验',
      componentProps: {
        allowClear: true,
        afterFetch: (res: any) => {
          for (const item of res?.list ?? []) {
            appointmentMap.set(item.id, {
              clinicId: item.clinicId,
              userId: item.userId,
            });
          }
          return res;
        },
        api: getAppointments,
        immediate: false,
        labelFn: (item: any) =>
          `${item.appointmentDate} ${item.startTime} · ${item.doctorName}${item.departmentName ? ` · ${item.departmentName}` : ''}（${appointmentStatusMap[item.status] ?? item.status}）`,
        placeholder: '请先选择客户',
        resultField: 'list',
        shouldFetch: async (params: any) => !!params?.userId,
        style: 'width: 100%',
        valueField: 'id',
      },
      dependencies: {
        componentProps: (values: any) => ({
          params: { userId: values.userId },
        }),
        // 消费门诊应与就诊门诊一致，否则流水会归错门店；预约是更具体的事实，故以它为准。
        // 校验归属是必需的：换客户时上方 trigger 对 appointmentId 的清空是异步的，
        // 本回调可能先读到旧客户的预约，不校验就会把门诊错改成上一位客户的。
        trigger(values: any, _actions: any, controller: any) {
          const appointment = appointmentMap.get(values.appointmentId);
          if (appointment && appointment.userId === values.userId) {
            controller.setFieldValue('clinicId', appointment.clinicId);
          }
        },
        triggerFields: ['userId', 'appointmentId'],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'originalAmount',
      label: '项目原价',
      rules: 'required',
      componentProps: {
        min: 0.01,
        precision: 2,
        placeholder: '未打折的应收金额',
        style: 'width: 100%',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'userCouponId',
      label: '优惠券',
      help: '仅现金券可在消费录入中抵扣；折扣券与服务券会被后端拒绝',
      componentProps: {
        allowClear: true,
        // afterFetch 直接返回数组，ApiSelect 便不再走 resultField 取数
        afterFetch: (res: any) =>
          (res?.list ?? []).map((item: any) => ({
            ...item,
            disabled: item.couponType !== 'cash',
          })),
        api: getCouponRecords,
        immediate: false,
        labelFn: (item: any) => couponLabel(item),
        placeholder: '请先选择客户',
        shouldFetch: async (params: any) => !!params?.userId,
        style: 'width: 100%',
        valueField: 'id',
      },
      dependencies: {
        componentProps: (values: any) => ({
          params: { pageSize: 100, status: 'unused', userId: values.userId },
        }),
        triggerFields: ['userId'],
      },
    },
    {
      component: 'Switch',
      fieldName: 'useBalance',
      label: '余额优先',
      defaultValue: true,
      help: '开启后先用钱包余额（含赠送余额）抵扣，不足部分记为微信支付；关闭则全额记为微信支付',
      componentProps: {
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'usePoints',
      label: '积分抵扣',
      defaultValue: 0,
      help: '本次消耗的积分数量（100积分=1元，单笔最高抵扣应付金额的20%）',
      componentProps: {
        min: 0,
        step: 1,
        placeholder: '0 表示不使用积分抵扣',
        style: 'width: 100%',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: '消费项目',
      componentProps: { rows: 2, placeholder: '选填，如「洁牙 + 补牙」' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      formApi.resetForm();
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    modalApi.setState({ confirmLoading: true });
    try {
      const settlement = await createConsumption({
        userId: values.userId,
        clinicId: values.clinicId,
        originalAmount: values.originalAmount,
        appointmentId: values.appointmentId || null,
        userCouponId: values.userCouponId || null,
        useBalance: values.useBalance ?? true,
        usePoints: values.usePoints || 0,
        description: values.description || null,
      });
      message.success('录入成功');
      emit('success', settlement);
      modalApi.close();
    } finally {
      modalApi.setState({ confirmLoading: false });
    }
  },
});
</script>

<template>
  <Modal title="录入消费">
    <Form />
  </Modal>
</template>
