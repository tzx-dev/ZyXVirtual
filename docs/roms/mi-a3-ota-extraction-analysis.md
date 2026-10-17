# Inspeção da OTA LineageOS 23.2 — Xiaomi Mi A3

Verificação em 17/10/2026. A OTA oficial `lineage-23.2-20261006-nightly-laurel_sprout-signed.zip` tem 1.169.332.586 bytes e SHA-256 `f9b5e939b639a209c3d85b6f0870752d906226967043660ada33b80dae28a861`, correspondente ao catálogo oficial da LineageOS. O pacote está destinado a `laurel_sprout`, tem `ota-type=AB` e `post-sdk-level=36` (Android 16). Fontes: [catálogo oficial da build](https://download.lineageos.org/api/v2/devices/laurel_sprout/builds) e [download oficial](https://mirrorbits.lineageos.org/full/laurel_sprout/20261006/lineage-23.2-20261006-nightly-laurel_sprout-signed.zip).

## Extração realizada

O ZIP externo passou pelo teste de CRC. Seus sete itens incluem `META-INF/com/android/metadata`, `payload.bin`, `payload_properties.txt`, `apex_info.pb` e `care_map.pb`. O payload tem versão 2, manifesto de 124.382 bytes e assinatura de metadados de 267 bytes. O formato A/B guarda no manifesto as operações e partições a atualizar, conforme a [documentação AOSP de OTA A/B](https://source.android.com/docs/core/ota/ab).

A lista do payload contém `abl`, `bluetooth`, `boot`, `cmnlib`, `cmnlib64`, `devcfg`, `dsp`, `dtbo`, `hyp`, `imagefv`, `keymaster`, `modem`, `qupfw`, `rpm`, `storsec`, `system`, `tz`, `uefisecapp`, `vbmeta`, `vendor`, `xbl` e `xbl_config`. Para inspeção foram extraídas somente `system.img` (3.221.225.472 bytes), `vendor.img` (1.073.741.824 bytes), `boot.img` (67.108.864 bytes), `dtbo.img` (8.388.608 bytes) e `vbmeta.img` (4.096 bytes). Nenhuma imagem de rádio, modem ou bootloader foi extraída.

A extração foi feita com `payload-dumper-go` 2.1.0, cuja documentação descreve suporte a OTA ZIP e `payload.bin` e verificação dos hashes de operação e das imagens. O arquivo de imagens e o ZIP original permanecem apenas no workspace local, em `build/rom-inspection/mi-a3-20261006/`; não são adicionados ao Git e não formam um pacote `.zyxos`.

## Evidências de dependência do telefone

O metadado identifica `pre-device=laurel_sprout`. O `system.img` informa `ro.build.version.sdk=36`, mas também contém o fingerprint `Xiaomi/laurel_sprout/laurel_sprout:11/RKQ1.200903.002/V12.0.26.0.RFQMIXM:user/release-keys`. Esse fingerprint isolado não muda o nível SDK 36 publicado para a build; não se deve inferir a versão do sistema somente pelo texto do fingerprint.

A imagem `vendor.img` declara `ro.board.platform=trinket` e inclui HALs Qualcomm, entre eles `hwcomposer.qcom.so`, `gralloc.qcom.so` e bibliotecas `vendor.qti.*`. A `boot.img` usa argumentos de inicialização `androidboot.hardware=qcom`, `androidboot.console=ttyMSM0` e `video=vfb`. Essas referências correspondem à plataforma física do Mi A3, não à máquina virtual genérica QEMU `virt` com dispositivos virtio.

## Consequência para a ZyX OS

A extração permite inspecionar a OTA, mas não transforma as partições do telefone em um guest Android ARM64. Trocar nomes de produto ou inserir essas imagens em `.zyxos` não substitui o kernel, device tree, VINTF/HALs e configuração de partições esperados pelo alvo `virtio_arm64only`; o resultado não foi testado e não deve ser apresentado como inicializável.

Com base nisso, `scripts/package-zyx-guest.py` agora detecta o par `META-INF/com/android/metadata` + `payload.bin` e rejeita explicitamente OTA de telefone como fonte de `.zyxos`. Dois testes unitários cobrem a rejeição da OTA física e a aceitação de um bundle virtual UTM. A tentativa de empacotar o ZIP real do Mi A3 foi recusada antes de criar qualquer arquivo.

A adaptação correta continua sendo o produto virtual `lineage_zyx_virtual`, herdado do device tree `virtio_arm64only`. A OTA do Mi A3 serve agora como referência comparativa para reconhecer componentes físicos a excluir. Nesta etapa não foram modificadas as imagens extraídas, criado um `.zyxos`, iniciado um build LineageOS ou habilitado o boot no app.

## Referência da ferramenta

[Repositório `payload-dumper-go`](https://github.com/ssut/payload-dumper-go) — projeto Apache-2.0; versão 2.1.0 usada para listar e extrair partições do payload.
