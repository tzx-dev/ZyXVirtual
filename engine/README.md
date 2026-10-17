# ZyX Virtual engine

Este diretório descreve o backend que o APK realmente empacota. A primeira etapa preserva o motor funcional do upstream, mas passa a expor suas capacidades de forma explícita no código e nos diagnósticos.

## Backend atual

- **VMM:** QEMU user-mode modificado, executado como processo Android.
- **Guest ABI validada:** ARM32 (`armeabi`/`armeabi-v7a`).
- **Kernel guest:** não é virtualizado; syscalls, Binder, propriedades, entrada, áudio e GL são adaptados pelo conjunto de daemons/shims.
- **Gráficos:** ponte GL para a GPU do host e framebuffer compartilhado.
- **Persistência:** árvore de firmware e `/data` vivem no diretório privado de cada imagem; o cartão compartilhado usa `ZyXVirtual/` com fallback de leitura do diretório legado `AEmulator/`.
- **Bibliotecas:** os ELF do motor continuam em `app/src/main/jniLibs/arm64-v8a/` e os shims guest em `app/src/main/assets/engines/`.

A presença de `/dev/kvm` no host é mostrada como diagnóstico, mas **não transforma o backend atual em uma VM KVM**. A segunda etapa deve introduzir um backend separado, preferencialmente baseado no AVF/crosvm quando o aparelho expuser os pré-requisitos no bootloader, kernel e serviço de virtualização.

## Manifesto do backend

`manifest/zyx-engine.json` é a fonte legível por ferramentas e documentação. `scripts/check-engine-assets.sh` verifica se os artefatos binários necessários continuam presentes.

## Regra de honestidade

Não coloque uma imagem LineageOS/AOSP moderna neste backend alegando que ela está virtualizada. O contrato atual aceita apenas guests legados validados. O scaffolding em `guest/zyx-os/` é uma sobreposição de produto para a futura árvore AOSP/LineageOS; não é uma cópia completa desses repositórios.
