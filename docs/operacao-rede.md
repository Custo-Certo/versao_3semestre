# Operação e Rede — Custo Certo

Guia prático para subir o sistema e para quando **mudar de rede ou o IP do PC mudar**.
O PC do Lucas é o "servidor" (MySQL + servidor Java + balança). O painel web e os
apps desktop conversam com ele.

## O que precisa estar no ar

| Peça | Porta | Como conferir |
|---|---|---|
| MySQL (banco `custo_certo`) | 3306 | `sudo systemctl status mysql` |
| Servidor Java (painel + balança) | 3000 | `curl -s http://localhost:3000/health` → `{"status":"ok"}` |
| ESP32 (balança) | — | ligado, na mesma rede, apontando pro IP do PC |

## Toda vez que ligar o PC (rotina de início)

1. **MySQL** (normalmente já sobe sozinho no boot):
   ```bash
   sudo systemctl start mysql
   ```
2. **Servidor Java:**
   ```bash
   cd ~/Custo-Certo.java/servidor-java
   java -jar target/custo-certo-api-1.0.0.jar
   ```
   Em segundo plano (continua no ar mesmo fechando o terminal):
   ```bash
   nohup java -jar target/custo-certo-api-1.0.0.jar > ~/servidor.log 2>&1 &
   ```
3. **Teste:** `curl -s http://localhost:3000/health` deve responder `{"status":"ok"}`.

> Se o `.jar` não existir, rode `mvn package` dentro de `servidor-java/` antes.

## Quando mudar de rede (ou o IP do PC mudar)

**Sintoma típico:** a balança para de aparecer no painel; `localhost:3000` funciona,
mas pelo IP (`http://192.168.x.x:3000`) não — ou a balança simplesmente não atualiza.

1. **Todos na MESMA rede WiFi.** O PC servidor, o ESP32 e qualquer celular/tablet que
   vá abrir o painel precisam estar na mesma rede — de preferência a **2.4 GHz**, que é
   a que o ESP32 enxerga.
2. **Descubra o IP atual do PC:**
   ```bash
   hostname -I
   ```
   Use o primeiro endereço `192.168.x.x` (os longos são IPv6, ignore).
3. **Edite `firmware/secrets.ini`:**
   ```ini
   wifi_ssid       = NOME_DA_REDE
   wifi_pass       = SENHA_DA_REDE
   server_url_dev  = http://IP_DO_PC:3000
   ```
   (Esse arquivo é ignorado pelo Git — a senha fica só na sua máquina.)
4. **Regrave a balança** (ESP32 no USB, ambiente `dev`):
   ```bash
   cd ~/Custo-Certo.java/firmware
   pio run -e dev -t upload
   ```
   Se o `pio` não for encontrado:
   ```bash
   export PATH="$PATH:$HOME/.platformio/penv/bin:$HOME/.local/bin"
   ```
5. **Libere a porta no firewall** (uma vez por máquina):
   ```bash
   sudo ufw allow 3000/tcp
   # e 3306/tcp se outros PCs forem usar o banco pela rede
   ```
6. **Teste de outro aparelho:** abra `http://IP_DO_PC:3000` no celular (mesma rede).
   Se o painel abrir, a balança também alcança o servidor.

> **Importante:** só precisa regravar o firmware se o **IP** ou a **rede WiFi** mudaram.
> Se o IP continuar o mesmo (confira com `hostname -I`), o problema quase sempre é o
> **servidor Java parado** — é só subir de novo (rotina de início acima).

## Para PARAR de ter esse trabalho (recomendado)

- **Reserva de DHCP no roteador:** fixe o IP do PC pelo MAC address (config do roteador,
  "DHCP reservation" / "IP fixo"). Assim o PC sempre recebe o mesmo IP naquela rede e você
  não precisa regravar a balança após reiniciar.
- **Servidor subindo no boot:** crie um serviço systemd (abaixo) — o servidor Java sobe
  sozinho quando o PC liga. Isso resolve o problema que mais apareceu: ligar o PC e o
  servidor estar fora do ar.
