#ifndef ZYX_QEMU_API_H
#define ZYX_QEMU_API_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ZYX_QEMU_API_ABI 1u

typedef struct ZyxQemuApi {
    uint32_t abi_version;
    int (*run)(int argc, const char *const *argv);
    int (*request_shutdown)(void);
    int (*request_reset)(void);
    const char *(*version)(void);
} ZyxQemuApi;

/* Implemented by the ZyX adapter compiled into the QEMU system library. */
const ZyxQemuApi *zyx_qemu_get_api(uint32_t requested_abi);

#ifdef __cplusplus
}
#endif
#endif
