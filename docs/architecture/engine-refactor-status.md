# Estado da refatoração do motor

O pedido exige execução full-system real, sem simular uma tela. O engine original do AEmulator executa QEMU user-mode para guests ARM32; não contém VMM, kernel ARM64 virtual, máquina `virt` ou Android 16. Por isso a OTA do Mi A3 não pode ser usada como guest ZyX.

## Implementado nesta etapa

- Selecionado o produto LineageOS virtual ARM64 `lineage_zyx_virtual`, baseado no device tree upstream `virtio_arm64only`, sem GApps.
- Criado o construtor de argumentos `ZyxSystemCommand` para QEMU AArch64 system-mode, máquina `virt`, TCG, virtio, VNC local, QMP e armazenamento.
- Criada a ABI versionada `zyx_qemu_get_api`, bridge JNI C de ciclo de vida e wrapper Kotlin.
- Criada instalação `.zyxos` via SAF/Drive, em armazenamento privado, com manifesto, hashes por arquivo, rejeição de pacotes incompatíveis e proteção contra path traversal.
- Removidos do fluxo principal os imports genéricos de ROM, o intent externo de boot legacy e permissões amplas de armazenamento.
- O diagnóstico verifica a presença das duas bibliotecas necessárias; a inicialização permanece desabilitada porque os binários/renderer não existem no APK.

## Validações efetivamente realizadas

- Build Android concluído para standard debug, clone debug e standard release unsigned.
- Testes unitários: **10/10 passaram** — DeviceOtaProbe (3), política do pacote ZyXOS (4) e builder QEMU system-mode (3).
- `zyx_qemu_jni.c` passou por sintaxe C com `gcc -Wall -Wextra -Werror`; isso não é build Android NDK nem valida ligação/runtime.
- Scripts shell, Python, XML de strings e manifesto JSON passaram por validação estática.
- Debug APK verificado com assinatura de desenvolvimento V2. Nenhum APK foi instalado em aparelho.

## Ainda ausente

O adaptador QEMU que implementa `zyx_qemu_get_api`, a build QEMU `aarch64-softmmu` para Android, o APK com `libqemu-system-aarch64.so`, display VNC/SDL conectado à tela, eventos de toque/teclado, cópia/overlay gravável de disco, rede e teste de boot. A bridge JNI não foi compilada porque não há NDK Android instalado neste sandbox. O guest LineageOS e o pacote `.zyxos` também não foram construídos.

## Limites do host

A documentação AOSP exige host x86-64, 400 GB de espaço livre e 64 GB RAM para checkout/build. Este sandbox ARM64 tem aproximadamente 35 GB livres e 15 GiB RAM total (cerca de 13 GiB disponíveis na medição atual). O build do APK Android é viável; o build completo da ROM não é. Fontes e medições estão em `docs/architecture/qemu-system-and-build-constraints.md`.
