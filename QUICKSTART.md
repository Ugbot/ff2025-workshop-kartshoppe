# 🚀 KartShoppe Quick Start Guide

This guide will help you get the KartShoppe platform up and running in minutes.

## 📋 Prerequisites

Before starting, ensure you have:

- ✅ **Docker Desktop** installed and running
- ✅ **Java 11+** (for building) and **Java 17+** (for running Quarkus)
- ✅ **Node.js 18+** (for frontend)
- ✅ **8GB RAM** minimum

Check versions:
```bash
docker --version
java -version
node --version
```

---

## 🎯 Option 1: One-Command Startup (Easiest)

Run everything in the correct order automatically:

```bash
./start-platform.sh
```

This will:
1. Start infrastructure (Redpanda, PostgreSQL)
2. Open Quarkus in a new terminal window
3. Open Frontend in a new terminal window

**That's it!** Access the app at: **http://localhost:3000**

---

## 🔧 Option 2: Manual Step-by-Step Startup

If you prefer to run each component separately or the automatic script doesn't work:

### Step 1: Start Infrastructure

In **Terminal 1**:
```bash
./1-start-infrastructure.sh
```

Wait for the "Infrastructure Ready!" message before continuing.

### Step 2: Start Quarkus API

In **Terminal 2**:
```bash
./2-start-quarkus.sh
```

Wait for Quarkus to show "Listening on: http://0.0.0.0:8080" before continuing.

### Step 3: Start Frontend

In **Terminal 3**:
```bash
./3-start-frontend.sh
```

Wait for Vite to show "Local: http://localhost:3000"

---

## 🎉 Access the Platform

Once all services are running:

| Service | URL | Purpose |
|---------|-----|---------|
| **KartShoppe App** | http://localhost:3000 | Main application |
| **Quarkus Dev UI** | http://localhost:8080/q/dev | API management |
| **Redpanda Console** | http://localhost:8085 | Kafka topic viewer |

---

## 🔄 Running Flink Jobs (Optional)

The platform runs without Flink jobs, but they add real-time features:

### Inventory Management Job
```bash
./start-inventory.sh
```

### Basket Analysis Job (Recommendations)
```bash
./start-basket-job.sh
```

### Shopping Assistant (AI Chat)
```bash
./start-shopping-assistant.sh
```

**Monitor Flink jobs:** http://localhost:8081

---

## 🛑 Stopping Everything

To cleanly shut down all services:

```bash
./stop-platform.sh
```

This will:
- Stop the frontend (port 3000)
- Stop Quarkus API (port 8080)
- Stop Docker services (Redpanda, PostgreSQL)
- Clean up processes and temporary files

---

## 🐛 Troubleshooting

### Port Already in Use

If you get "port already in use" errors:

```bash
# Kill process on port 8080 (Quarkus)
lsof -ti:8080 | xargs kill -9

# Kill process on port 3000 (Frontend)
lsof -ti:3000 | xargs kill -9
```

### Docker Services Won't Start

```bash
# Clean up and restart
docker compose down
docker system prune -f
./1-start-infrastructure.sh
```

### Quarkus Build Fails

```bash
# Clean build
./gradlew clean build
```

### Frontend "Offline" Status

Check that:
1. Redpanda is running: `lsof -i:19092`
2. Quarkus is running: `curl http://localhost:8080/q/health`
3. WebSocket connects to port 8080 (check browser console)

---

## 📁 Project Structure

```
Ververica-visual-demo-1/
├── 1-start-infrastructure.sh    # Start Docker services
├── 2-start-quarkus.sh            # Start Quarkus API
├── 3-start-frontend.sh           # Start React frontend
├── start-platform.sh             # All-in-one startup
├── stop-platform.sh              # Shutdown all services
├── quarkus-api/                  # Backend API
├── kartshoppe-frontend/          # React frontend
├── flink-inventory/              # Flink inventory job
├── flink-recommendations/        # Flink basket analysis
└── docker-compose.yml            # Infrastructure config
```

---

## 🎓 What's Running?

### Without Flink Jobs
- **Frontend**: Real-time UI with WebSockets
- **Quarkus API**: REST API + WebSocket server
- **Redpanda**: Kafka-compatible event streaming
- **PostgreSQL**: Operational database

### With Flink Jobs
- **Inventory Job**: Real-time stock tracking
- **Basket Analysis**: ML-powered recommendations
- **Shopping Assistant**: AI-powered chat

---

## 📖 Next Steps

1. **Explore the UI**: http://localhost:3000
2. **View Kafka topics**: http://localhost:8085
3. **Check API docs**: http://localhost:8080/q/dev
4. **Run a Flink job**: `./start-basket-job.sh`
5. **Read the full README**: `README.md`

---

## 💡 Tips

- **First time?** Use `./start-platform.sh` for the easiest experience
- **Developing?** Run steps manually to restart individual components
- **Flink jobs are optional** - the platform works without them
- **Check logs** in each terminal window for debugging
- **Redpanda Console** (port 8085) is great for seeing real-time events

---

## 🆘 Need Help?

- Check the main **README.md** for detailed documentation
- View Quarkus logs in Terminal 2
- View Frontend logs in Terminal 3
- Check Docker logs: `docker compose logs -f`

Happy building! 🎉
