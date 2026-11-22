#!/usr/bin/env node
// Testes da biblioteca calamidade_fx.glsl executados numa GPU de verdade (WebGL2 no Chromium headless, que roda
// GLSL ES 3.00 via SwiftShader). Cada caso é uma expressão GLSL que vira um pixel de uma textura RGBA32F; o
// script lê os valores e compara com o esperado.
//
// Requer Node e Playwright com Chromium (require('playwright')). Uso, na raiz do repositório:
//     node scripts/testar_glsl.cjs
// Sai com código 1 se algum caso falhar. O jogo compila a mesma biblioteca em GLSL 1.50 (checkShaders no Gradle).
'use strict';
const fs = require('fs');
const path = require('path');

const LIB = path.join(__dirname, '..', 'src/main/resources/assets/calamidade_awakening/shaders/fx/calamidade_fx.glsl');
// 0.11.0: ruído e formas da Terrain API, iguais ao Java (o passe terrain do motor inclui este arquivo).
const TERRAIN = path.join(__dirname, '..', 'src/main/resources/assets/calamidade_awakening/shaders/fx/calamidade_terrain.glsl');

// [nome, expressão GLSL float, esperado, tolerância]
const S3 = Math.sqrt(3);
const CASES = [
  // caRayBox
  ['caixa: entrada de fora', 'caRayBox(vec3(-5.0, 0.5, 0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).x', 5, 1e-5],
  ['caixa: saída de fora', 'caRayBox(vec3(-5.0, 0.5, 0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).y', 6, 1e-5],
  ['caixa: erra', 'caRayBox(vec3(-5.0, 2.0, 0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).x', -1, 0],
  ['caixa: dentro, entrada 0', 'caRayBox(vec3(0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).x', 0, 0],
  ['caixa: dentro, saída', 'caRayBox(vec3(0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).y', 0.5, 1e-5],
  ['caixa: atrás', 'caRayBox(vec3(5.0, 0.5, 0.5), vec3(1.0, 0.0, 0.0), vec3(0.0), vec3(1.0)).x', -1, 0],
  ['caixa: diagonal entrada', 'caRayBox(vec3(-1.0), normalize(vec3(1.0)), vec3(0.0), vec3(1.0)).x', S3, 1e-4],
  ['caixa: diagonal saída', 'caRayBox(vec3(-1.0), normalize(vec3(1.0)), vec3(0.0), vec3(1.0)).y', 2 * S3, 1e-4],
  ['caixa: direção negativa', 'caRayBox(vec3(0.5, 0.5, 9.0), vec3(0.0, 0.0, -1.0), vec3(0.0), vec3(1.0)).x', 8, 1e-5],
  ['caixa: normal +x', 'caBoxNormal(vec3(1.0, 0.4, 0.6), vec3(0.0), vec3(1.0)).x', 1, 0],
  ['caixa: normal -y', 'caBoxNormal(vec3(0.3, 0.0, 0.6), vec3(0.0), vec3(1.0)).y', -1, 0],
  // caRaySphere
  ['esfera: entrada', 'caRaySphere(vec3(0.0, 0.0, -5.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 1.0).x', 4, 1e-5],
  ['esfera: saída', 'caRaySphere(vec3(0.0, 0.0, -5.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 1.0).y', 6, 1e-5],
  ['esfera: dentro', 'caRaySphere(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 2.0).x', 0, 0],
  ['esfera: dentro, saída', 'caRaySphere(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 2.0).y', 2, 1e-5],
  ['esfera: erra', 'caRaySphere(vec3(3.0, 0.0, -5.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 1.0).x', -1, 0],
  ['esfera: atrás', 'caRaySphere(vec3(0.0, 0.0, 5.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 1.0).x', -1, 0],
  ['esfera: tangente', 'caRaySphere(vec3(1.0, 0.0, -5.0), vec3(0.0, 0.0, 1.0), vec3(0.0), 1.0).x', 5, 1e-3],
  // caSegmentGlow (segmento vertical em z = 5)
  ['feixe: núcleo no eixo', 'caSegmentGlow(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).x', 1, 1e-4],
  ['feixe: halo no eixo', 'caSegmentGlow(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).y', 1, 1e-4],
  ['feixe: núcleo fora', 'caSegmentGlow(vec3(0.3, 0.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).x', 0, 0],
  ['feixe: halo a 0,3', 'caSegmentGlow(vec3(0.3, 0.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).y', 0.25 / 0.34, 1e-4],
  ['feixe: atrás do terreno', 'caSegmentGlow(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 4.0).y', 0, 0],
  ['feixe: largura mínima longe', 'caSegmentGlow(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 1000.0), vec3(0.0, 1.0, 1000.0), 0.1, 0.5, 0.001, 2000.0).x', 0.1, 1e-3],
  ['feixe: além da ponta', 'caSegmentGlow(vec3(0.0, 3.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).x', 0, 0],
  ['feixe: halo além da ponta', 'caSegmentGlow(vec3(0.0, 3.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, -1.0, 5.0), vec3(0.0, 1.0, 5.0), 0.1, 0.5, 0.001, 100.0).y', 0.25 / 4.25, 1e-4],
  // grades
  ['grade 2D: sobre a linha', 'caGrid2(vec2(2.0, 0.7), 1.0, 0.05, 0.001)', 1, 1e-4],
  ['grade 2D: meio da célula', 'caGrid2(vec2(2.5, 0.5), 1.0, 0.05, 0.001)', 0, 0],
  ['grade 2D: some sem moiré', 'caGrid2(vec2(2.0, 0.7), 1.0, 0.05, 0.6)', 0, 0],
  ['grade 2D: linha fina vira 1 px', 'caGrid2(vec2(2.0, 0.7), 1.0, 0.01, 0.02)', 0.5, 1e-4],
  ['grade mundo: face +x usa yz', 'caWorldGrid(vec3(0.0, 2.5, 3.0), vec3(1.0, 0.0, 0.0), 1.0, 0.05, 0.001)', 1, 1e-4],
  ['grade mundo: face +x não acende inteira', 'caWorldGrid(vec3(4.0, 2.5, 3.5), vec3(1.0, 0.0, 0.0), 1.0, 0.05, 0.001)', 0, 0],
  ['grade mundo: chão usa xz', 'caWorldGrid(vec3(1.5, 7.0, 3.5), vec3(0.0, 1.0, 0.0), 1.0, 0.05, 0.001)', 0, 0],
  // cor
  ['arco-íris 0: vermelho r', 'caRainbow(0.0).r', 1, 1e-5],
  ['arco-íris 0: vermelho g', 'caRainbow(0.0).g', 0, 1e-5],
  ['arco-íris 1/3: verde', 'caRainbow(1.0 / 3.0).g', 1, 1e-4],
  ['arco-íris 1/3: sem vermelho', 'caRainbow(1.0 / 3.0).r', 0, 1e-4],
  ['arco-íris 2/3: azul', 'caRainbow(2.0 / 3.0).b', 1, 1e-4],
  ['arco-íris 1/6: amarelo', 'caRainbow(1.0 / 6.0).r + caRainbow(1.0 / 6.0).g', 2, 1e-4],
  ['arco-íris repete', 'caRainbow(1.25).b - caRainbow(0.25).b', 0, 1e-4],
  ['luma do branco', 'caLuma(vec3(1.0))', 1, 1e-5],
  // tempo
  ['envelope antes', 'caEnvelope(-1.0, 0.0, 10.0, 20.0, 30.0)', 0, 0],
  ['envelope subida', 'caEnvelope(5.0, 0.0, 10.0, 20.0, 30.0)', 0.5, 1e-5],
  ['envelope platô', 'caEnvelope(15.0, 0.0, 10.0, 20.0, 30.0)', 1, 0],
  ['envelope queda', 'caEnvelope(25.0, 0.0, 10.0, 20.0, 30.0)', 0.5, 1e-5],
  ['envelope depois', 'caEnvelope(31.0, 0.0, 10.0, 20.0, 30.0)', 0, 0],
  ['envelope degrau na entrada', 'caEnvelope(10.0, 10.0, 10.0, 20.0, 30.0)', 1, 0],
  ['envelope degrau na saída', 'caEnvelope(20.0, 0.0, 10.0, 20.0, 20.0)', 0, 0],
  ['onda: na frente', 'caDistanceWave(7.0, 20.0, 0.35, 0.6, 24.0)', 1, 1e-5],
  ['onda: uma largura atrás', 'caDistanceWave(6.4, 20.0, 0.35, 0.6, 24.0)', Math.exp(-1), 1e-4],
  ['onda: antes de emitir', 'caDistanceWave(0.0, -1.0, 0.35, 0.6, 24.0)', 0, 0],
  ['onda: depois do alcance', 'caDistanceWave(30.0, 100.0, 0.3, 0.6, 24.0)', 0, 0],
  // feixes (versão 2)
  ['segmento: t no meio', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(1.0, -1.0, 5.0), vec3(1.0, 1.0, 5.0)).x', 5, 1e-5],
  ['segmento: s no meio', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(1.0, -1.0, 5.0), vec3(1.0, 1.0, 5.0)).y', 0.5, 1e-5],
  ['segmento: distância', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(1.0, -1.0, 5.0), vec3(1.0, 1.0, 5.0)).z', 1, 1e-5],
  ['segmento: além da ponta usa A', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 2.0, 5.0), vec3(0.0, 4.0, 5.0)).z', 2, 1e-5],
  ['segmento: além da ponta s = 0', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 2.0, 5.0), vec3(0.0, 4.0, 5.0)).y', 0, 1e-6],
  ['segmento: paralelo', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 2.0), vec3(1.0, 0.0, 6.0)).z', 1, 1e-5],
  ['segmento: paralelo, t', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 2.0), vec3(1.0, 0.0, 6.0)).x', 2, 1e-5],
  ['segmento: atrás da câmera', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, -5.0), vec3(0.0, -1.0, -5.0)).z', 5, 1e-5],
  ['segmento: atrás, t = 0', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, -5.0), vec3(0.0, -1.0, -5.0)).x', 0, 0],
  ['segmento: degenerado', 'caRaySegment(vec3(0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 3.0, 4.0), vec3(0.0, 3.0, 4.0)).z', 3, 1e-5],
  ['núcleo: no eixo', 'caBeamCore(0.0, 0.1, 0.001)', 1, 1e-5],
  ['núcleo: fora', 'caBeamCore(0.3, 0.1, 0.001)', 0, 0],
  ['núcleo: largura mínima de um pixel', 'caBeamCore(0.0, 0.1, 1.0)', 0.1, 1e-5],
  ['núcleo: largura 0', 'caBeamCore(0.0, 0.0, 0.001)', 0, 0],
  ['halo: no eixo', 'caBeamHalo(0.0, 0.5, 0.001)', 1, 1e-5],
  ['halo: na meia-largura', 'caBeamHalo(0.5, 0.5, 0.001)', 0.5, 1e-5],
  ['halo: a 2 meias-larguras', 'caBeamHalo(1.0, 0.5, 0.001)', 0.2, 1e-5],
  ['halo: cortado a 5 meias-larguras', 'caBeamHalo(3.0, 0.5, 0.001)', 0, 0],
  ['pulso: no centro', 'caPulseTrain(5.0, 10.0, 2.0)', 1, 1e-5],
  ['pulso: a meio comprimento', 'caPulseTrain(6.0, 10.0, 2.0)', Math.exp(-2), 1e-4],
  ['pulso: entre pulsos', 'caPulseTrain(0.0, 10.0, 2.0)', 0, 1e-6],
  ['pulso: periódico', 'caPulseTrain(15.0, 10.0, 2.0)', 1, 1e-4],
  ['pulso: tamanho 0', 'caPulseTrain(5.0, 10.0, 0.0)', 0, 0],
  ['afinamento: na ponta', 'caBeamTaper(0.0, 20.0, 2.0)', 0, 0],
  ['afinamento: no meio', 'caBeamTaper(0.5, 20.0, 2.0)', 1, 0],
  ['afinamento: a 1 bloco da ponta', 'caBeamTaper(0.05, 20.0, 2.0)', 0.5, 1e-4],
  ['afinamento: desligado', 'caBeamTaper(0.0, 20.0, 0.0)', 1, 0],
  ['profundidade: na frente', 'caDepthFade(5.0, 10.0, 0.5)', 1, 0],
  ['profundidade: meio da borda', 'caDepthFade(10.25, 10.0, 0.5)', 0.5, 1e-4],
  ['profundidade: atrás', 'caDepthFade(11.0, 10.0, 0.5)', 0, 0],
  ['profundidade: céu longe', 'caDepthFade(500.0, 100000.0, 0.15)', 1, 0],
  // luz no mundo (versão 2)
  ['normal: chão', 'caNormalFromNeighbors(vec3(0.0, -2.0, 5.0), vec3(-0.01, -2.0, 5.0), vec3(0.01, -2.0, 5.0), vec3(0.0, -2.0, 4.99), vec3(0.0, -2.0, 5.01)).y', 1, 1e-4],
  ['normal: céu ao lado não estraga', 'caNormalFromNeighbors(vec3(0.0, -2.0, 5.0), vec3(-100000.0, 0.0, 0.0), vec3(0.01, -2.0, 5.0), vec3(0.0, -2.0, 4.99), vec3(0.0, -2.0, 5.01)).y', 1, 1e-4],
  ['normal: parede virada para a câmera', 'caNormalFromNeighbors(vec3(0.0, 0.0, 5.0), vec3(-0.01, 0.0, 5.0), vec3(0.01, 0.0, 5.0), vec3(0.0, -0.01, 5.0), vec3(0.0, 0.01, 5.0)).z', -1, 1e-4],
  ['luz pontual: 1 bloco acima', 'caPointLight(vec3(0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 1.0, 0.0), 4.0, 2.0)', 0.5625, 1e-5],
  ['luz pontual: além do alcance', 'caPointLight(vec3(0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 5.0, 0.0), 4.0, 2.0)', 0, 0],
  ['luz pontual: face de costas', 'caPointLight(vec3(0.0), vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0), 4.0, 2.0)', 0, 0],
  ['luz pontual: face de lado', 'caPointLight(vec3(0.0), vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), 4.0, 2.0)', 0.5625 * 0.3 / 1.3, 1e-5],
  ['luz de segmento: ponto mais próximo', 'caSegmentLight(vec3(2.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(-5.0, 1.0, 0.0), vec3(5.0, 1.0, 0.0), 4.0, 2.0)', 0.5625, 1e-5],
  ['luz de segmento: além do alcance da ponta', 'caSegmentLight(vec3(10.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(-5.0, 1.0, 0.0), vec3(5.0, 1.0, 0.0), 4.0, 2.0)', 0.0, 1e-5],
  ['disco: dentro', 'caGroundLight(vec3(3.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 10.0, 2.5, 3.0, 0.0)', 1, 1e-5],
  ['disco: fora', 'caGroundLight(vec3(12.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 10.0, 2.5, 3.0, 0.0)', 0, 0],
  ['disco: alto demais', 'caGroundLight(vec3(3.0, 5.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 10.0, 2.5, 3.0, 0.0)', 0, 0],
  ['disco: teto', 'caGroundLight(vec3(3.0, 0.0, 0.0), vec3(0.0, -1.0, 0.0), vec3(0.0), 10.0, 2.5, 3.0, 0.0)', 0, 0],
  ['disco: parede 40%', 'caGroundLight(vec3(3.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0), vec3(0.0), 10.0, 2.5, 3.0, 0.0)', 0.4, 1e-5],
  ['anel: no raio', 'caGroundLight(vec3(6.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 6.0, 2.0, 3.0, 1.0)', 1, 1e-5],
  ['anel: meia banda', 'caGroundLight(vec3(6.5, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 6.0, 2.0, 3.0, 1.0)', 0.5, 1e-5],
  ['anel: fora da banda', 'caGroundLight(vec3(7.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 6.0, 2.0, 3.0, 1.0)', 0, 0],
  ['anel: no centro, nada', 'caGroundLight(vec3(0.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0), 6.0, 2.0, 3.0, 1.0)', 0, 0],
  ['cor iluminada', 'caLitColor(vec3(0.25), vec3(1.0)).r', 0.25 + Math.pow(0.25, 0.4) * 0.8, 1e-5],
  ['cor sem luz', 'caLitColor(vec3(0.3), vec3(0.0)).g', 0.3, 1e-6],
  // céu
  ['estrelas: faixa 0..1', 'clamp(caStars(normalize(vec3(0.3, 0.8, 0.2)), 3.0), 0.0, 1.0) - caStars(normalize(vec3(0.3, 0.8, 0.2)), 3.0)', 0, 0],
  ['estrelas: nada abaixo do horizonte', 'caStars(normalize(vec3(0.3, -0.2, 0.5)), 2.0)', 0, 0],
  ['estrelas: determinístico', 'caStars(normalize(vec3(-0.4, 0.7, 0.5)), 1.0) - caStars(normalize(vec3(-0.4, 0.7, 0.5)), 1.0)', 0, 0],
  // Terrain API (0.11.0): calamidade_terrain.glsl contra valores calculados pelo Java (TerrainNoise, TerrainShape).
  ['terreno: hash 0', 'float(caTerrainHash(ivec3(0, 0, 0), 0u) >> 8u)', 10959206.0, 0],
  ['terreno: hash 1', 'float(caTerrainHash(ivec3(-5, 70, 12), 123456u) >> 8u)', 9649050.00, 0],
  ['terreno: hash 2', 'float(caTerrainHash(ivec3(123, -64, -999), 4294967295u) >> 8u)', 12545503.0, 0],
  ['terreno: hash 3', 'float(caTerrainHash(ivec3(-32000, 319, 31000), 2147483647u) >> 8u)', 2023522.00, 0],
  ['terreno: hash 4', 'float(caTerrainHash(ivec3(7, 7, 7), 2654435769u) >> 8u)', 16508714.0, 0],
  ['terreno: valor 0', 'caTerrainValue(vec3(0.5, 0.25, 0.75), 0u)', 0.615853119, 2e-6],
  ['terreno: fbm 0', 'caTerrainFbm(vec3(0.5, 0.25, 0.75), 0u)', 0.624893738, 4e-6],
  ['terreno: valor 1', 'caTerrainValue(vec3(-3.3, 12.7, 8.1), 123456u)', 0.761718588, 2e-6],
  ['terreno: fbm 1', 'caTerrainFbm(vec3(-3.3, 12.7, 8.1), 123456u)', 0.643063629, 4e-6],
  ['terreno: valor 2', 'caTerrainValue(vec3(100.01, -20.5, 7.25), 4294967295u)', 0.358327745, 2e-6],
  ['terreno: fbm 2', 'caTerrainFbm(vec3(100.01, -20.5, 7.25), 4294967295u)', 0.350805977, 4e-6],
  ['terreno: valor 3', 'caTerrainValue(vec3(0.0, 0.0, 0.0), 2147483647u)', 0.285040200, 2e-6],
  ['terreno: fbm 3', 'caTerrainFbm(vec3(0.0, 0.0, 0.0), 2147483647u)', 0.517712629, 4e-6],
  ['terreno: valor 4', 'caTerrainValue(vec3(-0.001, 0.999, -7.5), 2654435769u)', 0.274688276, 2e-6],
  ['terreno: fbm 4', 'caTerrainFbm(vec3(-0.001, 0.999, -7.5), 2654435769u)', 0.359049219, 4e-6],
  ['terreno: sphere 0', 'caTerrainPrimitive(vec3(0.0, 0.0, 0.0), vec4(0.0, 5.0, 0.0, 0.0), vec4(-1.0, 2.0, 3.0, 0.0))', -1.25834261, 2e-5],
  ['terreno: sphere 2', 'caTerrainPrimitive(vec3(9.0, -0.5, 1.0), vec4(0.0, 5.0, 0.0, 0.0), vec4(-1.0, 2.0, 3.0, 0.0))', 5.50000000, 2e-5],
  ['terreno: sphere 4', 'caTerrainPrimitive(vec3(0.5, -3.9, 0.2), vec4(0.0, 5.0, 0.0, 0.0), vec4(-1.0, 2.0, 3.0, 0.0))', 1.70074623, 2e-5],
  ['terreno: crater 0', 'caTerrainPrimitive(vec3(3.0, -2.0, 4.0), vec4(1.0, 10.0, 4.0, 2.5), vec4(0.0, 0.0, 0.0, 3.0))', -1.21895142, 2e-5],
  ['terreno: crater 2', 'caTerrainPrimitive(vec3(-2.0, 1.0, 11.0), vec4(1.0, 10.0, 4.0, 2.5), vec4(0.0, 0.0, 0.0, 3.0))', 1.18033989, 2e-5],
  ['terreno: crater 4', 'caTerrainPrimitive(vec3(6.0, 6.0, -6.0), vec4(1.0, 10.0, 4.0, 2.5), vec4(0.0, 0.0, 0.0, 3.0))', 3.00000000, 2e-5],
  ['terreno: trench 0', 'caTerrainPrimitive(vec3(9.0, -0.5, 1.0), vec4(2.0, 2.5, 20.0, 0.0), vec4(0.0, -1.0, 2.0, 0.0))', 6.51387819, 2e-5],
  ['terreno: trench 2', 'caTerrainPrimitive(vec3(0.5, -3.9, 0.2), vec4(2.0, 2.5, 20.0, 0.0), vec4(0.0, -1.0, 2.0, 0.0))', 0.442787794, 2e-5],
  ['terreno: trench 4', 'caTerrainPrimitive(vec3(0.0, 0.0, 0.0), vec4(2.0, 2.5, 20.0, 0.0), vec4(0.0, -1.0, 2.0, 0.0))', -1.50000000, 2e-5],
  ['terreno: cylinder 0', 'caTerrainPrimitive(vec3(-2.0, 1.0, 11.0), vec4(3.0, 4.0, 6.0, 0.0), vec4(0.0, 0.0, 0.0, 0.0))', 7.18033989, 2e-5],
  ['terreno: cylinder 2', 'caTerrainPrimitive(vec3(6.0, 6.0, -6.0), vec4(3.0, 4.0, 6.0, 0.0), vec4(0.0, 0.0, 0.0, 0.0))', 5.39608645, 2e-5],
  ['terreno: cylinder 4', 'caTerrainPrimitive(vec3(3.0, -2.0, 4.0), vec4(3.0, 4.0, 6.0, 0.0), vec4(0.0, 0.0, 0.0, 0.0))', 1.00000000, 2e-5],
  ['terreno: crater 0', 'caTerrainPrimitive(vec3(0.5, -3.9, 0.2), vec4(1.0, 8.0, 6.0, 1.0), vec4(0.0, 0.0, 0.0, 6.0))', -1.35689011, 2e-5],
  ['terreno: crater 2', 'caTerrainPrimitive(vec3(0.0, 0.0, 0.0), vec4(1.0, 8.0, 6.0, 1.0), vec4(0.0, 0.0, 0.0, 6.0))', -4.80000000, 2e-5],
  ['terreno: crater 4', 'caTerrainPrimitive(vec3(9.0, -0.5, 1.0), vec4(1.0, 8.0, 6.0, 1.0), vec4(0.0, 0.0, 0.0, 6.0))', 1.05538514, 2e-5],
  ['terreno: borda irregular', '2.0 * caTerrainFbm(vec3(8.7, 0.3, 1.1) / 3.0, 2290649224u) - 1.0', 0.243114745, 1e-5],
  ['terreno: borda irregular', '2.0 * caTerrainFbm(vec3(-2.0, 8.5, 3.0) / 3.0, 2290649224u) - 1.0', -0.192072581, 1e-5],
  ['terreno: borda irregular', '2.0 * caTerrainFbm(vec3(0.0, 0.0, 9.2) / 3.0, 2290649224u) - 1.0', -0.601829563, 1e-5],
  // céu (versão 3, 0.12.0): valores tirados do Java (Sky.Direction, SkyMath.basis/local/fromLocal)
  ['céu: direção az 0 = sul', 'caSkyDir(0.0, 0.0).z', 1, 1e-6],
  ['céu: direção az 90 = oeste', 'caSkyDir(1.5707963, 0.0).x', -1, 1e-6],
  ['céu: direção el 90 = zênite', 'caSkyDir(0.3, 1.5707963).y', 1, 1e-6],
  ['céu: direção 37/21 x', 'caSkyDir(radians(37.0), radians(21.0)).x', -0.561842726, 2e-6],
  ['céu: direção 37/21 y', 'caSkyDir(radians(37.0), radians(21.0)).y', 0.358367950, 2e-6],
  ['céu: direção 37/21 z', 'caSkyDir(radians(37.0), radians(21.0)).z', 0.745590480, 2e-6],
  ['céu: ângulo entre direções', 'caAngle(caSkyDir(radians(37.0), radians(21.0)), caSkyDir(radians(-120.0), radians(64.0)))', 1.625445503, 2e-5],
  ['céu: ângulo pequeno estável', 'caAngle(vec3(0.0, 0.0, 1.0), normalize(vec3(1e-3, 0.0, 1.0)))', 1e-3, 2e-6],
  ['céu: ângulo oposto', 'caAngle(vec3(0.0, 1.0, 0.0), vec3(0.0, -1.0, 0.0))', Math.PI, 1e-5],
  ['céu: base direita x', 'caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)[0].x', -0.537365201, 2e-5],
  ['céu: base direita z', 'caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)[0].z', -0.658303080, 2e-5],
  ['céu: base cima y', 'caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)[1].y', 0.770517175, 2e-5],
  ['céu: base vertical aponta ao sul', 'caSkyBasis(vec3(0.0, 1.0, 0.0), 0.0)[1].z', 1, 1e-6],
  ['céu: local x', 'caSkyLocal(caSkyDir(radians(45.0), radians(30.0)), caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)).x', 0.190795709, 3e-5],
  ['céu: local y', 'caSkyLocal(caSkyDir(radians(45.0), radians(30.0)), caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)).y', 0.063979786, 3e-5],
  ['céu: local do centro é 0', 'length(caSkyLocal(vec3(0.0, 0.0, 1.0), caSkyBasis(vec3(0.0, 0.0, 1.0), 0.0)))', 0, 1e-6],
  ['céu: local mede o ângulo', 'length(caSkyLocal(normalize(vec3(1.0, 0.0, 1.0)), caSkyBasis(vec3(0.0, 0.0, 1.0), 0.0)))', Math.PI / 4, 1e-5],
  ['céu: de volta x', 'caSkyFromLocal(vec2(0.12, -0.3), caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)).x', -0.781553856, 3e-5],
  ['céu: de volta y', 'caSkyFromLocal(vec2(0.12, -0.3), caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)).y', 0.174831094, 3e-5],
  ['céu: de volta z', 'caSkyFromLocal(vec2(0.12, -0.3), caSkyBasis(caSkyDir(radians(37.0), radians(21.0)), 0.6)).z', 0.598838591, 3e-5],
  ['céu: ida e volta', 'length(caSkyLocal(caSkyFromLocal(vec2(0.4, 0.25), caSkyBasis(vec3(0.6, 0.0, 0.8), 1.1)), caSkyBasis(vec3(0.6, 0.0, 0.8), 1.1)) - vec2(0.4, 0.25))', 0, 2e-5],
  ['lente: desvio para dentro', 'caLensSource(vec2(0.2, 0.1), 0.15, 1.0).x', 0.118775000, 1e-5],
  ['lente: anel de Einstein vem do centro', 'length(caLensSource(vec2(0.15, 0.0), 0.15, 100.0))', 0, 2e-6],
  ['lente: some suave perto do alcance', 'caLensSource(vec2(0.8, 0.0), 0.15, 1.0).x', 0.796355000, 1e-5],
  ['lente: fora do alcance não desvia', 'caLensSource(vec2(1.2, 0.3), 0.15, 1.0).y', 0.3, 0],
  ['disco: dentro', 'caDisc(0.5, 1.0, 0.01)', 1, 0],
  ['disco: na borda', 'caDisc(1.0, 1.0, 0.01)', 0.5, 1e-5],
  ['disco: fora', 'caDisc(1.5, 1.0, 0.01)', 0, 0],
  ['ruído: nos inteiros é o hash', 'caNoise2(vec2(3.0, 5.0)) - caHash13(vec3(3.0, 5.0, 3.0))', 0, 1e-6],
  ['ruído: contínuo', 'abs(caNoise2(vec2(3.4999, 5.2)) - caNoise2(vec2(3.5001, 5.2)))', 0, 1e-3],
  ['fbm: dentro de 0..1', 'step(0.0, caFbm2(vec2(7.3, -2.1), 4.0)) * step(caFbm2(vec2(7.3, -2.1), 4.0), 1.0)', 1, 0],
  ['fbm: uma oitava é o ruído', 'caFbm2(vec2(1.7, 2.3), 1.0) - caNoise2(vec2(1.7, 2.3))', 0, 1e-6],
  ['céu do jogo: no alto, a cor do céu', 'caVanillaSky(vec3(0.0, 1.0, 0.0), vec3(0.3, 0.5, 0.9), vec3(0.8), 192.0).b', 0.9, 1e-5],
  ['céu do jogo: abaixo do horizonte, a névoa', 'caVanillaSky(normalize(vec3(1.0, -0.2, 0.0)), vec3(0.3, 0.5, 0.9), vec3(0.8), 192.0).r', 0.8, 1e-6],
];

