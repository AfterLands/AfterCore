<div align="center">
  <img src="https://afterlands.com/assets/images/logo.png" width="600" alt="Afterlands Logo">

  # AfterCore

  **The Core Library for the AfterLands Ecosystem**

  [![Website](https://img.shields.io/website?label=afterlands.com&style=for-the-badge&url=https://afterlands.com/)](https://afterlands.com/)
  [![Discord](https://img.shields.io/discord/1072151613805953106?color=5865F2&label=Community&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/ySZjhRFyCy)
  [![Status](https://img.shields.io/badge/Status-Stable-success?style=for-the-badge)](https://github.com/AfterLands/AfterCore)

  ---
  
  <p align="center">
    <strong>AfterCore</strong> is a high-performance library plugin for <strong>Minecraft 1.8.8 (Java 21)</strong>.
    <br>
    It creates a shared infrastructure (Database, Inventory GUI, Actions, Conditions) to eliminate code duplication across all AfterLands plugins while maintaining <strong>20 TPS at 500+ concurrent players</strong>.
  </p>
</div>

## 📚 Documentation

The complete documentation is available in our **[GitHub Wiki](docs/wiki/Home)**.

*   **[Getting Started](docs/wiki/Getting-Started)**
*   **[Inventory Framework](docs/wiki/InventoryService)**
*   **[Core APIs](docs/wiki/AfterCoreAPI)**

## ✨ Key Features

### 🎨 Inventory Framework
A complete, high-performance GUI system.
*   **YAML Configuration**: Create menus without code.
*   **Performance**: Hybrid pagination (35x faster), smart caching (Caffeine).
*   **Features**: Animations, Drag-and-drop, Shared Inventories (Sessions).
*   **[Examples](docs/wiki/Inventory-Framework-Examples)** | **[API Reference](docs/wiki/Inventory-Framework-API)**

### 🔧 Core Services
*   **Async Database**: HikariCP pooling for MySQL/SQLite.
*   **Conditions & Actions**: Unified engine for gameplay logic.
*   **Diagnostics**: `/acore` command for real-time health checks.
*   **Metrics**: Custom metrics collection with low overhead.

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-333333?style=for-the-badge&logo=minecraft&logoColor=25D366)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

## 🚀 Usage

### Building
```bash
mvn clean package
```

### Dependency (Maven)
```xml
<dependency>
    <groupId>com.afterlands</groupId>
    <artifactId>aftercore</artifactId>
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

### Getting the API
```java
AfterCoreAPI core = AfterCore.get();
InventoryService inv = core.inventory();
```

---

<div align="center">
  <sub><strong>Developed with ❤️ by the AfterLands Team.</strong></sub>
</div>
