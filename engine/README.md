# ZyX Virtual engine

O APK ainda empacota os ELF herdados do AEmulator para QEMU user-mode. Eles executam guests ARM32 legados e não virtualizam kernel. O projeto agora adiciona uma rota separada de system-mode ARM64 para o ZyX OS; ela não substitui o backend antigo até que QEMU Android e a ROM passem por boot real.

## Backend legado

- ABI de guest validada: ARM32 (`armeabi` / `armeabi-v7a`).
- Syscalls, Binder, propriedades, entrada, áudio e GL são adaptados por daemons e shims; não há kernel Android moderno executando no guest.
- Os artefatos e shims herdados ficam em `app/src/main/jniLibs/arm64-v8a/` e `app/src/main/assets/engines/`.
- A tela principal ZyX não oferece mais importação genérica de outras ROMs; os componentes permanecem no código legado para compatibilidade, mas não representam o alvo do produto.

## Novo alvo system-mode

`app/.../ZyxSystemCommand.kt` cria argumentos QEMU para AArch64 `virt`/TCG/virtio e tem testes unitários. `native/qemu-system/` define uma ABI versionada e a bridge JNI em C; o wrapper Kotlin é `ZyxQemuSystemBridge.kt`. A bridge não está compilada com NDK e ainda falta o adapter QEMU que implementa `zyx_qemu_get_api`, o binário `libqemu-system-aarch64.so`, renderer, input e disco gravável separado. Por isso o diagnóstico informa que o QEMU system-mode não está empacotado e a inicialização permanece desabilitada.

O manifesto `manifest/zyx-engine.json` diferencia claramente capabilities reais e futuras. `scripts/check-engine-assets.sh` valida o backend legado e imprime o estado do system-mode sem confundir presença de código-fonte com VM funcional.

## Regra de honestidade

AVF/KVM não são presumidos nem usados pelo backend legado. A OTA de telefone Mi A3 não é uma VM. Não declare suporte a LineageOS 23.2/Android 16 até que a imagem ZyX OS, o adapter QEMU, a bridge/renderização e o ciclo de boot completo tenham sido compilados e testados.
