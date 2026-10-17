# ZyX OS system-mode: fontes e limites verificados

**Verificação:** 17/10/2026. Este documento distingue o que foi confirmado nas fontes do que ainda não foi compilado/testado.

## Base guest selecionada

A escolha técnica é uma build LineageOS 23.2 / Android 16 para o produto virtual ARM64 `virtio_arm64only`, não a OTA do Xiaomi Mi A3, uma ROM de telefone, nem uma GSI. O overlay ZyX herda o device tree virtual upstream e troca a identidade do produto para `lineage_zyx_virtual`; não reutiliza kernel, vendor, modem, bootloader ou partições do `laurel_sprout`.

Os repositórios públicos declarados em `lineage.dependencies` publicam o branch `lineage-23.2`. SHA observado no branch na verificação: `android_device_virt_virtio_arm64only` `05609653c67fe93de3a7f0e115a1680355c9a8e5`; `android_device_virt_virtio_arm64` `3346a2fb77da6c8a2728cca8313530ba74de3798`; `android_device_virt_virtio-common` `f17927b21dd5eceaf85c8dd3f3e7f8e1a7d3d074`. Esses hashes são referências da checagem, não pins de build; para reproduzir, o manifest `repo` deve ser salvo com revisões exatas.

Guias upstream consultados: [LineageOS libvirt/QEMU](https://wiki.lineageos.org/libvirt-qemu) e [LineageOS UTM ARM64](https://wiki.lineageos.org/utm-vm-on-apple-silicon-mac). O wrapper ZyX solicita o target `vm-utm-zip`, mas ainda não executou esse build nesta árvore.

## Motor necessário

O QEMU upstream documenta a máquina `virt` como plataforma virtual genérica (não hardware físico), com dispositivos PCI/virtio, `virtio-gpu-pci`, UARTs e acelerador TCG. Para guest AArch64 deve ser selecionada uma CPU ARM64; o default de máquina `virt` é Cortex-A15, que é 32-bit. A documentação recomenda CPU explícita e descreve `cortex-a53`, `cortex-a57` e outras CPUs AArch64. Fonte: [QEMU ARM `virt`](https://qemu-project.gitlab.io/qemu/system/arm/virt.html).

O AEmulator atual continua sendo QEMU user-mode/ARM32 e não pode arrancar um kernel Android ARM64 completo. A tela de boot do guest, entrada por toque/teclado, áudio, rede e ciclo de vida precisam migrar para um backend QEMU system-mode. O APK atual ainda não contém `qemu-system-aarch64` nem um renderer VNC/virtio; o app agora valida e armazena apenas o pacote ZyXOS, sem oferecer um botão de boot falso.

Como referência de viabilidade, [Limbo para Android](https://github.com/limboemu/limbo) documenta build nativo `aarch64-softmmu` para hosts Android ARM64 e empacota um `libqemu-system-*.so`; a documentação de desenvolvedor ainda aponta QEMU 5.1.0/2.9.1 e NDK r23. A ficha [F-Droid do Limbo](https://f-droid.org/en/packages/com.limbo.emu.main/) alerta que emulação completa pode ser lenta para sistemas modernos. Isso comprova uma rota histórica, não um backend ZyX compatível: seu fork é antigo, GPL-2.0-or-later e não foi incorporado ao projeto. A implementação ZyX ainda deve escolher e fixar uma versão moderna de QEMU, portar o build Android, preservar avisos GPL e validar o boot real.

## Bloqueio quantitativo do build completo nesta sessão

A documentação oficial AOSP atual exige workstation Linux x86-64, pelo menos **400 GB livres** para checkout+build e **64 GB RAM**. Fonte: [AOSP build requirements](https://source.android.com/docs/setup/start/requirements) (atualizada em 17/06/2026).

O sandbox desta sessão é ARM64 e apresenta cerca de **35 GB livres** e **11 GiB de RAM**. Portanto, não é possível fazer aqui o checkout/build completo de LineageOS nem alegar uma imagem Android 16 compilada. O device tree, o produto e o pipeline de empacotamento foram preparados, mas um host apropriado ainda é necessário para produzir o `.zyxos` final e fazer o boot smoke test.

## Integração Drive no app

O app usa Android Storage Access Framework (`OpenDocument`) para o usuário selecionar o arquivo no provedor Google Drive do próprio dispositivo; nenhum token OAuth ou chave do Drive é embutido no APK. A instalação copia o arquivo para armazenamento privado, verifica o manifesto ZyXOS, hashes por entrada, caminhos, arquitetura, produto, Android 16 e ausência de GApps. Isso é uma etapa de ingestão, não execução da VM.

## Próximos marcos antes de habilitar “Iniciar”

1. Construir a árvore LineageOS 23.2 num host que atenda aos requisitos oficiais e salvar o manifest de revisões usado.
2. Inspecionar o ZIP `vm-utm-zip` real; confirmar nome/formatos de firmware, disco, config UTM, particionamento e requisitos de CPU/RAM.
3. Construir um QEMU system-mode Android arm64 atual, com `virt`, TCG, virtio-gpu, rede user-mode, VNC ou Surface renderer, console, shutdown/reboot e logs.
4. Adaptar o parser `.zyxos` ao layout real, carregar o disco persistente e implementar toque/teclado na tela existente do app.
5. Testar boot em QEMU de host primeiro; depois testar no Galaxy A07 4G. Somente então habilitar a ação de inicialização no APK.
