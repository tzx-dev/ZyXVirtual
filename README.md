# ZyX Virtual

ZyX Virtual é uma evolução arquitetural do [AEmulator](https://github.com/uxazu/AEmulator), preservando sua interface visual e seu fluxo de biblioteca, mas separando claramente o backend legado do futuro guest ZyX OS.

## Estado desta entrega

O APK já compila e continua conectado ao motor real herdado: QEMU user-mode, Binder proxy, ponte GL, áudio, entrada, armazenamento persistente, importação de firmware, ciclo de vida, logs e ADB. A identidade foi migrada para **ZyX Virtual**, a UI foi localizada para português brasileiro e o ícone usa o monograma ZyX inspirado na arte AE original.

O contrato atual **não é uma VM Android arm64 moderna**. Os ELF empacotados executam guests ARM32 legados; `/dev/kvm` e AVF não são usados pelo backend atual. O diagnóstico aparece na própria aplicação e nos logs de boot para evitar que uma imagem seja chamada de virtualizada quando é apenas adaptação user-mode.

A camada `guest/zyx-os/` contém o overlay de produto e o contrato inicial para uma futura árvore LineageOS/AOSP. **Este APK não contém uma ROM LineageOS/GSI.** A árvore completa do LineageOS, o kernel GKI/ACK, as imagens AVB e um APK guest não são inventados nem incluídos sem fontes, ferramentas e boot validado. A análise específica está em `docs/architecture/lineageos-gsi-status.md`.

## Estrutura

```text
ZyXVirtual/
├── app/                         # APK Android; UI preservada do AEmulator
├── engine/                      # manifesto e contrato do backend real
├── native/                      # fontes nativas reproduzíveis existentes
├── guest/zyx-os/                # overlay de produto ZyX OS
├── guest/kernel/                # contrato e bloqueios do kernel guest
├── scripts/                     # auditoria, host check, APK e guest build
├── docs/architecture/           # auditoria técnica e plano por etapas
├── docs/build/                  # relatório de build e limitações
├── docs/assets/                 # logotipo ZyX e artefatos de marca
└── .github/workflows/           # builds Android em push e pull request
```

## Build do aplicativo

No sandbox ARM64 desta entrega, o Android Gradle Plugin fornece AAPT2 x86_64. `scripts/build-apk.sh` compila um launcher ARM64 temporário e usa QEMU apenas para esse utilitário de build; isso não virtualiza o guest Android.

```bash
export ANDROID_HOME=/caminho/para/android-sdk
scripts/check-engine-assets.sh
scripts/build-apk.sh assembleStandardDebug
```

O APK gerado fica em `app/build/outputs/apk/standard/debug/`.

O workflow GitHub Actions compila variantes debug e release sem assinatura a cada push em `main` e guarda os APKs como artefatos do workflow por 30 dias. O repositório ZyX é privado; a verificação de atualização dentro do app não consegue ler releases privadas sem credenciais embutidas (o que seria inseguro). Releases públicas e instalação pelo atualizador do app ainda não estão configuradas.

## Guest ZyX OS

```bash
LINEAGE_DIR=/caminho/para/lineage scripts/build-zyx-guest.sh
```

O script falha se a árvore AOSP/LineageOS não estiver presente ou se o overlay não estiver registrado. Isso é intencional: uma compilação Android bem-sucedida do app não prova que o guest inicializa.

## Licença e upstream

O código herdado permanece sob GPL-3.0. Firmware, AOSP, LineageOS e blobs de fabricantes mantêm suas próprias licenças. O snapshot upstream usado na auditoria foi mantido apenas localmente e não é duplicado no repositório ZyX; o commit e os achados estão documentados em `docs/architecture/initial-analysis.md`.
