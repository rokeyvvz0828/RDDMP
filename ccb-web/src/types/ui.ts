export type AppearanceMode = 'light' | 'dark' | 'system'
export type PaletteKey = 'ocean' | 'emerald' | 'sunset' | 'graphite' | 'tech-blue' | 'violet' | 'amber'
export type LayoutMode = 'side' | 'top' | 'mixed'
export type DensityMode = 'comfortable' | 'compact'

export interface PaletteOption {
  key: PaletteKey
  label: string
  description: string
  color: string
  accent: string
}

export const paletteOptions: PaletteOption[] = [
  { key: 'ocean', label: '海湾蓝', description: '清晰、克制、适合平台运营', color: '#147d92', accent: '#d86b42' },
  { key: 'emerald', label: '森林绿', description: '稳定、自然、适合流程管理', color: '#247a62', accent: '#be6a3e' },
  { key: 'sunset', label: '落日橙', description: '温暖、醒目、适合业务协同', color: '#b9503d', accent: '#e3a23b' },
  { key: 'graphite', label: '石墨灰', description: '中性、专业，适合高密度工作', color: '#596273', accent: '#b06c46' },
  { key: 'tech-blue', label: '科技蓝', description: '清晰、理性，适合平台与数据场景', color: '#2563eb', accent: '#22d3ee' },
  { key: 'violet', label: '星云紫', description: '灵动、现代，适合创新业务协作', color: '#7657a6', accent: '#d977b7' },
  { key: 'amber', label: '琥珀青', description: '明快、稳重，兼顾提醒与信息层级', color: '#b45309', accent: '#0f766e' }
]

export const layoutOptions: Array<{ key: LayoutMode; label: string; description: string }> = [
  { key: 'side', label: '侧边布局', description: '菜单固定在左侧' },
  { key: 'top', label: '顶部布局', description: '菜单横向置于顶部' },
  { key: 'mixed', label: '混合布局', description: '顶部导航配合侧边菜单' }
]
