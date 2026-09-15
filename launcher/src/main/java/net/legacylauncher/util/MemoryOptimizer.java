package net.legacylauncher.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.async.AsyncThread;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class MemoryOptimizer {
    private interface WinKernel32 extends Library {
        WinKernel32 INSTANCE = Native.load("kernel32", WinKernel32.class);
        boolean SetProcessWorkingSetSize(Pointer hProcess, long dwMinimumWorkingSetSize, long dwMaximumWorkingSetSize);
        Pointer GetCurrentProcess();
    }

    private MemoryOptimizer() {
    }

    public static void trimMemory() {
        try {
            System.gc();
            if (OS.WINDOWS.isCurrent()) {
                WinKernel32.INSTANCE.SetProcessWorkingSetSize(WinKernel32.INSTANCE.GetCurrentProcess(), -1L, -1L);
            }
        } catch (Throwable t) {
            log.trace("Memory trim failed: {}", t.getMessage());
        }
    }

    public static void startTrimmer() {
        AsyncThread.DELAYER.schedule(MemoryOptimizer::trimMemory, 3, TimeUnit.SECONDS);
        AsyncThread.DELAYER.scheduleWithFixedDelay(MemoryOptimizer::trimMemory, 20, 20, TimeUnit.SECONDS);
    }
}
