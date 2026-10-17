#!/usr/bin/env sh
set -eu
printf 'host_arch='; uname -m
printf 'kernel='; uname -r
printf 'cpu_count='; getconf _NPROCESSORS_ONLN 2>/dev/null || echo unknown
printf 'memory='; awk '/MemTotal/ { printf "%.0f MB\n", $2/1024 }' /proc/meminfo
if [ -r /dev/kvm ]; then
  echo 'kvm_device=readable'
else
  echo 'kvm_device=unavailable'
fi
if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
  echo 'adb=device-online'
  adb shell getprop ro.product.model || true
  adb shell getprop ro.build.version.release || true
  adb shell getprop ro.product.cpu.abilist || true
  adb shell getprop ro.hardware.egl || true
  if adb shell 'test -r /dev/kvm' >/dev/null 2>&1; then
    echo 'device_kvm=readable'
  else
    echo 'device_kvm=unavailable'
  fi
else
  echo 'adb=device-not-connected'
fi