- **(Avançado) mDNS:** fazer o firmware usar `nome-do-pc.local` em vez de IP. Sobrevive à
  troca de IP, mas exige mudança no firmware e nem toda rede permite multicast.

## Servidor Java automático no boot (systemd)

Crie o arquivo `/etc/systemd/system/custo-certo.service`:

```ini
[Unit]
Description=Custo Certo - Servidor Java
After=network-online.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=lucas
WorkingDirectory=/home/lucas/Custo-Certo.java/servidor-java
ExecStart=/usr/bin/java -jar /home/lucas/Custo-Certo.java/servidor-java/target/custo-certo-api-1.0.0.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Ative:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now custo-certo
sudo systemctl status custo-certo     # deve ficar "active (running)"
```

> O `WorkingDirectory` precisa ser `servidor-java/` para o servidor achar o
> `../desktop/config.properties` (credenciais) e a pasta `../public` (painel).

## Multi-PC: outros PCs rodando o app desktop (topologia de rede)

### Lado servidor (no PC do Lucas) — FEITO e validado
MySQL escutando em `0.0.0.0:3306`, usuário de rede `custocerto` criado, firewall
liberado. Para refazer numa instalação nova:

```bash
# 1. MySQL passa a escutar na rede (nao so localhost)
sudo sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
sudo systemctl restart mysql
ss -tlnp | grep 3306        # deve mostrar 0.0.0.0:3306

# 2. Usuario de rede -- dentro de:  mysql -u root -p
#   CREATE USER IF NOT EXISTS 'custocerto'@'%' IDENTIFIED WITH caching_sha2_password BY 'CustoCerto2026';
#   GRANT ALL PRIVILEGES ON custo_certo.* TO 'custocerto'@'%';
#   FLUSH PRIVILEGES;

# 3. Firewall
sudo ufw allow 3306/tcp

# 4. Validar pela rede (troque pelo IP do servidor)
mysql -u custocerto -p -h IP_DO_SERVIDOR -e "SELECT COUNT(*) FROM custo_certo.ingredientes;"
```

### Lado cliente (cada outro PC)
1. Instalar o JDK 21: `sudo apt install openjdk-21-jdk -y`
2. Pegar o app desktop: `git clone https://github.com/tsaistrinta/Custo-Certo.java.git`
   (ou copiar só a pasta `desktop/`).
3. Criar `desktop/config.properties` apontando pro servidor:
   ```ini
   db.host=IP_DO_SERVIDOR
   db.porta=3306
   db.nome=custo_certo
   db.usuario=custocerto
   db.senha=CustoCerto2026
   db.parametros=useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo&characterEncoding=UTF-8
   ```
4. Rodar: `cd desktop && mvn javafx:run` (ou `./mvnw javafx:run`). A 1ª vez baixa o JavaFX.
5. Logar (ex.: `admin@custocerto.com.br` / `admin123`) e conferir que vê o mesmo estoque.

O **painel web** nos outros aparelhos é ainda mais simples: só abrir `http://IP_DO_SERVIDOR:3000`
no navegador (nada a instalar).

> **Atenção ao IP:** o `db.host` dos clientes é o IP do servidor. Se esse IP mudar, os
> clientes param de conectar. Por isso a **reserva de DHCP** (IP fixo do servidor) é tão
> importante na apresentação — sem ela, você teria que reeditar o config de cada PC.

## Diagnóstico rápido

| Sintoma | Causa provável | Ação |
|---|---|---|
| Painel não abre em `localhost:3000` | servidor Java parado | suba o servidor (rotina de início) |
| `localhost:3000` ok, mas `IP:3000` não | firewall ou redes diferentes | `sudo ufw allow 3000/tcp`; confira mesma rede |
| Balança não atualiza o peso | servidor parado, IP errado no `secrets.ini`, ou ESP32 fora da rede | cheque os três |
| Erro de banco ao subir o servidor | MySQL parado | `sudo systemctl start mysql` |
| `pio: command not found` | PATH do PlatformIO | `export PATH="$PATH:$HOME/.platformio/penv/bin:$HOME/.local/bin"` |
