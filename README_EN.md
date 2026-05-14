# RabbitHole.fm

RabbitHole.fm is a web radio project that combines Netease Cloud Music integration, song request workflows, and AI DJ voice narration. It uses a separated frontend-backend architecture: the frontend is built with Vue 3 + Vite, while the backend is built with Spring Boot 3. The system supports user authentication, channel playback, music search, song requests, favorites, listening history, and AI-generated DJ transitions.

[中文](./README.md)

![RabbitHole.fm Preview](./frontend/src/assets/hero.png)

## Highlights

- Continuous radio-style playback with channel-based queues
- AI DJ transition generation based on song context and TTS synthesis
- Interactive song request flow for authenticated users
- Music search, song detail lookup, lyrics, covers, and playback URLs
- User features including favorites, listening history, profile editing, and avatar upload

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
├─ start-netease-api.bat    Windows helper script for the Netease API
└─ README.md                Chinese README
```

## Quick Start

### Requirements

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8.x
- Redis 7.x

### 1. Initialize the database

Run [sql/init.sql](./sql/init.sql) to create the required tables.

### 2. Configure the backend

The backend configuration file is [backend/src/main/resources/application.yml](./backend/src/main/resources/application.yml).

Default settings include:

- Server port: `8080`
- MySQL URL: `jdbc:mysql://127.0.0.1:3306/RabbitHole.fm`
- Redis host: `127.0.0.1:6379`
- Netease API base URL: `http://127.0.0.1:3000`
- Mimo API key: loaded from the `MIMO_API_KEY` environment variable

Update these values if your local environment uses different credentials or service addresses.

### 3. Start dependency services

Start Redis and the Netease API with Docker Compose:

```bash
docker compose up -d
```

Or start the Netease API separately:

```bash
npx NeteaseCloudMusicApi
```

The repository also includes [start-netease-api.bat](./start-netease-api.bat) as a Windows helper script.

### 4. Start the backend

```bash
cd backend
mvn spring-boot:run
```

### 5. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/avatars` requests to `http://localhost:8080`.

## Core API Modules

- `/api/user`: registration, login, profile, avatar, favorites, history
- `/api/music`: search, song detail lookup, cover proxy, API status
- `/api/radio`: channel loading, next-item retrieval, DJ audio generation
- `/api/request`: song requests, cancellation, personal request history, channel queue
- `/api/tts`: text-to-speech testing and synthesis

## Deployment Notes

- Build the frontend with `npm run build` and deploy it as static assets
- Package and run the backend independently with Maven
- Full functionality requires MySQL, Redis, the Netease API service, and Mimo API access

## Notes

- The current [backend/src/main/resources/application.yml](./backend/src/main/resources/application.yml) contains sample database credentials, Redis password, and JWT settings. Replace them before production use
- Music features depend on a third-party Netease API service, so availability depends on that service
- AI DJ and TTS features require `MIMO_API_KEY` to be configured

## License

This project is licensed under the terms defined in [LICENSE](./LICENSE).
