# ZyX Virtual

ZyX Virtual é um fork experimental do [AEmulator](https://github.com/uxazu/AEmulator), mantendo o layout reconhecível e substituindo a biblioteca genérica por um fluxo destinado a um único guest: **ZyX OS**, baseado no produto virtual ARM64 da LineageOS 23.2 / Android 16, sem Google Apps.

## Estado real nesta revisão

- O app e os testes unitários compilam no sandbox ARM64.
- A tela principal aceita apenas um pacote `.zyxos`; o usuário pode escolhê-lo no seletor de documentos Android, inclusive no provedor Google Drive autenticado no próprio celular. O app não incorpora credenciais Google.
- O instalador copia o pacote para armazenamento privado, valida a identidade/versão do produto, rejeita GApps, confere cada SHA-256 e impede caminhos ZIP inseguros. Há estado de progresso, cancelamento e remoção local; apagar do app não apaga o original no Drive.
- Os hashes protegem contra corrupção e divergência entre arquivos e manifesto, mas o manifesto ainda não tem assinatura criptográfica do publicador. A distribuição não deve ser tratada como autenticada até uma chave de release estar configurada.
- O engine original do AEmulator executa guests ARM32 por QEMU user-mode. Esses componentes permanecem no código legado, mas não estão mais expostos na tela principal como suporte a outras ROMs.
- O construtor testável `ZyxSystemCommand` define argumentos para um QEMU AArch64 system-mode (`virt`, TCG, virtio, VNC local, QMP e disco persistente). A ABI e o código-fonte da bridge JNI ZyX também foram adicionados; a sintaxe C foi validada no host. Eles ainda não iniciam VM: a bridge não foi compilada com NDK, falta o adaptador QEMU que implementa a ABI, o binário QEMU system-mode, o renderer VNC/Surface e a cópia/overlay gravável do disco. **O boot permanece desabilitado e a interface avisa isso explicitamente.**
- O pacote `.zyxos` final ainda não foi compilado; a árvore LineageOS e o overlay do produto existem, mas não foi realizado boot do guest.

Não se deve apresentar a OTA de telefone `laurel_sprout` do Xiaomi Mi A3 como imagem de VM. Ela não foi convertida nem instalada como guest. O guest ZyX deve ser reconstruído da árvore-fonte para `virtio_arm64only`; não é uma GSI nem firmware de telefone.

## Estrutura

```text
ZyXVirtual/
├── app/                         # aplicativo, instalador .zyxos e builder QEMU system-mode
├── engine/                      # contratos, manifesto e desenho do backend full-system
├── native/                      # fontes nativas herdadas e documentação
├── guest/zyx-os/                # produto LineageOS virtual lineage_zyx_virtual
├── guest/kernel/                # dependências e bloqueios do kernel guest
├── scripts/                     # build APK, build guest, pacote e verificações
├── docs/architecture/           # auditoria, fontes e status do backend/guest
├── docs/build/                  # resultados de build/teste
├── docs/roms/                   # metadados da OTA Mi A3 (não é guest)
└── .github/workflows/           # CI Android
```

## App

```bash
export ANDROID_HOME=/caminho/para/android-sdk
scripts/check-engine-assets.sh
scripts/build-apk.sh testStandardDebugUnitTest assembleStandardDebug
```

O APK debug fica em `app/build/outputs/apk/standard/debug/app-standard-debug.apk`. No sandbox ARM64, o wrapper AAPT2 usa QEMU somente para executar a ferramenta de build x86_64; isso não virtualiza o Android convidado.

## Build da ROM virtual

```bash
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

Este comando só pode produzir um `.zyxos` depois que existir uma árvore LineageOS 23.2 completa. A documentação AOSP atual pede host x86-64, 400 GB livres e 64 GB RAM; este sandbox tem ARM64, aproximadamente 35 GB livres e 15 GiB RAM total (cerca de 13 GiB disponíveis na medição atual). Não foi possível compilar a ROM completa aqui. Consulte `docs/architecture/qemu-system-and-build-constraints.md` e `docs/architecture/engine-refactor-status.md`.

## Google Drive

No APK, o seletor Android permite abrir o provedor Google Drive se o usuário estiver autenticado no app Drive instalado no aparelho. A sessão Sandbox não tem uma conexão gws autenticada para subir um artefato ao Drive; além disso, ainda não há ROM `.zyxos` final para enviar.

## GitHub e licença

O código ZyX está no repositório GitHub `tzx-dev/ZyXVirtual`, atualmente público; a CI Android grava APKs como artefatos de workflow. Os componentes herdados e de terceiros mantêm as licenças próprias. O snapshot local de auditoria não é duplicado como diretório upstream.
