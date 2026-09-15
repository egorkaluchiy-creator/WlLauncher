package net.legacylauncher.bootstrap.util;

import java.util.Arrays;

public class SplitArgs {
    private final String[] bootstrap;
    private final String[] launcher;

    public SplitArgs(String[] bootstrap, String[] launcher) {
        this.bootstrap = emptyIfNull(bootstrap);
        this.launcher = emptyIfNull(launcher);
    }

    public String[] getBootstrap() {
        return bootstrap.clone();
    }

    public String[] getLauncher() {
        return launcher.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SplitArgs splitArgs = (SplitArgs) o;
        return Arrays.equals(bootstrap, splitArgs.bootstrap) && Arrays.equals(launcher, splitArgs.launcher);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(bootstrap);
        result = 31 * result + Arrays.hashCode(launcher);
        return result;
    }

    public static SplitArgs splitArgs(String[] args) {
        String[] bootstrap = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--")) {
                bootstrap = new String[i];
                break;
            }
        }
        String[] launcher;
        if (bootstrap != null) {
            System.arraycopy(args, 0, bootstrap, 0, bootstrap.length);
            launcher = new String[args.length - bootstrap.length - 1];
            if (launcher.length > 0) {
                System.arraycopy(args, bootstrap.length + 1, launcher, 0, launcher.length);
            }
        } else {
            java.util.List<String> bList = new java.util.ArrayList<>();
            java.util.List<String> lList = new java.util.ArrayList<>();
            java.util.Set<String> bUnary = new java.util.HashSet<>(Arrays.asList(
                    "--ignoreUpdate", "--ignoreSelfUpdate", "--headlessMode", "--fork", "--requireMinecraftAccount"
            ));
            java.util.Set<String> bBinary = new java.util.HashSet<>(Arrays.asList(
                    "--targetJar", "--targetLibFolder", "--brand", "--packageMode", "--updateMetaFile", "--restartExec"
            ));
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (bUnary.contains(a)) {
                    bList.add(a);
                } else if (bBinary.contains(a)) {
                    bList.add(a);
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        bList.add(args[++i]);
                    }
                } else {
                    lList.add(a);
                }
            }
            bootstrap = bList.toArray(new String[0]);
            launcher = lList.toArray(new String[0]);
        }
        return new SplitArgs(bootstrap, launcher);
    }

    @Override
    public String toString() {
        return "SplitArgs{" +
                "bootstrap=" + Arrays.toString(bootstrap) +
                ", launcher=" + Arrays.toString(launcher) +
                '}';
    }

    private static String[] emptyIfNull(String[] s) {
        return s == null ? new String[0] : s;
    }
}
