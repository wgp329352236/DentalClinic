<script lang="ts" setup>
/**
 * 游戏详情页 - 所有渠道平级 Tab 展示
 * 每个平台独立 Tab，独立编辑/保存
 */
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button, Card, Descriptions, DescriptionsItem, Input, InputPassword,
  message, Modal, Spin, Tabs, TabPane, Tag,
} from 'ant-design-vue';

import {
  getGameDetail, getGamePlatformStatus, updateGameBasic,
  updatePlatformConfig, testFeishuConnection, getFeishuSheetMapping,
} from '#/api/game';

const route = useRoute();
const router = useRouter();
const code = route.params.code as string;
const game = ref<any>(null);
const loading = ref(true);
const activeTab = ref('basic');
const platformStatus = ref<Record<string, boolean>>({});
const testingFeishu = ref(false);
const saving = ref(false);

// Tab 编辑状态（每个 Tab 独立）
const editingTabs = ref<Record<string, boolean>>({});
const tabForms = ref<Record<string, any>>({});

// 所有 Tab 平级展示（不再有"其他渠道"折叠）
const ALL_TABS = [
  { key: 'basic', label: '基础配置' },
  { key: 'feishu', label: '飞书' },
  { key: 'google', label: 'Google Ads' },
  { key: 'meta', label: 'Meta' },
  { key: 'tiktok', label: 'TikTok' },
  { key: 'apple', label: 'Apple' },
  { key: 'app_store', label: 'App Store' },
  { key: 'adjust', label: 'Adjust' },
  { key: 'applovin', label: 'Applovin' },
  { key: 'moloco', label: 'Moloco' },
  { key: 'unity', label: 'Unity' },
  { key: 'mintegral', label: 'Mintegral' },
  { key: 'vk', label: 'VK' },
  { key: 'yandex', label: 'Yandex' },
  { key: 'reddit', label: 'Reddit' },
  { key: 'remerge', label: 'Remerge' },
  { key: 'ironsource', label: 'IronSource' },
  { key: 'mistplay', label: 'Mistplay' },
  { key: 'tiktok_mini', label: 'TikTok Mini' },
];

const TYPE_MAP: Record<number, string> = { 1: '测试', 2: '正式', 4: '预约', 8: 'MINI' };
const PAY_TYPE_MAP: Record<number, string> = { 1: 'SIMPLE', 2: 'IAP', 4: 'IAA', 8: 'MINI', 16: 'PRE' };

function decodeBitmask(value: number, map: Record<number, string>): string {
  const labels: string[] = [];
  for (const [bit, label] of Object.entries(map)) {
    if (value & Number(bit)) labels.push(label);
  }
  return labels.length > 0 ? labels.join('+') : String(value);
}

function formatDate(value: number): string {
  if (!value || value === 0) return '运营中';
  if (value >= 29990000) return '运营中';
  const str = String(value);
  if (str.length === 8) return `${str.slice(0, 4)}-${str.slice(4, 6)}-${str.slice(6, 8)}`;
  return str;
}

function maskSecret(value: string | undefined | null): string {
  if (!value) return '-';
  if (value.length <= 4) return '••••';
  return '••••••••' + value.slice(-4);
}

function isConfigured(platform: string): boolean {
  if (platform === 'basic') return true;
  return platformStatus.value[platform] ?? false;
}

function isEditing(tabKey: string): boolean {
  return editingTabs.value[tabKey] ?? false;
}

function startEdit(tabKey: string) {
  editingTabs.value[tabKey] = true;
  if (tabKey === 'basic') {
    tabForms.value[tabKey] = {
      name: game.value.name,
      area: game.value.area,
      type: game.value.type,
      payType: game.value.payType,
      openTime: game.value.openTime,
      closeTime: game.value.closeTime,
      supportAreas: [...(game.value.supportAreas || [])],
      pullChannels: [...(game.value.pullChannels || [])],
      pushChannels: [...(game.value.pushChannels || [])],
    };
  } else {
    const existing = game.value?.configs?.[tabKey];
    if (existing && Object.keys(existing).length > 0) {
      tabForms.value[tabKey] = JSON.parse(JSON.stringify(existing));
    } else {
      // 空配置时提供默认字段模板
      tabForms.value[tabKey] = getDefaultTemplate(tabKey);
    }
  }
}

