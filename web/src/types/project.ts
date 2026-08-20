import type { ApiResponse } from './auth'
import type { ProjectAttachment } from './attachments'
export type ProjectPlanGroupColorToken = 'brand' | 'accent' | 'success' | 'warning' | 'danger' | 'muted'

export type ProjectStatus = 'PLANNING' | 'RUNNING' | 'COMPLETED' | 'SUSPENDED'
export type PlanStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED'
export interface ProjectPhaseOption { value: string; label: string }
export interface ProjectOrganizationOption { id: number; parent_id: number; org_name: string; status: number; children?: ProjectOrganizationOption[] }
export interface ProjectOrganization { id: number; project_id: number; parent_id: number; org_code: string; org_name: string; sort_no: number; status: number; created_at?: string; updated_at?: string }
export interface ProjectOptions { project_phases: ProjectPhaseOption[]; plan_phases: ProjectPhaseOption[]; risk_urgencies: ProjectPhaseOption[]; risk_report_levels: ProjectPhaseOption[]; risk_statuses: ProjectPhaseOption[]; risk_attention_levels: ProjectPhaseOption[]; risk_escalation_levels: ProjectPhaseOption[]; risk_problem_levels: ProjectPhaseOption[]; organizations: ProjectOrganizationOption[] }
export interface ProjectPlanGroup { id: number; project_id: number; phase: string; phase_name?: string | null; stage_plan_code?: string | null; group_name: string; color_key?: ProjectPlanGroupColorToken | null; description?: string | null; sort_no: number; created_at?: string; updated_at?: string }
export interface ProjectPlanGroupPayload { phase: string; color_key?: ProjectPlanGroupColorToken; description?: string; sort_no?: number }

export interface ProjectRisk {
  id: number; project_id: number; risk_code: string; occurred_date?: string | null;
  project_phase?: string | null; project_phase_name?: string | null; urgency?: string | null; urgency_name?: string | null;
  report_level?: string | null; report_level_name?: string | null; current_status?: string | null; current_status_name?: string | null;
  proposer_org_id?: number | null; proposer_org_name?: string | null; proposer_subsystem?: string | null; proposer_contact_name?: string | null; proposer_contact_phone?: string | null;
  involved_org_id?: number | null; involved_org_name?: string | null; involved_subsystem?: string | null; problem_description?: string | null;
  expected_resolution_date?: string | null; suggested_solution?: string | null; current_handler_name?: string | null; current_handler_phone?: string | null;
  progress_description?: string | null; attention_level?: string | null; attention_level_name?: string | null; problem_nature?: string | null; problem_domain?: string | null; pmo_contact?: string | null;
  escalation_level?: string | null; escalation_level_name?: string | null; current_problem_level?: string | null; current_problem_level_name?: string | null;
  planned_resolution_date?: string | null; actual_resolution_date?: string | null; resolution_solution?: string | null; created_at?: string; updated_at?: string;
}
export interface ProjectRiskComment { id: number; project_id: number; risk_id: number; user_id: number; display_name?: string | null; username?: string | null; avatar_url?: string | null; org_name?: string | null; comment_text: string; created_at?: string; updated_at?: string }

export interface ProjectRoleMember { id: number; user_id: number; username: string; display_name: string; avatar_url?: string | null }
export interface ProjectRole { id: number; project_id: number; role_code: string; role_name: string; description?: string | null; member_count?: number; members?: ProjectRoleMember[] }
export interface ProjectMember { id: number; project_id: number; user_id: number; org_id?: number | null; org_name?: string | null; username: string; display_name: string; avatar_url?: string | null; status: number; joined_at?: string; roles: ProjectRole[] }
export interface ProjectPlan { id: number; project_id: number; group_id?: number | null; stage_plan_code?: string | null; group_name?: string | null; parent_id: number; plan_name: string; plan_code?: string | null; description?: string | null; owner_id?: number | null; owner_name?: string | null; planned_start_date?: string | null; planned_end_date?: string | null; progress: number; status: PlanStatus; phase?: string | null; phase_name?: string | null; lead_org_id?: number | null; lead_org_name?: string | null; cooperating_org_ids?: number[]; cooperating_org_names?: string[]; sort_no: number; created_at?: string | null }
export interface Project { id: number; project_code: string; project_name: string; description?: string | null; status: ProjectStatus; plan_number_rule?: string | null; child_plan_number_rule?: string | null; risk_number_rule?: string | null; owner_id: number; owner_name?: string | null; planned_start_date?: string | null; planned_end_date?: string | null; actual_end_date?: string | null; member_count: number; plan_count: number; completed_plan_count: number; plan_progress: number; plans?: ProjectPlan[]; plan_groups?: ProjectPlanGroup[]; risks?: ProjectRisk[]; members?: ProjectMember[]; roles?: ProjectRole[]; project_organizations?: ProjectOrganization[]; attachments?: ProjectAttachment[]; created_at?: string; updated_at?: string }
export interface ProjectUserOption { id: number; username: string; display_name: string; org_id?: number }
export type ProjectResponse<T> = ApiResponse<T>
