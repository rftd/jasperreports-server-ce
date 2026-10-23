# OpenJRS Server local — Docker Compose

Ambiente de desenvolvimento/teste com JasperReports 7.1.0, Java 8, Tomcat 9 e
PostgreSQL 14. O frontend é compilado com Node 14/Yarn Classic dentro do Docker.
Não é necessário instalar Java, Maven, Node ou PostgreSQL na máquina.

## Iniciar

Instale Docker Engine com Compose v2 ou Docker Desktop e obtenha este repositório.
Na raiz do checkout, execute:

```sh
docker compose up -d --build --wait --wait-timeout 900
```

A primeira compilação baixa as dependências e pode demorar vários minutos.
Reserve aproximadamente 8 GB de RAM e 15 GB de disco para build/cache/imagens.
O tempo de espera acima cobre a inicialização depois do build.

Abra **http://localhost:8080/jasperserver**:

- Usuário: `jasperadmin`
- Senha inicial: `jasperadmin`

Os exemplos SugarCRM/Foodmart e os relatórios de exemplo são carregados na primeira
inicialização. Comece por `Reports / Interactive / Customers Report` para testar
o viewer e as exportações PDF/XLSX.

O HTTP é publicado apenas em `127.0.0.1`; o PostgreSQL não publica porta no host.
As credenciais fornecidas são exclusivas deste ambiente de teste local.

Para mudar a porta ou a senha do banco **antes da primeira inicialização**, crie
um `.env` na raiz (esse arquivo não entra no contexto de build):

```dotenv
OPENJRS_PORT=8081
OPENJRS_DB_PASSWORD=uma-senha-local
OPENJRS_JAVA_OPTS=-Xms256m -Xmx2048m -Djava.awt.headless=true
```

A senha do banco não altera a senha da conta `jasperadmin`. Depois que o volume
PostgreSQL existir, alterar apenas `OPENJRS_DB_PASSWORD` não altera a senha já
armazenada no banco: conserve o valor usado na criação ou altere a senha no banco
antes de atualizar a configuração.

## Serviços e dados persistentes

- `postgres`: banco PostgreSQL 14.
- `initialize`: prepara schema, usuários, temas e exemplos, gera as chaves de
  criptografia e a configuração do runtime; termina com código zero.
- `openjrs`: Tomcat 9/Java 8, executado como usuário sem privilégios; inclui
  Chromium para visualizações personalizadas e fontes DejaVu para relatórios.

Os volumes `postgres-data`, `encryption-keys` e `runtime-config` preservam banco,
chaves e configuração. Faça backup do banco e das chaves **juntos**. O inicializador
verifica se o repositório já foi carregado e se as chaves correspondem; não usa o
target legado que apaga o banco e não repete a importação em um banco existente.

Parar e voltar preservando os dados:

```sh
docker compose down
docker compose up -d --wait --wait-timeout 900
```

Para acompanhar a inicialização:

```sh
docker compose logs -f initialize openjrs
docker compose ps -a
```

`initialize` aparecer como `Exited (0)` é esperado. Em uma falha durante a primeira
carga, o inicializador recusa sobrescrever um repositório incompleto. Consulte os
logs. Para **descartar todos os dados de teste e as chaves** e começar novamente:

```sh
docker compose down --volumes
docker compose up -d --build --wait --wait-timeout 900
```

Essa remoção é definitiva para os volumes deste projeto Compose. Não a execute
em um ambiente cujo conteúdo queira preservar. Não reutilize volumes de uma
instalação existente como destino do bootstrap local.

## Limitações conhecidas

O build executa os testes focados de JR7/JRXML/ações do viewer e usa o mesmo fluxo
Buildomatic do CI. A auditoria diagnóstica só aceita as diferenças de dependências
já enumeradas, com versões e hashes fixos. A verificação original de produção
permanece rígida. A procedência do SiteMesh e a compatibilidade ampliada continuam
pendentes: esta imagem não é uma distribuição de produção aprovada.

O Chromium usa seu sandbox; não acrescente `--no-sandbox` para contornar falhas.
Políticas do kernel/Docker podem limitar a criação de namespaces exigida pelo
browser. Os diagnósticos do container permitem identificar essa situação.

As versões legadas de Node/Java são mantidas para reproduzir a stack atual.
Suporte a outras arquiteturas, principalmente ARM64, exige validação específica.
O rebuild de uma imagem não implementa migração automática de schema ou de
relatórios compilados antigos; consulte [MIGRATION.md](../../MIGRATION.md).
