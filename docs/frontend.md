# 映序前端

## 运行

Node.js 22.12+，pnpm 10.28.1。使用 `frontend/pnpm-lock.yaml` 固定安装结果。

```bash
./scripts/frontend.sh install
./scripts/frontend.sh dev
```

开发地址为 <http://127.0.0.1:15173/>，默认后端为 `127.0.0.1:18080`。端口占用时直接报错，不自动换端口。可复制 `frontend/.env.example` 为 `frontend/.env.local` 设置 `DRAMA_API_TARGET`。前端环境文件只能包含公开配置，不能放数据库密码、模型密钥或管理员密码。

使用现有 Session 登录接口，账号读取根目录 `.env` 的 `DRAMA_BOOTSTRAP_USERNAME` 和 `DRAMA_BOOTSTRAP_PASSWORD`，在登录窗口输入。账号信息不会打包到前端或保存在 localStorage。

```bash
./scripts/frontend.sh typecheck
./scripts/frontend.sh format:check
./scripts/frontend.sh build
```

没有新增测试用例。构建包含严格 TypeScript 检查；界面、接口和保存流程通过手动浏览器操作验收。构建产物在 `frontend/dist/`。`preview` 与开发服务使用同一端口，先停止开发服务再执行 `./scripts/frontend.sh preview`。

## 页面和使用方式

- 首页：切换工作空间、项目搜索、卡片/列表切换、创建/编辑项目、案例精选。
- 项目：顶部项目名最多显示五个字符，悬停显示全名；右侧紧邻剧集选择、集名和新增按钮。不放永久左侧菜单。
- 剧集：新项目默认第一集，选择器中切换或重命名，`+` 创建下一集。每集分别保存剧本、角色、分镜归属、时间轴和对话草稿。
- 剧本：Tiptap 编辑器，正文、场景标题、粗体、斜体、对白、列表、撤销/重做。`01、02…` 是场景标题的自动显示编号，不是剧集或镜头编号；把段落改为「场景标题」即可添加。编号随标题顺序变化，不写入正文。TXT 导出保留文字，不包含这些显示编号。
- 导入：UTF-8 TXT/Markdown 文本，最大 1 MB；按照纯文本导入，不执行 HTML。以“场景”或数字序号开头的行识别为场景标题，其余为正文。已有内容保留，导入文本追加到末尾。Markdown 不是完整富文本解析。
- 角色：创建、编辑角色描述，并从素材库选择图片参考。
- 分镜：创建/编辑镜头、设置描述/台词/时长、关联参考图片或视频；真实项目保存到后端，保留修订号。
- 时间轴：镜头加入、前后排序、修改 1–60 秒时长、播放/暂停和进度预览、导出 JSON 编排。当前是参考素材预览，没有音轨混音、转场、字幕或 MP4 渲染。
- 智能体：随当前集和步骤显示上下文。模型未连接时可保存想法，界面明确标记；案例中的回复标记为示例，不进行假生成。
- 素材库：上传、搜索、按图片/视频/音频过滤、预览和下载；列表显示最近 100 个素材，单文件最大 100 MB。
- 智能画布：与项目工作区分开，是可拖动、缩放、编辑、导出的文字灵感画布；没有生成节点或工作流执行。

窄屏下四个步骤移到顶部第二行，智能体由右上角按钮展开/收起；桌面保留右侧面板。

## 模块边界

| 目录 | 职责 |
| --- | --- |
| `src/pages` | 路由页面、页面编排和表单 |
| `src/components/studio` | 剧本、角色、分镜、时间轴和智能体组件 |
| `src/components` | 通用对话框、图标、登录、素材选择 |
| `src/composables/useStudio.ts` | 项目加载、剧集切换、草稿保存、镜头持久化 |
| `src/stores/app.ts` | Pinia 会话、工作空间、角色权限、通知 |
| `src/api/client.ts` | 同源 HTTP、CSRF、超时、业务错误、分页、API 类型 |
| `src/domain` | 领域类型、独立案例数据 |
| `src/lib` | 本机草稿校验和持久化、显示格式、文件导出 |
| `src/styles` | 颜色与字体 token、页面布局、响应式规则 |

