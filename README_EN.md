# RabbitHole.fm

RabbitHole.fm is a web radio project that combines Netease Cloud Music integration, song request workflows, and AI DJ voice narration. It uses a separated frontend-backend architecture: the frontend is built with Vue 3 + Vite, while the backend is built with Spring Boot 3. The system supports user authentication, channel playback, music search, song requests, favorites, listening history, and AI-generated DJ transitions.

中文说明: [README.md](D:\JAVA\ideaProjects\RabbitHole.fm\README.md)

## Features

- Radio channel playback with continuous queue-based listening
- Music search and song detail retrieval through the Netease Cloud Music API
- AI DJ transitions generated from song context and synthesized into audio
- User system with registration, login, profile updates, and avatar upload
- Favorites and history for songs, channels, and playback records
- Song request queue for authenticated users
- Cover image proxying to bypass direct access restrictions on some media links

## Tech Stack

- Frontend: Vue 3, Vite, Vue Router, Pinia, Axios
- Backend: Spring Boot 3, Spring Security, MyBatis-Plus, Redis, OkHttp, JWT
- Database: MySQL
- External services: NeteaseCloudMusicApi, Xiaomi Mimo TTS / LLM

## Project Structure

```text
RabbitHole.fm/
├─ backend/                 Spring Boot backend
├─ frontend/                Vue 3 frontend
├─ sql/                     Database initialization script
├─ doc/                     Development notes
├─ NeteaseCloudMusicApi/    Third-party API related directory
├─ docker-compose.yml       Redis and Netease API helper services
├─ start-netease-api.bat    Windows helper script for Netease API
└─ README.md                Chinese README
```

## Requirements

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.x
- Redis 7.x

## Setup

### 1. Initialize the database

Run [sql/init.sql](D:\JAVA\ideaProjects\RabbitHole.fm\sql\init.sql) to create the required tables.

### 2. Configure backend settings

The backend configuration file is located at [backend/src/main/resources/application.yml](D:\JAVA\ideaProjects\RabbitHole.fm\backend\src\main\resources\application.yml).

Default settings include:

- Server port: `8080`
- MySQL URL: `jdbc:mysql://127.0.0.1:3306/RabbitHole.fm`
- Redis host: `127.0.0.1:6379`
- Netease API base URL: `http://127.0.0.1:3000`
- Mimo API key: read from the `MIMO_API_KEY` environment variable

Update these values if your local environment uses different credentials or service addresses.

### 3. Start Redis and the Netease API service

You can use Docker Compose:

```bash
docker compose up -d
```

Or start the Netease API separately:

```bash
npx NeteaseCloudMusicApi
```

The repository also includes [start-netease-api.bat](D:\JAVA\ideaProjects\RabbitHole.fm\start-netease-api.bat) as a Windows helper script.

## Run the Project

### Start the backend

```bash
cd backend
mvn spring-boot:run
```

### Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/avatars` requests to `http://localhost:8080`.

## Main API Modules

- `/api/user`: registration, login, profile, avatars, favorites, history
- `/api/music`: song search, song detail lookup, cover proxy, API health check
- `/api/radio`: channel loading, next-item retrieval, DJ audio generation
- `/api/request`: submit request, cancel request, personal request history, channel queue
- `/api/tts`: text-to-speech testing and synthesis

## Deployment Notes

- The frontend can be built with `npm run build` and deployed as static assets
- The backend can be packaged and run independently with Maven
- For full functionality, MySQL, Redis, the Netease API service, and Mimo API access must all be available

## Notes

- The current [backend/src/main/resources/application.yml](D:\JAVA\ideaProjects\RabbitHole.fm\backend\src\main\resources\application.yml) contains sample database credentials, Redis password, and JWT settings. Replace them before production deployment
- Music-related features depend on a third-party Netease API service, so availability depends on that service
- AI DJ and TTS features will not work unless `MIMO_API_KEY` is configured

## License

This project is licensed under the terms defined in [LICENSE](D:\JAVA\ideaProjects\RabbitHole.fm\LICENSE).
