<script lang="ts" setup>
import { ref, onMounted } from 'vue';

import { Page } from '@vben/common-ui';

import { Button, Card, Input, InputNumber, message, Switch, Table, Tag } from 'ant-design-vue';

import { getConfigs, updateConfig } from '#/api/config';

const configs = ref<any[]>([]);
const loading = ref(true);
const editingKey = ref<string>('');
const editValue = ref<string>('');

async function fetchConfigs() {
  loading.value = true;
  try {
    const data = await getConfigs();
    // 后端返回 [{category, items: [{id, configKey, configValue, configType, ...}]}]
    if (Array.isArray(data)) {
      const flat: any[] = [];
      for (const group of data) {
        if (group.items && Array.isArray(group.items)) {
          for (const item of group.items) {
            flat.push({
              key: item.configKey,
              value: item.configValue,
              valueType: item.configType === 'int' ? 'number' : item.configType === 'bool' ? 'boolean' : 'string',
              category: item.category || group.category,
              description: item.description,
              sensitive: item.isSensitive === 1,
              hotReload: item.reloadType === 'hot',
              min: item.minValue,
              max: item.maxValue,
            });
          }
        }
      }
      configs.value = flat;
    }
  } catch (_) {}
  loading.value = false;
}

function startEdit(record: any) {
  editingKey.value = record.key;
  editValue.value = record.value;
}

function cancelEdit() {
  editingKey.value = '';
}

async function saveEdit(record: any) {
  try {
    await updateConfig(record.key, { value: editValue.value });
    message.success('配置已更新');
    record.value = editValue.value;
    editingKey.value = '';
    await fetchConfigs();
  } catch (e: any) {
    message.error(e.message || '更新失败');
  }
}

function maskSensitive(value: string, sensitive: boolean): string {
  if (!sensitive || !value) return value;
  if (value.length <= 4) return '••••';
  return '••••••••' + value.slice(-4);
}

const columns = [
  { title: '分类', dataIndex: 'category', width: 120 },
  { title: '配置项', dataIndex: 'key', width: 200 },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '值', dataIndex: 'value', width: 200, key: 'value' },
  { title: '类型', dataIndex: 'valueType', width: 80 },
  { title: '热更新', dataIndex: 'hotReload', width: 80, key: 'hotReload' },
  { title: '操作', key: 'action', width: 120, fixed: 'right' },
];

onMounted(fetchConfigs);
</script>

<template>
  <Page title="系统配置" auto-content-height>
    <Card :loading="loading">
      <Table :columns="columns" :data-source="configs" :pagination="false" row-key="key" size="small" :scroll="{ y: 500 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'value'">
            <template v-if="editingKey === record.key">
              <Input v-if="record.valueType === 'string'" v-model:value="editValue" size="small" />
              <InputNumber v-else-if="record.valueType === 'number'" v-model:value="editValue" size="small" :min="record.min" :max="record.max" />
              <Switch v-else-if="record.valueType === 'boolean'" :checked="editValue === 'true'" @change="(v: boolean) => editValue = String(v)" size="small" />
              <Input v-else v-model:value="editValue" size="small" />
            </template>
            <span v-else>{{ maskSensitive(record.value, record.sensitive) }}</span>
          </template>
          <template v-if="column.key === 'hotReload'">
            <Tag :color="record.hotReload ? 'green' : 'default'">{{ record.hotReload ? '热更新' : '需重启' }}</Tag>
          </template>
          <template v-if="column.key === 'action'">
            <template v-if="editingKey === record.key">
              <Button type="link" size="small" @click="saveEdit(record)">保存</Button>
              <Button type="link" size="small" @click="cancelEdit">取消</Button>
            </template>
            <Button v-else type="link" size="small" @click="startEdit(record)">编辑</Button>
          </template>
        </template>
      </Table>
    </Card>
  </Page>
</template>
