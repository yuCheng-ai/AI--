# 后端底座运行说明

当前可运行认证、工作空间、项目、镜头及其版本、本地媒体上传与受权限保护的下载，并提供 Swagger/OpenAPI。前端、AI 模型调用、生成任务调度、云对象存储、时间轴及成片合成尚未实现；对应 Maven 模块是后续扩展边界。

接口字段、状态码、分页和时间约定见 [接口与时间规范](contracts.md#接口与时间规范)。项目启动脚本和容器将 JVM、数据库连接及日志时区固定为 UTC；接口返回带 `Z` 的时间，前端按用户时区展示。直接在 IDE 启动时也应设置 `-Duser.timezone=UTC`。这不会修改 macOS 的系统时区。

## 本地快速启动

需要 JDK 25、支持 Compose Specification 的 Docker Compose、Bash、OpenSSL、curl。脚本优先使用仓库中锁定 Maven 3.9.12 的 Maven Wrapper，并检测 JDK 25，不要求修改系统默认 Java。

在仓库根目录运行：

```bash
./scripts/dev-init.sh
./scripts/backend.sh up
```

`dev-init.sh` 生成权限为 `600` 的 `.env`，分别随机生成数据库迁移账户、应用账户和初始管理员的密码，已有文件不会被覆盖。媒体目录写成绝对路径，供本地 Java 和容器共用。`.env` 是本机可信配置，脚本会读取它；不要粘贴来源不明的 Shell 内容。

`up` 依次启动本项目 PostgreSQL、等待数据库健康、编译可执行 JAR、执行一次性 Flyway 迁移，再在后台启动 API 并等待就绪。构建使用 `-Dmaven.test.skip=true`；当前没有新增测试用例。已有本地 API 实例运行时只显示状态，更新代码需要先停止再启动。

默认入口：

| 用途 | 地址或位置 |
| --- | --- |
| Swagger | http://127.0.0.1:18080/swagger-ui/index.html |
| OpenAPI JSON | http://127.0.0.1:18080/v3/api-docs |
| API 就绪状态 | http://127.0.0.1:18080/actuator/health/readiness |
| PostgreSQL | `127.0.0.1:15432`，数据库 `drama` |
| API 日志 | `.local/logs/backend.log` |
| 迁移日志 | `.local/logs/migrate.log` |
| 本地 API PID | `.local/run/backend.pid` |
| 媒体文件 | `.env` 的 `DRAMA_STORAGE_ROOT`，默认 `.local/data/media` |

密码只保存在本地 `.env`，脚本不会打印。用本地编辑器查看初始管理员凭据。修改 `.env` 的管理员密码不会重置已创建用户；初始化只创建尚不存在的用户。

## 常用命令

```bash
./scripts/backend.sh status
./scripts/backend.sh stop
./scripts/backend.sh build
./scripts/backend.sh migrate
./scripts/backend.sh start
```

- `build` 执行 clean package，清除改包或删除类后遗留的旧 class；优先使用仓库 Wrapper，Wrapper 不可执行时才回退到已安装的 Maven（要求 3.9.9 至 3.x）。首次运行可能下载 Maven 与 Java 依赖；构建关闭逐文件下载进度，保留警告和错误。
- `migrate` 要求数据库已启动、JAR 已构建。以 `drama_owner` 运行非 Web 迁移进程，成功后退出，不创建管理员、不初始化媒体目录，也不挂载媒体卷。
- `start` 要求迁移已完成。API 使用 `drama_app`，不执行 Flyway，进程环境不接收迁移账户密码。开发启动默认启用一次性管理员初始化；已有管理员后可设置 `DRAMA_BOOTSTRAP_ENABLED=false`。
- `stop` 只向 PID 文件中且命令标识匹配本项目的 API 发送正常终止信号，不停止数据库，不操作其他项目。
- `status` 显示本地 API 和本 Compose 项目的容器状态。

如未自动找到 JDK 25，在 `.env` 增加 `DRAMA_JAVA_HOME='/实际的/JDK25/路径'`，或者启动前设置 `JAVA_HOME`。不要把 JRE 路径当作构建使用的 JDK。

本项目默认占用 `18080` 和 `15432`，不会复用或停止其他项目的服务。API 端口由 `DRAMA_HTTP_PORT` 控制。若修改数据库端口，需同时修改 `compose.yaml` 中 PostgreSQL 的宿主机映射及 `.env` 的 `DRAMA_DB_URL`。

## Swagger 登录与接口操作

1. 打开 Swagger，执行 `GET /api/v1/auth/csrf`，获取 CSRF 令牌并写入 CSRF Cookie。
2. 执行 `POST /api/v1/auth/login`，使用 `.env` 中的管理员用户名和密码。
3. 登录成功会轮换会话并废弃旧 CSRF 令牌，再执行一次 `GET /api/v1/auth/csrf`。
4. 执行 `GET /api/v1/auth/me` 确认登录，然后创建工作空间、项目、镜头或上传媒体。

Swagger 已配置读取 CSRF Cookie 并在写请求中发送头部。使用其他客户端时，需要保存 Cookie，并按 `/auth/csrf` 返回的 `headerName` 和 `token` 发送写请求；登录前的令牌不能继续用于登录后的写操作。不要把浏览器中的 `localhost` 与 `127.0.0.1` 混用，否则会话 Cookie 属于不同主机。

主要业务入口以当前 Swagger 为准：

| 能力 | 路径 |
| --- | --- |
| 会话、登录、CSRF | `/api/v1/auth/*` |
| 工作空间 | `/api/v1/workspaces` |
| 项目 | `/api/v1/workspaces/{workspaceId}/projects` |
| 镜头列表与创建 | `/api/v1/workspaces/{workspaceId}/projects/{projectId}/shots` |
| 镜头详情、修改与历史版本 | `/api/v1/workspaces/{workspaceId}/shots/{shotId}` |
| 媒体上传、列表与详情 | `/api/v1/workspaces/{workspaceId}/media` |
| 媒体内容 | `/api/v1/workspaces/{workspaceId}/media/{mediaId}/content` |

镜头与资源修改需要按接口提交当前版本；冲突时重新读取最新版本后决定如何修改。媒体当前经 API 上传到本地目录，不能把它当成已实现的 S3 直传或转码服务。

## 使用容器运行 API

默认 Compose 只启动 PostgreSQL；`app` profile 才包含一次性 `migrate` 和 `api`。先在宿主机构建 JAR，Dockerfile 只封装 JRE 25 和该 JAR，不在镜像内下载 Maven 或编译代码。

```bash
./scripts/dev-init.sh
./scripts/backend.sh stop
./scripts/backend.sh build
docker compose --profile app up --build -d
docker compose --profile app ps -a
```

API 在 PostgreSQL 健康、`migrate` 成功退出后启动。可通过 Swagger 或 readiness 地址检查 API 是否就绪，Compose 的“容器运行中”不单独代表应用已就绪。容器日志使用 json-file 轮换，每个文件 10 MiB、保留 3 个。Docker 路径首次运行会下载 `eclipse-temurin:25-jre`；本地 Java 模式只需要 PostgreSQL 镜像。

```bash
docker compose logs --tail 100 api
docker compose logs --tail 100 migrate
docker compose --profile app stop api
```

容器内 API 固定监听 `0.0.0.0:18080`，只发布到宿主机 `127.0.0.1`。本地 Java API 与容器 API 使用相同端口和媒体目录，两种模式选择一种运行。`backend.sh stop` 不负责停止容器 API。

本地 Java 模式适合普通终端。某些自动化执行环境会在命令结束后回收后台子进程，即使启动时就绪检查已通过；需要在这类环境中持续驻留时，使用 Compose 的 API 容器，并再次读取 readiness 确认实际状态。

容器使用 `.env` 中的 `DRAMA_UID` 与 `DRAMA_GID` 访问挂载媒体目录；初始化脚本记录当前宿主用户。移动到另一台机器时，重新检查目录所有者与这两个值，不要使用开放写权限绕过问题。

## 数据库与数据保留

Compose 项目名固定为 `drama-foundation`。PostgreSQL 18 数据挂载到项目独立 named volume 的 `/var/lib/postgresql`，媒体用 `DRAMA_STORAGE_ROOT` 绑定挂载，两者都不随普通停机删除。目录挂载提供数据隔离，并不自动提供硬盘容量配额；需要固定容量时由宿主机独立分区或云盘配额控制。单次媒体上传上限默认 100 MiB。

首次初始化空数据库时，PostgreSQL 的 init 脚本创建非超级用户 `drama_app`。密码通过 psql 的环境变量读取和 SQL literal 引用传递，不嵌入 Shell 命令或打印。Flyway 的基础迁移为应用角色授予 schema 使用、DML 和后续表的默认权限，DDL 由 `drama_owner` 执行。

修改 `.env` 不会自动修改已有 PostgreSQL 角色密码；保留已有 `.env`，或者通过明确的数据库密码轮换流程同步更新角色与配置。不要删除数据卷来解决密码不一致。

数据库备份和媒体目录需要一起保存。当前本地文件写入与数据库元数据使用独立存储；备份时先停止写请求，避免数据库与文件快照处于不同时间点。

## 排障与部署边界

- 端口占用：脚本不终止占用者，检查本项目或其他服务的监听端口后调整配置。
- 迁移失败：查看 `.local/logs/migrate.log`，确认 JAR 为最新构建、数据库账户与 `.env` 一致。不要修改已应用的 Flyway 文件或执行清空数据库命令。
- 本地 API 未就绪：查看 `.local/logs/backend.log` 和 PID 状态；启动等待超时不会强杀进程，可明确执行 `stop` 后修复。
- 存储初始化失败：确认路径可写且不经过符号链接；当前本地存储要求文件系统支持 Java `SecureDirectoryStream`。
- `401`：尚未登录或会话失效；`403`：检查工作空间权限及写请求的 CSRF；版本冲突需重新读取资源，不能盲目覆盖。
- `.env`、日志和数据库转储可能包含本机配置或业务内容，不放入版本库或公开分享。

这些 Compose 和脚本用于本地开发。生产部署需要独立迁移步骤、HTTPS、密钥管理、持久存储与备份；生产 `prod` profile 关闭初始管理员创建和 Swagger，并启用 Secure 会话 Cookie。`prod` 要求显式配置 `DRAMA_DB_URL` 和绝对路径 `DRAMA_STORAGE_ROOT`，不继承开发数据库地址或相对媒体目录。生产使用 `SPRING_PROFILES_ACTIVE=prod` 或启动参数明确激活；当前开发 Compose 不会自动切为生产部署。当前文档不把尚未实现的 AI、任务队列或云存储能力描述为可运行功能。

## 配置归属与校验

通用默认值放在 application.yml；prod 覆盖安全默认值；migrate 仅用于独立迁移。密码只从外部环境注入，`.env.example` 不含可用密码。媒体配置采用 `MediaStorageProperties` 类型绑定和启动校验；跨模块与框架配置的一致性由 boot 的 `RuntimeConfigurationChecks` 校验。

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| DRAMA_UPLOAD_MAX_FILE_SIZE | 100MB | 单文件上限，同步用于 Servlet 解析器和媒体写入；不单独覆盖 spring.servlet.multipart.max-file-size |
| DRAMA_UPLOAD_MAX_REQUEST_SIZE | 101MB | 整个 multipart 请求上限，必须大于单文件上限，为边界和文件名等保留余量 |
| DRAMA_DB_POOL_MAX / DRAMA_DB_POOL_MIN | 10 / 2 | 最大连接数和最小空闲数；要求 0 ≤ min ≤ max，max > 0 |
| DRAMA_SQL_SLOW_THRESHOLD_MS | 300 | 慢 SQL 阈值，必须为正数；不记录绑定参数 |
| DRAMA_DOCS_ENABLED | true | 开发环境 Swagger/OpenAPI 开关；prod 默认关闭 |
| DRAMA_DOCS_PUBLIC | true | false 时启用的文档要求登录；关闭文档仍由 DOCS_ENABLED 决定 |
| DRAMA_COOKIE_SECURE | false | 本机 HTTP 开发默认；prod 固定启用 Secure |
| DRAMA_BOOTSTRAP_ENABLED | true（开发启动器） | 仅初始化尚不存在的管理员；prod 关闭 |

这些可选项已在开发 Compose 显式传递，修改 `.env` 后需重建 API 容器使其生效。提高上传上限时同时提高整个请求上限；冲突配置会在启动阶段报错。未来使用网关时还需配套修改网关请求大小限制。

## 本轮实际验证记录

2026-10-11 在本机使用 JDK 25、Maven Wrapper 3.9.12 和 Docker Desktop 完成构建与实际 HTTP 检查，没有编写测试用例。

- 全部 12 个 Maven 模块构建通过，可执行 JAR 与 JRE 25 容器构建成功。
- PostgreSQL 独立容器健康；4 个 Flyway 迁移成功，重复迁移无变更并正常退出。
- `drama_app` 对业务表及 Session 表有 DML 权限，对 public/content schema 无 CREATE 权限。
- Session 登录、CSRF 校验、退出正常；API 和数据库容器重建后，已有登录会话仍有效。
- 工作空间、项目、镜头创建成功；镜头修改产生第二份修订，旧修订可读，过期版本更新返回 409。
- 跨工作空间项目与媒体读取返回 404；无成员权限返回 403；坏 JSON、非法 UUID 和非法数字参数返回 400。
- 媒体落盘并保存元数据，下载内容与上传内容 SHA-256 一致；Range 请求返回 206；SVG 上传返回 415。
- Swagger 页面在浏览器正常展示，OpenAPI 提供 23 个操作，CSRF 接口无多余必填参数，媒体创建正确标注 201。

本机当前以 Compose 运行 API 与数据库，迁移容器以状态 0 退出。保留一个“示例制作空间”、示例项目、两个镜头修订和一张微型示例图，便于登录后查看。当前没有自动化回归、负载验证或生产部署验证。

2026-10-11 时区规范更新后再次构建并重建容器：PostgreSQL timezone/log_timezone 均为 UTC，JVM 入口显式携带 UTC 参数，实际日志输出完整 UTC 时间。真实接口验证了字符串 expectedRevision 更新、Swagger 字符串类型声明，以及媒体创建与读取的 createdAt 完全一致；本次核验工作空间已归档。

2026-10-11 分层与配置复核后，以 clean package 重新构建全部模块，并进行以下运行核验，没有新增测试用例或验证专用业务接口：

- 从真实 Spring 上下文读取 JsonMapper、MVC ConversionService 和 Clock，确认带偏移输入转换为 UTC、纯日期保持不变、嵌套 DTO 生效；无偏移日期时间、数字时间戳、非法日期和超过 6 位的小数秒被拒绝，Clock 对齐微秒。
- 容器中完成登录、创建临时工作空间和项目、媒体上传与下载；下载摘要一致，Range 返回 206，媒体创建与回读时间一致。OpenAPI 时间字段仍为 string/date-time，新增统一时间说明；临时空间已归档并退出会话。
- 私有文档配置下，未登录访问 OpenAPI 返回 401，登录后返回 200。将单文件上限临时配置为 1 KiB，实际超限上传返回 413；互相冲突的文件/请求上限阻止启动。
- prod 缺少数据库地址或存储目录时在初始化前失败，不创建占位符路径；使用完整配置的 prod 本地实例就绪返回 200，CSRF Cookie 带 Secure。检查实例已关闭；这不等同于已完成 HTTPS 网关和云端生产部署验证。
- 迁移容器无媒体挂载，成功退出为 0。当前驻留的仍是开发 Compose 实例；启动脚本及 Compose 配置语法检查通过。
