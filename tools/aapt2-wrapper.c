#define _GNU_SOURCE
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const char *env_or(const char *name, const char *fallback) {
    const char *value = getenv(name);
    return value && *value ? value : fallback;
}

static void die(const char *message, const char *value) {
    fprintf(stderr, "ZyX Virtual AAPT2 wrapper: %s%s%s\n", message,
            value ? ": " : "", value ? value : "");
    exit(127);
}

int main(int argc, char **argv) {
    const char *qemu = env_or("ZYX_QEMU_X86_64", "/usr/bin/qemu-x86_64-static");
    const char *sysroot = getenv("ZYX_X86_64_SYSROOT");
    const char *android_home = env_or("ANDROID_HOME", getenv("ANDROID_SDK_ROOT"));
    const char *aapt2 = getenv("ZYX_AAPT2_BIN");
    char sysroot_buf[4096];
    char aapt2_buf[4096];

    if (!sysroot || !*sysroot) {
        const char *home = env_or("HOME", "/home/ubuntu");
        if (snprintf(sysroot_buf, sizeof(sysroot_buf), "%s/x86_64-sysroot", home) >= (int)sizeof(sysroot_buf))
            die("sysroot path is too long", NULL);
        sysroot = sysroot_buf;
    }
    if (!aapt2 || !*aapt2) {
        if (!android_home || !*android_home)
            die("ANDROID_HOME or ANDROID_SDK_ROOT is not set", NULL);
        if (snprintf(aapt2_buf, sizeof(aapt2_buf), "%s/build-tools/35.0.0/aapt2", android_home) >= (int)sizeof(aapt2_buf))
            die("AAPT2 path is too long", NULL);
        aapt2 = aapt2_buf;
    }

    char **child = calloc((size_t)argc + 4, sizeof(char *));
    if (!child) die("cannot allocate argv", NULL);
    child[0] = (char *)qemu;
    child[1] = (char *)"-L";
    child[2] = (char *)sysroot;
    child[3] = (char *)aapt2;
    for (int i = 1; i < argc; ++i) child[i + 3] = argv[i];
    child[argc + 3] = NULL;

    execv(qemu, child);
    fprintf(stderr, "ZyX Virtual AAPT2 wrapper: exec %s failed: %s\n", qemu, strerror(errno));
    return 127;
}
