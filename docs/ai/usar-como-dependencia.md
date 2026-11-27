# Usar o Calamidade_Awakening como dependência (sem Maven)

Modelo pronto para outro repositório (outro mod NeoForge 1.21.1) compilar contra o Calamidade e carregá-lo no `runClient`, sem servidor Maven. É o mesmo esquema que o próprio Calamidade usa com a RimuLib: uma task do Gradle baixa o JAR do **release do GitHub**, confere o **SHA-256** e o coloca em `libs/`; o projeto compila com `compileOnly` e roda com `runtimeOnly`. Nada é versionado nem embutido no seu JAR.

O modelo abaixo é o projeto real [`examples/consumidor`](../../examples/consumidor): o `checkRecipes` do build do Calamidade falha se os blocos desta página divergirem dos arquivos, e o projeto compila (`./gradlew -p examples/consumidor build`).

## Resumo para quem tem pressa

1. Copie o `build.gradle` abaixo (ou só o trecho entre as linhas `----`) para o seu projeto.
2. Ponha `libs/` no `.gitignore`.
3. Declare a dependência no `neoforge.mods.toml` (bloco abaixo).
4. `./gradlew baixarDependencias` (opcional: o `build` já chama) e `./gradlew build`.
5. Quem joga instala na pasta `mods`: o seu mod, o `calamidade_awakening-0.14.0.jar` (do release v0.14.0) e a RimuLib 1.3.0 (do CurseForge). Servidor e clientes com a mesma versão do Calamidade.

## build.gradle

O trecho importante vai de `---- Calamidade_Awakening como dependência` até o bloco `dependencies`. Os SHA-256 vêm das notas do release e do arquivo `SHA256SUMS.txt` anexado a ele; o build dos releases é reproduzível (o JAR gerado localmente a partir da tag tem o mesmo hash).

<!-- arquivo: examples/consumidor/build.gradle -->
```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.148'
}

version = '1.0.0'
java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = '21.1.265'
    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
    }
    mods { consumidor { sourceSet sourceSets.main } }
}

// ---- Calamidade_Awakening como dependência, sem Maven ----------------------------------------------------------
// O JAR do Calamidade vem do release do GitHub e a RimuLib (dependência dele) do CurseForge; os dois são conferidos
// por SHA-256 e ficam em libs/ (fora do git). Nenhum dos dois entra no seu JAR: quem joga instala os dois na pasta
// mods, ao lado do seu mod. Para outra versão, troque calamidadeVersao e calamidadeSha256 pelos do release
// (o SHA-256 está nas notas do release e em SHA256SUMS.txt). -Pcalamidade.url=... troca o endereço (espelho ou
// arquivo local, por exemplo file:///caminho/calamidade_awakening-0.14.0.jar); o SHA-256 continua valendo.
def calamidadeVersao = '0.14.0'
def calamidadeSha256 = '02851af9f70ae404783bc8eaec985b065221828f0ecad8f35bd22d287bcb0cb7'
def dependenciasBaixadas = [
    [arquivo: file("libs/calamidade_awakening-${calamidadeVersao}.jar"),
     url: providers.gradleProperty('calamidade.url').getOrElse(
         "https://github.com/Juann027/Calamidade_Awakening-Mod-1.21.1/releases/download/v${calamidadeVersao}/calamidade_awakening-${calamidadeVersao}.jar"),
     sha256: calamidadeSha256],
    [arquivo: file('libs/rimulib-1.3.0-neoforge.jar'),
     url: 'https://mediafilez.forgecdn.net/files/9676/532/rimulib-1.3.0-neoforge.jar',
     sha256: '15ccd4958a0412cbcf618a10962b0b6a7e1683fd66b94d07e10bc7cabeedb774']
]
def sha256De = { File arquivo ->
    def digest = java.security.MessageDigest.getInstance('SHA-256')
    arquivo.withInputStream { input ->
        byte[] buffer = new byte[65536]
        for (int lidos = input.read(buffer); lidos > 0; lidos = input.read(buffer)) digest.update(buffer, 0, lidos)
    }
    digest.digest().encodeHex().toString()
}
def baixarDependencias = tasks.register('baixarDependencias') {
    group = 'build setup'
    description = 'Baixa o Calamidade_Awakening (release do GitHub) e a RimuLib (CurseForge) para libs/ e confere o SHA-256.'
    dependenciasBaixadas.each { outputs.file(it.arquivo) }
    doLast {
        dependenciasBaixadas.each { dep ->
            File destino = dep.arquivo
            if (!destino.exists()) {
                logger.lifecycle("Baixando ${dep.url}")
                destino.parentFile.mkdirs()
                def parcial = new File(destino.parentFile, destino.name + '.part')
                new URI(dep.url).toURL().withInputStream { input -> parcial.withOutputStream { it << input } }
                def baixado = sha256De(parcial)
                if (baixado != dep.sha256) {
                    parcial.delete()
                    throw new GradleException("SHA-256 de ${destino.name} não confere: ${baixado} (esperado ${dep.sha256})")
                }
                if (!parcial.renameTo(destino)) throw new GradleException("Não foi possível gravar ${destino}")
            }
            def atual = sha256De(destino)
            if (atual != dep.sha256)
                throw new GradleException("SHA-256 de ${destino} não confere: ${atual} (esperado ${dep.sha256}). Apague o arquivo e rode de novo.")
        }
    }
}
def jarsBaixados = files(dependenciasBaixadas.collect { it.arquivo }).builtBy(baixarDependencias)
dependencies {
    compileOnly jarsBaixados   // compila contra a API do Calamidade (e os tipos da RimuLib que ela expõe), sem embutir
    runtimeOnly jarsBaixados   // e carrega os dois mods no runClient e no runServer
}
```

