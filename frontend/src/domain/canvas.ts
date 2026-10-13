import { z } from 'zod';

export const nodeKinds = ['text', 'image', 'video', 'audio', 'script'] as const;
export type CanvasKind = (typeof nodeKinds)[number];
export const textCreationHint = '写下故事、场景或角色设定，或说明你希望怎样修改上方内容…';
export const canvasTools: { kind: CanvasKind; label: string; icon: string; hint: string }[] = [
  { kind: 'text', label: '文本', icon: 'align', hint: '写下灵感、提示词或角色设定…' },
  { kind: 'image', label: '图片', icon: 'image', hint: '描述画面主体、场景、光线和风格…' },
  { kind: 'video', label: '视频', icon: 'video', hint: '描述镜头运动、人物动作和节奏…' },
  { kind: 'audio', label: '音频', icon: 'waveform', hint: '描述声音氛围、配乐或对白…' },
  { kind: 'script', label: '剧本', icon: 'script', hint: '故事从哪里开始？写下人物、冲突和场景…' },
];
// Canvas authoring rules. Model-specific input restrictions will further narrow these on execution.
export const canvasConnectionTargets: Readonly<Record<CanvasKind, readonly CanvasKind[]>> = {
  text: ['text', 'image', 'video', 'audio', 'script'],
  image: ['text', 'image', 'video', 'audio', 'script'],
  video: ['text', 'video', 'script'],
  audio: ['text', 'video', 'audio', 'script'],
  script: ['text', 'image', 'video', 'audio', 'script'],
};
export function canConnectKinds(source: CanvasKind, target: CanvasKind): boolean {
  return canvasConnectionTargets[source].includes(target);
}
export function connectionRestriction(source: CanvasKind, target: CanvasKind): string {
  const from = canvasTools.find((tool) => tool.kind === source)!.label;
  const to = canvasTools.find((tool) => tool.kind === target)!.label;
  return `暂不支持将${from}节点连接到${to}节点`;
}
const point = z.object({ x: z.number().finite(), y: z.number().finite() });
const dataSchema = z.object({
  kind: z.enum(nodeKinds),
  title: z.string().max(80),
  text: z.string().max(20000),
  // Text/script instructions must not overwrite the authored content.
  prompt: z.string().max(20000).default(''),
  ratio: z.enum(['16:9', '9:16', '1:1']).default('16:9'),
  duration: z.enum(['5', '10', '15']).default('5'),
  media: z
    .object({
      id: z
        .string()
        .min(1)
        .max(100)
        .regex(/^[a-zA-Z0-9_-]+$/),
      workspaceId: z
        .string()
        .min(1)
        .max(100)
        .regex(/^[a-zA-Z0-9_-]+$/),
      fileName: z.string().max(500),
      contentType: z.string().max(100),
    })
    .optional(),
});
export type CanvasData = z.infer<typeof dataSchema>;
export interface CanvasNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: CanvasData;
  selected?: boolean;
}
export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string | null;
  targetHandle?: string | null;
  selected?: boolean;
}
const nodeSchema = z.object({
  id: z.string().min(1).max(100),
  type: z.literal('creative'),
  position: point,
  data: dataSchema,
});
const edgeSchema = z.object({
  id: z.string().min(1).max(150),
  source: z.string(),
  target: z.string(),
  sourceHandle: z.string().nullable().optional(),
  targetHandle: z.string().nullable().optional(),
});
export const boardSchema = z
  .object({
    id: z.string().min(1).max(100),
    name: z.string().min(1).max(50),
    nodes: z.array(nodeSchema).max(200),
    edges: z.array(edgeSchema).max(500),
    storyOrder: z.array(z.string().min(1).max(100)).max(200).default([]),
    viewport: z.object({
      x: z.number().finite(),
      y: z.number().finite(),
      zoom: z.number().min(0.2).max(2),
    }),
  })
  .superRefine((board, ctx) => {
    const ids = new Set(board.nodes.map((n) => n.id));
    if (
      ids.size !== board.nodes.length ||
      new Set(board.edges.map((e) => e.id)).size !== board.edges.length ||
      board.edges.some((e) => !ids.has(e.source) || !ids.has(e.target) || e.source === e.target)
    )
      ctx.addIssue({ code: 'custom', message: '节点或连线不完整' });
  });
export type CanvasBoard = z.infer<typeof boardSchema>;
export const canvasSchema = z
  .object({
    version: z.literal(2),
    activeId: z.string(),
    boards: z.array(boardSchema).min(1).max(20),
    snap: z.boolean(),
    showEdges: z.boolean(),
    minimap: z.boolean(),
  })
  .refine(
    (value) => new Set(value.boards.map((b) => b.id)).size === value.boards.length,
    '画布 ID 重复',
  );
export type CanvasDocument = z.infer<typeof canvasSchema>;
export function blankBoard(name = '画布 1'): CanvasBoard {
  return {
    id: crypto.randomUUID(),
    name,
    nodes: [],
    edges: [],
    storyOrder: [],
    viewport: { x: 0, y: 0, zoom: 1 },
  };
}
// Presentation order is independent of graph coordinates and reference edges.
export function storyNodeOrder(nodes: readonly CanvasNode[], order: readonly string[]): string[] {
  const ids = new Set(nodes.map((n) => n.id));
  return [...new Set([...order.filter((id) => ids.has(id)), ...ids])];
}
export function blankCanvas(): CanvasDocument {
  const b = blankBoard();
  return { version: 2, activeId: b.id, boards: [b], snap: false, showEdges: true, minimap: false };
}
export function parseCanvas(raw: string): CanvasDocument {
  if (raw.length > 4_000_000) throw new Error('画布文件超过 4 MB。');
  const value = JSON.parse(raw);
  if (value?.version === 2) return canvasSchema.parse(value);
  if (!Array.isArray(value) && value?.version !== 1) throw new Error('不支持的画布文件版本。');
  const legacy = z
    .array(
      z.object({
        id: z.string(),
        title: z.string().max(80),
        text: z.string().max(20000),
        x: z.number().finite(),
        y: z.number().finite(),
      }),
    )
    .max(200)
    .parse(Array.isArray(value) ? value : value.nodes);
  const result = blankCanvas();
  result.boards[0]!.nodes = legacy.map((n) => ({
    id: n.id,
    type: 'creative',
    position: { x: n.x, y: n.y },
    data: { kind: 'text', title: n.title, text: n.text, prompt: '', ratio: '16:9', duration: '5' },
  }));
  return canvasSchema.parse(result);
}
