import { onBeforeUnmount, ref, type Ref } from 'vue'

export function useLatestOptions<T>(loader: (keyword: string) => Promise<T[]>, delay = 260) {
  const options = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  let sequence = 0
  let timer: number | undefined

  async function execute(keyword: string, request: number) {
    loading.value = true
    try {
      const result = await loader(keyword.trim())
      if (request === sequence) options.value = result
    } finally {
      if (request === sequence) loading.value = false
    }
  }

  function search(keyword = '') {
    window.clearTimeout(timer)
    const request = ++sequence
    loading.value = true
    timer = window.setTimeout(() => { void execute(keyword, request) }, delay)
  }

  function loadNow(keyword = '') {
    window.clearTimeout(timer)
    const request = ++sequence
    return execute(keyword, request)
  }

  onBeforeUnmount(() => {
    sequence += 1
    window.clearTimeout(timer)
  })

  return { options, loading, search, loadNow }
}
