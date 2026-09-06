# Postador Mobile

Aplicativo Android auxiliar do Postador Mobile.

Esta primeira versão recebe uma ordem via Firebase Cloud Messaging (FCM), mostra uma notificação e, após um toque do usuário, abre o compartilhamento nativo do Android com texto e mídia preparados.

## Build automático

O GitHub Actions gera um APK de teste automaticamente a cada atualização da branch `main` e também permite execução manual pela aba **Actions**.

> Não coloque chaves privadas de conta de serviço neste repositório. O arquivo `google-services.json` é apenas a configuração cliente do app Android.
