export type ReleaseViewKey = 'windows' | 'applications' | 'production-ledger' | 'current-production' | 'analytics' | 'workflow-bindings'

export interface ReleaseSearchOption {
  value: string | number
  label: string
  description: string
  keywords: string
}
