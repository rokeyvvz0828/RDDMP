<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RefreshRight, TopRight } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  url: string | null
  fileName?: string
}>(), {
  fileName: '文件预览'
})

const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()
const loading = ref(false)
const failed = ref(false)
const frameKey = ref(0)

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

watch(() => [props.modelValue, props.url], ([open, url]) => {
  loading.value = Boolean(open && url)
  failed.value = false
})

function refresh() {
  if (!props.url) return
  loading.value = true
  failed.value = false
  frameKey.value += 1
}

function openInNewWindow() {
  if (props.url) window.open(props.url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="fileName"
    width="min(94vw, 1440px)"
    top="3vh"
    class="ui-file-preview"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="ui-file-preview__toolbar">
      <span>{{ fileName }}</span>
      <div>
        <el-tooltip content="刷新预览" placement="bottom">
          <el-button :icon="RefreshRight" circle aria-label="刷新预览" :disabled="!url" @click="refresh" />
        </el-tooltip>
        <el-tooltip content="在新窗口打开" placement="bottom">
          <el-button :icon="TopRight" circle aria-label="在新窗口打开" :disabled="!url" @click="openInNewWindow" />
        </el-tooltip>
      </div>
    </div>
    <div v-loading="loading" class="ui-file-preview__viewport">
      <el-result v-if="failed" icon="error" title="预览加载失败" sub-title="请检查预览服务后重试">
        <template #extra><el-button type="primary" @click="refresh">重新加载</el-button></template>
      </el-result>
      <iframe
        v-else-if="url"
        :key="frameKey"
        :src="url"
        :title="`${fileName}预览`"
        sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-downloads"
        referrerpolicy="no-referrer"
        @load="loading = false"
        @error="failed = true; loading = false"
      />
      <el-empty v-else description="暂无可预览文件" />
    </div>
    <template #footer><el-button @click="visible = false">关闭</el-button></template>
  </el-dialog>
</template>
