import type { ApiResponse } from './auth'

export interface FormMetadataRule {
  id?: number
  action_code: string
  condition_type: string
  condition_key: string
  required: number | boolean
  editable: number | boolean
  visible: number | boolean
  validation_json?: string | null
  enabled?: number | boolean
}

export interface FormMetadataOption {
  id?: number
  option_value: string
  option_label: string
  option_group?: string | null
  sort_no: number
  enabled?: number | boolean
}

export interface FormMetadataScope {
  id: number
  scope_key: string
  scope_name: string
  module_key: string
  entity_type: string
  form_key: string
  status_field?: string | null
  permission_prefix: string
  published_revision_id?: number | null
  enabled: number
  section_count?: number
  field_count?: number
}

export interface FormMetadataSection {
  id: number
  scope_id: number
  section_key: string
  title: string
  layout_mode: 'left' | 'right' | 'full'
  show_title: number | boolean
  collapsed: number | boolean
  sort_no: number
  is_builtin?: number
  enabled: number
}

export interface FormMetadataField {
  id: number
  scope_id: number
  section_id?: number | null
  field_key: string
  label: string
  field_kind: 'builtin' | 'extension'
  input_type: string
  value_type: string
  source_type: string
  source_key?: string | null
  component_key?: string | null
  native_column?: string | null
  multiple: number | boolean
  column_span: number
  visible: number | boolean
  list_visible: number | boolean
  filterable: number | boolean
  sortable: number | boolean
  dashboard_dimension: number | boolean
  placeholder?: string | null
  help_text?: string | null
  default_value_json?: string | null
  sort_no: number
  is_builtin?: number
  enabled: number
  rules: FormMetadataRule[]
  options: FormMetadataOption[]
}

export interface FormMetadataSchema {
  scope: FormMetadataScope
  sections: FormMetadataSection[]
  fields: FormMetadataField[]
  revisions: Array<{ id: number; revision_no: number; revision_status: string; change_summary?: string | null; created_at?: string; published_at?: string | null }>
}

export type FormMetadataSchemaResponse = ApiResponse<FormMetadataSchema>
