# LineageOS/GSI: situação e bloqueios para o Galaxy A07

## Resultado nesta entrega

O app **não inclui uma ROM LineageOS/GSI**. O motor atual do ZyX Virtual herda o caminho de compatibilidade do AEmulator: QEMU user-mode para sistemas ARM de 32 bits antigos. Ele não inicializa um kernel Android arm64 e não carrega uma GSI moderna como convidado. Colocar um arquivo `system.img` no APK, sem o kernel, o vendor e o backend de VM compatíveis, não produziria um sistema inicializável.

Também não removi o importador que já funciona com os firmwares antigos. Desativá-lo agora, antes de existir uma ROM ZyX testada, deixaria o app sem um sistema que consiga iniciar e daria a impressão errada de que a GSI está pronta.

## O que a documentação confirma

Verificação atualizada em **17/10/2026**, assumindo o alvo descrito no pedido original: Galaxy A07 4G. O portal oficial publica builds LineageOS 23.x para Android 16, e o catálogo da API oficial mostra as versões 22.2, 23.2 e 24.0. Pesquisei a lista de codenames dessas versões: não há `a07`, `a075` nem `sm-a075f`. Portanto, não existe um pacote de dispositivo A07 para baixar no portal oficial nesta consulta [5] [6].

A documentação oficial confirma que as aplicações Google não vêm pré-instaladas com LineageOS; são um add-on separado e opcional. Assim, “Android 16 sem GApps” é possível em uma build LineageOS compatível. O bloqueio aqui é o aparelho: a FAQ oficial diz que modelos ausentes da lista não são suportados [7] [8].

Uma imagem de outro aparelho não serviria como ROM não-GSI para o A07. Não encontrei uma build comunitária verificável e específica para `SM-A075F`/`SM-A075M` nesta busca, então não baixei um ZIP aleatório nem o coloquei no app.

A documentação do Android define GSI como uma imagem AOSP genérica destinada a aparelhos físicos compatíveis. Os requisitos publicados incluem bootloader desbloqueável, compatibilidade completa com Project Treble e arquitetura de CPU correspondente. A própria documentação alerta que instalar uma GSI em aparelho incompatível pode impedir o boot; instalação e recuperação são específicas do dispositivo [2]. Isso não torna uma GSI automaticamente um pacote de VM para o app.

O AVF usa um caminho distinto, baseado em ARM64, KVM/pKVM, kernel e processo de boot do dispositivo, além do serviço de virtualização/crosvm. A arquitetura não pode ser adicionada apenas empacotando uma ROM dentro de um APK [3].

A ficha oficial da Samsung identifica o Galaxy A07 (SM-A075F) e fornece manual do modelo em Android 16; a ficha não declara suporte a `/dev/kvm` ou AVF para apps de terceiros [4]. Neste ambiente não há Galaxy A07 conectado por ADB e o sandbox não disponibiliza `/dev/kvm`. O teste físico que confirmaria Treble, ABI, partições e virtualização ainda não foi feito.

## Requisitos para continuar sem fingir compatibilidade

1. Conectar o Galaxy A07 por ADB e coletar `ro.treble.enabled`, `ro.product.cpu.abi`, `ro.product.cpu.abilist`, `ro.boot.dynamic_partitions`, versão do kernel e existência/permissões de `/dev/kvm`.
2. Identificar o modelo exato e região (`SM-A075F`, `SM-A075M` ou outro) e confirmar método oficial/seguro de recuperação antes de qualquer teste de flash físico.
3. Escolher conscientemente entre uma GSI destinada a instalação no aparelho e um guest virtual arm64. São produtos diferentes; o segundo requer VMM/backend, kernel/ramdisk de guest, dispositivos virtuais, HALs compatíveis e um teste de boot.
4. Obter e compilar as fontes e blobs necessários respeitando licenças. Só então retirar o importador de outras ROMs e distribuir a LineageOS pré-carregada.

Não baixei nem incorporei uma imagem de terceiros porque não há compatibilidade demonstrada para este backend e para o aparelho-alvo.

## Referências

[1]: https://wiki.lineageos.org/devices/ "LineageOS Wiki — lista oficial de dispositivos"
[2]: https://developer.android.com/topic/generic-system-image "Android Developers — Generic System Images (GSIs)"
[3]: https://source.android.com/docs/core/virtualization/architecture "Android Open Source Project — AVF architecture"
[4]: https://www.samsung.com/ae/business/smartphones/galaxy-a/galaxy-a07-black-64gb-sm-a075fzkdmea/ "Samsung — Galaxy A07 SM-A075F e suporte"
[5]: https://download.lineageos.org/api/v1/devices "LineageOS — API oficial de dispositivos por versão"
[6]: https://lineageos.org/Changelog-30/ "LineageOS — Android 16 lançado como versão 23.0"
[7]: https://wiki.lineageos.org/gapps/ "LineageOS Wiki — Google Apps são add-on separado e não vêm na ROM"
[8]: https://wiki.lineageos.org/faq.html "LineageOS Wiki — dispositivo fora da lista não é suportado"
