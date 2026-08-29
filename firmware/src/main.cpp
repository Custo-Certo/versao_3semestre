/**
 * Custo Certo — Firmware ESP32 (Balança HX711)
 *
 * Lê o peso do amplificador HX711 e envia para o servidor a cada loop.
 * Também consulta se o servidor solicitou tara.
 *
 * CONFIGURAÇÃO:
 *  - WIFI_SSID / WIFI_PASS: credenciais da rede da cafeteria
 *  - SERVER_URL: URL do servidor (Render em produção, IP local em dev)
 *
 * Defina via build_flags no platformio.ini (preferido) ou edite os defines abaixo.
 */

#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "HX711.h"

// =====================================================
// CONFIGURAÇÕES (sobrescreva via platformio.ini build_flags)
// =====================================================
#ifndef WIFI_SSID
#define WIFI_SSID "DEFINA_NO_SECRETS_INI"
#endif

#ifndef WIFI_PASS
#define WIFI_PASS "DEFINA_NO_SECRETS_INI"
#endif

#ifndef SERVER_URL
// DEV (rede local):  http://192.168.0.11:3000
// PROD (Render):     https://custo-certo.onrender.com
#define SERVER_URL "https://custo-certo.onrender.com"
#endif

// =====================================================
// PINOS DA BALANÇA
// =====================================================
#define HX711_DT  32
#define HX711_SCK 33

// Calibração — ajuste com peso conhecido
#define SCALE_FACTOR 297313.0f

// =====================================================
// CONSTANTES DE TEMPO
// =====================================================
// O HX711 amostra a 10 Hz, ou seja, uma leitura fica pronta a cada ~100 ms.
// Pedir menos que isso não adianta: o chip não teria dado novo para entregar.
const unsigned long INTERVALO_LEITURA_MS = 100;     // envia peso ~10x/s
const unsigned long WIFI_TIMEOUT_MS      = 20000;

// Suavização por média móvel exponencial, feita aqui em software.
// Substitui o scale.get_units(5), que travava o laço por meio segundo
// esperando cinco amostras do chip. Com uma amostra só e este filtro, a
// leitura fica igualmente estável e chega cinco vezes mais rápido.
// Quanto menor o peso do filtro, mais suave e mais lento; 0.35 equilibra
// tremor de leitura e resposta ao colocar o insumo na balança.
const float SUAVIZACAO = 0.35f;

// =====================================================
// ESTADO GLOBAL
// =====================================================
HX711 scale;
unsigned long ultimaLeitura = 0;
float pesoSuavizado = 0.0f;
bool primeiraLeitura = true;

bool serverIsHttps() {
  return String(SERVER_URL).startsWith("https://");
}

// =====================================================
// CONEXÃO WI-FI
// =====================================================
void conectarWiFi() {
  Serial.print("📶 Conectando ao Wi-Fi: ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - inicio > WIFI_TIMEOUT_MS) {
      Serial.println("\n❌ Falha ao conectar — reiniciando...");
      ESP.restart();
    }
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.print("✅ Wi-Fi OK | IP: ");
  Serial.println(WiFi.localIP());
}

// =====================================================
// HTTP HELPERS
// =====================================================
// Para HTTPS sem certificado raiz embarcado, usamos setInsecure().
// Em produção crítica recomenda-se subir a CA do Render para o ESP32.
/**
 * Envia um POST e devolve o corpo da resposta.
 *
 * O cliente e a conexão são estáticos de propósito. Antes, cada chamada criava
 * um HTTPClient novo e abria uma conexão TCP do zero — com dez envios por
 * segundo, isso significaria dez handshakes por segundo. Com setReuse(true) a
 * conexão fica aberta entre as leituras, o que corta a maior parte do custo de
 * rede de cada envio.
 */
