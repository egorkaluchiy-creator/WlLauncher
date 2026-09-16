#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <shlobj.h>
#include <shlwapi.h>
#include <shellapi.h>
#include <string>
#include <vector>
#include <fstream>
#include <thread>
#include <filesystem>

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "uuid.lib")

namespace fs = std::filesystem;

#define ID_BTN_INSTALL  1001
#define ID_BTN_BROWSE   1002
#define ID_BTN_CANCEL   1003
#define ID_CHK_DESKTOP  1004
#define ID_CHK_START    1005
#define ID_CHK_LAUNCH   1006
#define ID_EDIT_PATH    1007
#define ID_PROGRESS     1008
#define ID_STATUS_LBL   1009

HWND g_hWnd = nullptr;
HWND g_hEditPath = nullptr;
HWND g_hBtnBrowse = nullptr;
HWND g_hBtnInstall = nullptr;
HWND g_hBtnCancel = nullptr;
HWND g_hChkDesktop = nullptr;
HWND g_hChkStart = nullptr;
HWND g_hChkLaunch = nullptr;
HWND g_hProgress = nullptr;
HWND g_hStatus = nullptr;
HFONT g_hFontTitle = nullptr;
HFONT g_hFontNormal = nullptr;
HFONT g_hFontSemibold = nullptr;
HFONT g_hFontBold = nullptr;
HICON g_hIcon = nullptr;

HBRUSH g_hBrushBody = nullptr;
HBRUSH g_hBrushBottom = nullptr;

bool g_isInstalling = false;
bool g_isFinished = false;

static std::wstring GetDefaultInstallDir() {
    wchar_t appData[MAX_PATH];
    if (SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, appData) == S_OK) {
        return std::wstring(appData) + L"\\.minecraft";
    }
    return L"C:\\.minecraft";
}

static bool CreateShortcut(const std::wstring& targetExe, const std::wstring& shortcutPath, const std::wstring& desc, const std::wstring& iconPath) {
    HRESULT hr = CoInitialize(nullptr);
    IShellLinkW* psl = nullptr;
    hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_IShellLinkW, (void**)&psl);
    if (SUCCEEDED(hr)) {
        psl->SetPath(targetExe.c_str());
        psl->SetDescription(desc.c_str());
        fs::path p(targetExe);
        psl->SetWorkingDirectory(p.parent_path().c_str());
        if (!iconPath.empty()) {
            psl->SetIconLocation(iconPath.c_str(), 0);
        }
        IPersistFile* ppf = nullptr;
        hr = psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
        if (SUCCEEDED(hr)) {
            hr = ppf->Save(shortcutPath.c_str(), TRUE);
            ppf->Release();
        }
        psl->Release();
    }
    return SUCCEEDED(hr);
}