// 各平台默认字段模板（空配置时使用）
function getDefaultTemplate(platform: string): Record<string, any> {
  const templates: Record<string, Record<string, any>> = {
    feishu: { appId: '', appSecret: '', iOSSpreadsheetToken: '', androidSpreadsheetToken: '', webSpreadsheetToken: '', syncSpreadsheetToken: '' },
    google: { token: '', jsonFile: '', accountIds: [] },
    meta: { token: '', iOSAccountIds: [], androidAccountIds: [] },
    tiktok: { accessToken: '', advertiserIds: [] },
    apple: { items: [{ clientId: '', clientSecret: '', orgIds: [] }] },
    app_store: { items: [{ appleId: '', appKey: '', issuerId: '', privateKey: '' }] },
    adjust: { apiToken: '', appToken: '', timeZone: 'UTC+0', adRevenueSources: 'unknown' },
    applovin: { reportKey: '', timeZone: 'UTC+0' },
    moloco: { apiKey: '', accountId: '', timeZone: 'UTC+0' },
    unity: { organizationId: '', keyId: '', secretKey: '' },
    mintegral: { accessKey: '', apiKey: '', timeZone: 'UTC+0' },
    vk: { iOSAccessToken: '', androidAccessToken: '' },
    yandex: { accessToken: '', clientLogins: [] },
    reddit: { appId: '', token: '', customIds: [] },
    remerge: { email: '', userToken: '' },
    ironsource: { accessKey: '', secretKey: '', refreshToken: '' },
    mistplay: { accountId: '', apiSecret: '', apiToken: '' },
    tiktok_mini: { accessToken: '', advertiserIds: [] },
  };
  return templates[platform] || {};
}

function cancelEdit(tabKey: string) {
  editingTabs.value[tabKey] = false;
}

function onTabChange(key: string) {
  if (isEditing(activeTab.value)) {
    Modal.confirm({
      title: '未保存的修改',
      content: '当前 Tab 有未保存的修改，切换将丢失更改。确定切换？',
      okText: '确定切换',
      cancelText: '取消',
      onOk() {
        editingTabs.value[activeTab.value] = false;
        activeTab.value = key;
      },
    });
  } else {
    activeTab.value = key;
  }
}

async function saveBasic() {
  saving.value = true;
  try {
    await updateGameBasic(code, tabForms.value['basic']);
    message.success('基础配置保存成功');
    editingTabs.value['basic'] = false;
    await fetchGame();
  } catch (e: any) {
    message.error(e.message || '保存失败');
  }
  saving.value = false;
}

async function savePlatform(platform: string) {
  saving.value = true;
  try {
    await updatePlatformConfig(code, platform, tabForms.value[platform]);
    message.success(`${platform} 配置保存成功`);
    editingTabs.value[platform] = false;
    await fetchGame();
  } catch (e: any) {
    message.error(e.message || '保存失败');
  }
  saving.value = false;
}

async function onTestFeishu() {
  testingFeishu.value = true;
  try {
    await testFeishuConnection(code);
    message.success('飞书连接成功');
  } catch (e: any) {
    message.error('连接失败: ' + (e.message || ''));
  }
  testingFeishu.value = false;
}

const sheetMappingData = ref<any>(null);
const showSheetModal = ref(false);

async function onViewSheetMapping() {
  try {
    const data = await getFeishuSheetMapping(code);
    sheetMappingData.value = data;
    showSheetModal.value = true;
  } catch (e: any) {
    message.error('获取失败: ' + (e.message || ''));
  }
}

// 判断字段是否为敏感字段
function isSensitiveField(key: string): boolean {
  const lower = key.toLowerCase();
  return lower.includes('token') || lower.includes('secret') || lower.includes('key') || lower.includes('password');
}

async function fetchGame() {
  loading.value = true;
  try {
    game.value = await getGameDetail(code);
    const statusList = await getGamePlatformStatus(code);
    const statusMap: Record<string, boolean> = {};
    if (Array.isArray(statusList)) {
      statusList.forEach((s: any) => { statusMap[s.platform] = s.configured; });
    }
    platformStatus.value = statusMap;
  } catch (e) {
    message.error('加载游戏数据失败');
  }
  loading.value = false;
}

onMounted(fetchGame);
</script>

