// Runs package-manager commands as the shell user, inside Shizuku's process (docs/SHRIMPL_SPEC.md R6.1).
package com.valghora.shrimpl.install;

interface IShellService {
    /** Shizuku's reserved transaction for stopping a user service. */
    void destroy() = 16777114;

    /** `pm install -r -S size`, reading the APK from apk; returns "exit=N" and the command output. */
    String install(in ParcelFileDescriptor apk, long size) = 1;

    /** `pm uninstall packageName`; returns "exit=N" and the command output. */
    String uninstall(String packageName) = 2;
}
