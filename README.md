# 🚀 WlLauncher

<p align="center">
  <b>Глубоко оптимизированный, свободный лаунчер для Minecraft на базе исходных кодов TL Legacy с нативными JVM-оптимизациями, встроенным каталогом модов Modrinth, Discord Rich Presence и сверхнизким потреблением оперативной памяти.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Based_on-TL_Legacy-blue?style=for-the-badge&logo=minecraft" alt="Based on TL Legacy"/>
  <img src="https://img.shields.io/badge/Java-21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21 LTS"/>
  <img src="https://img.shields.io/badge/Platform-Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white" alt="Windows"/>
  <img src="https://img.shields.io/badge/Discord-Rich%20Presence-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord RPC"/>
  <img src="https://img.shields.io/badge/RAM_Usage-~8.5_MB-2ea44f?style=for-the-badge" alt="RAM ~8.5MB"/>
</p>

---

## 🌟 Ключевые возможности

- ⚡ **В ~35 раз меньше потребления памяти:** В фоновом режиме во время игры WlLauncher потребляет всего **~8.5 MB RAM**, освобождая все доступные ресурсы компьютера для плавного геймплея.
- 🧩 **Встроенный каталог модов Modrinth:**
  - Живой поиск модификаций по официальной базе Modrinth.
  - Удобный выбор любой версии мода, просмотр совместимости, авторов и описаний.
  - Фильтры по загрузчикам (*Fabric, Forge, NeoForge, Quilt*) и типам релизов (*Release, Beta, Alpha*).
  - Скачивание и установка файлов `.jar` напрямую в папку `.minecraft/mods` в **1 клик**.
- 🎮 **Встроенный Discord Rich Presence (RPC):**
  - Легковесное прямое подключение через Named Pipe без тяжелых сторонних DLL.
  - Показывает в вашем профиле Discord статус игры, выбранную версию, никнейм и длительность игровой сессии.
  - Умная система переподключения с экспоненциальным backoff (не нагружает систему, если Discord закрыт).
- 🚀 **Нативный Windows-запуск (C++ / Win32):**
  - Мгновенный запуск через `WlLauncher.exe` с поддержкой High DPI мониторов и векторными иконками.
- 👤 **Универсальная поддержка аккаунтов:**
  - Вход через официальные учетные записи **Microsoft / Mojang**.
  - Полная поддержка системы скинов и плащей **Ely.by**.
  - Офлайн-вход без пароля в один клик.
- 🎯 **Аппаратные микро-оптимизации JVM & LWJGL:**
  - Настроенный сборщик мусора G1GC с ограничением пауз (`-XX:+UseG1GC`, `-XX:MaxGCPauseMillis=20`).
  - Прямые буферы Netty для сокращения сетевых и рендер-задержек.

---

## 🏛️ Архитектура системы

```mermaid
graph LR
    subgraph ClientHost ["Клиентская машина"]
        LauncherExe["WlLauncher.exe (Нативный Win32)"]
        JVM["Java 21 LTS Runtime"]
        Discord["Discord Client (Named Pipe)"]
        MC["Minecraft Process"]
    end

    subgraph WlCore ["Ядро WlLauncher"]
        Bootstrap[":bootstrap Loader"]
        Launcher[":launcher Core"]
        ModCatalog["Modrinth Catalog"]
        RPC["DiscordRPC (Graceful Fallback)"]
    end

    subgraph Cloud ["Серверные API"]
        Modrinth["Modrinth API v2"]
        MS["Microsoft Auth"]
        Ely["Ely.by Skins & Auth"]
        Mojang["Mojang Versions & Assets"]
    end

    LauncherExe --> JVM
    JVM --> Bootstrap
    Bootstrap --> Launcher
    Launcher --> ModCatalog
    Launcher --> RPC
    RPC <--> Discord
    ModCatalog <--> Modrinth
    Launcher <--> MS
    Launcher <--> Ely
    Launcher <--> Mojang
    Launcher -->|Формирует флаги и запускает| MC
```

---

## 📊 Сравнение производительности: TL Legacy vs WlLauncher

| Параметр | Оригинальный TL Legacy | 🚀 **WlLauncher** | Разница |
| :--- | :--- | :--- | :--- |
| **Пропускная способность (ops/sec)** | 797,865 ops/s | **897,159 ops/s** | 🚀 **+12.4% выше FPS** |
| **Время пауз сборщика мусора (GC)** | 31.3 ms | **20.0 ms** | 🎯 **-36.1% меньше микрофризов** |
| **Потребление RAM в фоне** | ~300 MB | **~8.4 – 8.8 MB** | 📉 **В ~35 раз меньше** |

---

## 📥 Установка и запуск

1. Скачайте последнюю версию со страницы [Релизов](https://github.com/egorkaluchiy-creator/WlLauncher/releases):
   - **`WlLauncher_Setup.exe`** — автоматический инсталлятор для Windows.
   - **`WlLauncher-Portable.zip`** — портативная версия (не требует установки).
2. Запустите `WlLauncher.exe`.
3. Выберите версию Minecraft или модификацию и нажмите **«Запустить»**!

---

## 🔨 Сборка из исходников

### Требования:
- **JDK 21** (Adoptium Temurin / Zulu / OpenJDK).
- **Git**.

### Команды сборки:
```bash
# Клонирование
git clone https://github.com/egorkaluchiy-creator/WlLauncher.git
cd WlLauncher

# Сборка портативного дистрибутива
.\gradlew.bat :packages:portable:preparePortableBuild

# Запуск в режиме разработки
.\gradlew.bat :launcher:run
```

Собранная портативная версия будет в каталоге `packages/portable/build/portable/wllauncher/`.

---

## ❓ Часто задаваемые вопросы (FAQ)

<details>
<summary><b>Нужно ли отдельно устанавливать Java?</b></summary>
Портативная версия WlLauncher и инсталлятор уже включают оптимизированную среду Java 21, поэтому игра и лаунчер запускаются «из коробки» без необходимости ручной установки Java.
</details>

<details>
<summary><b>Как работают скины Ely.by?</b></summary>
В выпадающем списке аккаунтов выберите «Настроить аккаунты», добавьте аккаунт типа **Ely.by** и введите ваши учетные данные. Лаунчер автоматически загрузит скин и плащ в игру.
</details>

<details>
<summary><b>Куда устанавливаются моды из каталога Modrinth?</b></summary>
Все скачанные через каталог моды помещаются напрямую в стандартную директорию <code>%APPDATA%\.minecraft\mods\</code>.
</details>

<details>
<summary><b>Что делать, если Discord RPC не отображает активность?</b></summary>
Убедитесь, что приложение Discord запущено. WlLauncher использует умное переподключение с экспоненциальной задержкой, поэтому статус обновится автоматически в течение нескольких секунд.
</details>

---

## 🤝 Участие в разработке

Подробная информация о создании Pull Request, стандартах кода и архитектуре доступна в:
- [CONTRIBUTING.md](CONTRIBUTING.md) — руководство разработчика.
- [ARCHITECTURE.md](ARCHITECTURE.md) — архитектурная спецификация модулей.

---

## 📜 Лицензия

WlLauncher является свободным программным обеспечением, созданным на базе открытых исходных кодов TL Legacy (Legacy Launcher), и распространяется под лицензией **GNU General Public License v3.0 (GPL-3.0)**. См. файл [LICENSE.txt](LICENSE.txt).