<template>
  <Page :title="`游戏配置 - ${code}`" auto-content-height>
    <template #extra>
      <Button @click="router.push('/game/list')">返回列表</Button>
    </template>

    <!-- Sheet 映射 Modal -->
    <Modal v-model:open="showSheetModal" title="飞书 Sheet 映射" :footer="null" width="700px">
      <div v-if="sheetMappingData" class="max-h-96 overflow-auto">
        <div v-for="(items, platform) in sheetMappingData" :key="platform" class="mb-4">
          <h4 class="mb-2 font-medium">{{ platform }}</h4>
          <table class="w-full border-collapse text-sm">
            <thead><tr class="border-b"><th class="p-2 text-left">Sheet 名称</th><th class="p-2 text-left">Sheet ID</th></tr></thead>
            <tbody><tr v-for="item in items" :key="item.sheetId" class="border-b"><td class="p-2">{{ item.sheetName }}</td><td class="p-2 font-mono text-xs">{{ item.sheetId }}</td></tr></tbody>
          </table>
        </div>
      </div>
      <div v-else class="py-4 text-center text-gray-400">无映射数据</div>
    </Modal>

    <Spin :spinning="loading">
      <Card v-if="game">
        <Tabs :activeKey="activeTab" @change="onTabChange" type="card" size="small">
          <TabPane v-for="tab in ALL_TABS" :key="tab.key">
            <template #tab>
              <span class="inline-flex items-center gap-1">
                {{ tab.label }}
                <Tag v-if="tab.key !== 'basic'" :color="isConfigured(tab.key) ? 'success' : 'error'" class="ml-1 scale-75">
                  {{ isConfigured(tab.key) ? '✅' : '❌' }}
                </Tag>
              </span>
            </template>

            <!-- ==================== 基础配置 Tab ==================== -->
            <div v-if="tab.key === 'basic'">
              <div class="mb-4 flex justify-end gap-2">
                <Button v-if="!isEditing('basic')" type="primary" @click="startEdit('basic')">编辑</Button>
                <Button v-if="isEditing('basic')" type="primary" :loading="saving" @click="saveBasic">保存</Button>
                <Button v-if="isEditing('basic')" @click="cancelEdit('basic')">取消</Button>
              </div>
              <Descriptions v-if="!isEditing('basic')" :column="2" bordered size="small">
                <DescriptionsItem label="游戏代码">{{ game.code }}</DescriptionsItem>
                <DescriptionsItem label="游戏名称">{{ game.name }}</DescriptionsItem>
                <DescriptionsItem label="地区">{{ game.area }}</DescriptionsItem>
                <DescriptionsItem label="类型">{{ decodeBitmask(game.type, TYPE_MAP) }}</DescriptionsItem>
                <DescriptionsItem label="付费类型">{{ decodeBitmask(game.payType, PAY_TYPE_MAP) }}</DescriptionsItem>
                <DescriptionsItem label="开服时间">{{ formatDate(game.openTime) }}</DescriptionsItem>
                <DescriptionsItem label="关服时间">{{ formatDate(game.closeTime) }}</DescriptionsItem>
                <DescriptionsItem label="支持地区" :span="2">
                  <Tag v-for="a in (game.supportAreas || [])" :key="a">{{ a }}</Tag>
                  <span v-if="!game.supportAreas?.length" class="text-gray-400">-</span>
                </DescriptionsItem>
                <DescriptionsItem label="拉取渠道" :span="2">
                  <Tag v-for="c in (game.pullChannels || [])" :key="c" color="blue">{{ c }}</Tag>
                  <span v-if="!game.pullChannels?.length" class="text-gray-400">-</span>
                </DescriptionsItem>
                <DescriptionsItem label="推送渠道" :span="2">
                  <Tag v-for="c in (game.pushChannels || [])" :key="c" color="green">{{ c }}</Tag>
                  <span v-if="!game.pushChannels?.length" class="text-gray-400">-</span>
                </DescriptionsItem>
              </Descriptions>
              <div v-else class="grid max-w-3xl grid-cols-2 gap-4">
                <div><label class="mb-1 block text-sm opacity-60">游戏名称 *</label><Input v-model:value="tabForms['basic'].name" /></div>
                <div><label class="mb-1 block text-sm opacity-60">地区 *</label><Input v-model:value="tabForms['basic'].area" /></div>
                <div><label class="mb-1 block text-sm opacity-60">类型 (bitmask)</label><Input v-model:value="tabForms['basic'].type" type="number" /></div>
                <div><label class="mb-1 block text-sm opacity-60">付费类型 (bitmask)</label><Input v-model:value="tabForms['basic'].payType" type="number" /></div>
                <div><label class="mb-1 block text-sm opacity-60">开服时间 (YYYYMMDD)</label><Input v-model:value="tabForms['basic'].openTime" type="number" /></div>
                <div><label class="mb-1 block text-sm opacity-60">关服时间 (0=运营中)</label><Input v-model:value="tabForms['basic'].closeTime" type="number" /></div>
                <div class="col-span-2"><label class="mb-1 block text-sm opacity-60">支持地区（逗号分隔）</label><Input v-model:value="tabForms['basic'].supportAreas" placeholder="US, JP, KR" /></div>
                <div class="col-span-2"><label class="mb-1 block text-sm opacity-60">拉取渠道（逗号分隔）</label><Input v-model:value="tabForms['basic'].pullChannels" placeholder="google, meta, tiktok" /></div>
                <div class="col-span-2"><label class="mb-1 block text-sm opacity-60">推送渠道（逗号分隔）</label><Input v-model:value="tabForms['basic'].pushChannels" placeholder="google, meta, tiktok" /></div>
              </div>
            </div>

            <!-- ==================== 飞书 Tab ==================== -->
            <div v-else-if="tab.key === 'feishu'">
              <div class="mb-4 flex justify-end gap-2">
                <Button v-if="!isEditing('feishu')" type="primary" @click="startEdit('feishu')">编辑</Button>
                <Button v-if="isEditing('feishu')" type="primary" :loading="saving" @click="savePlatform('feishu')">保存</Button>
                <Button v-if="isEditing('feishu')" @click="cancelEdit('feishu')">取消</Button>
              </div>
              <div v-if="!game.configs?.feishu?.appId && !isEditing('feishu')" class="py-8 text-center text-gray-400">
                未配置飞书凭证，请点击"编辑"添加配置
              </div>
              <Descriptions v-else-if="!isEditing('feishu')" :column="2" bordered size="small">
                <DescriptionsItem label="App ID">{{ game.configs?.feishu?.appId || '-' }}</DescriptionsItem>
                <DescriptionsItem label="App Secret">{{ maskSecret(game.configs?.feishu?.appSecret) }}</DescriptionsItem>
                <DescriptionsItem label="iOS Token"><span class="font-mono text-xs">{{ game.configs?.feishu?.iOSSpreadsheetToken || '-' }}</span></DescriptionsItem>
                <DescriptionsItem label="Android Token"><span class="font-mono text-xs">{{ game.configs?.feishu?.androidSpreadsheetToken || '-' }}</span></DescriptionsItem>
                <DescriptionsItem label="Web Token"><span class="font-mono text-xs">{{ game.configs?.feishu?.webSpreadsheetToken || '-' }}</span></DescriptionsItem>
                <DescriptionsItem label="Sync Token"><span class="font-mono text-xs">{{ game.configs?.feishu?.syncSpreadsheetToken || '-' }}</span></DescriptionsItem>
              </Descriptions>
              <div v-if="isEditing('feishu')" class="grid max-w-3xl grid-cols-2 gap-4">
                <div><label class="mb-1 block text-sm opacity-60">App ID *</label><Input v-model:value="tabForms['feishu'].appId" /></div>
                <div><label class="mb-1 block text-sm opacity-60">App Secret *</label><InputPassword v-model:value="tabForms['feishu'].appSecret" /></div>
                <div><label class="mb-1 block text-sm opacity-60">iOS Token *</label><Input v-model:value="tabForms['feishu'].iOSSpreadsheetToken" /></div>
                <div><label class="mb-1 block text-sm opacity-60">Android Token *</label><Input v-model:value="tabForms['feishu'].androidSpreadsheetToken" /></div>
                <div><label class="mb-1 block text-sm opacity-60">Web Token</label><Input v-model:value="tabForms['feishu'].webSpreadsheetToken" /></div>
                <div><label class="mb-1 block text-sm opacity-60">Sync Token</label><Input v-model:value="tabForms['feishu'].syncSpreadsheetToken" /></div>
              </div>
              <div class="mt-4 flex gap-2">
                <Button :loading="testingFeishu" @click="onTestFeishu">测试连接</Button>
                <Button @click="onViewSheetMapping">查看 Sheet 映射</Button>
              </div>
            </div>

            <!-- ==================== 通用平台 Tab（非 basic/feishu） ==================== -->
            <div v-else>
              <div class="mb-4 flex justify-end gap-2">
                <Button v-if="!isEditing(tab.key)" type="primary" @click="startEdit(tab.key)">编辑</Button>
                <Button v-if="isEditing(tab.key)" type="primary" :loading="saving" @click="savePlatform(tab.key)">保存</Button>
                <Button v-if="isEditing(tab.key)" @click="cancelEdit(tab.key)">取消</Button>
              </div>

              <!-- 查看模式 -->
              <div v-if="!isEditing(tab.key)">
                <div v-if="!game.configs?.[tab.key] || Object.keys(game.configs[tab.key]).length === 0" class="py-8 text-center text-gray-400">
                  未配置，请点击"编辑"添加配置
                </div>
                <Descriptions v-else :column="2" bordered size="small">
                  <DescriptionsItem v-for="(val, key) in game.configs[tab.key]" :key="key" :label="String(key)">
                    <template v-if="isSensitiveField(String(key))">
                      {{ maskSecret(typeof val === 'string' ? val : JSON.stringify(val)) }}
                    </template>
                    <template v-else-if="Array.isArray(val)">
                      <Tag v-for="(item, i) in val" :key="i">{{ item }}</Tag>
                      <span v-if="val.length === 0" class="text-gray-400">-</span>
                    </template>
                    <template v-else>{{ val ?? '-' }}</template>
                  </DescriptionsItem>
                </Descriptions>
              </div>

              <!-- 编辑模式 -->
              <div v-else class="max-w-3xl">
                <div v-for="(val, key) in (tabForms[tab.key] || {})" :key="key" class="mb-3">
                  <label class="mb-1 block text-sm font-medium opacity-70">{{ key }}</label>

                  <!-- 敏感字段用 Password -->
                  <InputPassword
                    v-if="isSensitiveField(String(key)) && (typeof val === 'string')"
                    v-model:value="tabForms[tab.key][key]"
                  />

                  <!-- 字符串/数字用普通 Input -->
                  <Input
                    v-else-if="typeof val === 'string' || typeof val === 'number'"
                    v-model:value="tabForms[tab.key][key]"
                  />

                  <!-- 数组类型：items[] 动态列表 -->
                  <div v-else-if="Array.isArray(val) && val.length > 0 && typeof val[0] === 'object'" class="space-y-3">
                    <Card v-for="(item, idx) in val" :key="idx" size="small" class="border">
                      <template #title><span class="text-sm">凭证 {{ idx + 1 }}</span></template>
                      <template #extra>
                        <Button size="small" danger type="link" @click="val.splice(idx, 1)">删除</Button>
                      </template>
                      <div class="grid grid-cols-2 gap-3">
                        <div v-for="(fieldVal, fieldKey) in item" :key="fieldKey">
                          <label class="mb-1 block text-xs opacity-60">{{ fieldKey }}</label>
                          <InputPassword v-if="isSensitiveField(String(fieldKey))" v-model:value="item[fieldKey]" size="small" />
                          <Input v-else-if="typeof fieldVal === 'string'" v-model:value="item[fieldKey]" size="small" />
                          <Input v-else :value="JSON.stringify(fieldVal)" size="small" @change="(e: any) => { try { item[fieldKey] = JSON.parse(e.target.value) } catch {} }" />
                        </div>
                      </div>
                    </Card>
                    <Button size="small" @click="val.push(JSON.parse(JSON.stringify(val[0] || {})))">+ 添加凭证</Button>
                  </div>

                  <!-- 简单字符串数组：逗号分隔编辑 -->
                  <Input
                    v-else-if="Array.isArray(val)"
                    :value="val.join(', ')"
                    @change="(e: any) => { tabForms[tab.key][key] = e.target.value.split(',').map((s: string) => s.trim()).filter(Boolean) }"
                    placeholder="逗号分隔多个值"
                  />

                  <!-- 其他复杂对象：JSON 编辑 -->
                  <Input
                    v-else
                    :value="JSON.stringify(val)"
                    @change="(e: any) => { try { tabForms[tab.key][key] = JSON.parse(e.target.value) } catch {} }"
                    placeholder="JSON 格式"
                  />
                </div>
                <div v-if="!tabForms[tab.key] || Object.keys(tabForms[tab.key]).length === 0" class="text-gray-400">
                  无已有配置字段，请输入 JSON 格式配置后保存
                </div>
              </div>
            </div>
          </TabPane>
        </Tabs>
      </Card>
      <Card v-else-if="!loading" class="py-8 text-center">
        <p class="text-gray-500">游戏数据加载失败，请返回列表重试</p>
      </Card>
    </Spin>
  </Page>
</template>
