<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { createAiModel, createAiProvider, createAiRoute, listAiModels, listAiProviders, listAiRoutes } from '../api/ai'
import type { AiModel, AiProvider, AiRoute } from '../api/ai'
import { apiErrorMessage } from '../api/error'

const route = useRoute()
const section = computed(() => String(route.params.section || 'models'))
const providers = ref<AiProvider[]>([])
const models = ref<AiModel[]>([])
const routes = ref<AiRoute[]>([])
const loading = ref(false)
const drawerOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, string | number>>({ providerCode: '', providerName: '', endpoint: '', providerId: 0, modelCode: '', modelName: '', capabilities: '', credentialSecret: '', capability: '', modelId: 0, priority: 100 })
const isProviders = computed(() => section.value === 'providers')
const isRoutes = computed(() => section.value === 'routes')
const title = computed(() => isProviders.value ? '智能服务商' : isRoutes.value ? '能力路由' : '智能模型')

async function load() {
  loading.value = true
  try {
    if (isProviders.value) providers.value = (await listAiProviders()).data.data
    else if (isRoutes.value) routes.value = (await listAiRoutes()).data.data
    else { models.value = (await listAiModels()).data.data; if (!providers.value.length) providers.value = (await listAiProviders()).data.data }
  } catch (error) { ElMessage.error(apiErrorMessage(error, '智能配置加载失败，请检查权限和服务状态')) } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { providerCode: '', providerName: '', endpoint: '', providerId: providers.value[0]?.id || 0, modelCode: '', modelName: '', capabilities: '', credentialSecret: '', capability: '', modelId: models.value[0]?.id || 0, priority: 100 })
  drawerOpen.value = true
}

async function save() {
  saving.value = true
  try {
    if (isProviders.value) await createAiProvider({ providerCode: String(form.providerCode), providerName: String(form.providerName), endpoint: String(form.endpoint) })
    else if (isRoutes.value) await createAiRoute({ capability: String(form.capability), modelId: Number(form.modelId), priority: Number(form.priority) })
    else await createAiModel({ providerId: Number(form.providerId), modelCode: String(form.modelCode), modelName: String(form.modelName), capabilities: String(form.capabilities), credentialSecret: String(form.credentialSecret) })
    drawerOpen.value = false; ElMessage.success('智能配置已保存'); await load()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '智能配置保存失败，请检查字段和权限')) } finally { saving.value = false }
}

watch(section, load)
onMounted(load)
</script>

<template>
  <section class="ai-page">
    <UiToolbar><span class="muted">服务端统一管理模型凭据和能力路由</span><template #actions><el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增配置</el-button></template></UiToolbar>
    <UiDataTable v-if="isProviders" :data="providers" :loading="loading" row-key="id" border><el-table-column prop="provider_code" label="服务商编码" min-width="160" /><el-table-column prop="provider_name" label="服务商名称" min-width="180" /><el-table-column prop="endpoint" label="服务地址" min-width="260" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column></UiDataTable>
    <UiDataTable v-else-if="isRoutes" :data="routes" :loading="loading" row-key="id" border><el-table-column prop="capability" label="能力名" min-width="220" /><el-table-column prop="model_id" label="模型 ID" width="140" /><el-table-column prop="priority" label="优先级" width="120" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column></UiDataTable>
    <UiDataTable v-else :data="models" :loading="loading" row-key="id" border><el-table-column prop="model_code" label="模型编码" min-width="180" /><el-table-column prop="model_name" label="模型名称" min-width="180" /><el-table-column prop="provider_id" label="服务商 ID" width="130" /><el-table-column prop="capabilities" label="能力集合" min-width="240" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column></UiDataTable>
    <UiFormDrawer v-model="drawerOpen" :title="`新增${title}`" :loading="saving" @submit="save">
      <el-form label-position="top">
        <template v-if="isProviders"><el-form-item label="服务商编码" required><el-input v-model="form.providerCode" /></el-form-item><el-form-item label="服务商名称" required><el-input v-model="form.providerName" /></el-form-item><el-form-item label="服务地址"><el-input v-model="form.endpoint" /></el-form-item></template>
        <template v-else-if="isRoutes"><el-form-item label="能力名" required><el-input v-model="form.capability" placeholder="例如 text.generate" /></el-form-item><el-form-item label="模型 ID" required><el-input v-model.number="form.modelId" type="number" /></el-form-item><el-form-item label="优先级"><el-input v-model.number="form.priority" type="number" /></el-form-item></template>
        <template v-else><el-form-item label="服务商 ID" required><el-input v-model.number="form.providerId" type="number" /></el-form-item><el-form-item label="模型编码" required><el-input v-model="form.modelCode" /></el-form-item><el-form-item label="模型名称" required><el-input v-model="form.modelName" /></el-form-item><el-form-item label="能力集合"><el-input v-model="form.capabilities" placeholder="text.generate,embedding" /></el-form-item><el-form-item label="服务商密钥"><el-input v-model="form.credentialSecret" type="password" show-password /></el-form-item></template>
      </el-form>
    </UiFormDrawer>
  </section>
</template>
