# OpenJRS Server local — Docker Compose

Ambiente de desenvolvimento/teste com JasperReports 7.1.0, Java 8, Tomcat 9 e
PostgreSQL 14. O frontend é compilado com Node 14/Yarn Classic dentro do Docker.
Não é necessário instalar Java, Maven, Node ou PostgreSQL na máquina.

## Iniciar

Instale Docker Engine com Compose v2 ou Docker Desktop e obtenha este repositório.
O comando `docker` fornecido pelo pacote `podman-docker` usa outro runtime;
nesse caso, consulte a seção de Podman abaixo antes de iniciar.
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

## Podman: erro ao abrir o perfil seccomp

Se aparecer `opening seccomp profile failed: open {"defaultAction":...}: file name too long`
e `executing /usr/local/bin/docker-compose`, o Docker Compose está sendo executado
sobre a API de compatibilidade do Podman. O Compose lê o arquivo seccomp e envia
seu conteúdo JSON; essa API do Podman tenta interpretar o JSON como um caminho.
A falha acontece na criação do container, mesmo quando as imagens já compilaram.

Para usar Podman, instale o **podman-compose 1.5.0** pelo método disponível na sua
distribuição e execute-o diretamente, sem o provider `docker-compose`. Na raiz do
checkout, usando o mesmo usuário e o mesmo projeto da tentativa anterior:

```sh
podman-compose --version
# Usa o provider anterior apenas para remover os containers e a rede da tentativa.
# Conserva as imagens e os volumes com o banco e as chaves.
podman compose -p openjrs-local -f compose.yaml down
podman-compose -p openjrs-local -f compose.yaml up -d --no-build
podman-compose -p openjrs-local -f compose.yaml logs -f initialize openjrs
```

Esse procedimento reaproveita as imagens do build que terminou com sucesso. Se
você usou outro nome de projeto, substitua `openjrs-local` pelo mesmo nome usado
antes; não troque de usuário nem acrescente `sudo` entre as tentativas. Não use
`down --volumes` para esta correção.

O `podman-compose` passa o caminho do perfil diretamente ao Podman e mantém
seccomp, `no-new-privileges` e o sandbox do Chromium. Os argumentos `--wait` e
`--wait-timeout` do Docker Compose não fazem parte desse comando; acompanhe os
logs até o inicializador terminar com código zero e o Tomcat iniciar, depois
acesse a URL do login. Use `podman-compose -p openjrs-local -f compose.yaml ps -a`
para consultar o estado dos containers.

O CI valida a execução completa com **Docker Engine/Compose v2**. Este contorno
do transporte do perfil não representa validação completa com Podman; políticas
de SELinux, namespaces e execução rootless ainda podem exigir diagnóstico próprio.

## Limitações conhecidas

O build executa os testes focados de JR7/JRXML/ações do viewer e usa o mesmo fluxo
Buildomatic do CI. A auditoria diagnóstica só aceita as diferenças de dependências
já enumeradas, com versões e hashes fixos. A verificação original de produção
permanece rígida. A procedência do SiteMesh e a compatibilidade ampliada continuam
pendentes: esta imagem não é uma distribuição de produção aprovada.

O Chromium usa seu sandbox; não acrescente `--no-sandbox` para contornar falhas.
O serviço usa o perfil seccomp padrão do Moby com uma regra adicional para os
namespaces do browser, mantendo as demais restrições, usuário sem privilégios e
`no-new-privileges`. A origem, licença e alteração estão em
[seccomp/NOTICE.md](seccomp/NOTICE.md). Políticas adicionais do kernel/Docker
podem limitar esses namespaces; consulte os diagnósticos do container.

As versões legadas de Node/Java são mantidas para reproduzir a stack atual.
Suporte a outras arquiteturas, principalmente ARM64, exige validação específica.
O rebuild de uma imagem não implementa migração automática de schema ou de
relatórios compilados antigos; consulte [MIGRATION.md](../../MIGRATION.md).
