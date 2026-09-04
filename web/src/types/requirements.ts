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
  workflow_instance_id?: string | number | null
  dev_status: string
  test_status: string
  baseline_id?: number | null
  source?: string
  created_by?: number | null
  current_handler_user_id?: number | null
  current_handler_user_name?: string | null
  can_edit?: boolean
  created_at?: string
  updated_at?: string
}

export interface RequirementApprovalLog {
  id: number
  action_code: string
  operator_id: number
  operator_name?: string
  target_user_id?: number | null
  target_user_name?: string | null
  comment?: string | null
  created_at: string
  task_type?: string
  assignee_name?: string | null
  task_status?: string
  node_id?: string | null
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
  project_id?: number | null
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
  version_no?: string
  workload_change?: string | null
  current_flow_user_id?: number | null
  current_flow_user_name?: string | null
  can_edit?: boolean
  system_items?: LegacySystemItem[]
  flow_logs?: LegacyFlowLog[]
  versions?: RequirementVersionRow[]
  [key: string]: unknown
}

export interface LegacySystemItem {
  id: number
  requirement_id?: number
  system_role: string
  system_code?: string | null
  system_name?: string | null
  owner_user_id?: number | null
  owner_user_name?: string | null
  members?: Array<{ user_id: number; user_name?: string | null }>
  remark?: string | null
  created_at?: string
}

export interface LegacyMember {
  id: number
  requirement_id: number
  user_id: number
  member_role: string
  username?: string
  display_name?: string
}

export interface LegacyFlowLog {
  id?: number
  action: string
  from_user_id?: number | null
  from_user_name?: string | null
  to_user_id?: number | null
  to_user_name?: string | null
  comment?: string | null
  created_at: string
}

export interface RequirementVersionRow {
  version_no: string
  change_summary?: string | null
  snapshot_json?: string | null
  created_at?: string
}

export interface LegacyDeliverable {
  id: number
  requirement_id: number
  system_item_id?: number | null
  system_code?: string | null
  doc_name?: string | null
  version_no: string
  review_status: string
  review_record_id?: number | null
  review_approver_ids?: string | null
  review_approver_names?: string | null
  review_report_name?: string | null
  review_remark?: string | null
  remark?: string | null
  created_at?: string
  updated_at?: string
}

export interface CoordinationItem {
  id: number
  requirement_id: number
  system_item_id?: number | null
  item_type: string
  system_code?: string | null
  system_name?: string | null
  owner_user_id?: number | null
  owner_user_name?: string | null
  start_date?: string | null
  end_date?: string | null
  status: string
  description?: string | null
  created_at?: string
}

export interface ReviewRecord {
  id: number
  biz_type: string
  biz_id: number
  review_no?: string | null
  reviewer_id: number
  reviewer_name?: string | null
  review_time?: string | null
  conclusion: string
  comment?: string | null
  report_doc_name?: string | null
  created_at?: string
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
  approval_result?: string | null
  workflow_instance_id?: string | null
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
