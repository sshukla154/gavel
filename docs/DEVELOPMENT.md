# Development guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended: https://adoptium.net |
| Maven | 3.9+ | Or use `./mvnw` wrapper if added later |
| Docker Desktop | latest stable | Required for Phase 0.2+ (Testcontainers, Compose) |
| kind | 0.24+ | Kubernetes in Docker — Phase 0.4 |
| kubectl | 1.31+ | Phase 0.4 |
| helm | 3.16+ | Phase 0.4 |
| argocd CLI | 2.13+ | Phase 0.4 |
| Node.js | 20 LTS | Angular frontend — Phase 1 |

## Clone and build

```bash
git clone https://github.com/sshukla154/gavel.git
cd gavel
mvn clean verify
```

`mvn clean verify` compiles all modules, runs unit and integration tests, and produces the executable jar. Expect \~1 minute on a warm Maven cache.

## Run hello-service locally

> **Windows note:** the system default `JAVA_HOME` may point at a non-21 JDK. Prefix Maven commands with the override below, or set `JAVA_HOME` permanently in your user environment variables.
> ```powershell
> $env:JAVA_HOME = "$env:USERPROFILE\scoop\apps\zulu21-jdk\current"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> ```

```bash
# Start the service (port 8081)
mvn -pl services/hello-service -am spring-boot:run

# In a second terminal
curl http://localhost:8081/api/v1/ping
curl http://localhost:8081/actuator/health
```

The `-am` flag builds `gavel-common` first so hello-service has its dependency.

## IDE setup (IntelliJ IDEA)

1. **Open**: File → Open → select the root `pom.xml`, open as project.
2. **SDK**: File → Project Structure → SDKs → add JDK 21 if not present. Set Project SDK and language level to 21.
3. **Maven**: IntelliJ auto-imports. If not, right-click `pom.xml` → Maven → Reimport.
4. **Run config**: Right-click `HelloApplication.java` → Run. The embedded Tomcat starts on 8081.
5. **EditorConfig**: IntelliJ respects `.editorconfig` natively — no plugin needed.

## Project structure

```
gavel/
├── pom.xml                          # aggregator + dependency management
├── common/                          # gavel-common library
│   └── src/main/java/com/shukla/gavel/common/
│       ├── api/ApiResponse.java     # generic response envelope
│       └── error/ProblemDetails.java # RFC 7807 error shape
└── services/
    └── hello-service/
        ├── src/main/java/com/shukla/gavel/hello/
        │   ├── HelloApplication.java
        │   ├── PingController.java
        │   └── infrastructure/TomcatNio2Config.java
        ├── src/main/resources/
        │   ├── application.yaml
        │   └── logback-spring.xml
        └── src/test/java/com/shukla/gavel/hello/
            └── PingControllerTest.java
```

## Troubleshooting

**`mvn` not found on PATH (Git Bash / PowerShell)**
- Git Bash: add Maven's `bin/` to `~/.bashrc` or use the full path.
- PowerShell: add to system PATH via Environment Variables, or prefix commands with the full Maven path.

**Port 8081 already in use**
- `netstat -ano | findstr 8081` (PowerShell) to find the PID, then `taskkill /PID <pid> /F`.

**Tests fail with connection refused**
- Phase 0.1 tests have no external dependencies; if they fail it is a compilation or classpath issue. Paste the full Maven output.

**`Unable to establish loopback connection` on startup (Windows 11 + JDK 21)**
- JDK 21's `WEPollSelectorImpl` uses Unix Domain Socket loopback for its internal NIO pipe. On some Windows 11 Enterprise builds this returns `WSAEINVAL`. The codebase already works around this via `TomcatNio2Config`, which switches Tomcat to the NIO2 connector (IOCP-based, no `Selector.open()`). If you see this error despite the fix, verify that `TomcatNio2Config` is on the classpath and that you're using JDK 21 (not JDK 8 which is the system default on this machine).
