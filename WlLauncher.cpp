#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <shlwapi.h>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>
#include <sstream>
#include <algorithm>

namespace fs = std::filesystem;

enum PROCESS_DPI_AWARENESS {
    PROCESS_DPI_UNAWARE = 0,
    PROCESS_SYSTEM_DPI_AWARE = 1,
    PROCESS_PER_MONITOR_DPI_AWARE = 2
};

static void enable_dpi_awareness() {
    if (HMODULE u32 = GetModuleHandleW(L"user32.dll")) {
        using Fn = BOOL(WINAPI*)(HANDLE);
        if (auto p = reinterpret_cast<Fn>(GetProcAddress(u32, "SetProcessDpiAwarenessContext"))) {
            const HANDLE PER_MONITOR_V2 = reinterpret_cast<HANDLE>(-4);
            if (p(PER_MONITOR_V2)) return;
        }
    }
    if (HMODULE shc = LoadLibraryW(L"shcore.dll")) {
        using Fn = HRESULT(WINAPI*)(PROCESS_DPI_AWARENESS);
        if (auto p = reinterpret_cast<Fn>(GetProcAddress(shc, "SetProcessDpiAwareness"))) {
            p(PROCESS_PER_MONITOR_DPI_AWARE);
        }
    }
    SetProcessDPIAware();
}

static fs::path get_exe_dir() {
    wchar_t buf[MAX_PATH];
    DWORD n = GetModuleFileNameW(nullptr, buf, MAX_PATH);
    return fs::path(buf, buf + n).remove_filename();
}

static std::wstring win_quote(const std::wstring& in) {
    if (in.find_first_of(L" \t\"") == std::wstring::npos && !in.empty())
        return in;
    std::wstring out;
    out.reserve(in.size() + 2);
    out.push_back(L'"');
    unsigned bs = 0;
    for (wchar_t ch : in) {
        if (ch == L'\\') {
            ++bs;
        } else if (ch == L'"') {
            out.append(bs * 2 + 1, L'\\');
            out.push_back(ch);
            bs = 0;
        } else {
            out.append(bs, L'\\');
            bs = 0;
            out.push_back(ch);
        }
    }
    out.append(bs, L'\\');
    out.push_back(L'"');
    return out;
}

static bool check_java(const fs::path& p, fs::path& found) {
    std::error_code ec;
    if (!p.empty() && fs::exists(p, ec) && !fs::is_directory(p, ec)) {
        found = p;
        return true;
    }
    return false;
}

static bool check_java_home(const fs::path& home, fs::path& found) {
    if (home.empty()) return false;
    if (check_java(home / L"bin" / L"javaw.exe", found)) return true;
    if (check_java(home / L"bin" / L"java.exe", found)) return true;
    return false;
}

static void search_registry_keys(HKEY root, const wchar_t* subkey, REGSAM access, std::vector<fs::path>& candidates) {
    HKEY hKey;
    if (RegOpenKeyExW(root, subkey, 0, KEY_READ | access, &hKey) == ERROR_SUCCESS) {
        wchar_t name[256];
        DWORD nameLen = 256;
        DWORD index = 0;
        while (RegEnumKeyExW(hKey, index++, name, &nameLen, nullptr, nullptr, nullptr, nullptr) == ERROR_SUCCESS) {
            nameLen = 256;
            HKEY hSub;
            std::wstring fullSub = std::wstring(subkey) + L"\\" + name;
            if (RegOpenKeyExW(root, fullSub.c_str(), 0, KEY_READ | access, &hSub) == ERROR_SUCCESS) {
                wchar_t val[MAX_PATH] = {0};
                DWORD valLen = sizeof(val);
                if (RegQueryValueExW(hSub, L"JavaHome", nullptr, nullptr, (LPBYTE)val, &valLen) == ERROR_SUCCESS) {
                    candidates.push_back(fs::path(val));
                } else if (RegQueryValueExW(hSub, L"Path", nullptr, nullptr, (LPBYTE)val, &valLen) == ERROR_SUCCESS) {
                    candidates.push_back(fs::path(val));
                }
                
                // Also check subkeys like "MSI" or "hotspot"
                wchar_t child[256];
                DWORD childLen = 256;
                DWORD childIdx = 0;
                while (RegEnumKeyExW(hSub, childIdx++, child, &childLen, nullptr, nullptr, nullptr, nullptr) == ERROR_SUCCESS) {
                    childLen = 256;
                    HKEY hChild;
                    std::wstring childFull = fullSub + L"\\" + child;
                    if (RegOpenKeyExW(root, childFull.c_str(), 0, KEY_READ | access, &hChild) == ERROR_SUCCESS) {
                        wchar_t childVal[MAX_PATH] = {0};
                        DWORD cvalLen = sizeof(childVal);
                        if (RegQueryValueExW(hChild, L"JavaHome", nullptr, nullptr, (LPBYTE)childVal, &cvalLen) == ERROR_SUCCESS) {
                            candidates.push_back(fs::path(childVal));
                        } else if (RegQueryValueExW(hChild, L"Path", nullptr, nullptr, (LPBYTE)childVal, &cvalLen) == ERROR_SUCCESS) {
                            candidates.push_back(fs::path(childVal));
                        }
                        RegCloseKey(hChild);
                    }
                }
                RegCloseKey(hSub);
            }
        }
        RegCloseKey(hKey);
    }
}

