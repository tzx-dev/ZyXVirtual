# ZyX OS — base virtual LineageOS 23.2 (bootstrap)

## Estado e ressalvas

Foi preparado um pacote `.zyxos` de bootstrap para a etapa de integração do ZyX Virtual. Ele contém uma VM virtual ARM64 precompilada de LineageOS 23.2 / Android 16, mais firmware UEFI AAVMF. **Não é ainda um build final da ZyX OS:** a imagem interna continua sendo o produto upstream `lineage_virtio_arm64only`; o overlay `lineage_zyx_virtual` ainda não foi compilado ou aplicado dentro do guest. O nome ZyX e a identidade no manifesto identificam o pacote de desenvolvimento, não alteram o `build.prop`/framework da imagem upstream.

O pacote local é `build/zyxos/ZyXOS-lineage-23.2-20260917-bootstrap-aavmf.zyxos` (SHA-256 `9d5643729dff4bdf7ad489ec48bb7718702db49c4eab01659cae9ece9eae6247`). Os oito arquivos declarados no manifesto foram lidos e verificados individualmente contra seus hashes. A estrutura não foi instalada ainda pelo app Android nem enviada ao Drive.

O boot permanece desabilitado no app. O smoke test abaixo foi feito no QEMU de host do sandbox, não no APK nem no Galaxy A07. Ele chegou ao `init` do Android e à descompressão de APEX, mas ainda não confirmou zygote, framework ou interface Android completa.

## Origem da imagem

- Repositório do build: <https://github.com/jqssun/android-lineage-qemu>
- Release: `v2026.09.17`
- Asset: `UTM-VM-lineage-23.2-20260917-jqssun-virtio_arm64only.zip`
- URL: <https://github.com/jqssun/android-lineage-qemu/releases/download/v2026.09.17/UTM-VM-lineage-23.2-20260917-jqssun-virtio_arm64only.zip>
- SHA-256 do asset upstream: `0a50afc821d905848b4560c33a5a706e6aee5ac414f85b31f9e52fc1ffabbd61`
- A atestação SLSA do GitHub foi verificada com `gh attestation verify`; o repositório e workflow assinante coincidem com `jqssun/android-lineage-qemu` e `.github/workflows/build.yml@refs/heads/main`.
- Produto e versão declarados no bundle: `lineage_virtio_arm64only`, LineageOS 23.2 / Android 16, sem GApps.

A OTA física do Mi A3 não foi usada. O asset é um bundle de VM UTM/QEMU para `aarch64`, alvo `virt`, e por isso é uma base virtual adequada para adaptação, diferentemente da OTA `laurel_sprout` de telefone.

## Conteúdo extraído e topologia

O bundle UTM original contém `config.plist`, `efi_vars.fd` e dois discos QCOW2. O `config.plist` declara arquitetura `aarch64`, machine `virt`, 2 CPUs, 2048 MiB de RAM, UEFI e discos virtio `vda`/`vdb`.

- `vda.qcow2`: arquivo de 2.259.877.888 bytes; capacidade virtual de 5 GiB.
- `vdb.qcow2`: arquivo de 196.864 bytes; capacidade virtual de 16 GiB.
- `efi_vars.fd` do bundle UTM: 334.848 bytes.
- O disco principal e o disco de dados devem ser expostos como dispositivos virtio distintos. Nenhuma imagem-base deve ser alterada pelo runtime.

O construtor `ZyxSystemCommand` foi ajustado para usar CPU virtual `max`, montar o segundo disco, aceitar o par UEFI pflash code/vars e desabilitar apenas a Option ROM PXE do virtio-net (`romfile=`), que não é necessária ao boot pelo disco. A rede virtio continua habilitada.

## Firmware incluído

O app/QEMU system-mode precisa de uma imagem de firmware UEFI code; ela não vinha dentro do bundle UTM original. Foi incluído no `.zyxos` o firmware de VM AAVMF do pacote Ubuntu `qemu-efi-aarch64` versão `2024.02-2ubuntu0.10`:

- `ZyXFirmware/AAVMF_CODE.no-secboot.fd` — code pflash, somente leitura.
- `ZyXFirmware/AAVMF_VARS.fd` — template de variáveis; o runtime deve copiá-lo para um arquivo gravável novo antes de iniciar a VM.
- `ZyXFirmware/EDK2-COPYRIGHT` — avisos e licenças do EDK2/terceiros.
- SHA-256 do `.deb` Ubuntu: `fad8e80e929cd00dae8c5c8e5dd3ec85f37d9a832eea776957e5e2cf86ef2ab5`.
- O pacote declara EDK2 sob BSD-2-Clause-Patent, além dos avisos de terceiros reproduzidos no arquivo de copyright.

A cópia `AAVMF_VARS.fd` incluída é template imutável. Usar o template diretamente como NVRAM gravável pode corromper a instalação do pacote; sempre provisionar uma cópia no estado privado da VM. Este firmware não é o binário QEMU nem a bridge JNI.

## Prova de inicialização no sandbox

O teste usou QEMU AArch64 8.2.2 com TCG, 2 vCPU, 2 GiB de RAM, UEFI AAVMF e `-snapshot` nos dois QCOW2; o template NVRAM foi copiado para a pasta temporária do teste. Os discos originais e o pacote upstream permaneceram intactos.

- Com `-cpu cortex-a57`, o kernel 6.12.81 chegou ao Android `init`, mas sofreu panic ao carregar `virt_wifi.ko`, em `__pi_scs_handle_fde_frame`.
- Com `-cpu max`, o panic não ocorreu; o guest passou pelo kernel/init, montou as partições e iniciou a varredura/descompressão dos APEX do Android. Após 300 segundos de TCG ainda não havia marcador de início do zygote/framework, então o teste foi encerrado sem alegar boot completo.
- O QEMU de host falhou inicialmente porque faltava `efi-virtio.rom`; zerar a propriedade `romfile` do dispositivo virtio-net resolveu a dependência sem remover a rede. Essa opção foi incorporada ao builder Kotlin.

Este resultado demonstra progresso real de boot do guest em QEMU, mas não prova que o APK já consiga inicializá-lo. O aparelho Galaxy A07 ainda não foi testado.

## Bloqueios que continuam

1. Compilar e integrar o QEMU `aarch64-softmmu` para Android, mais o adapter que implemente a ABI `zyx_qemu_get_api` consumida pela JNI.
2. Provisionar cópias graváveis/overlays persistentes para `vda`, `vdb` e NVRAM, mantendo os arquivos verificados do pacote como base imutável.
3. Integrar display VNC/Surface, vídeo, toque e entrada ao app.
4. Fazer o produto/overlay ZyX ser aplicado no build guest — o bootstrap atual não recebeu esse overlay.
5. Repetir o boot até o framework e validar no Galaxy A07; só então retirar o estado “boot desabilitado”.

A compilação completa da árvore LineageOS não foi executada nesta etapa. O pacote é um bootstrap de engenharia e não deve ser publicado como ROM final.
