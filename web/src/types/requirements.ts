// 需求管理平台类型（字段与后端 req_* 表列一致，snake_case）
export interface RequirementEnums {
  options: Record<string, string[]>
  fieldLabels: Record<string, string>
}

export interface RequirementProject {
  id: number
  project_code: string
  project_name: string
  project_type: string
  start_time: string | null
  status: string
  description?: string | null
  difference_count?: number
  reviewed_count?: number
  created_at?: string
  updated_at?: string
}

export interface ProjectMember {
  id: number
  project_id: number
  user_id: number
  member_role: string
  username?: string
  display_name?: string
}

export interface RequirementDifference {
  id: number
  project_id: number
  seq_no?: number
  business_conglomerate?: string
  business_section?: string
  business_group?: string
  requirement_no?: string
  category?: string
  name: string
  system_id?: number | null
  jinke_practice?: string
  difference_type?: string
  monshang_practice?: string
  difference_desc?: string
  monshang_dept?: string
  monshang_analyst?: string
  jinke_analyst?: string
  adapt_mode?: string
  handle_status?: string
  coord_group?: string
  solution?: string
  is_special?: string
  decision_level?: string
  decision_conclusion?: string
  monshang_confirm_dept?: string
  jinke_confirmer?: string
  review_status: string
  review_comment?: string | null
  reviewed_by?: number | null
  reviewed_at?: string | null
  dev_status: string
  test_status: string
  baseline_id?: number | null
  source?: string
  created_at?: string
  updated_at?: string
}

export interface RequirementBaseline {
  id: number
  project_id: number
  baseline_no: string
  baseline_name?: string
  status: string
  difference_count: number
  item_count?: number
  remark?: string
  created_by?: number
  created_at?: string
}

export interface BaselineItem {
  id: number
  baseline_id: number
  difference_id: number
  snapshot_json: string
  created_at?: string
}

export interface LegacyRequirement {
  id: number
  requirement_no: string
  requirement_name: string
  business_group: string
  current_stage: string
  requirement_status?: string
  propose_stage_status: string
  docking_stage_status: string
  workload_stage_status: string
  project_stage_status: string
  soft_stage_status: string
  launch_stage_status: string
  source?: string
  [key: string]: unknown
}

export interface RequirementSystem {
  id: number
  system_code: string
  system_name: string
  english_name?: string
  conglomerate?: string
  status: string
  logical_subsystem_code?: string
  logical_subsystem_name?: string
  business_component_code?: string
  business_component_name?: string
  business_domain?: string
  product_view?: string
  launch_point?: string
  category?: string
  introduction?: string
  disaster_level?: string
  source_type?: string
}

export interface ChangeLogRow {
  field_name: string | null
  old_value: string | null
  new_value: string | null
  change_type: string
  operator_id: number
  operator_name?: string
  source: string
  trace_id?: string
  created_at: string
}

export interface StageLogRow {
  from_stage: string
  to_stage: string
  from_status: string
  to_status: string
  operator_id: number
  operator_name?: string
  comment?: string
  created_at: string
}

export interface ImportBatch {
  id: number
  biz_type: string
  project_id?: number | null
  file_name?: string
  template_type?: string
  total_rows: number
  success_rows: number
  error_rows: number
  status: string
  operator_name?: string
  created_at?: string
}

export interface ImportPreviewReport {
  bizType: string
  projectId?: number | null
  totalRows: number
  successRows: number
  errorRows: number
  errors: Array<{ row: number; messages: string[] }>
  rows: Array<Record<string, unknown>>
}

export interface PageEnvelope<T> {
  records: T[]
  total: number
  page: number
  size: number
}