Vue Router 懒加载页面，组件使用 Composition API 和 TypeScript。Tiptap JSON 是剧本文档的结构；不把富文本 HTML 当成存储协议。图标来自 Phosphor，案例图和分镜参考图为本次生成的本地原创素材，未抓取竞品素材。

## 数据和接口规范

| 数据 | 当前落点 | 边界 |
| --- | --- | --- |
| 登录、成员权限、工作空间 | Spring Boot / PostgreSQL | Session Cookie + CSRF |
| 项目、镜头、镜头修订 | Spring Boot / PostgreSQL | PATCH 携带字符串 `expectedRevision`，冲突不自动覆盖 |
| 上传素材 | 后端媒体接口 + 配置的磁盘目录 | 浏览器只持有媒体 ID，通过授权内容接口读取 |
| 剧集、剧本、角色、镜头所属集、编排、想法、项目封面 | localStorage | 用户/空间/项目分键；300 ms 防抖保存；不是团队共享数据 |
| 案例修改 | 独立 `demo` 命名空间 | 不创建服务器项目、不消耗模型额度 |
| 灵感画布 | localStorage | 用户/空间分键，独立于项目 |

接口使用 `/api/v1` 相对路径，开发代理及容器代理都保持同源。登录后重新获取 CSRF，写入请求不会自动重试，避免重复创建。一般请求 30 秒超时，上传 120 秒。API 时间按原始 UTC 字符串保留，仅在展示层集中使用 `Intl.DateTimeFormat`；当前显示时区为 `Asia/Shanghai`。ID 与 revision 始终按字符串处理。

草稿用 Zod 校验，单项目保存文本上限约 2 MB；损坏时保留原始数据，提供导出恢复副本。配额不足会显示保存失败和备份入口。图片/视频通过素材接口上传，不写入 localStorage。浏览器清理数据、更换设备、域名或端口会使本机草稿不可见。当前不支持同项目多标签并行编辑冲突合并，也没有 JSON 草稿重新导入入口；重要内容应先导出备份。

后端暂时只有项目级镜头接口。当前浏览器保存镜头所属剧集，换设备后未分配的镜头会出现在第一集。接入服务端 Episode/Script/Character/Timeline 模块时，需要同时迁移这层归属关系，不应把现状当作最终数据模型。

## Docker 本地运行

先构建静态产物，再打包运行镜像。容器仅含静态文件与 Caddy，不带源代码、Node.js、pnpm 缓存或密钥。

```bash
./scripts/frontend.sh install
./scripts/frontend.sh build
docker compose --profile app --profile web up -d --build
```

容器前端默认 <http://127.0.0.1:15174/>，可通过 `DRAMA_WEB_PORT` 修改。容器 `/api/*` 代理到 `api:18080`，SPA 深层路由刷新回退到 `index.html`，入口页不缓存，带内容哈希的 JS/CSS 长缓存。配置依据 [Caddy 官方 SPA 部署模式](https://caddyserver.com/docs/caddyfile/patterns#single-page-apps-spas)。

容器以非 root 用户运行，根文件系统只读，只允许 `/data`、`/config`、`/tmp` 临时写入。前端容器不挂载媒体目录，素材继续由后端存储和鉴权。对外发布时接 HTTPS 域名/反向代理，并启用后端 Secure Cookie；当前 Compose 仅绑定本机回环地址。

开发地址与容器地址端口不同，本机草稿不会互通。真实项目和镜头共用同一个后端。

## 下一阶段

先补齐 Episode、Script、Character、Timeline 的服务端持久化、修订冲突和本机草稿迁移，再连接智能体任务流与模型服务，最后接生成素材入库、配音字幕及视频渲染。界面已经为这些能力分开了组件与 API 边界。
