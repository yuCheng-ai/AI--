import type {
  Media,
  Page,
  Project,
  ProjectInput,
  Shot,
  ShotInput,
  User,
  Workspace,
} from '../domain/types';

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public traceId?: string,
    public fieldErrors?: Record<string, string>,
  ) {
    super(message);
  }
}
type Csrf = { headerName: string; token: string };
let csrf: Csrf | undefined;
let csrfRequest: Promise<Csrf> | undefined;
async function token(): Promise<Csrf> {
  if (csrf) return csrf;
  csrfRequest ??= request<Csrf>('/auth/csrf')
    .then((value) => (csrf = value))
    .finally(() => {
      csrfRequest = undefined;
    });
  return csrfRequest;
}
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  if (init.method && !['GET', 'HEAD'].includes(init.method)) {
    const value = await token();
    headers.set(value.headerName, value.token);
  }
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
      signal: init.signal ?? AbortSignal.timeout(init.body instanceof FormData ? 120000 : 30000),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '连接中断，请检查后端服务后重试。');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    if (response.status === 401 || response.status === 403) csrf = undefined;
    throw new ApiError(
      response.status,
      error.code || 'REQUEST_FAILED',
      error.message || `请求失败（${response.status}）`,
      error.traceId,
      error.fieldErrors,
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
const json = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
});
export const api = {
  me: () => request<User>('/auth/me'),
  async login(username: string, password: string) {
    csrf = undefined;
    const result = await request<{ user: User }>(
      '/auth/login',
      json('POST', { username, password }),
    );
    csrf = undefined;
    await token();
    return result.user;
  },
  async logout() {
    await request<void>('/auth/logout', { method: 'POST' });
    csrf = undefined;
  },
  workspaces: (cursor?: string) =>
    request<Page<Workspace>>(
      `/workspaces?limit=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`,
    ),
  createWorkspace: (name: string) => request<Workspace>('/workspaces', json('POST', { name })),
  projects: (wid: string, cursor?: string) =>
    request<Page<Project>>(
      `/workspaces/${wid}/projects?limit=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`,
    ),
  project: (wid: string, pid: string) => request<Project>(`/workspaces/${wid}/projects/${pid}`),
  createProject: (wid: string, data: ProjectInput) =>
    request<Project>(`/workspaces/${wid}/projects`, json('POST', data)),
  updateProject: (wid: string, id: string, data: ProjectInput, expectedRevision: string) =>
    request<Project>(
      `/workspaces/${wid}/projects/${id}`,
      json('PATCH', { ...data, expectedRevision }),
    ),
  shots: (wid: string, pid: string, cursor?: string) =>
    request<Page<Shot>>(
      `/workspaces/${wid}/projects/${pid}/shots?limit=100${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`,
    ),
  createShot: (wid: string, pid: string, data: ShotInput) =>
    request<Shot>(`/workspaces/${wid}/projects/${pid}/shots`, json('POST', data)),
  reviseShot: (wid: string, id: string, data: ShotInput, expectedRevision: string) =>
    request<Shot>(`/workspaces/${wid}/shots/${id}`, json('PATCH', { ...data, expectedRevision })),
  media: (wid: string) => request<Media[]>(`/workspaces/${wid}/media?limit=100`),
  mediaItem: (wid: string, id: string) => request<Media>(`/workspaces/${wid}/media/${id}`),
  upload: (wid: string, file: File) => {
    const body = new FormData();
    body.append('file', file);
    return request<Media>(`/workspaces/${wid}/media`, { method: 'POST', body });
  },
  mediaUrl: (wid: string, id: string, download = false) =>
    `/api/v1/workspaces/${wid}/media/${id}/content${download ? '?download=true' : ''}`,
};
export async function collectPages<T>(load: (cursor?: string) => Promise<Page<T>>): Promise<T[]> {
  const items: T[] = [];
  let cursor: string | undefined;
  const visited = new Set<string>();
  do {
    const page = await load(cursor);
    items.push(...page.items);
    cursor = page.nextCursor || undefined;
    if (cursor) {
      if (visited.has(cursor)) throw new Error('分页游标重复，请刷新重试。');
      visited.add(cursor);
    }
  } while (cursor);
  return items;
}
