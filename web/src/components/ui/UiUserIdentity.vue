<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import UiPersonProfileCard from './UiPersonProfileCard.vue'
import { toUserId, usePersonDirectoryStore } from '../../stores/person-directory'
import type { UserProfile } from '../../types/system'

type PersonVariant = 'compact' | 'standard' | 'full'

const props = withDefaults(defineProps<{
  user?: UserProfile | null
  userId?: number | string | null
  variant?: PersonVariant
  size?: number | string
  showName?: boolean
  showProfile?: boolean
  showAvatar?: boolean
  showPhone?: boolean
  showTeam?: boolean
  fallbackName?: string | null
}>(), {
  user: null,
  userId: null,
  // 默认保持改造前的「头像 + 姓名」外观，使既有调用点零改动即可维持原有观感。
  variant: 'compact',
  size: 32,
  showName: true,
  showProfile: true,
  showAvatar: undefined,
  showPhone: undefined,
  showTeam: undefined,
  fallbackName: null
})

const store = usePersonDirectoryStore()
const resolvedId = computed(() => toUserId(props.userId ?? props.user?.id))
const profile = computed(() => store.get(resolvedId.value))
const state = computed(() => store.stateOf(resolvedId.value))

const showAvatarFinal = computed(() => props.showAvatar ?? true)
const showPhoneFinal = computed(() => props.showPhone ?? props.variant !== 'compact')
const showTeamFinal = computed(() => props.showTeam ?? props.variant === 'full')
const displayName = computed(() => profile.value?.displayName || props.user?.displayName || props.fallbackName || '未登录用户')
const initial = computed(() => (profile.value?.displayName || props.user?.displayName || '用').slice(0, 1))
const avatarUrl = computed(() => profile.value?.avatarUrl || props.user?.avatarUrl || undefined)
const phone = computed(() => (showPhoneFinal.value ? profile.value?.mobilePhone || null : null))
const team = computed(() => (showTeamFinal.value ? profile.value?.orgName || null : null))
// 没有可解析标识时不悬浮、不请求；这是既有调用点的兼容边界。
const profileEnabled = computed(() => props.showProfile && resolvedId.value !== null)
const trigger = ref<'hover' | 'click'>('hover')
const hoverMedia = typeof window === 'undefined' ? undefined : window.matchMedia?.('(hover: none)')

function syncTrigger() {
  trigger.value = hoverMedia?.matches ? 'click' : 'hover'
}

function loadProfile() {
  if (resolvedId.value === null) return
  if (!profileEnabled.value && !showPhoneFinal.value && !showTeamFinal.value) return
  store.ensure([resolvedId.value])
}

onMounted(() => {
  syncTrigger()
  loadProfile()
})
watch(resolvedId, () => loadProfile())
watch([showPhoneFinal, showTeamFinal, profileEnabled], () => loadProfile())
hoverMedia?.addEventListener?.('change', syncTrigger)
onBeforeUnmount(() => hoverMedia?.removeEventListener?.('change', syncTrigger))
</script>

<template>
  <el-popover
    v-if="profileEnabled"
    :trigger="trigger"
    :show-after="trigger === 'hover' ? 120 : 0"
    :width="300"
    placement="top-start"
    popper-class="ui-person-popover"
  >
    <template #reference>
      <span class="ui-user-identity" :class="`ui-user-identity--${variant}`">
        <el-avatar v-if="showAvatarFinal" :size="size" :src="avatarUrl">
          <span v-if="user || profile">{{ initial }}</span>
          <el-icon v-else><UserFilled /></el-icon>
        </el-avatar>
        <span class="ui-user-identity__lines">
          <strong v-if="showName">{{ displayName }}</strong>
          <span v-if="phone" class="ui-user-identity__sub">{{ phone }}</span>
          <span v-if="team" class="ui-user-identity__sub">{{ team }}</span>
        </span>
      </span>
    </template>
    <UiPersonProfileCard
      :profile="profile"
      :state="state"
      :fallback-name="displayName"
      :fallback-account="user?.username || null"
      :fallback-avatar-url="avatarUrl || null"
      @retry="store.retry([resolvedId])"
    />
  </el-popover>
  <span v-else class="ui-user-identity" :class="`ui-user-identity--${variant}`">
    <el-avatar v-if="showAvatarFinal" :size="size" :src="avatarUrl">
      <span v-if="user || profile">{{ initial }}</span>
      <el-icon v-else><UserFilled /></el-icon>
    </el-avatar>
    <span class="ui-user-identity__lines">
      <strong v-if="showName">{{ displayName }}</strong>
      <span v-if="phone" class="ui-user-identity__sub">{{ phone }}</span>
      <span v-if="team" class="ui-user-identity__sub">{{ team }}</span>
    </span>
  </span>
</template>