static fs::path find_java(const fs::path& baseDir) {
    fs::path found;

    // 1. Local bundled JRE
    if (check_java_home(baseDir / L"jre", found)) return found;
    if (check_java_home(baseDir / L"jre" / L"x64", found)) return found;
    if (check_java_home(baseDir / L"jre" / L"arm64", found)) return found;
    if (check_java_home(baseDir / L"launcher" / L"jre", found)) return found;
    if (check_java_home(baseDir / L"launcher" / L"jre" / L"x64", found)) return found;
    if (check_java_home(baseDir / L"launcher" / L"jre" / L"arm64", found)) return found;
    if (check_java_home(baseDir / L"runtime", found)) return found;

    // 2. JAVA_HOME / JDK_HOME
    wchar_t envBuf[MAX_PATH];
    if (GetEnvironmentVariableW(L"JAVA_HOME", envBuf, MAX_PATH) > 0) {
        if (check_java_home(fs::path(envBuf), found)) return found;
    }
    if (GetEnvironmentVariableW(L"JDK_HOME", envBuf, MAX_PATH) > 0) {
        if (check_java_home(fs::path(envBuf), found)) return found;
    }

    // 3. Search Registry
    std::vector<fs::path> regHomes;
    const wchar_t* regPaths[] = {
        L"SOFTWARE\\Eclipse Adoptium\\JDK",
        L"SOFTWARE\\Eclipse Adoptium\\JRE",
        L"SOFTWARE\\JavaSoft\\JDK",
        L"SOFTWARE\\JavaSoft\\Java Development Kit",
        L"SOFTWARE\\JavaSoft\\Java Runtime Environment",
        L"SOFTWARE\\Microsoft\\JDK",
        L"SOFTWARE\\BellSoft\\Liberica JDK"
    };

    for (const wchar_t* rp : regPaths) {
        search_registry_keys(HKEY_LOCAL_MACHINE, rp, KEY_WOW64_64KEY, regHomes);
        search_registry_keys(HKEY_LOCAL_MACHINE, rp, KEY_WOW64_32KEY, regHomes);
        search_registry_keys(HKEY_CURRENT_USER, rp, 0, regHomes);
    }

    for (const auto& h : regHomes) {
        if (check_java_home(h, found)) return found;
    }

    // 4. Common install directories
    std::vector<fs::path> searchDirs;
    
    wchar_t progFiles[MAX_PATH];
    if (GetEnvironmentVariableW(L"ProgramFiles", progFiles, MAX_PATH) > 0) {
        searchDirs.push_back(fs::path(progFiles) / L"Eclipse Adoptium");
        searchDirs.push_back(fs::path(progFiles) / L"Java");
        searchDirs.push_back(fs::path(progFiles) / L"Microsoft");
        searchDirs.push_back(fs::path(progFiles) / L"BellSoft");
        searchDirs.push_back(fs::path(progFiles) / L"Amazon Corretto");
        searchDirs.push_back(fs::path(progFiles) / L"Zulu");
    }
    
    wchar_t progFiles86[MAX_PATH];
    if (GetEnvironmentVariableW(L"ProgramFiles(x86)", progFiles86, MAX_PATH) > 0) {
        searchDirs.push_back(fs::path(progFiles86) / L"Java");
        searchDirs.push_back(fs::path(progFiles86) / L"Eclipse Adoptium");
    }

    wchar_t appData[MAX_PATH];
    if (GetEnvironmentVariableW(L"APPDATA", appData, MAX_PATH) > 0) {
        searchDirs.push_back(fs::path(appData) / L".minecraft" / L"runtime");
    }

    wchar_t localAppData[MAX_PATH];
    if (GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, MAX_PATH) > 0) {
        searchDirs.push_back(fs::path(localAppData) / L"Programs" / L"Eclipse Adoptium");
    }

    for (const auto& parentDir : searchDirs) {
        std::error_code ec;
        if (fs::exists(parentDir, ec) && fs::is_directory(parentDir, ec)) {
            for (const auto& entry : fs::directory_iterator(parentDir, ec)) {
                if (entry.is_directory(ec)) {
                    if (check_java_home(entry.path(), found)) return found;
                    // Check sub-folders like jdk-17/hotspot
                    for (const auto& subEntry : fs::directory_iterator(entry.path(), ec)) {
                        if (subEntry.is_directory(ec)) {
                            if (check_java_home(subEntry.path(), found)) return found;
                        }
                    }
                }
            }
        }
    }

    // 5. Check PATH using SearchPathW
    wchar_t pathBuf[MAX_PATH];
    if (SearchPathW(nullptr, L"javaw.exe", nullptr, MAX_PATH, pathBuf, nullptr) > 0) {
        found = fs::path(pathBuf);
        return found;
    }
    if (SearchPathW(nullptr, L"java.exe", nullptr, MAX_PATH, pathBuf, nullptr) > 0) {
        found = fs::path(pathBuf);
        return found;
    }

    return fs::path();
}

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    enable_dpi_awareness();

    fs::path base = get_exe_dir();
    fs::path java_exe = find_java(base);

    if (java_exe.empty()) {
        MessageBoxW(nullptr,
            L"Java не найдена на вашем компьютере!\n\n"
            L"Для запуска WlLauncher необходима установленная Java (рекомендуется Java 17 или 21).\n"
            L"Пожалуйста, установите Java с официального сайта: https://adoptium.net/",
            L"WlLauncher — Требуется Java",
            MB_ICONERROR | MB_OK);
        return 1;
    }

    // Determine launcher and bootstrap paths
    fs::path bootstrap_jar;
    fs::path launcher_jar;
    fs::path libraries_dir;
    fs::path working_dir = base;

    if (fs::exists(base / L"launcher" / L"launcher" / L"bootstrap.jar")) {
        working_dir = base / L"launcher";
        bootstrap_jar = base / L"launcher" / L"launcher" / L"bootstrap.jar";
        launcher_jar = base / L"launcher" / L"launcher" / L"launcher.jar";
        libraries_dir = base / L"launcher" / L"launcher" / L"libraries";
    } else if (fs::exists(base / L"launcher" / L"bootstrap.jar")) {
        working_dir = base / L"launcher";
        bootstrap_jar = base / L"launcher" / L"bootstrap.jar";
        launcher_jar = base / L"launcher" / L"launcher.jar";
        libraries_dir = base / L"launcher" / L"libraries";
    } else if (fs::exists(base / L"bootstrap.jar")) {
        working_dir = base;
        bootstrap_jar = base / L"bootstrap.jar";
        launcher_jar = base / L"launcher.jar";
        libraries_dir = base / L"libraries";
    } else if (fs::exists(base / L"packages" / L"portable" / L"build" / L"portableBase" / L"wllauncher" / L"launcher" / L"bootstrap.jar")) {
        fs::path pBase = base / L"packages" / L"portable" / L"build" / L"portableBase" / L"wllauncher" / L"launcher";
        working_dir = pBase.parent_path();
        bootstrap_jar = pBase / L"bootstrap.jar";
        launcher_jar = pBase / L"launcher.jar";
        libraries_dir = pBase / L"libraries";
    } else {
        MessageBoxW(nullptr,
            L"Файлы WlLauncher не найдены!\n\nУбедитесь, что launcher/bootstrap.jar находится рядом с WlLauncher.exe.",
            L"WlLauncher — Ошибка",
            MB_ICONERROR | MB_OK);
        return 2;
    }

    std::vector<std::wstring> jvmArgs;

    // Memory and GC optimization
    jvmArgs.push_back(L"-Xms16M");
    jvmArgs.push_back(L"-Xmx128M");
    jvmArgs.push_back(L"-XX:+UseSerialGC");
    jvmArgs.push_back(L"-XX:TieredStopAtLevel=1");
    jvmArgs.push_back(L"-XX:CICompilerCount=2");
    jvmArgs.push_back(L"-Xss256k");
    jvmArgs.push_back(L"-XX:MinHeapFreeRatio=10");
    jvmArgs.push_back(L"-XX:MaxHeapFreeRatio=20");
    jvmArgs.push_back(L"-XX:MaxMetaspaceSize=64M");
    jvmArgs.push_back(L"-XX:ReservedCodeCacheSize=32M");
    jvmArgs.push_back(L"-XX:CompressedClassSpaceSize=16M");
    jvmArgs.push_back(L"-Dfile.encoding=UTF-8");
    jvmArgs.push_back(L"-Dsun.stdout.encoding=UTF-8");
    jvmArgs.push_back(L"-Dsun.stderr.encoding=UTF-8");
    jvmArgs.push_back(L"-Djava.net.useSystemProxies=true");
    jvmArgs.push_back(L"-Dsun.java2d.d3d=true");
    jvmArgs.push_back(L"-Dsun.java2d.noddraw=true");
    jvmArgs.push_back(L"-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT");
    jvmArgs.push_back(L"-Djna.tmpdir=natives/jna");

    // Java 9+ module exports for FlatLaf / JavaFX
    jvmArgs.push_back(L"--add-exports");
    jvmArgs.push_back(L"java.desktop/sun.awt=javafx.swing");
    jvmArgs.push_back(L"--add-exports");
    jvmArgs.push_back(L"javafx.graphics/com.sun.javafx.application=ALL-UNNAMED");

    fs::path abs_bootstrap = fs::absolute(bootstrap_jar);
    fs::path abs_launcher = fs::absolute(launcher_jar);
    fs::path abs_libraries = fs::absolute(libraries_dir);

    jvmArgs.push_back(L"-Dtlauncher.bootstrap.restartExec=WlLauncher.exe");
    jvmArgs.push_back(L"-Dtlauncher.bootstrap.ignoreUpdate=true");
    jvmArgs.push_back(L"-Dtlauncher.bootstrap.ignoreSelfUpdate=true");
    jvmArgs.push_back(L"-Dtlauncher.bootstrap.targetJar=" + abs_launcher.wstring());
    jvmArgs.push_back(L"-Dtlauncher.bootstrap.targetLibFolder=" + abs_libraries.wstring());
    jvmArgs.push_back(L"-Dloader.main=net.legacylauncher.bootstrap.Bootstrap");

    jvmArgs.push_back(L"-classpath");
    jvmArgs.push_back(abs_bootstrap.wstring());
    jvmArgs.push_back(L"org.springframework.boot.loader.PropertiesLauncher");

    jvmArgs.push_back(L"--packageMode");
    jvmArgs.push_back(L"portable");
    jvmArgs.push_back(L"--ignoreUpdate");
    jvmArgs.push_back(L"--ignoreSelfUpdate");
    jvmArgs.push_back(L"--targetJar");
    jvmArgs.push_back(abs_launcher.wstring());
    jvmArgs.push_back(L"--targetLibFolder");
    jvmArgs.push_back(abs_libraries.wstring());
    jvmArgs.push_back(L"--");

    fs::path settingsPath = working_dir / L"wl.properties";
    if (!fs::exists(settingsPath)) {
        settingsPath = base / L"wl.properties";
    }
    if (fs::exists(settingsPath)) {
        jvmArgs.push_back(L"--settings");
        jvmArgs.push_back(settingsPath.wstring());
    } else {
        jvmArgs.push_back(L"--settings");
        jvmArgs.push_back(L"wl.properties");
    }

    // Forward CLI args if any
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv) {
        for (int i = 1; i < argc; ++i) {
            jvmArgs.push_back(argv[i]);
        }
        LocalFree(argv);
    }

    std::wstring cmd = win_quote(java_exe.wstring());
    for (const auto& a : jvmArgs) {
        cmd.push_back(L' ');
        cmd += win_quote(a);
    }

    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi = {};

    BOOL success = CreateProcessW(
        nullptr,
        &cmd[0],
        nullptr,
        nullptr,
        FALSE,
        0,
        nullptr,
        working_dir.c_str(),
        &si,
        &pi
    );

    if (!success) {
        std::wstring errMsg = L"Не удалось запустить Java: " + java_exe.wstring() + L"\nКод ошибки: " + std::to_wstring(GetLastError());
        MessageBoxW(nullptr, errMsg.c_str(), L"WlLauncher — Ошибка запуска", MB_ICONERROR | MB_OK);
        return (int)GetLastError();
    }

    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
