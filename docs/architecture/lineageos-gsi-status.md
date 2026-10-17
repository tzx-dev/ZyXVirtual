# LineageOS, OTA Mi A3 e alvo virtual ZyX

## Decisão atual

O produto ZyX não usará uma GSI genérica, nem uma ROM de telefone para Galaxy A07/Xiaomi Mi A3. O alvo selecionado é **LineageOS 23.2 / Android 16**, recompilado para o device tree virtual ARM64 `virtio_arm64only`, com identidade `lineage_zyx_virtual` e sem GApps. Esta é uma build de sistema virtual, não uma ROM instalável no hardware físico.

A OTA oficial baixada para Xiaomi Mi A3 (`laurel_sprout`) é um pacote A/B de atualização de telefone. Ela não contém a configuração/kernels de VM que o app precisa e não foi transplantada para ZyX OS. O importador genérico de ROMs foi retirado do fluxo principal; o app agora só aceita um pacote `.zyxos` que passe pelo manifesto de produto e pelos hashes.

## Galaxy A07 físico

Verificação atualizada em **17/10/2026**, com o Galaxy A07 4G como o aparelho original do pedido: o portal oficial publica LineageOS 23.x/Android 16, mas a lista consultada não contém `a07`, `a075` ou `sm-a075f`. Não há uma build oficial device-specific do A07 para baixar. Não encontrei uma build comunitária verificável específica de `SM-A075F`/`SM-A075M`; nenhum ZIP aleatório foi baixado para o app.

A documentação Android define GSI como imagem AOSP genérica para aparelhos físicos com compatibilidade Project Treble e arquitetura correspondentes. Isso não a transforma em imagem de VM. O AVF também é uma arquitetura própria que depende do kernel/VMM/serviços expostos pelo host; não basta colocar `system.img` no APK.

A ficha Samsung identifica o Galaxy A07 SM-A075F, mas não declara suporte a `/dev/kvm` ou AVF para apps de terceiros. Não há aparelho A07 conectado por ADB nesta sessão e o sandbox não oferece `/dev/kvm`; compatibilidade física e boot no Galaxy continuam sem teste.

## ROM virtual Android 16

As fontes oficiais consultadas indicam uma rota distinta: produto LineageOS virtual ARM64, baseado no upstream `virtio_arm64only`, com pacote UTM/QEMU. O overlay ZyX e a receita de build estão versionados em `guest/zyx-os/device/zyx/zyx_virtual/` e `scripts/build-zyx-guest.sh`. O `.zyxos` final ainda não foi construído.

O instalador Android espera exclusivamente `lineage_zyx_virtual` / LineageOS 23.2 / Android 16 / AArch64 / sem GApps / QEMU `virt`; rejeita OTA de telefone, outra arquitetura ou pacotes com Google Apps. A instalação via Google Drive é feita pelo seletor Android (Storage Access Framework) no aparelho do usuário, sem credenciais embutidas no APK.

## Bloqueios e próximos passos

1. Fazer checkout/build do LineageOS 23.2 e guardar o manifest com revisões exatas.
2. Inspecionar o pacote UTM real e confirmar seu kernel, firmware, discos, config, boot e recursos.
3. Compilar e integrar QEMU AArch64 system-mode com TCG, virtio, armazenamento gravável, rede, logs e bridge JNI.
4. Implementar renderer de tela e toque/teclado, iniciar a VM e testar boot/persistência num host QEMU.
5. Testar em Galaxy A07 4G somente após validar RAM, desempenho e ciclo de vida Android; não instalar uma GSI no aparelho como substituto do guest virtual.

Este sandbox não atende aos requisitos oficiais de build AOSP: a documentação atual pede host x86-64, 400 GB de espaço livre e 64 GB de RAM; o sandbox é ARM64, tem ~35 GB livres e 15 GiB de RAM total (cerca de 13 GiB disponíveis na medição atual). Por isso, não foi produzida a ROM completa nesta sessão.

## Referências

- [1]: https://wiki.lineageos.org/devices/ — lista oficial de dispositivos LineageOS.
- [2]: https://developer.android.com/topic/generic-system-image — documentação Android sobre GSI.
- [3]: https://source.android.com/docs/core/virtualization/architecture — arquitetura oficial AVF.
- [4]: https://www.samsung.com/ae/business/smartphones/galaxy-a/galaxy-a07-black-64gb-sm-a075fzkdmea/ — Samsung Galaxy A07 SM-A075F.
- [5]: https://download.lineageos.org/api/v1/devices — API oficial de dispositivos LineageOS.
- [6]: https://lineageos.org/Changelog-30/ — LineageOS 23 baseada em Android 16.
- [7]: https://wiki.lineageos.org/gapps/ — GApps são add-on separado.
- [8]: https://wiki.lineageos.org/faq.html — política oficial sobre dispositivos não listados.
- [9]: https://wiki.lineageos.org/libvirt-qemu — guia LineageOS libvirt/QEMU.
- [10]: https://wiki.lineageos.org/utm-vm-on-apple-silicon-mac — guia LineageOS UTM ARM64.
- [11]: https://source.android.com/docs/setup/start/requirements — requisitos oficiais para build AOSP.