String httpPost(const String& path, const String& body) {
  static HTTPClient http;
  static WiFiClient clientPlain;
  static WiFiClientSecure clientSecure;
  static bool configurado = false;

  if (!configurado) {
    clientSecure.setInsecure();
    http.setReuse(true);
    configurado = true;
  }

  bool ok;
  if (serverIsHttps()) {
    ok = http.begin(clientSecure, String(SERVER_URL) + path);
  } else {
    ok = http.begin(clientPlain, String(SERVER_URL) + path);
  }

  if (!ok) {
    Serial.println("❌ http.begin falhou");
    return "";
  }

  http.addHeader("Content-Type", "application/json");
  // Timeout curto: se o servidor não responder rápido, é melhor perder uma
  // leitura do que travar o laço e atrasar todas as seguintes.
  http.setTimeout(2000);

  int code = http.POST(body);
  String resposta = "";

  if (code >= 200 && code < 300) {
    resposta = http.getString();
  } else {
    Serial.printf("⚠️  POST %s -> %d\n", path.c_str(), code);
  }

  http.end();
  return resposta;
}

String httpGet(const String& path) {
  HTTPClient http;
  WiFiClientSecure clientSecure;
  WiFiClient clientPlain;

  bool ok;
  if (serverIsHttps()) {
    clientSecure.setInsecure();
    ok = http.begin(clientSecure, String(SERVER_URL) + path);
  } else {
    ok = http.begin(clientPlain, String(SERVER_URL) + path);
  }

  if (!ok) return "";

  http.setTimeout(5000);
  int code = http.GET();
  String resp = "";
  if (code == 200) {
    resp = http.getString();
  } else {
    Serial.printf("⚠️  GET %s -> %d\n", path.c_str(), code);
  }
  http.end();
  return resp;
}

// =====================================================
// SETUP
// =====================================================
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== Custo Certo - Balança ===");

  conectarWiFi();

  Serial.print("⚖️  Iniciando HX711 nos pinos DT=");
  Serial.print(HX711_DT);
  Serial.print(" SCK=");
  Serial.println(HX711_SCK);

  scale.begin(HX711_DT, HX711_SCK);
  scale.set_scale(SCALE_FACTOR);
  scale.tare();

  Serial.println("✅ Balança calibrada e pronta");
  Serial.print("🌐 Servidor: ");
  Serial.println(SERVER_URL);
}

// =====================================================
// LOOP
// =====================================================
void loop() {
  // Reconecta se WiFi cair
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("⚠️  Wi-Fi caiu — reconectando...");
    conectarWiFi();
  }

  unsigned long agora = millis();

  // ----- ENVIAR PESO -----
  // scale.is_ready() evita o ponto mais caro do laço: sem ele, get_units()
  // fica bloqueado esperando o chip terminar a conversão. Aqui só lemos
  // quando já existe amostra pronta, e o laço nunca trava.
  if (agora - ultimaLeitura >= INTERVALO_LEITURA_MS && scale.is_ready()) {
    ultimaLeitura = agora;

    float bruto = scale.get_units(1);
    if (bruto < 0) bruto = 0;

    if (primeiraLeitura) {
      pesoSuavizado = bruto;
      primeiraLeitura = false;
    } else {
      pesoSuavizado = (SUAVIZACAO * bruto) + ((1.0f - SUAVIZACAO) * pesoSuavizado);
    }

    char body[64];
    snprintf(body, sizeof(body), "{\"peso\":%.3f}", pesoSuavizado);

    // A resposta do POST já traz o pedido de tara, o que dispensa a
    // requisição GET separada que antes rodava a cada segundo. São
    // metade das idas ao servidor, e a tara chega mais rápido.
    String resposta = httpPost("/balanca/peso", body);
    if (resposta.indexOf("\"tarar\":true") >= 0) {
      scale.tare();
      pesoSuavizado = 0.0f;
      primeiraLeitura = true;
      Serial.println("✅ Tara executada");
    }
  }

  delay(1);
}