<script lang="ts" setup>
import type { UploadProps } from 'ant-design-vue';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Image, Input, message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createBanner, getActivities, updateBanner } from '#/api/content';
import { uploadImage } from '#/api/upload';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

// 图片地址独立于表单管理：adapter 的 Upload 组件绑定的是 fileList 数组，
// 而后端存的是单个 URL 字符串，中间还要过一道「上传→拿 url」的异步流程，
// 塞进 schema 反而要在提交前反向拆包，不如直接放在 Modal 里
const imageUrl = ref<string>('');
const uploading = ref<boolean>(false);

const title = computed(() =>
  mode.value === 'create' ? '新建轮播图' : '编辑轮播图',
);

const customRequest: UploadProps['customRequest'] = async (options) => {
  const file = options.file as File;
  // 前端先拦一道体积，省掉一次注定失败的往返；后端有同样的校验兜底
  if (file.size / 1024 / 1024 > 5) {
    message.error('图片大小不能超过 5MB');
    options.onError?.(new Error('file too large'));
    return;
  }
  uploading.value = true;
  try {
    const result = await uploadImage(file);
    imageUrl.value = result.url;
    message.success('上传成功');
    options.onSuccess?.(result);
  } catch (error: any) {
    // requestClient 的 errorMessageResponseInterceptor 已弹过后端文案，
    // 这里只处理没走到响应拦截的情况（如网络中断）
    if (!error?.response) message.error(error?.message || '上传失败');
    options.onError?.(error);
  } finally {
    uploading.value = false;
  }
};

/**
 * 加载可选活动并标注占用情况。
 * 已绑定其他轮播图的活动直接禁用，避免填完提交才被后端拒绝。
 */
async function loadActivityOptions() {
  const activities = await getActivities();
  const options = activities.map((item) => {
    const boundByOther = Boolean(item.bannerId) && item.bannerId !== editingId.value;
    const suffix = boundByOther
      ? '（已绑定其他轮播图）'
      : item.bannerId === editingId.value
        ? '（当前轮播图）'
        : '';
    return {
      disabled: boundByOther,
      label: `${item.title}${suffix}`,
      value: item.id,
    };
  });
  formApi.updateSchema([
    { componentProps: { options }, fieldName: 'activityId' },
  ]);
}

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'title',
      label: '标题',
      rules: 'required',
      componentProps: { placeholder: '如：新客首诊免费洁牙' },
    },
    {
      component: 'Select',
      fieldName: 'activityId',
      label: '关联活动',
      rules: 'required',
      help: '一张轮播图对应一个活动，点击轮播图进入活动详情页；活动在「内容管理 → 活动管理」维护',
      componentProps: {
        showSearch: true,
        optionFilterProp: 'label',
        placeholder: '请选择活动',
        style: 'width: 100%',
        options: [],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortOrder',
      label: '排序',
      defaultValue: 0,
      help: '数值越小越靠前',
      componentProps: { min: 0, precision: 0, style: 'width: 100%' },
    },
    {
      component: 'DatePicker',
      fieldName: 'startAt',
      label: '生效日期',
      help: '留空表示不限',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'DatePicker',
      fieldName: 'endAt',
      label: '失效日期',
      help: '留空表示不限，填写后当天 23:59:59 前仍生效',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '启用', value: 1 },
          { label: '停用', value: 0 },
        ],
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        imageUrl.value = data.row.imageUrl || '';
        formApi.setValues({
          ...data.row,
          // 后端返回 "YYYY-MM-DD HH:mm:ss"，DatePicker 仅需日期部分
          startAt: data.row.startAt?.slice(0, 10),
          endAt: data.row.endAt?.slice(0, 10),
        });
      } else {
        editingId.value = 0;
        imageUrl.value = '';
        formApi.resetForm();
      }

      try {
        await loadActivityOptions();
      } catch {
        message.error('加载活动列表失败，请关闭后重试');
      }
    }
  },
  async onConfirm() {
    // 图片必填校验放在表单之外：imageUrl 不是 schema 字段，formApi.validate() 管不到
    if (!imageUrl.value) {
      message.warning('请先上传轮播图片');
      return;
    }
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    const payload = { ...values, imageUrl: imageUrl.value };
    modalApi.setState({ confirmLoading: true });
    try {
      if (mode.value === 'create') {
        await createBanner(payload);
        message.success('创建成功');
      } else {
        await updateBanner(editingId.value, payload);
        message.success('更新成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.setState({ confirmLoading: false });
    }
  },
});
</script>

<template>
  <Modal :title="title">
    <div class="mb-4">
      <div class="mb-2">
        <span class="text-destructive">*</span>
        轮播图片
      </div>
      <div class="flex items-center gap-4">
        <Upload
          :custom-request="customRequest"
          :show-upload-list="false"
          accept="image/jpeg,image/png,image/gif,image/webp"
          list-type="picture-card"
        >
          <div v-if="uploading">
            <IconifyIcon
              class="animate-spin"
              icon="ant-design:loading-outlined"
            />
            <div class="mt-1 text-xs">上传中</div>
          </div>
          <div v-else>
            <IconifyIcon icon="ant-design:plus-outlined" />
            <div class="mt-1 text-xs">上传图片</div>
          </div>
        </Upload>
        <Image
          v-if="imageUrl"
          :src="imageUrl"
          :height="72"
          :width="180"
          class="rounded object-cover"
        />
      </div>
      <!-- 已有 CDN 图或历史素材时不必重复上传，直接粘地址更省事 -->
      <div class="mt-2 flex items-center gap-2">
        <Input
          v-model:value="imageUrl"
          allow-clear
          placeholder="或直接填写图片地址，如 /api/v1/files/xxx.png"
        />
        <span class="text-muted-foreground shrink-0 text-xs">
          建议 750×360，jpg/png/gif/webp，≤ 5MB
        </span>
      </div>
    </div>
    <Form />
  </Modal>
</template>
