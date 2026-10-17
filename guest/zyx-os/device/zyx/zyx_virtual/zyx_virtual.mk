# Copyright (C) 2026 ZyX Virtual contributors
# SPDX-License-Identifier: Apache-2.0
# ZyX OS Android 16 guest based on the official LineageOS VirtIO ARM64 product.
LOCAL_PATH := $(call my-dir)

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Keep the upstream LineageOS virtual-device defaults, but brand the product as ZyX.
TARGET_EXCLUDES_AUDIOFX := true
$(call inherit-product, vendor/lineage/config/common_full_tablet_wifionly.mk)
$(call inherit-product, device/zyx/zyx_virtual/device.mk)

PRODUCT_NAME := lineage_zyx_virtual
PRODUCT_DEVICE := zyx_virtual
PRODUCT_BRAND := ZyX
PRODUCT_MANUFACTURER := ZyX
PRODUCT_MODEL := ZyX Virtual
PRODUCT_CHARACTERISTICS := emulator

PRODUCT_SYSTEM_PROPERTIES += \
    ro.zyx.guest_product=zyx_virtual \
    ro.zyx.guest_contract=1 \
    ro.zyx.virtual_device=virtio_arm64only
