# Native layer

As fontes desta pasta são as partes nativas reproduzíveis encontradas no upstream: host JNI, áudio, ponte GL e shims guest. Os executáveis QEMU/Binder/GL arm64 que o app empacota não são gerados por estes scripts neste clone; eles permanecem em `app/src/main/jniLibs/arm64-v8a/` como artefatos upstream.

Antes de publicar um APK, rode:

```bash
scripts/check-engine-assets.sh
```

Não copie blobs de firmware ou bibliotecas proprietárias de um Galaxy A07 para esta pasta. O guest ZyX OS futuro deve usar interfaces virtuais e componentes cuja licença e origem estejam registradas.
