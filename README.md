# Royal Poker Platform

Enterprise-grade online poker platform supporting Texas Hold'em, Omaha, Pineapple, 5-Card Draw, and Razz.  
Built on the same architecture and aesthetic as the Royal Rummy platform, with full audio effects, dedicated Dark Titanium Admin Panel, and real-time live spectator.

---

## 🏗️ Technology Stack

- **Player Client (`frontend`):** React 18, Vite, TypeScript, Tailwind CSS, Zustand, Casino Audio Engine (Port `5173`)
- **Game Backend (`backend`):** Spring Boot 4, Java 21, WebSocket, Maven (Port `8080`)
- **Admin Backend (`admin-backend`):** Node.js, Express.js, TypeScript, Mongoose (Port `5050`)
- **Admin Portal (`admin-frontend`):** React 18, Vite, TypeScript, Lucide, Bespoke Dark Titanium Command Center (Port `5180`)
- **Database:** MongoDB 7 (Port `27017`)

---

## 🚀 How to Run Locally

### 1. Database (MongoDB)
```bash
docker compose up -d mongo
```

### 2. Game Backend (Java / Spring Boot)
```bash
cd backend
mvnw.cmd clean test
mvnw.cmd spring-boot:run
```
- API Status: http://localhost:8080/api/status
- Health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui.html

### 3. Player Frontend (React)
```bash
cd frontend
npm install
npm run dev
```
- Web UI: http://localhost:5173

### 4. Admin Backend (Express.js)
```bash
cd admin-backend
npm install
npm run dev
```
- Admin API: http://localhost:5050

### 5. Admin Portal Frontend (React)
```bash
cd admin-frontend
npm install
npm run dev
```
- Admin Command Center: http://localhost:5180

---

## 📁 Repository Structure

```text
E:\Pocker/
├── admin-backend/                 # Standalone Express.js Admin API (Port 5050)
│   ├── src/
│   │   ├── controllers/          # Overview, Poker Variants, Tables, Replay, Config
│   │   ├── models/               # Game, GameResult, GameEvent, WalletTransaction, PokerTable
│   │   └── routes/               # Admin endpoints & RBAC
├── admin-frontend/                # Standalone React Admin Portal (Port 5180)
│   ├── src/
│   │   ├── pages/variants/       # Holdem, Omaha, Pineapple, Draw & Razz Dashboards
│   │   ├── pages/HandReplayPage  # Street-by-street poker dispute replay
│   │   └── pages/RulesConfigPage # Live rake % and blind configurations
├── backend/                       # Java 21 Spring Boot Game Engine (Port 8080)
│   └── src/main/java/com/poker/  # Variants, TableService, Bots, WebSocket
└── frontend/                      # React Player Client with Casino Board & Audio (Port 5173)
    ├── public/sounds/             # Card deals, card slides, fan, coin clicks, fanfare win
    ├── public/avatars/            # 30 high-res Boy & Girl avatars
    └── src/audio/soundEngine.ts   # Hybrid Web Audio + sample engine
```
