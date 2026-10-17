# Relatório de build — ZyX Virtual 0.1.0-dev

## Ambiente

Validação feita no sandbox ARM64 (`aarch64`) com OpenJDK 21, Android SDK 36, Build Tools 35/36 e Gradle Wrapper 8.14.2. O sandbox tem 6 CPUs, aproximadamente 24 GB de RAM, não expõe `/dev/kvm` e não tinha dispositivo ADB conectado.

No host ARM64, o AGP fornece AAPT2 x86_64. `scripts/build-apk.sh` compila um launcher AAPT2 ARM64 e executa o utilitário de build via QEMU; isso não virtualiza o Android convidado. O script restaura `gradle.properties` ao terminar.

## Validações

Comandos executados:

```bash
python3 -m py_compile app/l10n/gen.py app/l10n/keys.py app/l10n/langs/pt-BR.py app/l10n/langs/en.py
python3 app/l10n/gen.py
scripts/check-engine-assets.sh
scripts/audit-upstream.sh
scripts/build-apk.sh \
  testStandardDebugUnitTest \
  assembleStandardDebug \
  assembleCloneDebug \
  assembleStandardRelease
```

Resultados: geração de strings concluída com **180 chaves em pt-BR**; `testStandardDebugUnitTest` terminou sem erro, mas não há testes declarados (`NO-SOURCE`); os três APKs compilaram com `BUILD SUCCESSFUL`. O aplicativo foi verificado com `applicationId` `app.zyx.virtual`, nome **ZyX Virtual** e apenas o locale `pt-BR`. O APK debug usa assinatura de desenvolvimento V2. `git diff --check` também foi executado antes de publicar o código.

## APKs

```text
app/build/outputs/apk/standard/debug/app-standard-debug.apk
app/build/outputs/apk/clone/debug/app-clone-debug.apk
app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk
```

A versão release é unsigned porque não foi fornecido um keystore de publicação. Não instalei nenhum APK nem iniciei um guest em um Galaxy A07 nesta sessão.

## Limites e avisos

O guest legado continua limitado ao backend QEMU user-mode ARM32 herdado. Nenhuma ROM LineageOS/GSI foi incluída, AVF/KVM não foi validado e o boot no aparelho real continua pendente. Consulte `docs/architecture/lineageos-gsi-status.md` antes de tentar instalar uma GSI no telefone.

Há avisos herdados de APIs Android/Compose depreciadas e o AGP avisa sobre `android:extractNativeLibs`; esse atributo é usado para extrair os executáveis do engine. Alguns ELF pré-compilados não podem passar por strip adicional.

O workflow `.github/workflows/android.yml` repete os builds em push/PR e publica os APKs como artefatos GitHub Actions por 30 dias. Não há publicação automática de releases.
