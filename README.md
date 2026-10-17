# ZyX Virtual

ZyX Virtual é um fork experimental do [AEmulator](https://github.com/uxazu/AEmulator), mantendo o layout reconhecível e substituindo a biblioteca genérica por um fluxo destinado a um único guest: **ZyX OS**, baseado no produto virtual ARM64 da LineageOS 23.2 / Android 16, sem Google Apps.

## Estado real nesta revisão

- O app e os testes unitários compilam no sandbox ARM64.
- A tela principal aceita apenas um pacote `.zyxos`; o usuário pode escolhê-lo no seletor de documentos Android, inclusive no provedor Google Drive autenticado no próprio celular. O app não incorpora credenciais Google.
- O instalador copia o pacote para armazenamento privado, valida a identidade/versão do produto, rejeita GApps, confere cada SHA-256 e impede caminhos ZIP inseguros. Há estado de progresso, cancelamento e remoção local; apagar do app não apaga o original no Drive.
- Os hashes protegem contra corrupção e divergência entre arquivos e manifesto, mas o manifesto ainda não tem assinatura criptográfica do publicador. A distribuição não deve ser tratada como autenticada até uma chave de release estar configurada.
- O engine original do AEmulator executa guests ARM32 por QEMU user-mode. Esses componentes permanecem no código legado, mas não estão mais expostos na tela principal como suporte a outras ROMs.
- O construtor testável `ZyxSystemCommand` define argumentos para QEMU AArch64 system-mode (`virt`, TCG, CPU `max`, virtio, dois discos, UEFI pflash, VNC local e QMP). A ABI e o código-fonte da bridge JNI ZyX também foram adicionados; a sintaxe C foi validada no host. Eles ainda não iniciam VM: faltam o adapter QEMU que implementa a ABI, o binário QEMU system-mode para Android, o renderer VNC/Surface e os overlays graváveis de disco/NVRAM. **O boot no app permanece desabilitado e a interface avisa isso explicitamente.**
- O pacote final do produto ZyX ainda não foi compilado. Foi gerado localmente um `.zyxos` bootstrap baseado em VM upstream pré-compilada, incluindo firmware AAVMF; manifesto e SHA-256 dos oito arquivos passaram. Em QEMU de host com `-cpu max`, o guest chegou ao `init` e à descompressão de APEX, mas após 300 s não houve confirmação de zygote/framework. Veja [`docs/roms/lineageos-23.2-virtual-bootstrap.md`](docs/roms/lineageos-23.2-virtual-bootstrap.md).

Não se deve apresentar a OTA de telefone `laurel_sprout` do Xiaomi Mi A3 como imagem de VM. Ela não foi convertida nem instalada como guest. A base virtual precompilada pode ser adaptada; para chamar a imagem de ZyX OS final, ainda falta aplicar/buildar o overlay `lineage_zyx_virtual`. Não é GSI nem firmware de telefone.

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
├── docs/roms/                   # análise da OTA Mi A3 e da base virtual bootstrap
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
mkdir -p /caminho/para/lineage-23.2
scripts/check-zyx-build-host.sh /caminho/para/lineage-23.2
LINEAGE_DIR=/caminho/para/lineage-23.2 scripts/build-zyx-guest.sh
```

O preflight verifica Linux x86-64, pelo menos 64 GiB de RAM efetiva (considerando limites cgroup) e 400 GiB livres no filesystem de destino. O script de build repete essa verificação antes de copiar o overlay ou chamar o sistema de compilação. A ROM completa ainda não foi compilada nesta sessão. Consulte `docs/architecture/qemu-system-and-build-constraints.md` e `docs/architecture/engine-refactor-status.md`.

## Google Drive

No APK, o seletor Android permite abrir o provedor Google Drive se o usuário estiver autenticado no app Drive instalado no aparelho. O bootstrap privado `ZyXOS-lineage-23.2-20260917-bootstrap-aavmf.zyxos` foi enviado ao Drive em 17/10/2026; tamanho e MD5 do arquivo remoto conferem com o artefato local. Ele continua sendo uma base upstream de desenvolvimento, não o produto ZyX final, e ainda não confirmou boot do framework.

## GitHub e licença

O código ZyX está no repositório GitHub `tzx-dev/ZyXVirtual`, atualmente público; a CI Android grava APKs como artefatos de workflow. A primeira prévia pública [`v0.1.1-alpha.1`](https://github.com/tzx-dev/ZyXVirtual/releases/tag/v0.1.1-alpha.1) contém somente o APK debug do aplicativo — **não inclui a ROM `.zyxos` e não inicializa o Android convidado**. Os componentes herdados e de terceiros mantêm as licenças próprias. O snapshot local de auditoria não é duplicado como diretório upstream.
