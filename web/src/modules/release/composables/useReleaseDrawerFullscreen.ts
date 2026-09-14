import { computed, ref, watch, type Ref } from 'vue'

export function useReleaseDrawerFullscreen(open: Ref<boolean>, defaultSize: string) {
  const fullscreen = ref(false)
  const size = computed(() => fullscreen.value ? '100vw' : defaultSize)
  // Reset on entry, not while the closing animation is still visible.
  watch(open, value => { if (value) fullscreen.value = false }, { flush: 'sync' })
  const toggle = () => { if (open.value) fullscreen.value = !fullscreen.value }
  return { fullscreen, size, toggle }
}
