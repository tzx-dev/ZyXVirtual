# ZyX OS product overlay. Keep this file small and review every inherited product.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

PRODUCT_NAME := zyx_virtual
PRODUCT_DEVICE := zyx_virtual
PRODUCT_BRAND := ZyX
PRODUCT_MODEL := ZyX Virtual Guest
PRODUCT_MANUFACTURER := ZyX

PRODUCT_CHARACTERISTICS := emulator
TARGET_SCREEN_WIDTH := 540
TARGET_SCREEN_HEIGHT := 960

# Host-provided services replace physical radio, camera and sensor dependencies.
PRODUCT_SYSTEM_PROPERTIES += \
    ro.product.name=zyx_virtual \
    ro.product.device=zyx_virtual \
    ro.product.model="ZyX Virtual Guest" \
    ro.kernel.qemu=1 \
    ro.zygote=zygote64_32
