# Auditoria inicial e plano técnico — ZyX Virtual

## O que foi verificado

O snapshot auditado é `uxazu/AEmulator` no commit `38d9a00fc5549605a625b1d67ae32778736932d6`. O clone local é raso e contém uma revisão, 210 arquivos rastreados, 139 arquivos sob `app/`, fontes nativas em `native/` e bibliotecas ELF já empacotadas em `app/src/main/jniLibs/arm64-v8a/`.

A arquitetura real é composta por:

- app Android Kotlin/Compose com `minSdk 26`, `compileSdk 36`, `targetSdk 28` e ABI do host `arm64-v8a`;
- dois perfis de engine (`GB` e `KK`) que selecionam QEMU, `dhdrun`, `binderd` e `glserverd` diferentes;
- `GuestRunner`, que monta a linha `dhdrun → qemu-arm -L rootfs` e injeta propriedades, sockets e shims;
- `GuestVm`, que prepara a árvore, sobe propriedades, Binder, GL, áudio, rede, vold/RIL, serviços do guest e watchdog;
- `InitPlan`, que lê `init*.rc`, filtra serviços dependentes de hardware e recria a ordem de boot;
- importer que extrai ZIP/TAR/Odin/factory images, ext4 sparse, `system.new.dat(.br)` e boot ramdisk;
- ponte GL, stubs Java, HAL de áudio e shims nativos ARM32 para compatibilidade com Android 2.3–7.x.

Os ELF dos engines são arm64 e os shims em `assets/engines/` incluem ELF ARM32. As strings dos QEMU registram uma base QEMU 8.2.2, mas o clone auditado não traz a árvore QEMU completa nem um patchset aplicável; por isso os binários são tratados como pré-compilados, não como uma recompilação feita nesta entrega.

## Incompatibilidades com o alvo moderno

O README do upstream declara firmware Android 2.3–7.x, ARM32 e execução em QEMU user-mode [1]. Isso é compatível com a estratégia atual de adaptar Binder, propriedades e dispositivos em user space, mas não equivale a iniciar um kernel Android convidado arm64.

A documentação do AVF descreve outro modelo: ARM64, pKVM no boot do dispositivo, `VirtualizationService`, crosvm/KVM, virtio e um kernel/guest assinado [2] [3]. A proteção e o ciclo de vida dependem do bootloader, kernel, pKVM e módulos específicos do SoC. Um aplicativo comum não consegue criar esses componentes retroativamente.

A página oficial do Galaxy A07 confirma o MediaTek G99 de 6 nm e disponibiliza manual do modelo em Android 16 [4]. Ela não é evidência de que o modelo exponha `/dev/kvm` ou AVF ao aplicativo. Essa capacidade precisa ser medida no aparelho com `scripts/check-host.sh`/ADB e com o diagnóstico do APK.

## Decisão do primeiro marco

1. Preservar o layout Compose, menus, botões e fluxo herdados; localização pt-BR e marca ZyX foram aplicadas nesta etapa.
2. Trocar a identidade pública para ZyX Virtual sem renomear os pacotes JNI `app.aemu`/`dev.lk`, pois os ELF atuais exportam símbolos JNI com esses nomes.
3. Instrumentar o app com `ZyxRuntime` para mostrar ABI, CPUs, RAM, GPU e `/dev/kvm` real.
4. Adicionar `ZyxGuestContract` para registrar, no boot e nos diagnósticos, que o backend atual é QEMU user-mode e valida ARM32 legado.
5. Organizar um overlay de produto ZyX OS e um script de build que exige uma árvore LineageOS/AOSP real; não entregar uma imagem fictícia.
6. Preparar o caminho de evolução para AVF/KVM, mas não apresentar AVF como implementado antes de haver backend, kernel, ramdisk, imagens e teste de boot.

## Matriz de viabilidade

| Componente | Estado nesta entrega | Evidência/teste |
|---|---|---|
| APK e UI preservada | Implementado | `scripts/build-apk.sh assembleStandardDebug` |
| CPU guest ARM32 por QEMU user-mode | Implementado no upstream e empacotado | `engine/manifest/zyx-engine.json`, ELF asset check |
| RAM/configuração/persistência | Implementado no engine herdado | `VmSettings`, `ImageStore`, `GuestVm` |
| Toque/teclado/botões | Implementado no engine herdado | `InputService`, `VmActivity` |
| GL/áudio/rede/Binder adaptados | Implementado no engine herdado, sujeito ao guest suportado | `native/`, `GuestVm`, `GuestRunner` |
| Guest arm64 moderno baseado em LineageOS | Pendente | exige backend kernel VM + kernel/HALs próprios |
| AVF/pKVM no Galaxy A07 | Não confirmado | medir no aparelho; a documentação AVF exige suporte de plataforma |
| Kernel e imagens ZyX OS | Pendente | `guest/kernel/README.md`, `scripts/build-zyx-guest.sh` |
| Boot real no celular alvo | Não testado nesta máquina | nenhum dispositivo ADB conectado |

## Próximas etapas

- Capturar `getprop`, ABI, GPU, memória, `/dev/kvm` e versão de kernel do A07 real.
- Escolher uma base LineageOS/AOSP que ainda tenha suporte a `arm64` genérico e encaixe no ciclo de publicação AOSP atual.
- Importar a árvore completa fora deste sandbox, aplicar o overlay `zyx_virtual` e produzir um primeiro boot image não protegido para crosvm/AVF de desenvolvimento.
- Implementar um backend separado, sem reutilizar o contrato de syscalls do QEMU user-mode, e criar testes host/guest de virtio-blk, console, vsock, input e display.
- Só então avaliar GPU; a Mali-G57 não deve ser tratada como GPU passthrough sem um caminho de renderização suportado pelo VMM.

## Referências

[1]: https://github.com/uxazu/AEmulator "AEmulator — repositório upstream e README"
[2]: https://source.android.com/docs/core/virtualization "Android Virtualization Framework — visão geral"
[3]: https://source.android.com/docs/core/virtualization/architecture "AVF architecture — pKVM, crosvm, virtio e boot"
[4]: https://www.samsung.com/ae/business/smartphones/galaxy-a/galaxy-a07-black-64gb-sm-a075fzkdmea/ "Samsung Galaxy A07 — página oficial e manual Android 16"
