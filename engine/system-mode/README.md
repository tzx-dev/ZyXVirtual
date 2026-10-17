# Backend QEMU system-mode AArch64

O runner herdado em `app/.../GuestRunner.kt` executa QEMU user-mode para guests ARM32. Ele não inicializa um kernel Android 16. `app/.../ZyxSystemCommand.kt` define e testa os argumentos para a futura VM completa: máquina ARM `virt`, CPU `cortex-a57`, TCG multi-thread, virtio, disco persistente, VNC local, QMP e log serial.

## Código novo de engine

A pasta `native/qemu-system/` agora define a ABI versionada `zyx_qemu_get_api` e uma bridge JNI em C que carrega uma biblioteca QEMU, executa argumentos sem shell e expõe start/stop/reset/estado. `app/.../ZyxQemuSystemBridge.kt` é o wrapper Kotlin. A sintaxe C da bridge passou por `gcc -fsyntax-only -Wall -Wextra -Werror` neste sandbox.

Isso ainda é **código-fonte**, não engine executável: a bridge não foi compilada com Android NDK, o adaptador QEMU que implementa `zyx_qemu_get_api` não existe, não há `libqemu-system-aarch64.so`, nem renderer VNC/Surface, eventos de toque/teclado ou cópia/overlay gravável do disco. O script `scripts/build-zyx-qemu-jni.sh` compila somente a bridge, não QEMU.

## Contrato da primeira versão

- Host Android: `arm64-v8a`.
- Guest: AArch64 system-mode em máquina QEMU `virt`; TCG é obrigatório porque KVM/AVF não podem ser presumidos.
- QEMU precisa ser compilado com `aarch64-softmmu`, TCG, virtio-gpu/blk/net e saída VNC ou SDL apropriada.
- O adapter QEMU implementa a ABI ZyX versão 1, encapsulando a API interna da versão QEMU fixada; a bridge não tenta chamar `main` ou símbolos internos instáveis.
- Argumentos passam como `String[]`, nunca por shell. QMP/VNC ficam em loopback/socket Unix privado.
- Não gravar na imagem base verificada. Criar cópia privada ou overlay qcow2 persistente antes de iniciar.

## Build da bridge JNI

```bash
ANDROID_NDK_HOME=/caminho/para/android-ndk scripts/build-zyx-qemu-jni.sh
```

O build do Android NDK não foi executado nesta sessão porque não há NDK instalado no sandbox ARM64. Mesmo compilada, a bridge sozinha não permite boot.

## Referência de viabilidade, não dependência

O Limbo documenta `aarch64-softmmu` em `arm64-v8a`, mas seus scripts fixam versões antigas (QEMU 5.1.0/2.9.1). Não copiamos os binários nem o código GPL dessa implementação. A versão ZyX deve ser escolhida e fixada junto com seu adapter e licenças, depois de comparar com o pacote VM produzido pela LineageOS 23.2.

## Critérios de aceite antes de expor Iniciar

1. Adaptador e build QEMU Android versionados, com fontes e avisos/licenças preservados.
2. Boot completo do produto ZyX em QEMU host, com `/system`, `/vendor`, tela e `adb` funcionais.
3. Start/stop/reboot, persistência, rede e logs testados.
4. Renderer e eventos touch/keyboard integrados no app.
5. Build arm64, instalação e boot em Galaxy A07 sem root; só então habilitar Iniciar.
