import { z } from 'zod';
import type { ProjectDraft } from '../domain/types';

const script = z.object({
  type: z.literal('doc'),
  content: z.array(z.record(z.string(), z.unknown())).max(10000),
});
const shot = z.object({
  id: z.string(),
  workspaceId: z.string(),
  projectId: z.string(),
  shotNumber: z.number(),
  title: z.string(),
  description: z
    .string()
    .nullable()
    .transform((v) => v || ''),
  dialogue: z
    .string()
    .nullable()
    .transform((v) => v || ''),
  durationSeconds: z.number().min(1).max(60),
  revision: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
const draftSchema = z.object({
  version: z.literal(1),
  cover: z.string().optional(),
  updatedAt: z.string(),
  episodes: z
    .array(
      z.object({
        id: z.string(),
        title: z.string().max(120),
        script,
        confirmed: z.boolean(),
        characters: z.array(
          z.object({
            id: z.string(),
            name: z.string(),
            role: z.string(),
            description: z.string(),
            mediaId: z.string().optional(),
          }),
        ),
        shots: z.array(shot),
        clips: z.array(
          z.object({
            id: z.string(),
            shotId: z.string(),
            duration: z.number().min(1).max(60),
            mediaId: z.string().optional(),
            image: z.string().optional(),
          }),
        ),
        messages: z.array(
          z.object({
            id: z.string(),
            text: z.string(),
            role: z.enum(['user', 'assistant']),
            sample: z.boolean().optional(),
          }),
        ),
      }),
    )
    .min(1)
    .max(500),
});
export const draftKey = (userId: string, workspaceId: string, projectId: string) =>
  `yingxu:draft:v1:${userId}:${workspaceId}:${projectId}`;
export function readDraft(key: string): ProjectDraft | null {
  const stored = localStorage.getItem(key);
  if (!stored) return null;
  const parsed = draftSchema.safeParse(JSON.parse(stored));
  if (!parsed.success) throw new Error('本机草稿格式无法读取。原始内容已保留，请先导出恢复副本。');
  return parsed.data as ProjectDraft;
}
export function saveDraft(key: string, draft: ProjectDraft) {
  const value = JSON.stringify(draft);
  if (value.length > 2_000_000)
    throw new Error('本机草稿过大，请先导出备份。图片和视频请使用素材库。');
  try {
    localStorage.setItem(key, value);
  } catch {
    throw new Error('本机保存失败，可能是存储空间不足。请立即导出草稿。');
  }
}
