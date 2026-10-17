# Bridge JNI/QEMU system-mode — interface experimental

A ABI versionada de `zyx_qemu_api.h`, a bridge C `zyx_qemu_jni.c` e o wrapper `ZyxQemuSystemBridge.kt` foram preparados como uma fronteira nativa possível para trabalho futuro. A bridge usa `dlopen`, procura o símbolo ZyX `zyx_qemu_get_api`, passa argumentos sem shell e expõe stop/reset/estado. Ela recusa inicialização se o backend não implementar a ABI explicitamente.

## Backend atualmente usado pelo app

O caminho de boot de desenvolvimento **não usa esta bridge JNI**. O APK `0.2.0-alpha.1` empacota QEMU 11.1.2 system-mode ARM64 e dependências como assets (preparados por `scripts/prepare_qemu_runtime.py`). `ZyxQemuAssetRuntime` verifica e extrai os arquivos para armazenamento privado; `ZyxQemuSystemProcess` inicia o executável por `ProcessBuilder`; `ZyxVmActivity` apresenta o framebuffer via VNC local; `ZyxOsVmStorage` prepara discos e NVRAM persistentes. A inicialização desse fluxo ainda não foi validada em aparelho físico.

## Estado desta bridge

- ABI e fonte C: presentes, sintaxe C verificada na CI.
- Build Android NDK da bridge: não executado nesta sessão.
- Adapter QEMU que implemente `zyx_qemu_get_api`: ausente.
- O launcher process-based atual não requer a bridge nem o adapter.

`scripts/build-zyx-qemu-jni.sh` compila somente `libzyx-qemu-jni.so` e não compila QEMU. Não inclua a bridge no estado de runtime ativo até existir um adapter completo e teste de start/stop/reset. Para o estado completo do motor, consulte [`../../docs/architecture/engine-refactor-status.md`](../../docs/architecture/engine-refactor-status.md).
