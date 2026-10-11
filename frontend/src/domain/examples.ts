import type { JSONContent } from '@tiptap/vue-3';
import type { Episode, Project, ProjectDraft, Shot } from './types';

export const examples = [
  {
    id: 'night-letter',
    title: '夜航来信',
    subtitle: '一封旧信，让故事从雨夜开始。',
    genre: '悬疑',
    image: '/images/night-letter.png',
    episode: '夜里的来信',
  },
  {
    id: 'coastal-train',
    title: '雨停之前',
    subtitle: '在最后一班电车上，重新遇见夏天。',
    genre: '治愈',
    image: '/images/coastal-train.png',
    episode: '最后一班电车',
  },
  {
    id: 'old-street',
    title: '长街旧梦',
    subtitle: '灯火里的旧街，藏着未说完的告别。',
    genre: '剧情',
    image: '/images/old-street.png',
    episode: '故人归来',
  },
];
const p = (text: string): JSONContent => ({
  type: 'paragraph',
  content: text ? [{ type: 'text', text }] : [],
});
const heading = (text: string): JSONContent => ({
  type: 'heading',
  attrs: { level: 2 },
  content: [{ type: 'text', text }],
});
const quote = (speaker: string, line: string): JSONContent => ({
  type: 'blockquote',
  content: [p(speaker), p(line)],
});
export function scriptFromText(value: string): JSONContent {
  return {
    type: 'doc',
    content: value
      .replace(/\r\n?/g, '\n')
      .split('\n')
      .map((line) => (/^场景|^\d+[.、 ]/.test(line) ? heading(line) : p(line))),
  };
}
export const emptyScript = (): JSONContent => ({ type: 'doc', content: [p('')] });
export function newEpisode(title = '未命名'): Episode {
  return {
    id: crypto.randomUUID(),
    title,
    script: emptyScript(),
    confirmed: false,
    characters: [],
    shots: [],
    clips: [],
    messages: [],
  };
}
export function blankDraft(): ProjectDraft {
  return { version: 1, episodes: [newEpisode()], updatedAt: new Date().toISOString() };
}

export function refreshExampleFrames(draft: ProjectDraft, id: string) {
  if (id !== 'night-letter') return;
  const replacements: Record<string, string> = {
    林夏推开店门: '/images/shot-door.png',
    柜台上的旧信: '/images/shot-letter.png',
    街角的真相: '/images/shot-corner.png',
  };
  for (const episode of draft.episodes) {
    for (const clip of episode.clips) {
      if (clip.mediaId || clip.image !== '/images/night-letter.png') continue;
      const title = episode.shots.find((shot) => shot.id === clip.shotId)?.title;
      if (title && replacements[title]) clip.image = replacements[title];
    }
  }
}
export function exampleProject(id: string): Project {
  const example = examples.find((item) => item.id === id) || examples[0]!;
  return {
    id: example.id,
    workspaceId: 'demo',
    name: example.title,
    description: example.subtitle,
    aspectRatio: '16:9',
    revision: '1',
    createdAt: '2026-10-11T08:00:00Z',
    updatedAt: '2026-10-11T08:00:00Z',
  };
}
export function exampleDraft(id: string): ProjectDraft {
  const example = examples.find((item) => item.id === id) || examples[0]!;
  const episode = newEpisode(example.episode);
  episode.script =
    id === 'night-letter'
      ? {
          type: 'doc',
          content: [
            heading('旧书店 · 夜'),
            p('雨声漫过整条街。林夏推开书店的门，暖光落在湿透的衣角。'),
            p('柜台上，一封没有署名的信正等着她。'),
            quote('林夏', '你知道我会来？'),
            quote('陈默', '不是我。是写这封信的人。'),
            { type: 'horizontalRule' },
            heading('街角 · 夜'),
            p('路灯忽明忽暗。林夏拆开信，脚步突然停住。'),
            p('别相信那个替你开门的人。'),
          ],
        }
      : {
          type: 'doc',
          content: [
            heading(id === 'coastal-train' ? '海边车站 · 黄昏' : '老街 · 夜'),
            p(example.subtitle),
            p('远处传来熟悉的声音。她停下脚步，回头望去。'),
            quote('林夏', '原来，你还记得。'),
            quote('陈默', '有些事，一直都记得。'),
          ],
        };
  episode.characters = [
    {
      id: crypto.randomUUID(),
      name: '林夏',
      role: '主角',
      description: '年轻的插画师。性格安静敏锐，总会留意被别人忽略的细节。短发，深色外套。',
    },
    {
      id: crypto.randomUUID(),
      name: '陈默',
      role: '配角',
      description: '旧书店的店主。寡言而温和，似乎早已知道那封信的秘密。',
    },
  ];
  episode.shots = ['雨夜的旧书店', '林夏推开店门', '柜台上的旧信', '街角的真相'].map(
    (title, index): Shot => ({
      id: crypto.randomUUID(),
      workspaceId: 'demo',
      projectId: id,
      shotNumber: index + 1,
      title,
      description: [
        '远景。雨水掠过灯光，书店橱窗透出暖色。',
        '中景。林夏收起伞，缓缓推开木门。',
        '特写。没有署名的旧信被推到灯下。',
        '近景。林夏停住脚步，目光落在信纸上。',
      ][index]!,
      dialogue: index === 1 ? '你知道我会来？' : '',
      durationSeconds: index === 2 ? 3 : 5,
      revision: '1',
      createdAt: '2026-10-11T08:00:00Z',
      updatedAt: '2026-10-11T08:00:00Z',
    }),
  );
  if (id !== 'night-letter') {
    const scenes =
      id === 'coastal-train'
        ? [
            ['海边的车站', '远景。夕阳映在海面上，最后一班电车缓缓进站。'],
            ['再次相遇', '中景。林夏回头，看见站台另一端的熟悉身影。'],
          ]
        : [
            ['雨后的长街', '远景。灯笼照亮湿润的石阶，一个熟悉的身影走进旧街。'],
            ['故人归来', '中景。林夏停下脚步，回头望向灯火中的来人。'],
          ];
    episode.shots = episode.shots.slice(0, 2).map((shot, index) => ({
      ...shot,
      title: scenes[index]![0]!,
      description: scenes[index]![1]!,
      dialogue: index ? '原来，你还记得。' : '',
    }));
  }
  const frames = [
    '/images/night-letter.png',
    '/images/shot-door.png',
    '/images/shot-letter.png',
    '/images/shot-corner.png',
  ];
  episode.clips = episode.shots.map((shot, index) => ({
    id: crypto.randomUUID(),
    shotId: shot.id,
    duration: shot.durationSeconds,
    image: id === 'night-letter' ? frames[index] : example.image,
  }));
  if (id === 'night-letter')
    episode.messages = [
      { id: crypto.randomUUID(), role: 'user', text: '结尾再加一点悬念。', sample: true },
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        text: '我把悬念放进了信里。这样，观众会重新审视书店里的相遇。',
        sample: true,
      },
    ];
  return {
    version: 1,
    episodes: [episode],
    cover: example.image,
    updatedAt: new Date().toISOString(),
  };
}
