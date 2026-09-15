# Руководство по участию в разработке (CONTRIBUTING)

Благодарим за интерес к развитию **WlLauncher**! Это руководство поможет настроить среду разработки, понять структуру проекта и правила внесения изменений.

---

## 🛠️ Среда разработки и требования

Для сборки и работы над проектом вам понадобятся:
- **Java Development Kit (JDK)**: **JDK 21** (рекомендуется Eclipse Temurin 21 или Azul Zulu 21).
- **Gradle Wrapper**: поставляется в репозитории (`gradlew.bat` / `./gradlew`), отдельная установка Gradle не требуется.
- **Git**: для управления версиями.
- **C++ Compiler (Опционально для Win32-бинарников)**: MinGW-w64 (GCC 11+) с поддержкой `windres` для сборки нативного `WlLauncher.exe`.
- **IDE**: IntelliJ IDEA (рекомендуется) или Eclipse / VS Code с плагином Java.

---

## 🚀 Быстрый старт

### 1. Клонирование репозитория:
```bash
git clone https://github.com/egorkaluchiy-creator/WlLauncher.git
cd WlLauncher
```

### 2. Сборка проекта:
```bash
# Windows
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

### 3. Запуск лаунчера в режиме разработки:
```bash
# Windows
.\gradlew.bat :launcher:run

# Linux / macOS
./gradlew :launcher:run
```

### 4. Сборка портативного пакета (включая JRE):
```bash
.\gradlew.bat :packages:portable:preparePortableBuild
```
Результат сборки будет помещен в `packages/portable/build/portable/wllauncher/`.

---

## 📐 Архитектура и модули

WlLauncher разделен на независимые Gradle-модули:

| Модуль | Назначение |
| :--- | :--- |
| `:bootstrap` | Легковесный загрузчик первого этапа, проверка целостности, сплэш-скрин, автообновление |
| `:launcher` | Основное приложение лаунчера (Swing UI, профили, версии, интеграция Modrinth, Discord RPC) |
| `:bridge` | Интерфейс межпроцессного взаимодействия и передачи параметров между bootstrap и launcher |
| `:common` | Общие доменные модели, сериализация конфигурации, протоколы авторизации |
| `:utils` | Низкоуровневые утилиты: работа с процессами, потоками ввода-вывода, сетью и асинхронностью |
| `:packages:portable` | Сборщик дистрибутива Windows с встроенной JRE 21 |
| `:packages:installer` | Скрипты генерации инсталлятора |

Подробное описание архитектуры доступно в [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 📝 Стандарты написания кода

### 1. Стиль Java
- Используйте стандартные конвенции именования Java (`camelCase` для методов и переменных, `PascalCase` для классов).
- Максимальная длина строки — 120 символов.
- Все текстовые ресурсы интерфейса должны выноситься в языковые файлы (`lang/ru_RU.properties`, `lang/en_US.properties` и др.) и форматироваться в **UTF-8**.
- Для логирования используйте **SLF4J**:
  ```java
  private static final Logger log = LoggerFactory.getLogger(MyClass.class);
  // или аннотацию Lombok:
  @Slf4j
  public class MyClass { ... }
  ```
- Избегайте прямого вывода через `System.out.println` в основном коде лаунчера.

### 2. Работа с исключениями
- Не подавляйте исключения пустыми блоками `catch`. Обязательно логируйте их: `log.warn("Failed to perform action", ex)`.
- Для фоновых задач используйте защищенные пулы потоков и `AsyncThread`.

### 3. Тестирование
- Новая функциональность должна сопровождаться unit-тестами на базе **JUnit 5** в директории `src/test/java/`.
- Запуск всех тестов:
  ```bash
  .\gradlew.bat test
  ```

---

## 🔀 Git и процесс создания Pull Request (PR)

1. **Создайте ветку для задачи**:
   ```bash
   git checkout -b feature/awesome-feature
   # или для исправления бага:
   git checkout -b fix/issue-name
   ```
2. **Формат сообщений коммитов** (Conventional Commits):
   - `feat(catalog): add category filter to Modrinth catalog`
   - `fix(auth): handle expired Ely.by token refresh`
   - `docs(readme): update build requirements`
   - `refactor(rpc): optimize discord pipe reconnect with backoff`
   - `test(config): add unit tests for memory slider validation`
3. **Проверьте сборку и тесты перед отправкой**:
   ```bash
   .\gradlew.bat check
   ```
4. **Отправьте ветку и создайте Pull Request** в ветку `main` основного репозитория.

---

## 💬 Сообщение об ошибках (Issues)

Если вы нашли баг или хотите предложить улучшение:
1. Проверьте существующие [Issues](https://github.com/egorkaluchiy-creator/WlLauncher/issues), чтобы избежать дубликатов.
2. Подробно опишите шаги для воспроизведения, приложите версию Java, ОС и лог-файл из консоли лаунчера.