function shaderSource(library) {
  const body = CASES.map((c, i) => `    if (i == ${i}) return ${c[1]};`).join('\n');
  return `#version 300 es
precision highp float;
precision highp int;
out vec4 fragColor;
${library}
float caseValue(int i) {
${body}
    return 12345.0;
}
void main() {
    int i = int(gl_FragCoord.x);
    fragColor = vec4(caseValue(i), 0.0, 0.0, 1.0);
}
`;
}

async function run() {
  const { chromium } = require('playwright');
  const library = fs.readFileSync(LIB, 'utf8') + '\n' + fs.readFileSync(TERRAIN, 'utf8');
  const fragment = shaderSource(library);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const page = await browser.newPage();
    const result = await page.evaluate(({ fragment, count }) => {
      const canvas = document.createElement('canvas');
      canvas.width = count; canvas.height = 1;
      const gl = canvas.getContext('webgl2');
      if (!gl) return { error: 'WebGL2 indisponível' };
      if (!gl.getExtension('EXT_color_buffer_float')) return { error: 'EXT_color_buffer_float indisponível' };
      const compile = (type, src) => {
        const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
        return s;
      };
      try {
        const vs = compile(gl.VERTEX_SHADER, '#version 300 es\nvoid main(){vec2 p[3]=vec2[](vec2(-1.0,-1.0),vec2(3.0,-1.0),vec2(-1.0,3.0));gl_Position=vec4(p[gl_VertexID],0.0,1.0);}');
        const fs = compile(gl.FRAGMENT_SHADER, fragment);
        const prog = gl.createProgram(); gl.attachShader(prog, vs); gl.attachShader(prog, fs); gl.linkProgram(prog);
        if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) return { error: gl.getProgramInfoLog(prog) };
        const tex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, tex);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA32F, count, 1, 0, gl.RGBA, gl.FLOAT, null);
        const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
        gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
        gl.viewport(0, 0, count, 1); gl.useProgram(prog); gl.drawArrays(gl.TRIANGLES, 0, 3);
        const out = new Float32Array(count * 4);
        gl.readPixels(0, 0, count, 1, gl.RGBA, gl.FLOAT, out);
        const values = []; for (let i = 0; i < count; i++) values.push(out[i * 4]);
        return { values, renderer: gl.getParameter(gl.VERSION) };
      } catch (e) { return { error: String(e.message || e) }; }
    }, { fragment, count: CASES.length });
    if (result.error) { console.error('Falha ao compilar ou executar:\n' + result.error); return 1; }
    let failed = 0;
    CASES.forEach(([name, , expected, tol], i) => {
      const v = result.values[i];
      const ok = Number.isFinite(v) && Math.abs(v - expected) <= tol + 1e-7;
      if (!ok) failed++;
      console.log(`${ok ? 'ok  ' : 'FALHA'} ${name}: ${v} (esperado ${expected}${tol ? ' ± ' + tol : ''})`);
    });
    console.log(`\n${CASES.length - failed}/${CASES.length} casos passaram em ${result.renderer}.`);
    return failed ? 1 : 0;
  } finally {
    await browser.close();
  }
}

run().then(code => process.exit(code), err => { console.error(err); process.exit(1); });
