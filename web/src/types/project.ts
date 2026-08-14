import type { ApiResponse } from './auth'
export type ProjectPlanGroupColorToken = 'brand' | 'accent' | 'success' | 'warning' | 'danger' | 'muted'

export type ProjectStatus = 'PLANNING' | 'RUNNING' | 'COMPLETED' | 'SUSPENDED'
export type PlanStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED'
export interface ProjectPhaseOption { value: string; label: string }
export interface ProjectOrganizationOption { id: number; parent_id: number; org_name: string; status: number; children?: ProjectOrganizationOption[] }
export interface ProjectOptions { project_phases: ProjectPhaseOption[]; plan_phases: ProjectPhaseOption[]; organizations: ProjectOrganizationOption[] }
export interface ProjectPlanGroup { id: number; project_id: number; group_name: string; color_key?: ProjectPlanGroupColorToken | null; description?: string | null; sort_no: number; created_at?: string; updated_at?: string }

export interface ProjectRole { id: number; project_id: number; role_code: string; role_name: string; description?: string | null; member_count?: number }
export interface ProjectMember { id: number; project_id: number; user_id: number; username: string; display_name: string; avatar_url?: string | null; status: number; joined_at?: string; roles: ProjectRole[] }
export interface ProjectPlan { id: number; project_id: number; group_id?: number | null; group_name?: string | null; parent_id: number; plan_name: string; plan_code?: string | null; description?: string | null; owner_id?: number | null; owner_name?: string | null; planned_start_date?: string | null; planned_end_date?: string | null; progress: number; status: PlanStatus; phase?: string | null; phase_name?: string | null; lead_org_id?: number | null; lead_org_name?: string | null; cooperating_org_ids?: number[]; cooperating_org_names?: string[]; sort_no: number }
export interface Project { id: number; project_code: string; project_name: string; description?: string | null; status: ProjectStatus; phase?: string | null; phase_name?: string | null; plan_number_rule?: string | null; child_plan_number_rule?: string | null; owner_id: number; owner_name?: string | null; planned_start_date?: string | null; planned_end_date?: string | null; actual_end_date?: string | null; member_count: number; plan_count: number; completed_plan_count: number; plan_progress: number; plans?: ProjectPlan[]; plan_groups?: ProjectPlanGroup[]; members?: ProjectMember[]; roles?: ProjectRole[]; created_at?: string; updated_at?: string }
export interface ProjectUserOption { id: number; username: string; display_name: string; org_id?: number }
export type ProjectResponse<T> = ApiResponse<T>
