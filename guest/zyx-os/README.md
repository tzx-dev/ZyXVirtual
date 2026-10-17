# ZyX OS — produto LineageOS virtual ARM64

Esta árvore define `lineage_zyx_virtual`, um produto baseado no device tree upstream `virtio_arm64only` da LineageOS 23.2 / Android 16. É um produto de VM próprio, não GSI e não a ROM física do Xiaomi Mi A3 ou Galaxy A07. A base LineageOS não inclui Google Apps; GApps são add-on separado e não fazem parte deste produto.

## Fontes e overlay

O overlay em `device/zyx/zyx_virtual/` altera identidade, propriedades do produto e init ZyX, herda o BoardConfig e `device.mk` upstream e declara as árvores VirtIO em `lineage.dependencies`. Não inventa kernel, `defconfig`, HAL, VINTF manifest ou blobs físicos. Os repositórios upstream confirmados são:

- `LineageOS/android_device_virt_virtio_arm64only`
- `LineageOS/android_device_virt_virtio_arm64`
- `LineageOS/android_device_virt_virtio-common`

## Build

```bash
mkdir -p /caminho/para/lineage-23.2
scripts/check-zyx-build-host.sh /caminho/para/lineage-23.2
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

O preflight verifica Linux x86-64, pelo menos 64 GiB de RAM efetiva (incluindo limites cgroup) e 400 GiB livres. O script principal repete a validação antes de alterar a árvore; com uma árvore LineageOS completa, instala o overlay, resolve as dependências declaradas, verifica o produto e executa `m vm-utm-zip`. Só empacota `.zyxos` se o ZIP UTM contiver `config.plist` e imagem de disco.

**Estado da ROM:** o device tree e o pipeline estão preparados, mas esta sessão não tem uma árvore LineageOS completa e não compilou `lineage_zyx_virtual`. O `.zyxos` enviado ao Drive privado é um bootstrap upstream, não uma ROM ZyX final; o smoke test de host avançou até `init`/APEX mas não confirmou zygote/framework. Veja [`../../docs/roms/lineageos-23.2-virtual-bootstrap.md`](../../docs/roms/lineageos-23.2-virtual-bootstrap.md).

## Relação com o app

O app aceita somente pacote `.zyxos` com identidade ZyX OS, Android 16, ARM64, QEMU system-mode e sem GApps. O APK de desenvolvimento `0.2.0-alpha.1` inclui QEMU ARM64, o launcher process-based, cópias persistentes dos discos/NVRAM e uma tela VNC com toque/teclado. A ROM permanece separada do APK e é escolhida via SAF/Drive.

O caminho de boot está integrado no código, mas **ainda não foi validado em Android físico**; o Galaxy A07 não foi testado e não se deve afirmar que o guest inicializa até que o APK e a imagem cheguem ao framework nesse aparelho. A bridge JNI experimental não é usada pelo launcher atual. A compilação do overlay ZyX continua pendente.
