#!/usr/bin/env bash
# =====================================================================
# Custo Certo - Sobe a feature de Receita
# Migra o banco, recompila o servidor e reinicia. Com rede de seguranca:
# se a compilacao falhar, o jar atual (que funciona) continua intacto.
# Rodar de dentro de ~/Custo-Certo.java com:  bash subir-receita.sh
# =====================================================================
set -u
cd ~/Custo-Certo.java || { echo "Pasta do projeto nao encontrada"; exit 1; }
export PATH="$PATH:$HOME/.platformio/penv/bin:$HOME/.local/bin"

echo "== 1/3  Migracao do banco (digite a senha do MySQL) =="
mysql -u root -p < desktop/src/main/resources/sql/03_schema_receitas.sql || {
  echo "!! Migracao falhou. Verifique o MySQL/senha. Nada mais foi alterado."; exit 1; }

echo "== 2/3  Recompilando o servidor =="
cd servidor-java
if ! mvn -q package; then
  echo ""
  echo "!! BUILD FALHOU - o servidor novo NAO foi gerado."
  echo "   O jar atual (que funciona) continua em target/. Rode-o e me mande o erro acima:"
  echo "   java -jar target/custo-certo-api-1.0.0.jar"
  exit 1
fi

echo "== 3/3  Reiniciando o servidor =="
pkill -f 'custo-certo-api' 2>/dev/null && sleep 1
nohup java -jar target/custo-certo-api-1.0.0.jar > ~/servidor.log 2>&1 &
sleep 3
echo -n "Health: "; curl -s http://localhost:3000/health; echo
echo ""
echo "Pronto! Abra http://localhost:3000 -> aba Balanca -> Iniciar receita."
echo "Se tiver systemd configurado, use: sudo systemctl restart custo-certo"
