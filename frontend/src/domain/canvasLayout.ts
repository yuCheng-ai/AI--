import type { CanvasEdge, CanvasNode } from './canvas';

/** Rank by reference direction; collapse cycles before ranking so every node gets a position. */
export function layoutCanvas(nodes: CanvasNode[], edges: CanvasEdge[]) {
  const outgoing = new Map(nodes.map((node) => [node.id, new Set<string>()]));
  for (const edge of edges) {
    if (outgoing.has(edge.target)) outgoing.get(edge.source)?.add(edge.target);
  }
  let cursor = 0;
  const indices = new Map<string, number>();
  const low = new Map<string, number>();
  const stack: string[] = [];
  const active = new Set<string>();
  const groups: string[][] = [];
  function visit(id: string) {
    indices.set(id, cursor);
    low.set(id, cursor++);
    stack.push(id);
    active.add(id);
    for (const target of outgoing.get(id)!) {
      if (!indices.has(target)) {
        visit(target);
        low.set(id, Math.min(low.get(id)!, low.get(target)!));
      } else if (active.has(target)) low.set(id, Math.min(low.get(id)!, indices.get(target)!));
    }
    if (low.get(id) === indices.get(id)) {
      const group: string[] = [];
      let member: string;
      do {
        member = stack.pop()!;
        active.delete(member);
        group.push(member);
      } while (member !== id);
      groups.push(group);
    }
  }
  for (const node of nodes) if (!indices.has(node.id)) visit(node.id);
  const groupOf = new Map(groups.flatMap((group, i) => group.map((id) => [id, i] as const)));
  const next = groups.map(() => new Set<number>());
  const indegree = groups.map(() => 0);
  for (const [source, targets] of outgoing) {
    for (const target of targets) {
      const from = groupOf.get(source)!;
      const to = groupOf.get(target)!;
      if (from !== to && !next[from]!.has(to)) {
        next[from]!.add(to);
        indegree[to]!++;
      }
    }
  }
  const rank = groups.map(() => 0);
  const queue = indegree.flatMap((count, i) => (count === 0 ? [i] : []));
  for (let i = 0; i < queue.length; i++) {
    const group = queue[i]!;
    for (const target of next[group]!) {
      rank[target] = Math.max(rank[target]!, rank[group]! + 1);
      if (--indegree[target]! === 0) queue.push(target);
    }
  }
  const columns = new Map<number, CanvasNode[]>();
  const linked = new Set(edges.flatMap((edge) => [edge.source, edge.target]));
  const standalone: CanvasNode[] = [];
  for (const node of nodes) {
    if (!linked.has(node.id)) {
      standalone.push(node);
      continue;
    }
    const column = rank[groupOf.get(node.id)!]!;
    const list = columns.get(column) || [];
    list.push(node);
    columns.set(column, list);
  }
  const positions = new Map<string, { x: number; y: number }>();
  // Reserve room for the composer even when a node is not currently selected.
  const height = (node: CanvasNode) => {
    const [w, h] = node.data.ratio.split(':').map(Number);
    return ['text', 'script'].includes(node.data.kind)
      ? 32 + 242 + 240
      : 32 + 350 * (node.data.kind === 'audio' ? 9 / 16 : h! / w!) + 340;
  };
  let bottom = 0;
  for (const [column, list] of columns) {
    let y = 0;
    for (const node of list) {
      positions.set(node.id, { x: column * 512, y });
      y += Math.ceil((height(node) + 72) / 16) * 16;
    }
    bottom = Math.max(bottom, y);
  }
  let rowY = bottom;
  for (let i = 0; i < standalone.length; i += 3) {
    const row = standalone.slice(i, i + 3);
    row.forEach((node, column) => positions.set(node.id, { x: column * 512, y: rowY }));
    rowY += Math.ceil((Math.max(...row.map(height)) + 72) / 16) * 16;
  }
  return positions;
}
