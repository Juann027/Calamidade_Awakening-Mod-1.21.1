# Dependência de desenvolvimento

A RimuLib **não** é versionada neste repositório nem anexada aos releases. O build a baixa do arquivo oficial no CurseForge e confere o SHA-256 antes de usar:

| Item | Valor |
|---|---|
| Arquivo | `rimulib-1.3.0-neoforge.jar` |
| Página | [RimuLib 1.3.0 NeoForge no CurseForge](https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532) |
| Download direto | `https://mediafilez.forgecdn.net/files/9676/532/rimulib-1.3.0-neoforge.jar` |
| SHA-256 | `15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774` |

- `./gradlew build` (ou qualquer task que compile) chama a task `rimulib`, que baixa o arquivo para esta pasta quando ele falta e falha se o SHA-256 não bater. URL e hash ficam em `gradle.properties` (`rimulib.url`, `rimulib.sha256`).
- Sem rede, baixe o arquivo pela página acima e coloque-o aqui com esse nome; o build confere o hash do mesmo jeito.
- `libs/*.jar` está no `.gitignore`. Não versione nem anexe este JAR: quem joga instala a RimuLib pelo CurseForge, na pasta mods do cliente e do servidor.
- O workflow de release baixa o mesmo arquivo, confere o hash e publica só os JARs do Calamidade e do exemplo.
