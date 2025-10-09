# Windows Scripts for KartShoppe Training Platform

This folder contains Windows batch scripts (.bat files) equivalent to the Unix shell scripts in the parent directory.

## Prerequisites for Windows

Before using these scripts, ensure you have:

- **Docker Desktop for Windows** - Running in either Windows or Linux (WSL2) mode
- **Java 17+** - For running Quarkus (Java 11 also needed for Flink jobs)
- **Node.js 18+** - For the frontend
- **Git Bash** (Optional) - Recommended for SDKMAN Java version management

### Installing Java on Windows

**Option 1: Direct Installation**
- Download Java 17+ from [Adoptium](https://adoptium.net/)
- Install and add to PATH

**Option 2: Using SDKMAN (Recommended)**
1. Install Git Bash: https://git-scm.com/download/win
2. Open Git Bash and run:
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source "$HOME/.sdkman/bin/sdkman-init.sh"
   ```
3. Install Java versions:
   ```bash
   sdk install java 11.0.25-tem
   sdk install java 17.0.13-tem
   sdk default java 17.0.13-tem
   ```

---

## Script Usage Order

### 1. One-Time Setup (Run Once)

```cmd
0-setup-environment.bat
```

This verifies all prerequisites are installed:
- Docker Desktop
- Java 17+
- Node.js 18+
- Gradle wrapper

---

### 2. Start the Platform

```cmd
start-training-platform.bat
```

This starts:
1. Redpanda (Kafka) in Docker
2. Creates all required Kafka topics
3. Installs frontend dependencies
4. Builds models module
5. Starts Quarkus API with integrated frontend

**Access points after startup:**
- KartShoppe App: http://localhost:8081
- Quarkus Dev UI: http://localhost:8081/q/dev
- Redpanda Console: http://localhost:8085

---

### 3. Start Flink Jobs (As Needed)

Run these in separate command prompt windows:

#### Module 1: Inventory Management
```cmd
flink-1-inventory-job.bat
```

#### Module 2: Basket Analysis & Recommendations
```cmd
flink-2-basket-analysis-job.bat
```

#### Module 3: Hybrid Source with Pre-training
```cmd
flink-3-hybrid-source-job.bat
```

#### Module 4: Shopping Assistant (AI Chat)
```cmd
flink-4-shopping-assistant-job.bat
```

---

### 4. Stop Everything

```cmd
stop-platform.bat
```

This stops:
- Quarkus API
- Frontend (if running separately)
- All Docker containers
- Gradle daemons

---

## Running Scripts

### From File Explorer
Double-click any `.bat` file to run it in a command prompt window.

### From Command Prompt
```cmd
cd path\to\Ververica-visual-demo-1\windows-scripts
start-training-platform.bat
```

### From PowerShell
```powershell
cd path\to\Ververica-visual-demo-1\windows-scripts
.\start-training-platform.bat
```

---

## Troubleshooting

### "Docker is not running"
- Start Docker Desktop
- Wait for it to fully initialize
- Run the script again

### "Java 17+ is required"
If you have multiple Java versions installed, ensure Java 17+ is in your PATH:

```cmd
java -version
```

Should show Java 17 or higher.

### Port Already in Use

Kill processes on specific ports:

```cmd
REM Find process on port 8081
netstat -ano | findstr :8081

REM Kill process (replace PID with actual process ID)
taskkill /F /PID <PID>
```

### Gradle Build Fails

Clean and rebuild:
```cmd
gradlew clean build
```

### Scripts Show Encoding Issues

If you see garbled characters instead of box-drawing characters:
1. Right-click Command Prompt title bar
2. Select "Properties"
3. Set Font to "Consolas" or "Lucida Console"

---

## Differences from Unix Scripts

| Feature | Unix Scripts | Windows Scripts |
|---------|-------------|-----------------|
| **Process Management** | `lsof`, `kill` | `netstat`, `taskkill` |
| **Background Jobs** | `&`, `nohup` | `start` command |
| **Path Separators** | `/` | `\` |
| **Environment Variables** | `$VAR` | `%VAR%` |
| **SDKMAN Integration** | Native | Via Git Bash |

---

## Notes for Windows Users

### WSL2 vs Native Windows

These scripts are designed for **native Windows** (cmd.exe). If you're using WSL2:
- Use the Unix `.sh` scripts in the parent directory instead
- Run `bash ./start-training-platform.sh` from WSL2 terminal

### Quarkus Dev Mode

The Quarkus dev server opens in a separate window. Keep this window open while developing. To see logs, check that window.

### Java Version Switching

If you need to switch between Java 11 (for Flink) and Java 17 (for Quarkus):

**With SDKMAN (Git Bash):**
```bash
sdk use java 11.0.25-tem  # For Flink
sdk use java 17.0.13-tem  # For Quarkus
```

**Without SDKMAN:**
Manually update your `JAVA_HOME` and `PATH` environment variables.

---

## Quick Reference

### Essential Commands

```cmd
REM One-time setup
0-setup-environment.bat

REM Start platform
start-training-platform.bat

REM Module 1: Inventory
flink-1-inventory-job.bat

REM Module 2: Recommendations
flink-2-basket-analysis-job.bat

REM Module 3: Hybrid Source
flink-3-hybrid-source-job.bat

REM Module 4: AI Assistant
flink-4-shopping-assistant-job.bat

REM Stop everything
stop-platform.bat
```

### Key URLs

| Service | URL |
|---------|-----|
| **KartShoppe App** | http://localhost:8081 |
| **Quarkus Dev UI** | http://localhost:8081/q/dev |
| **Redpanda Console** | http://localhost:8085 |
| **API Docs** | http://localhost:8081/q/swagger-ui |

---

## Getting Help

- Check the parent directory's `TRAINING-SETUP.md` for detailed training instructions
- Check `QUICKSTART.md` in the parent directory for general setup guidance
- For script issues, check the Command Prompt window for error messages
- Docker logs: `docker compose logs -f`

---

## Contributing

If you find issues with these Windows scripts or have improvements:

1. Test thoroughly on Windows 10/11
2. Ensure compatibility with both cmd.exe and PowerShell
3. Update this README with any changes
4. Keep scripts in sync with the Unix versions

---

**Happy Learning on Windows! 🎉**