Por que cada parte:

- **`compileOnly`**: o seu código compila contra a API (`dev.calamidade_awakening.api.v1`) sem copiar o Calamidade para dentro do seu JAR.
- **`runtimeOnly`**: no `runClient`/`runServer` o NeoForge encontra os dois JARs no classpath e os carrega como mods (o Calamidade e a RimuLib, de que ele depende).
- **RimuLib também**: o Calamidade precisa dela em tempo de execução, e algumas assinaturas da API (`AddonLayers.Post`, `AddonLayers.film`) usam tipos dela; com ela no `compileOnly` o compilador resolve tudo.
- **`.part` e SHA-256**: um download interrompido ou adulterado nunca fica em `libs/`; um arquivo existente com hash diferente para o build com uma mensagem clara.
- **`-Pcalamidade.url=...`**: troca o endereço (espelho, ou `file:///caminho/calamidade_awakening-0.14.0.jar` para testar um build local antes do release). O SHA-256 continua valendo, então o arquivo precisa ser o mesmo.

## neoforge.mods.toml

<!-- arquivo: examples/consumidor/src/main/resources/META-INF/neoforge.mods.toml -->
```toml
modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="consumidor"
version="1.0.0"
displayName="Consumidor do Calamidade"
description="Exemplo mínimo de um mod que depende do Calamidade_Awakening sem Maven."

# Obrigatório: o jogo recusa carregar sem o Calamidade. Para integração opcional use type="optional" e isole o código
# que chama o Calamidade (veja docs/ai/usar-como-dependencia.md).
[[dependencies.consumidor]]
modId="calamidade_awakening"
type="required"
versionRange="[0.14.0,0.15.0)"
ordering="AFTER"
side="BOTH"

[[dependencies.consumidor]]
modId="neoforge"
type="required"
versionRange="[21.1.265,21.2)"
ordering="NONE"
side="BOTH"
```

A faixa `[0.14.0,0.15.0)` aceita as correções da 0.14 e recusa a 0.15, que pode mudar a rede. A API (`api.v1`) só cresce: código escrito para a 0.10, a 0.11, a 0.12, a 0.13 ou a 0.14 compila nas versões seguintes; o que muda entre versões menores é o protocolo de rede, por isso servidor e clientes precisam da mesma versão.

## Código mínimo

<!-- arquivo: examples/consumidor/src/main/java/example/consumidor/Consumidor.java -->
```java
package example.consumidor;

import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.EffectTriggers;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Mod mínimo que usa o Calamidade_Awakening como biblioteca (docs/ai/usar-como-dependencia.md): registra um efeito
 * no construtor e o dispara, preso ao jogador e virado para o alvo, a cada golpe que o jogador acerta.
 */
@Mod("consumidor")
public final class Consumidor {
    static final ResourceLocation GOLPE = ResourceLocation.fromNamespaceAndPath("consumidor", "golpe");

    /** Feixe curto do peito do jogador para a frente, na direção do alvo; recarga de 10 ticks. */
    static final EffectTriggers.EntityTrigger GATILHO = EffectTriggers.entity(GOLPE).offset(0, 1.2, .4).cooldown(10);

    public Consumidor() {
        Effects.register(GOLPE, new Effects.Definition(20, 3, 0xffff5020, Effects.Shape.NONE, Effects.Material.NONE,
            null, null));
        Beam feixe = Beam.builder(Point.center(), Point.facing(0, 0, 3)).width(.1f, .7f).color(0xffffffff, 0xffff5020).build();
        Effects.beams(GOLPE, feixe);
        Effects.lights(GOLPE, Light.along(feixe).radius(3).intensity(1.5f).build());
        NeoForge.EVENT_BUS.addListener(Consumidor::golpe);
    }

    private static void golpe(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof Player jogador && event.getEntity() instanceof LivingEntity alvo && alvo != jogador)
            GATILHO.fire(jogador, alvo);
    }
}
```

