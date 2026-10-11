import type { JSONContent } from '@tiptap/vue-3';

export type Revision = string;
export interface User {
  id: string;
  username: string;
}
export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}
export interface Workspace {
  id: string;
  name: string;
  role: 'ADMIN' | 'EDITOR' | 'REVIEWER' | 'VIEWER';
  revision: Revision;
}
export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description: string;
  aspectRatio: string;
  revision: Revision;
  createdAt: string;
  updatedAt: string;
}
export interface ProjectInput {
  name: string;
  description: string;
  aspectRatio: string;
}
export interface ShotInput {
  title: string;
  description: string;
  dialogue: string;
  durationSeconds: number;
}
export interface Shot extends ShotInput {
  id: string;
  workspaceId: string;
  projectId: string;
  shotNumber: number;
  revision: Revision;
  createdAt: string;
  updatedAt: string;
}
export interface Media {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: string;
  createdAt: string;
}
export interface Character {
  id: string;
  name: string;
  role: string;
  description: string;
  mediaId?: string;
}
export interface Clip {
  id: string;
  shotId: string;
  duration: number;
  mediaId?: string;
  image?: string;
}
export interface Message {
  id: string;
  text: string;
  role: 'user' | 'assistant';
  sample?: boolean;
}
export interface Episode {
  id: string;
  title: string;
  script: JSONContent;
  confirmed: boolean;
  characters: Character[];
  shots: Shot[];
  clips: Clip[];
  messages: Message[];
}
export interface ProjectDraft {
  version: 1;
  episodes: Episode[];
  cover?: string;
  updatedAt: string;
}
export type Stage = 'script' | 'characters' | 'storyboard' | 'timeline';
export const stages: { key: Stage; title: string }[] = [
  { key: 'script', title: '剧本' },
  { key: 'characters', title: '角色' },
  { key: 'storyboard', title: '分镜板' },
  { key: 'timeline', title: '时间轴' },
];
