<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { defaultAvatars, type DefaultAvatar } from './default-avatars'

const props = withDefaults(defineProps<{ modelValue?: File | null; previewUrl?: string | null; size?: number }>(), { modelValue: null, previewUrl: null, size: 72 })
const emit = defineEmits<{ 'update:modelValue': [file: File | null]; 'update:previewUrl': [url: string | null] }>()
const input = ref<HTMLInputElement>()
const galleryOpen = ref(false)
const selectedDefaultId = ref<string | null>(null)
const objectUrl = ref<string | null>(null)

function choose() { input.value?.click() }
function releaseObjectUrl() {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
}
function change(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) { ElMessage.warning('头像仅支持 JPG、PNG、GIF 或 WebP'); return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  releaseObjectUrl()
  const preview = URL.createObjectURL(file)
  objectUrl.value = preview
  selectedDefaultId.value = null
  emit('update:modelValue', file)
  emit('update:previewUrl', preview)
  galleryOpen.value = false
}
async function selectDefault(avatar: DefaultAvatar) {
  try {
    const response = await fetch(avatar.src)
    if (!response.ok) throw new Error('avatar asset unavailable')
    const blob = await response.blob()
    const file = new File([blob], `default-${avatar.id}.png`, { type: 'image/png' })
    releaseObjectUrl()
    selectedDefaultId.value = avatar.id
    emit('update:modelValue', file)
    emit('update:previewUrl', avatar.src)
    galleryOpen.value = false
  } catch {
    ElMessage.error('默认头像加载失败，请重试')
  }
}

watch(() => props.previewUrl, value => {
  if (value !== objectUrl.value) releaseObjectUrl()
  selectedDefaultId.value = defaultAvatars.find(avatar => avatar.src === value)?.id || null
})

onBeforeUnmount(releaseObjectUrl)
</script>

<template>
  <div class="ui-avatar-upload" :style="{ '--avatar-upload-size': `${size}px` }">
    <el-avatar :size="size" :src="previewUrl || undefined"><el-icon><Camera /></el-icon></el-avatar>
    <div class="ui-avatar-upload__actions">
      <el-popover v-model:visible="galleryOpen" placement="bottom-start" :width="330" popper-class="avatar-gallery-popover" trigger="click">
        <template #reference><el-button link type="primary" @click.stop>选择卡通头像</el-button></template>
        <div class="ui-avatar-gallery">
          <button v-for="avatar in defaultAvatars" :key="avatar.id" type="button" class="ui-avatar-gallery__item" :class="{ 'is-selected': selectedDefaultId === avatar.id }" :aria-label="avatar.name" :title="avatar.name" @click="selectDefault(avatar)">
            <img :src="avatar.src" :alt="avatar.name">
            <span>{{ avatar.name }}</span>
          </button>
        </div>
      </el-popover>
      <el-button link @click.stop="choose">上传图片</el-button>
    </div>
    <input ref="input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden @change="change" />
  </div>
</template>
