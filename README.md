# RabbitHole.fm

RabbitHole.fm 是一个结合了网易云音乐接口、点歌流程与 AI DJ 语音播报的 Web 电台项目。项目采用前后端分离架构，前端基于 Vue 3 + Vite，后端基于 Spring Boot 3，支持用户登录、频道收听、歌曲搜索、点歌、收藏、播放历史和 AI DJ 串场播报。

[English](./README_EN.md)

![RabbitHole.fm Preview](./frontend/src/assets/hero.png)

## 项目亮点

- 电台式连续播放：按频道加载节目队列并连续播放歌曲
- AI DJ 串场播报：基于前后歌曲上下文生成播报文案，并通过 TTS 输出语音
- 点歌互动：支持用户登录后点歌、取消点歌、查看个人点歌记录和频道点歌队列
- 音乐信息整合：支持歌曲搜索、歌曲详情、歌词、封面与播放链接获取
- 用户能力：支持注册、登录、收藏歌曲、收藏频道、播放历史记录与头像上传

## 技术栈

- 前端：Vue 3、Vite、Vue Router、Pinia、Axios
- 后端：Spring Boot 3、Spring Security、MyBatis-Plus、Redis、OkHttp、JWT
- 数据库：MySQL
- 外部能力：NeteaseCloudMusicApi、小米 Mimo TTS / LLM

## 项目结构

```text
RabbitHole.fm/
├─ backend/                 Spring Boot 后端
├─ frontend/                Vue 3 前端
├─ sql/                     数据库初始化脚本
├─ doc/                     开发过程文档
├─ NeteaseCloudMusicApi/    第三方接口相关目录
├─ docker-compose.yml       Redis / 网易云接口辅助服务
├─ start-netease-api.bat    Windows 启动网易云接口脚本
└─ README_EN.md             英文说明
```

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.x
- Redis 7.x

### 1. 初始化数据库

执行 [sql/init.sql](./sql/init.sql) 创建项目所需表结构。

### 2. 配置后端

后端配置文件位于 [backend/src/main/resources/application.yml](./backend/src/main/resources/application.yml)。

默认配置包括：

- 服务端口：`8080`
- MySQL 地址：`jdbc:mysql://127.0.0.1:3306/RabbitHole.fm`
- Redis 地址：`127.0.0.1:6379`
- 网易云接口地址：`http://127.0.0.1:3000`
- Mimo API Key：从环境变量 `MIMO_API_KEY` 读取

如果你的本地数据库、Redis 密码或第三方接口配置不同，请按需修改。

### 3. 启动依赖服务

使用 Docker Compose 启动 Redis 和网易云接口：

```bash
docker compose up -d
```

也可以单独启动网易云接口：

```bash
npx NeteaseCloudMusicApi
```

仓库内提供了 [start-netease-api.bat](./start-netease-api.bat) 作为 Windows 启动脚本参考。

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认通过 Vite 代理将 `/api` 和 `/avatars` 请求转发到 `http://localhost:8080`。

## 核心模块

- `/api/user`：注册、登录、资料、头像、收藏、历史
- `/api/music`：搜索歌曲、查询歌曲详情、封面代理、接口状态
- `/api/radio`：频道加载、下一首获取、DJ 音频播报
- `/api/request`：点歌、取消点歌、个人点歌记录、频道点歌队列
- `/api/tts`：文本转语音测试与合成

## 部署说明

- 前端可通过 `npm run build` 生成静态资源后部署到 Nginx 等静态服务器
- 后端可通过 Maven 打包后独立运行
- 若需完整功能，部署时需同时保证 MySQL、Redis、网易云接口服务和 Mimo API 可用

## 注意事项

- 当前 [backend/src/main/resources/application.yml](./backend/src/main/resources/application.yml) 中包含示例数据库账号、Redis 密码和 JWT 配置，正式部署前建议替换为你自己的安全配置
- 网易云音乐相关接口依赖第三方服务，稳定性与可用性受其运行状态影响
- 若未配置 `MIMO_API_KEY`，AI DJ 与 TTS 相关能力将无法正常工作

## License

本项目使用 [LICENSE](./LICENSE) 中定义的许可协议。
