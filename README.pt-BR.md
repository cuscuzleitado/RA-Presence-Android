# RA Presence (Android)

App Android que mostra no Discord o jogo que você está jogando no [RetroAchievements](https://retroachievements.org), direto do celular, sem precisar de PC.

> Sem afiliação com o RetroAchievements nem com o Discord.

## Como funciona

1. Você informa seu usuário do RA e a Web API Key (retroachievements.org > Settings > Keys). Ficam só no seu aparelho.
2. Você entra no Discord dentro do app.
3. Um serviço em primeiro plano consulta o `API_GetUserSummary` a cada 30 segundos e envia título do jogo, ícone, texto de Rich Presence do RA e tempo de jogo ao Discord pelo Gateway.

Só o Application ID do Discord vem embutido (`Config.kt`).

## Importante

O app define o status usando o **token da sua conta**, como o Kizzy. Isso vai contra os Termos de Serviço do Discord e pode limitar ou banir a conta. Use por sua conta e risco, de preferência com uma conta secundária. O token fica só no armazenamento privado do app e é enviado apenas ao Discord.

## Compilar

- **Android Studio:** abra esta pasta, aguarde o sync e use Build > Build APK(s).
- **GitHub Actions:** dê push na `main`; o APK aparece como artefato do workflow "Build APK" (`app-debug.apk`).

Precisa de Android 8.0+ (API 26). Para instalar, permita apps de fontes desconhecidas.

## Dicas

- Permita notificações (Android 13+) e deixe a bateria do app como "Sem restrições" para o serviço não ser encerrado.
- O RA atualiza a cada alguns minutos, então há um pequeno atraso ao abrir ou fechar um jogo.
- Se o login do Discord não pegar a sessão, cole o token no campo manual.
