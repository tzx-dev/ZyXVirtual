# Copyright (C) 2026 ZyX Virtual contributors
# SPDX-License-Identifier: Apache-2.0
ZYX_VIRTUAL_DEVICE_PATH := $(call my-dir)

$(call inherit-product, device/virt/virtio_arm64only/device.mk)

PRODUCT_COPY_FILES += \
    $(ZYX_VIRTUAL_DEVICE_PATH)/init.zyx_virtual.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.zyx_virtual.rc