static void RegisterUninstall(const std::wstring& installDir, const std::wstring& exePath) {
    HKEY hKey;
    const wchar_t* subKey = L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\WlLauncher";
    if (RegCreateKeyExW(HKEY_CURRENT_USER, subKey, 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_ALL_ACCESS, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        const wchar_t* name = L"WlLauncher";
        const wchar_t* pub = L"WlLauncher Team";
        const wchar_t* ver = L"2.0.0";
        DWORD d1 = 1;

        RegSetValueExW(hKey, L"DisplayName", 0, REG_SZ, (const BYTE*)name, (DWORD)((wcslen(name) + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"Publisher", 0, REG_SZ, (const BYTE*)pub, (DWORD)((wcslen(pub) + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"DisplayVersion", 0, REG_SZ, (const BYTE*)ver, (DWORD)((wcslen(ver) + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"DisplayIcon", 0, REG_SZ, (const BYTE*)exePath.c_str(), (DWORD)((exePath.size() + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"InstallLocation", 0, REG_SZ, (const BYTE*)installDir.c_str(), (DWORD)((installDir.size() + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"NoModify", 0, REG_DWORD, (const BYTE*)&d1, sizeof(DWORD));
        RegSetValueExW(hKey, L"NoRepair", 0, REG_DWORD, (const BYTE*)&d1, sizeof(DWORD));

        std::wstring uninstCmd = L"cmd.exe /c \"del \\\"" + exePath + L"\\\" & reg delete \\\"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\WlLauncher\\\" /f\"";
        RegSetValueExW(hKey, L"UninstallString", 0, REG_SZ, (const BYTE*)uninstCmd.c_str(), (DWORD)((uninstCmd.size() + 1) * sizeof(wchar_t)));

        RegCloseKey(hKey);
    }
}

static void RunInstallation(std::wstring targetDir, bool createDesktop, bool createStart, bool launchAfter) {
    HRSRC hRes = FindResourceW(nullptr, MAKEINTRESOURCEW(102), MAKEINTRESOURCEW(10));
    if (!hRes) {
        MessageBoxW(g_hWnd, L"Ресурс данных установщика не найден!", L"Ошибка", MB_ICONERROR | MB_OK);
        EnableWindow(g_hBtnInstall, TRUE);
        EnableWindow(g_hBtnCancel, TRUE);
        g_isInstalling = false;
        return;
    }

    HGLOBAL hData = LoadResource(nullptr, hRes);
    DWORD resSize = SizeofResource(nullptr, hRes);
    void* pData = LockResource(hData);

    SendMessageW(g_hProgress, PBM_SETPOS, 15, 0);
    SetWindowTextW(g_hStatus, L"Подготовка файлов установки...");

    wchar_t tempPath[MAX_PATH];
    GetTempPathW(MAX_PATH, tempPath);
    std::wstring tempZip = std::wstring(tempPath) + L"wllauncher_payload.zip";

    std::ofstream out(fs::path(tempZip), std::ios::binary);
    out.write((const char*)pData, resSize);
    out.close();

    SendMessageW(g_hProgress, PBM_SETPOS, 35, 0);
    SetWindowTextW(g_hStatus, L"Создание целевых каталогов...");

    std::error_code ec;
    fs::create_directories(targetDir, ec);

    SendMessageW(g_hProgress, PBM_SETPOS, 55, 0);
    SetWindowTextW(g_hStatus, L"Распаковка компонентов WlLauncher...");

    std::wstring cmd = L"tar.exe -xf \"" + tempZip + L"\" -C \"" + targetDir + L"\"";
    
    STARTUPINFOW si = { sizeof(si) };
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = {};

    std::vector<wchar_t> cmdBuf(cmd.begin(), cmd.end());
    cmdBuf.push_back(0);

    if (CreateProcessW(nullptr, cmdBuf.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        WaitForSingleObject(pi.hProcess, INFINITE);
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
    } else {
        std::wstring psCmd = L"powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \"Expand-Archive -Path '" + tempZip + L"' -DestinationPath '" + targetDir + L"' -Force\"";
        std::vector<wchar_t> psBuf(psCmd.begin(), psCmd.end());
        psBuf.push_back(0);
        if (CreateProcessW(nullptr, psBuf.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
            WaitForSingleObject(pi.hProcess, INFINITE);
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
        }
    }

    _wremove(tempZip.c_str());

    SendMessageW(g_hProgress, PBM_SETPOS, 80, 0);
    SetWindowTextW(g_hStatus, L"Создание ярлыков...");

    std::wstring exePath = targetDir + L"\\WlLauncher.exe";
    std::wstring icoPath = targetDir + L"\\WlLauncher.ico";
    if (!fs::exists(icoPath)) icoPath = exePath;

    if (createDesktop) {
        wchar_t desktop[MAX_PATH];
        if (SHGetFolderPathW(nullptr, CSIDL_DESKTOPDIRECTORY, nullptr, 0, desktop) == S_OK) {
            std::wstring lnk = std::wstring(desktop) + L"\\WlLauncher.lnk";
            CreateShortcut(exePath, lnk, L"WlLauncher - Быстрый оптимизированный лаунчер Minecraft", icoPath);
        }
    }

    if (createStart) {
        wchar_t startMenu[MAX_PATH];
        if (SHGetFolderPathW(nullptr, CSIDL_PROGRAMS, nullptr, 0, startMenu) == S_OK) {
            std::wstring lnk = std::wstring(startMenu) + L"\\WlLauncher.lnk";
            CreateShortcut(exePath, lnk, L"WlLauncher - Быстрый оптимизированный лаунчер Minecraft", icoPath);
        }
    }

    RegisterUninstall(targetDir, exePath);

    SendMessageW(g_hProgress, PBM_SETPOS, 100, 0);
    SetWindowTextW(g_hStatus, L"Установка успешно завершена!");

    g_isFinished = true;
    EnableWindow(g_hBtnInstall, TRUE);
    SetWindowTextW(g_hBtnInstall, L"Готово");
    EnableWindow(g_hBtnCancel, FALSE);

    if (launchAfter) {
        ShellExecuteW(nullptr, L"open", exePath.c_str(), nullptr, targetDir.c_str(), SW_SHOW);
    }

    MessageBoxW(g_hWnd, L"WlLauncher успешно установлен и готов к игре!", L"Установка завершена", MB_ICONINFORMATION | MB_OK);
    PostMessageW(g_hWnd, WM_CLOSE, 0, 0);
}

static LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_CREATE: {
            g_hFontTitle = CreateFontW(21, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
            g_hFontSemibold = CreateFontW(15, 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
            g_hFontNormal = CreateFontW(14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
            g_hFontBold = CreateFontW(14, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");

            g_hBrushBody = CreateSolidBrush(RGB(250, 250, 252));
            g_hBrushBottom = CreateSolidBrush(RGB(241, 245, 249));

            g_hIcon = (HICON)LoadImageW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(101), IMAGE_ICON, 48, 48, LR_DEFAULTCOLOR);

            CreateWindowW(L"STATIC", L"Папка для установки:", WS_CHILD | WS_VISIBLE, 32, 112, 300, 20, hWnd, nullptr, nullptr, nullptr);
            
            std::wstring defDir = GetDefaultInstallDir();
            g_hEditPath = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", defDir.c_str(), WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL, 32, 134, 385, 26, hWnd, (HMENU)ID_EDIT_PATH, nullptr, nullptr);
            g_hBtnBrowse = CreateWindowW(L"BUTTON", L"Обзор...", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 427, 134, 95, 26, hWnd, (HMENU)ID_BTN_BROWSE, nullptr, nullptr);

            g_hChkDesktop = CreateWindowW(L"BUTTON", L"Создать ярлык на Рабочем столе", WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 32, 175, 380, 22, hWnd, (HMENU)ID_CHK_DESKTOP, nullptr, nullptr);
            g_hChkStart = CreateWindowW(L"BUTTON", L"Создать ярлык в меню «Пуск»", WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 32, 200, 380, 22, hWnd, (HMENU)ID_CHK_START, nullptr, nullptr);
            g_hChkLaunch = CreateWindowW(L"BUTTON", L"Запустить WlLauncher после завершения", WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 32, 225, 380, 22, hWnd, (HMENU)ID_CHK_LAUNCH, nullptr, nullptr);

            SendMessageW(g_hChkDesktop, BM_SETCHECK, BST_CHECKED, 0);
            SendMessageW(g_hChkStart, BM_SETCHECK, BST_CHECKED, 0);
            SendMessageW(g_hChkLaunch, BM_SETCHECK, BST_CHECKED, 0);

            g_hProgress = CreateWindowExW(0, PROGRESS_CLASSW, nullptr, WS_CHILD | WS_VISIBLE | PBS_SMOOTH, 32, 262, 490, 16, hWnd, (HMENU)ID_PROGRESS, nullptr, nullptr);
            SendMessageW(g_hProgress, PBM_SETRANGE32, 0, 100);
            SendMessageW(g_hProgress, PBM_SETPOS, 0, 0);

            g_hStatus = CreateWindowW(L"STATIC", L"Готов к установке", WS_CHILD | WS_VISIBLE, 32, 285, 490, 20, hWnd, (HMENU)ID_STATUS_LBL, nullptr, nullptr);

            g_hBtnInstall = CreateWindowW(L"BUTTON", L"Установить", WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON, 310, 345, 110, 32, hWnd, (HMENU)ID_BTN_INSTALL, nullptr, nullptr);
            g_hBtnCancel = CreateWindowW(L"BUTTON", L"Отмена", WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON, 430, 345, 92, 32, hWnd, (HMENU)ID_BTN_CANCEL, nullptr, nullptr);

            EnumChildWindows(hWnd, [](HWND hChild, LPARAM) -> BOOL {
                SendMessageW(hChild, WM_SETFONT, (WPARAM)g_hFontNormal, TRUE);
                return TRUE;
            }, 0);
            SendMessageW(g_hBtnInstall, WM_SETFONT, (WPARAM)g_hFontBold, TRUE);
            return 0;
        }

        case WM_CTLCOLORSTATIC: {
            HDC hdc = (HDC)wParam;
            HWND hStatic = (HWND)lParam;
            SetBkMode(hdc, TRANSPARENT);
            SetTextColor(hdc, RGB(40, 50, 65));
            return (LRESULT)g_hBrushBody;
        }

        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hWnd, &ps);

            RECT clientRect;
            GetClientRect(hWnd, &clientRect);
            int width = clientRect.right;
            int height = clientRect.bottom;

            // 1. Header background (Gradient from Dark Slate to Deep Blue-Grey)
            TRIVERTEX vertex[2];
            vertex[0].x = 0;
            vertex[0].y = 0;
            vertex[0].Red = (COLOR16)(18 << 8);
            vertex[0].Green = (COLOR16)(24 << 8);
            vertex[0].Blue = (COLOR16)(38 << 8);
            vertex[0].Alpha = 0x0000;

            vertex[1].x = width;
            vertex[1].y = 95;
            vertex[1].Red = (COLOR16)(30 << 8);
            vertex[1].Green = (COLOR16)(41 << 8);
            vertex[1].Blue = (COLOR16)(59 << 8);
            vertex[1].Alpha = 0x0000;

            GRADIENT_RECT gRect = { 0, 1 };
            GradientFill(hdc, vertex, 2, &gRect, 1, GRADIENT_FILL_RECT_V);

            // Top accent vibrant cyan/blue line
            RECT accentRect = { 0, 0, width, 3 };
            HBRUSH hAccentBrush = CreateSolidBrush(RGB(59, 130, 246));
            FillRect(hdc, &accentRect, hAccentBrush);
            DeleteObject(hAccentBrush);

            // Header bottom border
            HPEN hBorderPen = CreatePen(PS_SOLID, 1, RGB(45, 55, 75));
            HPEN hOldPen = (HPEN)SelectObject(hdc, hBorderPen);
            MoveToEx(hdc, 0, 95, nullptr);
            LineTo(hdc, width, 95);
            SelectObject(hdc, hOldPen);
            DeleteObject(hBorderPen);

            // App Icon
            if (g_hIcon) {
                DrawIconEx(hdc, 26, 22, g_hIcon, 48, 48, 0, nullptr, DI_NORMAL);
            }

            // Header Texts
            SetBkMode(hdc, TRANSPARENT);
            SelectObject(hdc, g_hFontTitle);
            SetTextColor(hdc, RGB(255, 255, 255));
            TextOutW(hdc, 90, 23, L"Установка WlLauncher", 20);

            SelectObject(hdc, g_hFontNormal);
            SetTextColor(hdc, RGB(148, 163, 184));
            TextOutW(hdc, 90, 52, L"Быстрый, легкий и оптимизированный лаунчер Minecraft", 52);

            // 2. Middle Body background
            RECT bodyRect = { 0, 96, width, 330 };
            FillRect(hdc, &bodyRect, g_hBrushBody);

            // 3. Bottom bar background
            RECT bottomRect = { 0, 330, width, height };
            FillRect(hdc, &bottomRect, g_hBrushBottom);

            // Bottom bar top border
            HPEN hBottomPen = CreatePen(PS_SOLID, 1, RGB(226, 232, 240));
            hOldPen = (HPEN)SelectObject(hdc, hBottomPen);
            MoveToEx(hdc, 0, 330, nullptr);
            LineTo(hdc, width, 330);
            SelectObject(hdc, hOldPen);
            DeleteObject(hBottomPen);

            EndPaint(hWnd, &ps);
            return 0;
        }

        case WM_COMMAND: {
            int wmId = LOWORD(wParam);
            if (wmId == ID_BTN_CANCEL) {
                if (g_isInstalling) {
                    if (MessageBoxW(hWnd, L"Прервать процесс установки?", L"Подтверждение", MB_YESNO | MB_ICONQUESTION) == IDYES) {
                        PostQuitMessage(0);
                    }
                } else {
                    PostQuitMessage(0);
                }
            } else if (wmId == ID_BTN_BROWSE && !g_isInstalling) {
                BROWSEINFOW bi = { 0 };
                bi.hwndOwner = hWnd;
                bi.lpszTitle = L"Выберите папку для установки WlLauncher:";
                bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
                LPITEMIDLIST pidl = SHBrowseForFolderW(&bi);
                if (pidl) {
                    wchar_t selected[MAX_PATH];
                    if (SHGetPathFromIDListW(pidl, selected)) {
                        SetWindowTextW(g_hEditPath, selected);
                    }
                    CoTaskMemFree(pidl);
                }
            } else if (wmId == ID_BTN_INSTALL) {
                if (g_isFinished) {
                    PostQuitMessage(0);
                    return 0;
                }
                if (g_isInstalling) return 0;

                wchar_t pathBuf[MAX_PATH] = { 0 };
                GetWindowTextW(g_hEditPath, pathBuf, MAX_PATH);
                std::wstring targetDir(pathBuf);
                if (targetDir.empty()) {
                    MessageBoxW(hWnd, L"Пожалуйста, укажите папку для установки.", L"Предупреждение", MB_ICONWARNING | MB_OK);
                    return 0;
                }

                g_isInstalling = true;
                EnableWindow(g_hEditPath, FALSE);
                EnableWindow(g_hBtnBrowse, FALSE);
                EnableWindow(g_hBtnInstall, FALSE);
                EnableWindow(g_hChkDesktop, FALSE);
                EnableWindow(g_hChkStart, FALSE);
                EnableWindow(g_hChkLaunch, FALSE);

                bool chkDesk = (SendMessageW(g_hChkDesktop, BM_GETCHECK, 0, 0) == BST_CHECKED);
                bool chkStart = (SendMessageW(g_hChkStart, BM_GETCHECK, 0, 0) == BST_CHECKED);
                bool chkLaunch = (SendMessageW(g_hChkLaunch, BM_GETCHECK, 0, 0) == BST_CHECKED);

                std::thread(RunInstallation, targetDir, chkDesk, chkStart, chkLaunch).detach();
            }
            return 0;
        }

        case WM_DESTROY: {
            if (g_hFontTitle) DeleteObject(g_hFontTitle);
            if (g_hFontSemibold) DeleteObject(g_hFontSemibold);
            if (g_hFontNormal) DeleteObject(g_hFontNormal);
            if (g_hFontBold) DeleteObject(g_hFontBold);
            if (g_hBrushBody) DeleteObject(g_hBrushBody);
            if (g_hBrushBottom) DeleteObject(g_hBrushBottom);
            if (g_hIcon) DestroyIcon(g_hIcon);
            PostQuitMessage(0);
            return 0;
        }
    }
    return DefWindowProcW(hWnd, msg, wParam, lParam);
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE, PWSTR, int) {
    INITCOMMONCONTROLSEX icex = { sizeof(icex), ICC_PROGRESS_CLASS | ICC_STANDARD_CLASSES };
    InitCommonControlsEx(&icex);

    WNDCLASSEXW wc = { sizeof(wc) };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"WlLauncherSetupWnd";
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.hIcon = (HICON)LoadImageW(hInstance, MAKEINTRESOURCEW(101), IMAGE_ICON, 32, 32, LR_DEFAULTCOLOR);

    RegisterClassExW(&wc);

    int w = 570, h = 435;
    int x = (GetSystemMetrics(SM_CXSCREEN) - w) / 2;
    int y = (GetSystemMetrics(SM_CYSCREEN) - h) / 2;

    g_hWnd = CreateWindowExW(
        WS_EX_APPWINDOW,
        wc.lpszClassName,
        L"Установка WlLauncher",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_VISIBLE,
        x, y, w, h,
        nullptr, nullptr, hInstance, nullptr
    );

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    return (int)msg.wParam;
}