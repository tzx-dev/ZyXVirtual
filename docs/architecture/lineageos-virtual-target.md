# Decisão para reconstruir o guest ZyX OS

Verificado em 17/10/2026. A ROM Mi A3 baixada não deve ser transformada diretamente em guest: ela é uma OTA A/B com `pre-device=laurel_sprout`, `payload.bin` delta e partições específicas do telefone. A rota de reconstrução é usar uma configuração virtual LineageOS ARM64 como base e compilar um produto ZyX próprio para dispositivos virtuais.

## Base de build escolhida

A wiki oficial do LineageOS lista builds virtuais para `lineage-23.2` e fornece o alvo `virtio_arm64only` (ARM64-only para PC), com artefato de instalação produzido por `m espimage-install`. O resultado é uma imagem de instalação de dispositivo virtual, não uma GSI de telefone. A wiki também descreve requisitos de máquina virtual QEMU/libvirt, firmware, discos VirtIO e tamanho mínimo de memória.

O changelog oficial do LineageOS 23 informa suporte a configurações Cuttlefish, descritas como dispositivos virtuais com periféricos emulados. A documentação AOSP descreve Cuttlefish como dispositivo Android virtual com fidelidade do framework e suporte local em Linux x86/ARM64. Para executar Cuttlefish, a documentação exige que o host ofereça KVM; a página recomenda verificar `/dev/kvm`. Isso não prova que um app Android comum no Galaxy A07 possa iniciar Cuttlefish ou abrir KVM.

## Arquitetura a implementar

1. Usar LineageOS 23.2 / Android 16 e o target virtual `virtio_arm64only` como base de produto e configuração de hardware virtual.
2. Fazer fork/overlay ZyX do produto virtual: branding ZyX, propriedades e permissões próprias, política SELinux, configurações e serviços guest específicos. Não importar kernel, vendor, modem, `dtbo` ou `vbmeta` do Mi A3.
3. Gerar os artefatos de guest a partir da árvore fonte: kernel/boot, partições Android e imagem de instalação. Empacotar o resultado num formato ZyX versionado com manifesto, hashes SHA-256 e requisitos do backend.
4. Criar no app um backend de sistema completo separado do atual QEMU linux-user ARM32. Esse backend precisa carregar o kernel e as partições virtuais, fornecer armazenamento persistente, console/ADB, rede, input, display, lifecycle e logs. Sem o VMM/backend e teste de boot, a imagem não será marcada como funcional.
5. Testar primeiro o guest em QEMU/crosvm/Cuttlefish num host Linux capaz de executar a virtualização. Depois medir no Galaxy A07 se há KVM/AVF acessível e validar desempenho, RAM, display e input no app.

## Limites atuais

- O APK continua usando `qemu-arm` linux-user, com ABI guest ARM32 e faixa Android legada; não carrega kernel nem imagens Android 16.
- Esta cópia do workspace tem aproximadamente 35 GB livres e o Sandbox dispõe de 15 GiB de RAM total (cerca de 13 GiB disponíveis na medição atual). A wiki LineageOS de máquinas virtuais informa requisitos de build muito maiores para versões recentes (cerca de 64 GB de RAM e 400 GB de armazenamento para LineageOS 21+). O ambiente atual não comporta o sync/build completo.
- Não há neste workspace uma árvore fonte LineageOS/AOSP completa, kernel guest ZyX, imagem system/boot virtual compilada nem runtime QEMU system/crosvm Android embutido.
- A sessão Google Drive não tem connector configurado e `gws auth status` informa `auth_method=none`. Não subir uma OTA de telefone como se fosse a ROM ZyX pronta; upload só poderá ocorrer após a imagem ZyX ser construída e depois de a autorização Drive estar disponível.

## Referências

[1]: https://wiki.lineageos.org/libvirt-qemu "LineageOS Wiki — build virtual ARM64 via QEMU/libvirt"
[2]: https://wiki.lineageos.org/utm-vm-on-apple-silicon-mac "LineageOS Wiki — target virtio para máquina virtual ARM64"
[3]: https://lineageos.org/Changelog-30/ "LineageOS 23 — changelog e configuração Cuttlefish"
[4]: https://source.android.com/docs/devices/cuttlefish "AOSP — dispositivos virtuais Cuttlefish"
[5]: https://source.android.com/docs/devices/cuttlefish/get-started "AOSP — build e execução de Cuttlefish, incluindo KVM"
