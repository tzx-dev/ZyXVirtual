# ZyX OS product overlay — not a Samsung physical-device BoardConfig.
# This file is consumed only after a compatible AOSP/LineageOS tree is supplied.

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_VARIANT := generic

# Keep the 32-bit secondary ABI available for legacy compatibility apps.
TARGET_2ND_ARCH := arm
TARGET_2ND_ARCH_VARIANT := armv8-a
TARGET_2ND_CPU_VARIANT := generic

TARGET_NO_KERNEL := false
TARGET_KERNEL_CONFIG := zyx_virtual_defconfig
BOARD_KERNEL_PAGESIZE := 4096
BOARD_BOOT_HEADER_VERSION := 4

TARGET_USES_64_BIT_BINDER := true
BOARD_SEPOLICY_DIRS += $(LOCAL_PATH)/sepolicy
