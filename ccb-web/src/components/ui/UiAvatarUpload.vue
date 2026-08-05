<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{ modelValue?: File | null; previewUrl?: string | null; size?: number }>(), { modelValue: null, previewUrl: null, size: 72 })
const emit = defineEmits<{ 'update:modelValue': [file: File | null]; 'update:previewUrl': [url: string | null] }>()
const input = ref<HTMLInputElement>()
function choose() { input.value?.click() }
function change(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) { ElMessage.warning('头像仅支持 JPG、PNG、GIF 或 WebP'); return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  emit('update:modelValue', file)
  emit('update:previewUrl', URL.createObjectURL(file))
}
</script>

<template>
  <div class="ui-avatar-upload" :style="{ '--avatar-upload-size': `${size}px` }" @click="choose">
    <el-avatar :size="size" :src="previewUrl || undefined"><el-icon><Camera /></el-icon></el-avatar>
    <span>更换头像</span>
    <input ref="input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden @change="change" />
  </div>
</template>