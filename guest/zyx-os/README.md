# ZyX OS — produto LineageOS virtual ARM64

Esta árvore define `lineage_zyx_virtual`, um produto baseado no device tree upstream `virtio_arm64only` da LineageOS 23.2 / Android 16. É um produto de VM próprio, não GSI e não a ROM física do Xiaomi Mi A3 ou Galaxy A07. A base LineageOS não inclui Google Apps; GApps são add-on separado e não fazem parte deste produto.

## Fontes e overlay

O overlay em `device/zyx/zyx_virtual/` altera identidade, propriedades do produto e init ZyX, herda o BoardConfig e `device.mk` upstream e declara as árvores VirtIO em `lineage.dependencies`. Não inventa kernel, `defconfig`, HAL, VINTF manifest ou blobs físicos. Os repositórios upstream confirmados são:

- `LineageOS/android_device_virt_virtio_arm64only`
- `LineageOS/android_device_virt_virtio_arm64`
- `LineageOS/android_device_virt_virtio-common`

## Build

```bash
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

O script espera uma árvore `repo` LineageOS completa, instala o overlay sem sobrescrever diretório diferente, resolve as dependências declaradas, verifica o produto e executa `m vm-utm-zip`. Só empacota o `.zyxos` se o ZIP UTM real contiver `config.plist` e imagem de disco; escreve manifesto e SHA-256 dos arquivos.

**Estado:** o device tree e o pipeline estão preparados, mas esta sessão não tem uma árvore LineageOS completa e não produziu a imagem. AOSP documenta requisito de workstation x86-64, 400 GB livres e 64 GB de RAM. O sandbox atual é ARM64, com ~35 GB livres e ~11 GiB RAM. Portanto, não há `.zyxos` pronto para Drive nem boot a relatar.

## Relação com o app

O app aceita somente pacote `.zyxos` com `lineage_zyx_virtual`, Android 16, ARM64, QEMU system-mode e sem GApps. O instalador verifica os hashes e extrai em armazenamento privado. O boot permanece desativado até existir QEMU system-mode Android, renderer de tela/entrada, disco persistente e teste de inicialização real.