Registre efeitos no construtor do mod (lado comum, para o servidor dedicado também conhecer o efeito); camadas de cliente (`AddonLayers`) vão no `FMLClientSetupEvent`. A ordem e o resto da API estão em [AGENTS.md](../../AGENTS.md) e na [referência](API-REFERENCIA.md).

## Versões do NeoForge

- O Calamidade 0.11.0 declara `neoforge [21.1,21.2)` e `minecraft [1.21.1]`. Os releases são compilados com a **21.1.267**.
- A 0.10.0 e a 0.11.0 foram **conferidas com a 21.1.265** (a do RespectCraft): o motor e o vfx-sample compilam contra ela, o `checkBudget` passa (`./gradlew build -Pneoforge.version=21.1.265`) e o JAR sai byte a byte igual; o roteiro em jogo da 0.10.0 também rodou nela, o da 0.11.0 só na 21.1.267 (veja [VALIDACAO.md](../VALIDACAO.md)).
- A RimuLib 1.3.0 declara `neoforge [21.1,)`.
- O seu projeto pode usar qualquer 21.1.x a partir da 21.1.265 no `neoForge { version = ... }`.

## Integração opcional (o seu mod funciona sem o Calamidade)

Com `type="optional"` no `neoforge.mods.toml`, o jogo carrega o seu mod mesmo sem o Calamidade. Então nenhuma classe que você carrega sempre pode citar tipos do Calamidade: a JVM resolve os tipos ao carregar a classe e quebraria com `NoClassDefFoundError`.

```java
// Sempre carregada: só pergunta se o mod existe.
public final class Efeitos {
    private static final boolean TEM_CALAMIDADE = net.neoforged.fml.ModList.get().isLoaded("calamidade_awakening");
    public static void registrar() { if (TEM_CALAMIDADE) EfeitosCalamidade.registrar(); }
    public static void golpe(LivingEntity atacante, LivingEntity alvo, int forca) {
        if (TEM_CALAMIDADE) EfeitosCalamidade.golpe(atacante, alvo, forca);
    }
}
// Só carregada quando o Calamidade existe: aqui pode usar Effects, Placement, EffectTriggers...
final class EfeitosCalamidade { ... }
```

Chame `Efeitos.registrar()` no construtor do mod. No `build.gradle` nada muda.

## Distribuição e licenças

- **Não embuta** (nada de `jarJar` nem de copiar classes): a RimuLib não permite redistribuição, e o Calamidade precisa estar uma vez só na pasta `mods` para servidor e clientes concordarem na versão.
- Na página do seu mod, liste as duas dependências com os links: o release do Calamidade no GitHub e a RimuLib 1.3.0 no CurseForge.

## Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `SHA-256 de ... não confere` | Versão e hash não batem, ou o download veio errado. Confira os dois no release; apague o arquivo de `libs/` e rode de novo. |
| `Mod calamidade_awakening requires rimulib` no `runClient` | A RimuLib não está no `runtimeOnly`. |
| Efeitos não aparecem num servidor | O servidor e os clientes precisam do Calamidade na mesma versão (desde a 0.10.0 o pacote de início é `event_v2`, a 0.11.0 criou o `terrain_v1` a 0.12.0 o `world_event_v1`, o `world_end_v1` e o `sky_profile_v1` e a 0.13.0 o `boss_v1`; a 0.14.0 não criou canal, mas exige a mesma versão do motor nos dois lados; versões diferentes simplesmente não trocam efeitos ou o terreno). |
| `Unknown effect` | O efeito não foi registrado no construtor do mod, ou foi registrado só no cliente. |
| `NoClassDefFoundError: dev/calamidade_awakening/...` com dependência opcional | Uma classe sempre carregada cita tipos do Calamidade; isole como na seção acima. |

## Checklist para agentes

- [ ] `libs/` no `.gitignore`; nenhum JAR de terceiros no git.
- [ ] Versão e SHA-256 do Calamidade copiados do mesmo release.
- [ ] `compileOnly` e `runtimeOnly` com os dois JARs (Calamidade e RimuLib).
- [ ] `neoforge.mods.toml` com `calamidade_awakening` em `[0.14.0,0.15.0)` (`required` ou `optional`).
- [ ] Efeitos registrados no construtor (lado comum); `./gradlew build` e um `runClient` com o efeito disparando.
