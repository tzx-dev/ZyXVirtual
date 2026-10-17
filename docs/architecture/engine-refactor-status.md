# Estado da refatoração do motor ZyX

O objetivo é executar Android full-system ARM64, não simular uma tela. O AEmulator original executa QEMU user-mode para guests ARM32 e não contém VMM, kernel ARM64 virtual ou Android 16. A OTA física `laurel_sprout` do Mi A3 não é guest utilizável. Para avançar sem compilar a árvore LineageOS completa, foi incorporada uma base virtual upstream `virtio_arm64only` como bootstrap de engenharia.

## O que está preparado

O produto-alvo é `lineage_zyx_virtual`, baseado em LineageOS 23.2 / Android 16 para `virtio_arm64only`, sem Google Apps. O overlay ZyX existe no repositório, mas ainda não foi compilado dentro da imagem bootstrap. Foi gerado localmente um pacote `.zyxos` a partir de uma VM UTM/QEMU upstream precompilada, com dois QCOW2, manifesto, hashes por arquivo e firmware AAVMF com avisos de licença. O pacote marca `customProductOverlayApplied: false`; seu nome ZyX no manifesto não altera a identidade do Android interno.

`ZyxSystemCommand` constrói argumentos QEMU AArch64 system-mode para `virt`, TCG, CPU `max`, virtio, dois discos, UEFI pflash, VNC local e QMP. A ordem de boot prioriza explicitamente `vda`; `vdb` é secundário. O dispositivo de rede virtio continua habilitado sem Option ROM PXE. A ABI `zyx_qemu_get_api`, a bridge JNI C e o wrapper Kotlin existem, mas a bridge ainda não foi compilada com NDK nem ligada a um backend QEMU.

## Validações desta revisão

` scripts/build-apk.sh testStandardDebugUnitTest assembleStandardDebug` concluiu com sucesso usando o wrapper AAPT2/QEMU do projeto. Passaram 11 testes Kotlin (4 de pacote ZyX, 4 de comando system-mode e 3 de DeviceOtaProbe), e o APK standard debug foi gerado. O hash do APK atual está no `docs/build/build-report.md`. Os testes Python do empacotador passaram 2/2; `py_compile`, `sh -n` dos scripts e `git diff --check` também passaram.

O pacote `.zyxos` local foi relido integralmente: os oito arquivos e seus SHA-256 conferem com `ZYX-MANIFEST.json`. Seu SHA-256 externo é `9d5643729dff4bdf7ad489ec48bb7718702db49c4eab01659cae9ece9eae6247`. O arquivo de 1,1 GB está em `build/zyxos/` e é um bootstrap upstream, não o produto ZyX final.

No QEMU de host 8.2.2 com TCG e discos em snapshot, `-cpu cortex-a57` causou panic em `virt_wifi.ko`; `-cpu max` não repetiu o panic e avançou pelo kernel, `init` Android e descompressão dos APEX. Após 300 segundos não houve confirmação de zygote/framework, então esse teste não demonstra boot Android completo. Um smoke test adicional de 60 segundos usou a sintaxe exata do builder Kotlin (`-blockdev`, `bootindex`, dois discos, AAVMF e virtio): o kernel reconheceu `vda`, `vdb`, GPU e teclado sem erro de inicialização. O Galaxy A07 não foi testado.

## O que ainda impede boot pelo app

O boot continua desabilitado. Ainda faltam o adapter QEMU que implementa a ABI, o binário `aarch64-softmmu`/`libqemu-system-aarch64.so` construído para Android, integração JNI/NDK, renderer VNC/Surface e eventos de toque/teclado. O runtime precisa criar overlays graváveis para os discos de sistema/dados e uma cópia gravável das variáveis UEFI, mantendo os arquivos-base imutáveis. A imagem bootstrap ainda é upstream, não recebeu o overlay ZyX e não alcançou o framework no smoke test; também falta teste real no Galaxy A07.

A sessão sandbox não tem credenciais do Google Drive; o pacote bootstrap permanece local e não foi enviado. No app, o provedor Drive pode ser aberto pelo seletor SAF quando o usuário está autenticado no aparelho. A compilação completa da árvore LineageOS também não foi executada: este sandbox ARM64 não tem os recursos de host necessários.

## Limites do host e referências

A documentação AOSP exige host Linux x86-64, pelo menos 64 GiB de RAM efetiva e 400 GiB livres para checkout/build completo. Este sandbox é ARM64, com aproximadamente 35 GB livres e 15 GiB de RAM total (cerca de 13 GiB disponíveis na medição anterior). Veja [`qemu-system-and-build-constraints.md`](qemu-system-and-build-constraints.md) e [`../roms/lineageos-23.2-virtual-bootstrap.md`](../roms/lineageos-23.2-virtual-bootstrap.md).
