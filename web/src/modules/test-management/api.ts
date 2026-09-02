/*
文件：web/src/modules/test-management/api.ts
说明：测试管理营业日功能的前端类型与 HTTP 请求契约。
用途：集中封装环境、日历、跑批需求、用户目录和 XLSX 下载接口。
作者：hengguan
*/
import http from "../../api/http";
import type { ApiResponse } from "../../types/auth";

export interface BusinessDayPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
export interface Environment {
  id: number;
  env_code: string;
  env_name: string;
  purpose?: string;
  theme: Theme;
  sort_no: number;
  enabled: number | boolean;
  remark?: string;
  schedule_count?: number;
  requirement_count?: number;
  updated_at?: string;
}
export type Theme = "brand" | "success" | "warning" | "danger" | "accent";
export interface Schedule {
  id: number;
  env_code: string;
  env_name?: string;
  theme?: Theme;
  natural_date: string;
  business_date: string;
  has_batch: number | boolean;
  batch_type?: string;
  batch_time?: string;
  systems: string[];
  validation_content?: string;
  maintainer?: string;
  created_at?: string;
  updated_at?: string;
  overwritten?: boolean;
}
export interface BatchRequirement extends Schedule {
  proposer_id: number;
  proposer_name?: string;
  proposer_username?: string;
  proposer_org_name?: string;
  proposer_mobile_phone?: string;
  reviewer_id?: number;
  reviewer_name?: string;
  adoption: "PENDING" | "ACCEPTED" | "REJECTED";
  review_comment?: string;
  reviewed_at?: string;
}
export interface UserDirectoryItem {
  id: number;
  username: string;
  displayName: string;
  orgId: number;
  orgName?: string;
  mobilePhone?: string;
}
export interface PageParams {
  page: number;
  size: number;
  keyword?: string;
}
export interface ScheduleFilters extends PageParams {
  envCode?: string;
  dateFrom?: string;
  dateTo?: string;
  hasBatch?: boolean;
  batchType?: string;
}
export interface RequirementFilters extends PageParams {
  envCode?: string;
  naturalDate?: string;
  adoption?: string;
}

const base = "/test-management/business-days";

export const listEnvironments = (params: PageParams & { enabled?: boolean }) =>
  http.get<ApiResponse<BusinessDayPage<Environment>>>(`${base}/environments`, {
    params,
  });
export const listEnvironmentOptions = () =>
  http.get<ApiResponse<Environment[]>>(`${base}/environment-options`);
export const createEnvironment = (data: Partial<Environment>) =>
  http.post<ApiResponse<Environment>>(`${base}/environments`, data);
export const updateEnvironment = (id: number, data: Partial<Environment>) =>
  http.put<ApiResponse<Environment>>(`${base}/environments/${id}`, data);
export const deleteEnvironment = (id: number) =>
  http.delete<ApiResponse<void>>(`${base}/environments/${id}`);

export const listSchedules = (params: ScheduleFilters) =>
  http.get<ApiResponse<BusinessDayPage<Schedule>>>(`${base}/schedules`, {
    params,
  });
export const listOverview = (params: { month: string; envCode?: string }) =>
  http.get<ApiResponse<Schedule[]>>(`${base}/overview`, { params });
export const createSchedule = (data: Partial<Schedule>) =>
  http.post<ApiResponse<Schedule>>(`${base}/schedules`, data);
export const updateSchedule = (id: number, data: Partial<Schedule>) =>
  http.put<ApiResponse<Schedule>>(`${base}/schedules/${id}`, data);
export const deleteSchedule = (id: number) =>
  http.delete<ApiResponse<void>>(`${base}/schedules/${id}`);
export const importSchedules = (file: File) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<
    ApiResponse<{ total: number; created: number; overwritten: number }>
  >(`${base}/schedules/import`, data, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const listRequirements = (params: RequirementFilters) =>
  http.get<ApiResponse<BusinessDayPage<BatchRequirement>>>(
    `${base}/requirements`,
    { params },
  );
export const createRequirement = (data: Partial<BatchRequirement>) =>
  http.post<ApiResponse<BatchRequirement>>(`${base}/requirements`, data);
export const updateRequirement = (
  id: number,
  data: Partial<BatchRequirement>,
) =>
  http.put<ApiResponse<BatchRequirement>>(`${base}/requirements/${id}`, data);
export const reviewRequirement = (
  id: number,
  adoption: "ACCEPTED" | "REJECTED",
  comment: string,
) =>
  http.patch<ApiResponse<BatchRequirement>>(
    `${base}/requirements/${id}/adoption`,
    { adoption, comment },
  );
export const deleteRequirement = (id: number) =>
  http.delete<ApiResponse<void>>(`${base}/requirements/${id}`);
export const listBusinessDayUsers = (keyword?: string) =>
  http.get<ApiResponse<UserDirectoryItem[]>>(`${base}/users`, {
    params: { keyword },
  });

// 关键逻辑：文件响应使用临时对象 URL 触发下载，完成后立即释放，避免浏览器内存长期占用。
export async function downloadBusinessDayFile(
  path: string,
  params: Record<string, unknown>,
  fallbackName: string,
) {
  const response = await http.get<Blob>(`${base}${path}`, {
    params,
    responseType: "blob",
  });
  const disposition = String(response.headers["content-disposition"] || "");
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const filename = encoded ? decodeURIComponent(encoded) : fallbackName;
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export type TestDomain =
  "application-assembly" | "user-testing" | "non-functional" | "security";
export interface TestProjectOption {
  id: number;
  project_code: string;
  project_name: string;
  status: string;
}
export interface ParticipatingSystem {
  physical_subsystem_id: number;
  code: string;
  short_name: string;
  name: string;
  enabled: number | boolean;
  remark?: string;
}
export interface SystemRole {
  id: number;
  user_id: number;
  role_code: string;
  role_name: string;
  created_at?: string;
}
export interface TestRound {
  id: number;
  round_code: string;
  round_name: string;
  planned_start_date?: string;
  planned_end_date?: string;
  status: string;
  sort_no: number;
  remark?: string;
}
export interface TestCycle {
  id: number;
  round_id?: number;
  cycle_code: string;
  cycle_name: string;
  planned_start_date?: string;
  planned_end_date?: string;
  status: string;
  sort_no: number;
  remark?: string;
  outside_round_warning?: boolean;
}
export interface TestDictionary {
  id: number;
  dictionary_code: string;
  dictionary_name: string;
  source_type: string;
  enabled: number | boolean;
  remark?: string;
}
export interface TestDictionaryOption {
  id: number;
  option_code: string;
  option_name: string;
  enabled: number | boolean;
  sort_no: number;
  remark?: string;
}
export interface ImportResult {
  total: number;
  valid: number;
  written: number;
  success: boolean;
  errors: Array<{ row_number: number; message: string }>;
}

const configurationBase = (domain: TestDomain) =>
  `/test-management/configuration/${domain}`;
export const listTestProjects = (domain: TestDomain) =>
  http.get<ApiResponse<TestProjectOption[]>>(
    `${configurationBase(domain)}/projects`,
  );
export const listParticipatingSystems = (
  domain: TestDomain,
  params: PageParams & { projectId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<ParticipatingSystem>>>(
    `${configurationBase(domain)}/systems`,
    { params },
  );
export const setParticipatingSystem = (
  domain: TestDomain,
  projectId: number,
  physicalId: number,
  data: { enabled: boolean; confirmed?: boolean; remark?: string },
) =>
  http.put<
    ApiResponse<
      | ParticipatingSystem
      | { confirmation_required: boolean; impact: Record<string, number> }
    >
  >(`${configurationBase(domain)}/systems/${physicalId}`, data, {
    params: { projectId },
  });
export const listSystemRoles = (
  domain: TestDomain,
  params: PageParams & { projectId: number; physicalId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<SystemRole>>>(
    `${configurationBase(domain)}/roles`,
    { params },
  );
export const listConfigurationUsers = (domain: TestDomain, keyword?: string) =>
  http.get<ApiResponse<UserDirectoryItem[]>>(
    `${configurationBase(domain)}/users`,
    { params: { keyword } },
  );
export const createSystemRole = (
  domain: TestDomain,
  projectId: number,
  physicalId: number,
  data: { user_id: number; role_code: string },
) =>
  http.post<ApiResponse<SystemRole>>(
    `${configurationBase(domain)}/roles`,
    data,
    { params: { projectId, physicalId } },
  );
export const deleteSystemRole = (
  domain: TestDomain,
  id: number,
  confirmed = true,
) =>
  http.delete<
    ApiResponse<{ deleted?: boolean; confirmation_required?: boolean }>
  >(`${configurationBase(domain)}/roles/${id}`, { data: { confirmed } });
export const listTestRounds = (
  domain: TestDomain,
  params: PageParams & { projectId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<TestRound>>>(
    `${configurationBase(domain)}/rounds`,
    { params },
  );
export const saveTestRound = (
  domain: TestDomain,
  projectId: number,
  record: Partial<TestRound>,
) =>
  record.id
    ? http.put<ApiResponse<TestRound>>(
        `${configurationBase(domain)}/rounds/${record.id}`,
        record,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestRound>>(
        `${configurationBase(domain)}/rounds`,
        record,
        { params: { projectId } },
      );
export const deleteTestRound = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${configurationBase(domain)}/rounds/${id}`, {
    params: { projectId },
  });
export const listTestCycles = (
  domain: TestDomain,
  params: PageParams & { projectId: number; roundId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<TestCycle>>>(
    `${configurationBase(domain)}/rounds/${params.roundId}/cycles`,
    { params },
  );
export const saveTestCycle = (
  domain: TestDomain,
  projectId: number,
  roundId: number,
  record: Partial<TestCycle>,
) =>
  record.id
    ? http.put<ApiResponse<TestCycle>>(
        `${configurationBase(domain)}/rounds/${roundId}/cycles/${record.id}`,
        record,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestCycle>>(
        `${configurationBase(domain)}/rounds/${roundId}/cycles`,
        record,
        { params: { projectId } },
      );
export const deleteTestCycle = (
  domain: TestDomain,
  projectId: number,
  roundId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(
    `${configurationBase(domain)}/rounds/${roundId}/cycles/${id}`,
    { params: { projectId } },
  );
export const listTestDictionaries = (
  domain: TestDomain,
  params: PageParams & { projectId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<TestDictionary>>>(
    `${configurationBase(domain)}/dictionaries`,
    { params },
  );
export const saveTestDictionary = (
  domain: TestDomain,
  projectId: number,
  record: Partial<TestDictionary>,
) =>
  record.id
    ? http.put<ApiResponse<TestDictionary>>(
        `${configurationBase(domain)}/dictionaries/${record.id}`,
        record,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestDictionary>>(
        `${configurationBase(domain)}/dictionaries`,
        record,
        { params: { projectId } },
      );
export const deleteTestDictionary = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(
    `${configurationBase(domain)}/dictionaries/${id}`,
    { params: { projectId } },
  );
export const listTestDictionaryOptions = (
  domain: TestDomain,
  params: PageParams & { projectId: number; dictionaryId: number },
) =>
  http.get<ApiResponse<BusinessDayPage<TestDictionaryOption>>>(
    `${configurationBase(domain)}/dictionaries/${params.dictionaryId}/options`,
    { params },
  );
export const saveTestDictionaryOption = (
  domain: TestDomain,
  projectId: number,
  dictionaryId: number,
  record: Partial<TestDictionaryOption>,
) =>
  record.id
    ? http.put<ApiResponse<TestDictionaryOption>>(
        `${configurationBase(domain)}/dictionaries/${dictionaryId}/options/${record.id}`,
        record,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestDictionaryOption>>(
        `${configurationBase(domain)}/dictionaries/${dictionaryId}/options`,
        record,
        { params: { projectId } },
      );
export const deleteTestDictionaryOption = (
  domain: TestDomain,
  projectId: number,
  dictionaryId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(
    `${configurationBase(domain)}/dictionaries/${dictionaryId}/options/${id}`,
    { params: { projectId } },
  );
export interface TestExecution {
  id: number;
  case_id: number;
  case_code: string;
  case_name: string;
  case_type?: string;
  scope_code?: string;
  scope_name?: string;
  execution_status: string;
  executor_name?: string;
  executed_at?: string;
  defect_count: number;
  invalidated?: boolean | number;
  remark_html?: string;
}
export interface TestExecutionTree {
  systems: ParticipatingSystem[];
  rounds: TestRound[];
  cycles: TestCycle[];
  directories: Array<{
    id: number;
    physical_subsystem_id: number;
    round_id: number;
    cycle_id: number;
    parent_id?: number;
    directory_name: string;
    sort_no?: number;
    execution_count: number;
  }>;
}
const executionBase = (domain: TestDomain) =>
  `/test-management/execution/${domain}`;
export const getTestExecutionTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestExecutionTree>>(`${executionBase(domain)}/tree`, {
    params: { projectId },
  });
export const listTestExecutions = (
  domain: TestDomain,
  params: PageParams & {
    projectId: number;
    physicalSubsystemId?: number;
    roundId?: number;
    cycleId?: number;
    directoryId?: number;
    keyword?: string;
    status?: string[];
    executorId?: number;
    defectLink?: string;
    validity?: string;
    sortBy?: string;
    sortOrder?: string;
  },
) =>
  http.get<ApiResponse<BusinessDayPage<TestExecution>>>(executionBase(domain), {
    params,
  });
export const previewTestExecutionImport = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/import/preview`,
    data,
    { params: { projectId } },
  );
export const importTestExecutions = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/import`,
    data,
    { params: { projectId } },
  );
export const saveTestExecutionDirectory = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
  id?: number,
) =>
  id
    ? http.put<ApiResponse<Record<string, unknown>>>(
        `${executionBase(domain)}/directories/${id}`,
        data,
        { params: { projectId } },
      )
    : http.post<ApiResponse<Record<string, unknown>>>(
        `${executionBase(domain)}/directories`,
        data,
        { params: { projectId } },
      );
export const deleteTestExecutionDirectory = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${executionBase(domain)}/directories/${id}`, {
    params: { projectId },
  });
export const getTestExecutionDetail = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.get<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/${id}`,
    { params: { projectId } },
  );
export const saveTestExecutionResult = (
  domain: TestDomain,
  projectId: number,
  id: number,
  data: Record<string, unknown>,
) =>
  http.put<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/${id}/result`,
    data,
    { params: { projectId } },
  );
export const batchTestExecutionStatus = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/batch/status`,
    data,
    { params: { projectId } },
  );
export const moveTestExecutions = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/move`,
    data,
    { params: { projectId } },
  );
export const removeTestExecutions = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${executionBase(domain)}/remove`,
    data,
    { params: { projectId } },
  );
export const associateTestExecutionDefects = (
  domain: TestDomain,
  projectId: number,
  id: number,
  defectIds: number[],
) =>
  http.post<ApiResponse<void>>(
    `${executionBase(domain)}/${id}/defects`,
    { defect_ids: defectIds },
    { params: { projectId } },
  );
export const detachTestExecutionDefect = (
  domain: TestDomain,
  projectId: number,
  id: number,
  defectId: number,
) =>
  http.delete<ApiResponse<void>>(
    `${executionBase(domain)}/${id}/defects/${defectId}`,
    { params: { projectId } },
  );
export async function downloadTestExecutionFile(
  domain: TestDomain,
  params: Record<string, unknown>,
) {
  const response = await http.get<Blob>(`${executionBase(domain)}/export`, {
    params,
    responseType: "blob",
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = "测试执行导出.xlsx";
  link.click();
  URL.revokeObjectURL(url);
}
export interface TestDefect {
  id: number;
  defect_code: string;
  summary: string;
  physical_subsystem_id: number;
  physical_system_name?: string;
  defect_category: string;
  severity: string;
  priority: string;
  urgency: string;
  status: string;
  handler_name?: string;
  proposer_name?: string;
  proposed_at?: string;
  execution_count: number;
  description_html?: string;
}
export interface TestDefectTree {
  systems: TestPlanSystem[];
  rounds?: TestRound[];
  cycles?: TestCycle[];
  environments?: Array<{ id: number; env_code: string; env_name: string }>;
  handlers?: Array<{
    id: number;
    physical_subsystem_id: number;
    display_name: string;
    username: string;
  }>;
}
const defectBase = (domain: TestDomain) => `/test-management/defects/${domain}`;
export const getTestDefectTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestDefectTree>>(`${defectBase(domain)}/tree`, {
    params: { projectId },
  });
export const listTestDefects = (
  domain: TestDomain,
  params: PageParams & {
    projectId: number;
    physicalSubsystemId?: number;
    keyword?: string;
    status?: string[];
    category?: string[];
    severity?: string[];
    priority?: string[];
    urgency?: string[];
    handlerId?: number;
    proposerId?: number;
    executionLink?: string;
    recycle?: boolean;
    quick?: "SUBMITTED" | "HANDLED" | "VERIFY";
    sortBy?: string;
    sortOrder?: string;
  },
) =>
  http.get<ApiResponse<BusinessDayPage<TestDefect>>>(defectBase(domain), {
    params,
  });
export const getTestDefect = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.get<ApiResponse<Record<string, unknown>>>(
    `${defectBase(domain)}/${id}`,
    { params: { projectId } },
  );
export const saveTestDefect = (
  domain: TestDomain,
  projectId: number,
  data: Record<string, unknown>,
  id?: number,
) =>
  id
    ? http.put<ApiResponse<Record<string, unknown>>>(
        `${defectBase(domain)}/${id}`,
        data,
        { params: { projectId } },
      )
    : http.post<ApiResponse<Record<string, unknown>>>(
        defectBase(domain),
        data,
        { params: { projectId } },
      );
export const transitionTestDefect = (
  domain: TestDomain,
  projectId: number,
  id: number,
  status: string,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${defectBase(domain)}/${id}/transition`,
    { status },
    { params: { projectId } },
  );
export const associateTestDefectExecutions = (
  domain: TestDomain,
  projectId: number,
  id: number,
  executionIds: number[],
) =>
  http.post<ApiResponse<void>>(
    `${defectBase(domain)}/${id}/executions`,
    { execution_ids: executionIds },
    { params: { projectId } },
  );
export const detachTestDefectExecution = (
  domain: TestDomain,
  projectId: number,
  id: number,
  executionId: number,
) =>
  http.delete<ApiResponse<void>>(
    `${defectBase(domain)}/${id}/executions/${executionId}`,
    { params: { projectId } },
  );
export const restoreTestDefect = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.post<ApiResponse<void>>(
    `${defectBase(domain)}/${id}/restore`,
    {},
    { params: { projectId } },
  );
export const deleteTestDefect = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${defectBase(domain)}/${id}`, {
    params: { projectId },
  });
export async function downloadTestDefectFile(
  domain: TestDomain,
  params: Record<string, unknown>,
) {
  const response = await http.get<Blob>(`${defectBase(domain)}/export`, {
    params,
    responseType: "blob",
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = "测试缺陷导出.xlsx";
  link.click();
  URL.revokeObjectURL(url);
}
export const importParticipatingSystems = (
  domain: TestDomain,
  projectId: number,
  file: File,
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<ImportResult>>(
    `${configurationBase(domain)}/systems/import`,
    data,
    {
      params: { projectId },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export const importSystemRoles = (
  domain: TestDomain,
  projectId: number,
  file: File,
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<ImportResult>>(
    `${configurationBase(domain)}/roles/import`,
    data,
    {
      params: { projectId },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export async function downloadConfigurationTemplate(
  domain: TestDomain,
  path: "/systems/template" | "/roles/template",
  fallbackName: string,
) {
  const response = await http.get<Blob>(`${configurationBase(domain)}${path}`, {
    responseType: "blob",
  });
  const disposition = String(response.headers["content-disposition"] || "");
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const filename = encoded ? decodeURIComponent(encoded) : fallbackName;
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export interface TestAnnouncementAttachment {
  id: number;
  fileName: string;
  fileSize: number;
  contentType?: string;
  createdAt?: string;
  attachmentType: "FILE" | "INLINE";
}
export interface TestAnnouncement {
  id: number;
  title: string;
  content_html?: string;
  summary?: string;
  pinned: boolean | number;
  pinned_at?: string;
  published_by?: number;
  publisher_name?: string;
  published_at?: string;
  last_edited_by?: number;
  last_edited_at?: string;
  attachment_count?: number;
  attachments?: TestAnnouncementAttachment[];
}
const announcementBase = (domain: TestDomain) =>
  `/test-management/announcements/${domain}`;
export const listAnnouncementProjects = (domain: TestDomain) =>
  http.get<ApiResponse<TestProjectOption[]>>(
    `${announcementBase(domain)}/projects`,
  );
export const currentAnnouncements = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestAnnouncement[]>>(
    `${announcementBase(domain)}/current`,
    { params: { projectId } },
  );
export const listAnnouncements = (
  domain: TestDomain,
  params: PageParams & {
    projectId: number;
    publisher?: string;
    from?: string;
    to?: string;
  },
) =>
  http.get<ApiResponse<BusinessDayPage<TestAnnouncement>>>(
    announcementBase(domain),
    { params },
  );
export const announcementDetail = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.get<ApiResponse<TestAnnouncement>>(`${announcementBase(domain)}/${id}`, {
    params: { projectId },
  });
export const saveAnnouncement = (
  domain: TestDomain,
  projectId: number,
  data: Partial<TestAnnouncement> & {
    attachment_ids?: number[];
    inline_attachment_ids?: number[];
  },
) =>
  data.id
    ? http.put<ApiResponse<TestAnnouncement>>(
        `${announcementBase(domain)}/${data.id}`,
        data,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestAnnouncement>>(announcementBase(domain), data, {
        params: { projectId },
      });
export const pinAnnouncement = (
  domain: TestDomain,
  projectId: number,
  id: number,
  pinned: boolean,
) =>
  http.put<ApiResponse<TestAnnouncement>>(
    `${announcementBase(domain)}/${id}/pin`,
    { pinned },
    { params: { projectId } },
  );
export const deleteAnnouncement = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${announcementBase(domain)}/${id}`, {
    params: { projectId },
  });

export type TestPlanNodeType = "PROJECT" | "SYSTEM" | "SPECIAL";
export interface TestPlanSystem {
  id: number;
  code: string;
  name: string;
  short_name?: string;
}
export interface TestPlanSpecialNode {
  id: number;
  node_name: string;
  updated_at?: string;
  plan_count?: number;
}
export interface TestPlanTree {
  project: TestProjectOption;
  systems: TestPlanSystem[];
  specials: TestPlanSpecialNode[];
}
export interface TestPlanNode {
  node_type: TestPlanNodeType;
  physical_subsystem_id?: number;
  special_node_id?: number;
}
export interface TestPlanVersion {
  version_id: number;
  plan_id?: number;
  version_no: number;
  version_note: string;
  attachment_id: number;
  file_name: string;
  file_extension: "docx" | "xlsx";
  file_size?: number;
  uploaded_by?: number;
  uploaded_at?: string;
  uploader_name?: string;
}
export interface TestPlan extends TestPlanVersion {
  id: number;
  plan_name: string;
  updated_at?: string;
}
export interface TestPlanUploadPayload extends TestPlanNode {
  attachment_id: number;
  plan_name?: string;
  version_note: string;
  confirm_version?: boolean;
}
export interface TestPlanUploadResult extends TestPlanVersion {
  version_confirmation_required?: boolean;
  next_version?: number;
  plan_name?: string;
}

const planBase = (domain: TestDomain) => `/test-management/plans/${domain}`;
const planNodeParams = (node: TestPlanNode) => ({
  nodeType: node.node_type,
  physicalSubsystemId: node.physical_subsystem_id,
  specialNodeId: node.special_node_id,
});
export const getTestPlanTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestPlanTree>>(`${planBase(domain)}/tree`, {
    params: { projectId },
  });
export const listTestPlans = (
  domain: TestDomain,
  projectId: number,
  node: TestPlanNode,
  page = 1,
  size = 20,
) =>
  http.get<ApiResponse<BusinessDayPage<TestPlan>>>(planBase(domain), {
    params: { projectId, page, size, ...planNodeParams(node) },
  });
export const getCurrentTestPlan = (
  domain: TestDomain,
  projectId: number,
  node: TestPlanNode,
) =>
  http.get<ApiResponse<TestPlan>>(`${planBase(domain)}/current`, {
    params: { projectId, ...planNodeParams(node) },
  });
export const listTestPlanVersions = (
  domain: TestDomain,
  projectId: number,
  planId: number,
) =>
  http.get<ApiResponse<TestPlanVersion[]>>(
    `${planBase(domain)}/${planId}/versions`,
    { params: { projectId } },
  );
export const createTestPlanSpecial = (
  domain: TestDomain,
  projectId: number,
  nodeName: string,
) =>
  http.post<ApiResponse<TestPlanSpecialNode>>(
    `${planBase(domain)}/special-nodes`,
    { node_name: nodeName },
    { params: { projectId } },
  );
export const updateTestPlanSpecial = (
  domain: TestDomain,
  projectId: number,
  id: number,
  nodeName: string,
) =>
  http.put<ApiResponse<TestPlanSpecialNode>>(
    `${planBase(domain)}/special-nodes/${id}`,
    { node_name: nodeName },
    { params: { projectId } },
  );
export const deleteTestPlanSpecial = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${planBase(domain)}/special-nodes/${id}`, {
    params: { projectId },
  });
export const uploadTestPlan = (
  domain: TestDomain,
  projectId: number,
  payload: TestPlanUploadPayload,
) =>
  http.post<ApiResponse<TestPlanUploadResult>>(
    `${planBase(domain)}/upload`,
    payload,
    { params: { projectId } },
  );
export const uploadTestPlanVersion = (
  domain: TestDomain,
  projectId: number,
  planId: number,
  payload: Pick<TestPlanUploadPayload, "attachment_id" | "version_note">,
) =>
  http.post<ApiResponse<TestPlanUploadResult>>(
    `${planBase(domain)}/${planId}/versions`,
    payload,
    { params: { projectId } },
  );
export const deleteTestPlan = (
  domain: TestDomain,
  projectId: number,
  planId: number,
) =>
  http.delete<ApiResponse<void>>(`${planBase(domain)}/${planId}`, {
    params: { projectId },
  });

export interface TestReportTree {
  project: { id: number; project_name: string };
  systems: TestPlanSystem[];
  specials: Array<{ id: number; node_name: string }>;
}
export interface TestReport {
  id: number;
  report_name: string;
  report_type: "PROJECT" | "ROUND" | "CYCLE";
  scope_type: "PROJECT" | "SYSTEM" | "SPECIAL";
  round_id?: number;
  cycle_id?: number;
  physical_subsystem_id?: number;
  special_node_id?: number;
  special_name?: string;
  physical_system_name?: string;
  current_version_no: number;
  current_version?: string;
  generator_name?: string;
  generated_at?: string;
  round_name?: string;
  cycle_name?: string;
}
export interface TestReportVersion {
  id: number;
  version_no: number;
  generated_at?: string;
  generator_name?: string;
}
export interface TestReportDetail {
  report: TestReport;
  versions: TestReportVersion[];
  version: TestReportVersion & { snapshot_json?: string };
  snapshot: Record<string, unknown>;
  supplements: Array<{
    chapter_code: string;
    content_html?: string;
    updated_at?: string;
    updater_name?: string;
  }>;
}
const reportBase = (domain: TestDomain) => `/test-management/reports/${domain}`;
export const getTestReportTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestReportTree>>(`${reportBase(domain)}/tree`, {
    params: { projectId },
  });
export const getTestReportOptions = (
  domain: TestDomain,
  projectId: number,
  physicalSubsystemId?: number,
) =>
  http.get<
    ApiResponse<{
      rounds: Array<{ id: number; round_name: string }>;
      cycles: Array<{ id: number; round_id: number; cycle_name: string }>;
      sections: string[];
    }>
  >(`${reportBase(domain)}/options`, {
    params: { projectId, physicalSubsystemId },
  });
export const listTestReports = (
  domain: TestDomain,
  params: PageParams & {
    projectId: number;
    physicalSubsystemId?: number;
    specialNodeId?: number;
    scopeType?: "PROJECT" | "SYSTEM" | "SPECIAL";
    keyword?: string;
  },
) =>
  http.get<ApiResponse<BusinessDayPage<TestReport>>>(reportBase(domain), {
    params,
  });
export const generateTestReport = (
  domain: TestDomain,
  projectId: number,
  scope: {
    physicalSubsystemId?: number;
    specialNodeId?: number;
    scopeType: "PROJECT" | "SYSTEM" | "SPECIAL";
  },
  body: Record<string, unknown>,
  id?: number,
) =>
  http.post<ApiResponse<TestReportDetail>>(
    `${reportBase(domain)}${id ? `/${id}/regenerate` : ""}`,
    body,
    { params: { projectId, ...scope } },
  );
export const getTestReport = (
  domain: TestDomain,
  projectId: number,
  id: number,
  versionId?: number,
) =>
  http.get<ApiResponse<TestReportDetail>>(`${reportBase(domain)}/${id}`, {
    params: { projectId, versionId },
  });
export const listTestReportVersions = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.get<ApiResponse<TestReportVersion[]>>(
    `${reportBase(domain)}/${id}/versions`,
    { params: { projectId } },
  );
export const saveTestReportSupplement = (
  domain: TestDomain,
  projectId: number,
  id: number,
  versionId: number,
  body: Record<string, unknown>,
) =>
  http.put<ApiResponse<void>>(
    `${reportBase(domain)}/${id}/versions/${versionId}/supplement`,
    body,
    { params: { projectId } },
  );
export const deleteTestReport = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${reportBase(domain)}/${id}`, {
    params: { projectId },
  });
export async function downloadTestReport(
  domain: TestDomain,
  projectId: number,
  id: number,
  format: "docx" | "pdf",
  versionId?: number,
) {
  const response = await http.get<Blob>(
    `${reportBase(domain)}/${id}/export/${format}`,
    { params: { projectId, versionId }, responseType: "blob" },
  );
  const disposition = String(response.headers["content-disposition"] || "");
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || `测试报告.${format}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export interface TestAnalyticsTree {
  presets: Array<{ key: string; name: string }>;
  mine: Array<{
    id: number;
    report_name: string;
    report_key: string;
    shared: boolean;
    config_json?: string;
  }>;
  shared: Array<{
    id: number;
    report_name: string;
    report_key: string;
    owner_name?: string;
    config_json?: string;
  }>;
}
const analyticsBase = (domain: TestDomain) =>
  `/test-management/analytics/${domain}`;
export const getTestAnalyticsTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestAnalyticsTree>>(`${analyticsBase(domain)}/tree`, {
    params: { projectId },
  });
export const getTestAnalyticsFilters = (
  domain: TestDomain,
  projectId: number,
) =>
  http.get<
    ApiResponse<{
      systems: TestPlanSystem[];
      rounds: Array<{
        id: number;
        round_name: string;
        planned_start_date?: string;
        planned_end_date?: string;
        status?: string;
      }>;
      cycles: Array<{
        id: number;
        round_id: number;
        cycle_name: string;
        planned_start_date?: string;
        planned_end_date?: string;
        status?: string;
      }>;
    }>
  >(`${analyticsBase(domain)}/filters`, { params: { projectId } });
export const getTestAnalyticsPreset = (
  domain: TestDomain,
  projectId: number,
  key: string,
  params: {
    physicalSubsystemId?: number;
    roundId?: number;
    cycleId?: number;
    view?: string;
    perspective?: string;
  } = {},
) =>
  http.get<ApiResponse<Record<string, unknown>>>(
    `${analyticsBase(domain)}/preset/${key}`,
    { params: { projectId, ...params } },
  );
export const compareTestAnalyticsSnapshots = (
  domain: TestDomain,
  projectId: number,
  roundIds: number[],
) =>
  http.get<ApiResponse<Array<Record<string, unknown>>>>(
    `${analyticsBase(domain)}/compare`,
    { params: { projectId, roundIds } },
  );
export const getTestAnalytics = (
  domain: TestDomain,
  projectId: number,
  key: string,
  params: {
    physicalSubsystemId?: number;
    roundId?: number;
    cycleId?: number;
  } = {},
) =>
  http.get<ApiResponse<Record<string, unknown>>>(
    `${analyticsBase(domain)}/view/${key}`,
    { params: { projectId, ...params } },
  );
export const getTestAnalyticsDrilldown = (
  domain: TestDomain,
  projectId: number,
  entity: string,
  params: {
    physicalSubsystemId?: number;
    roundId?: number;
    cycleId?: number;
  } = {},
) =>
  http.get<ApiResponse<Array<Record<string, unknown>>>>(
    `${analyticsBase(domain)}/drilldown/${entity}`,
    { params: { projectId, ...params } },
  );
export const saveTestAnalyticsReport = (
  domain: TestDomain,
  projectId: number,
  body: Record<string, unknown>,
  id?: number,
) =>
  id
    ? http.put<ApiResponse<Record<string, unknown>>>(
        `${analyticsBase(domain)}/reports/${id}`,
        body,
        { params: { projectId } },
      )
    : http.post<ApiResponse<Record<string, unknown>>>(
        `${analyticsBase(domain)}/reports`,
        body,
        { params: { projectId } },
      );
export const publishTestAnalyticsReport = (
  domain: TestDomain,
  projectId: number,
  id: number,
  shared: boolean,
) =>
  http.post<ApiResponse<void>>(
    `${analyticsBase(domain)}/reports/${id}/publish`,
    { shared },
    { params: { projectId } },
  );
export const deleteTestAnalyticsReport = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${analyticsBase(domain)}/reports/${id}`, {
    params: { projectId },
  });
export const archiveTestAnalytics = (
  domain: TestDomain,
  projectId: number,
  roundId: number,
) =>
  http.post<ApiResponse<Array<Record<string, unknown>>>>(
    `${analyticsBase(domain)}/archive`,
    { round_id: roundId },
    { params: { projectId } },
  );
export async function downloadTestAnalytics(
  domain: TestDomain,
  projectId: number,
  key: string,
  params: {
    physicalSubsystemId?: number;
    roundId?: number;
    cycleId?: number;
    view?: string;
    perspective?: string;
  } = {},
) {
  const response = await http.get<Blob>(
    `${analyticsBase(domain)}/export/${key}`,
    { params: { projectId, ...params }, responseType: "blob" },
  );
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = "分析统计.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export interface TestDirectory {
  id: number;
  physical_subsystem_id: number;
  parent_id?: number;
  directory_name: string;
  sort_no?: number;
  scope_count?: number;
}
export interface TestScope {
  id: number;
  scope_code: string;
  scope_name: string;
  physical_subsystem_id: number;
  physical_system_code?: string;
  physical_system_name?: string;
  directory_id?: number;
  directory_name?: string;
  leaf_menu?: string;
  function_type?: string;
  change_status?: string;
  importance?: string;
  accounting_flag?: string;
  invalidated?: boolean | number;
  invalid_reason?: string;
  status?: string;
  case_count?: number;
  created_by?: number;
  created_by_name?: string;
  created_at?: string;
  updated_by?: number;
  updated_by_name?: string;
  updated_at?: string;
  deleted?: boolean | number;
  deleted_at?: string;
  confirmation_required?: boolean;
  affected_case_count?: number;
}
export interface TestCase {
  id: number;
  case_code: string;
  case_name: string;
  physical_subsystem_id: number;
  physical_system_code?: string;
  physical_system_name?: string;
  scope_id: number;
  scope_code?: string;
  scope_name?: string;
  directory_id?: number;
  directory_name?: string;
  case_type?: string;
  case_nature?: string;
  test_level?: string;
  priority?: string;
  invalidated?: boolean | number;
  invalid_reason?: string;
  status?: string;
  accounting_result?: string;
  accounting_confirmed?: boolean | number;
  accounting_confirmed_by?: number;
  accounting_confirmer_name?: string;
  attachment_count?: number;
  execution_reference_count?: number;
  precondition_html?: string;
  steps_html?: string;
  expected_result_html?: string;
  remark?: string;
  created_by?: number;
  created_by_name?: string;
  created_at?: string;
  updated_by?: number;
  updated_by_name?: string;
  updated_at?: string;
  confirmation_required?: boolean;
  attachments?: Array<{
    id: number;
    file_name: string;
    file_size?: number;
    content_type?: string;
  }>;
}
export interface TestManagementTree {
  systems: TestPlanSystem[];
  directories: TestDirectory[];
}
const scopeBase = (domain: TestDomain) => `/test-management/scopes/${domain}`;
const caseBase = (domain: TestDomain) => `/test-management/cases/${domain}`;
export const getTestScopeTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestManagementTree>>(`${scopeBase(domain)}/tree`, {
    params: { projectId },
  });
export interface TestScopeFilters extends PageParams {
  projectId: number;
  physicalSubsystemId?: number;
  directoryId?: number;
  status?: string[];
  functionType?: string[];
  changeStatus?: string[];
  importance?: string[];
  accountingFlag?: string[];
  coverage?: "COVERED" | "UNCOVERED";
  recycled?: boolean;
  sortBy?: string;
  sortOrder?: "ascending" | "descending";
}
export interface TestScopeImportPreview {
  total: number;
  valid: number;
  failed: number;
  duplicate: number;
  directories: number;
  success: boolean;
  errors: Array<{ row_number: number; message: string }>;
  written?: number;
  created?: number;
  updated?: number;
  skipped?: number;
}
export const listTestScopes = (domain: TestDomain, params: TestScopeFilters) =>
  http.get<ApiResponse<BusinessDayPage<TestScope>>>(scopeBase(domain), {
    params,
  });
export const saveTestScope = (
  domain: TestDomain,
  projectId: number,
  item: Partial<TestScope>,
) =>
  item.id
    ? http.put<ApiResponse<TestScope>>(
        `${scopeBase(domain)}/${item.id}`,
        item,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestScope>>(scopeBase(domain), item, {
        params: { projectId },
      });
export const saveTestScopeDirectory = (
  domain: TestDomain,
  projectId: number,
  item: Partial<TestDirectory>,
) =>
  item.id
    ? http.put<ApiResponse<TestDirectory>>(
        `${scopeBase(domain)}/directories/${item.id}`,
        item,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestDirectory>>(
        `${scopeBase(domain)}/directories`,
        item,
        { params: { projectId } },
      );
export const deleteTestScopeDirectory = (
  domain: TestDomain,
  projectId: number,
  id: number,
  targetDirectoryId?: number,
) =>
  http.delete<ApiResponse<void>>(`${scopeBase(domain)}/directories/${id}`, {
    params: { projectId, targetDirectoryId },
  });
export const previewTestScopeCodeChange = (
  domain: TestDomain,
  projectId: number,
  id: number,
  scopeCode: string,
) =>
  http.post<ApiResponse<TestScope>>(
    `${scopeBase(domain)}/${id}/code-preview`,
    { scope_code: scopeCode },
    { params: { projectId } },
  );
export const deleteTestScope = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<{ associated_case_count: number }>>(
    `${scopeBase(domain)}/${id}`,
    { params: { projectId } },
  );
export const restoreTestScope = (
  domain: TestDomain,
  projectId: number,
  id: number,
  scopeCode?: string,
) =>
  http.put<ApiResponse<TestScope>>(
    `${scopeBase(domain)}/${id}/restore`,
    { scope_code: scopeCode },
    { params: { projectId } },
  );
export const setTestScopeInvalidated = (
  domain: TestDomain,
  projectId: number,
  id: number,
  invalidated: boolean,
  reason = "",
) =>
  http.put<ApiResponse<TestScope>>(
    `${scopeBase(domain)}/${id}/invalidated`,
    { invalidated, reason },
    { params: { projectId } },
  );
export const previewTestScopeImport = (
  domain: TestDomain,
  projectId: number,
  file: File,
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<TestScopeImportPreview>>(
    `${scopeBase(domain)}/import/preview`,
    data,
    {
      params: { projectId },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export const importTestScopes = (
  domain: TestDomain,
  projectId: number,
  file: File,
  duplicateAction: "SKIP" | "OVERWRITE",
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<TestScopeImportPreview>>(
    `${scopeBase(domain)}/import`,
    data,
    {
      params: { projectId, duplicateAction },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export async function downloadTestScopeFile(
  domain: TestDomain,
  path: "/template" | "/export",
  params: Record<string, unknown>,
  fallbackName: string,
) {
  const response = await http.get<Blob>(`${scopeBase(domain)}${path}`, {
    params,
    responseType: "blob",
  });
  const disposition = String(response.headers["content-disposition"] || "");
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = encoded ? decodeURIComponent(encoded) : fallbackName;
  link.click();
  URL.revokeObjectURL(url);
}
export const getTestCaseTree = (domain: TestDomain, projectId: number) =>
  http.get<ApiResponse<TestManagementTree>>(`${caseBase(domain)}/tree`, {
    params: { projectId },
  });
export interface TestCaseFilters extends PageParams {
  projectId: number;
  physicalSubsystemId?: number;
  directoryId?: number;
  scopeId?: number;
  caseType?: string[];
  caseNature?: string[];
  priority?: string[];
  status?: string[];
  accountingResult?: string[];
  designerId?: number;
  executionReference?: "REFERENCED" | "UNREFERENCED";
  sortBy?: string;
  sortOrder?: "ascending" | "descending";
}
export interface TestCaseImportPreview extends ImportResult {
  failed: number;
  duplicate: number;
  directories: number;
  created?: number;
  updated?: number;
  skipped?: number;
  rows?: Array<Record<string, unknown>>;
}
export const listTestCases = (domain: TestDomain, params: TestCaseFilters) =>
  http.get<ApiResponse<BusinessDayPage<TestCase>>>(caseBase(domain), {
    params,
  });
export const getTestCase = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.get<ApiResponse<TestCase>>(`${caseBase(domain)}/${id}`, {
    params: { projectId },
  });
export const listTestCaseScopes = (
  domain: TestDomain,
  projectId: number,
  physicalSubsystemId?: number,
) =>
  http.get<ApiResponse<TestScope[]>>(`${caseBase(domain)}/scopes`, {
    params: { projectId, physicalSubsystemId },
  });
export const previewTestCaseCode = (
  domain: TestDomain,
  projectId: number,
  scopeId: number,
) =>
  http.get<ApiResponse<{ case_code: string; scope_code: string }>>(
    `${caseBase(domain)}/code-preview`,
    { params: { projectId, scopeId } },
  );
export const saveTestCase = (
  domain: TestDomain,
  projectId: number,
  item: Partial<TestCase> & { attachment_ids?: number[] },
) =>
  item.id
    ? http.put<ApiResponse<TestCase>>(`${caseBase(domain)}/${item.id}`, item, {
        params: { projectId },
      })
    : http.post<ApiResponse<TestCase>>(caseBase(domain), item, {
        params: { projectId },
      });
export const saveTestCaseDirectory = (
  domain: TestDomain,
  projectId: number,
  item: Partial<TestDirectory>,
) =>
  item.id
    ? http.put<ApiResponse<TestDirectory>>(
        `${caseBase(domain)}/directories/${item.id}`,
        item,
        { params: { projectId } },
      )
    : http.post<ApiResponse<TestDirectory>>(
        `${caseBase(domain)}/directories`,
        item,
        { params: { projectId } },
      );
export const deleteTestCaseDirectory = (
  domain: TestDomain,
  projectId: number,
  id: number,
  targetDirectoryId?: number,
) =>
  http.delete<ApiResponse<void>>(`${caseBase(domain)}/directories/${id}`, {
    params: { projectId, targetDirectoryId },
  });
export const setTestCaseInvalidated = (
  domain: TestDomain,
  projectId: number,
  id: number,
  invalidated: boolean,
  reason = "",
) =>
  http.put<ApiResponse<TestCase>>(
    `${caseBase(domain)}/${id}/invalidated`,
    { invalidated, reason },
    { params: { projectId } },
  );
export const deleteTestCase = (
  domain: TestDomain,
  projectId: number,
  id: number,
) =>
  http.delete<ApiResponse<void>>(`${caseBase(domain)}/${id}`, {
    params: { projectId },
  });
export const moveTestCases = (
  domain: TestDomain,
  projectId: number,
  ids: number[],
  targetDirectoryId: number,
) =>
  http.put<ApiResponse<{ moved: number }>>(
    `${caseBase(domain)}/move`,
    { ids, target_directory_id: targetDirectoryId },
    { params: { projectId } },
  );
export const previewTestCaseBatch = (
  domain: TestDomain,
  projectId: number,
  body: Record<string, unknown>,
) =>
  http.post<ApiResponse<Record<string, unknown>>>(
    `${caseBase(domain)}/batch/preview`,
    body,
    { params: { projectId } },
  );
export const updateTestCaseBatch = (
  domain: TestDomain,
  projectId: number,
  body: Record<string, unknown>,
) =>
  http.put<ApiResponse<Record<string, unknown>>>(
    `${caseBase(domain)}/batch`,
    body,
    { params: { projectId } },
  );
export const previewTestCaseImport = (
  domain: TestDomain,
  projectId: number,
  file: File,
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<TestCaseImportPreview>>(
    `${caseBase(domain)}/import/preview`,
    data,
    {
      params: { projectId },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export const importTestCases = (
  domain: TestDomain,
  projectId: number,
  file: File,
  duplicateAction: "SKIP" | "OVERWRITE",
) => {
  const data = new FormData();
  data.append("file", file);
  return http.post<ApiResponse<TestCaseImportPreview>>(
    `${caseBase(domain)}/import`,
    data,
    {
      params: { projectId, duplicateAction },
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
};
export async function downloadTestCaseFile(
  domain: TestDomain,
  path: "/template" | "/export",
  params: Record<string, unknown>,
  fallbackName: string,
) {
  const response = await http.get<Blob>(`${caseBase(domain)}${path}`, {
    params,
    responseType: "blob",
  });
  const disposition = String(response.headers["content-disposition"] || "");
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = encoded ? decodeURIComponent(encoded) : fallbackName;
  link.click();
  URL.revokeObjectURL(url);
}
