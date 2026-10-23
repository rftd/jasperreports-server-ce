# OpenJRS Server

**Uma continuação independente e comunitária do JasperReports Server Community Edition.**

[English](README.md) · [Guia de compilação](jasperserver/README.md) · [Migração](MIGRATION.md) · [Política de manutenção](MAINTENANCE.md)

## Sobre o projeto

**OpenJRS Server** é um fork independente, criado a partir do código-fonte do **JasperReports Server Community Edition (JRS CE)**. O objetivo é preservar a edição comunitária, recuperar uma compilação reproduzível e modernizar gradualmente as dependências e o mecanismo de relatórios.

**Este projeto não é afiliado, patrocinado, endossado nem suportado pela Jaspersoft, Cloud Software Group ou pelos demais titulares do software original.** Problemas específicos deste fork devem ser comunicados aos seus mantenedores, não à empresa original.

As marcas JasperReports e Jaspersoft são mencionadas somente para identificar a origem e a compatibilidade. Seus direitos pertencem aos respectivos titulares.

## Estado atual

O OpenJRS Server **ainda não é uma substituição pronta para produção**.

| Componente | Situação |
| --- | --- |
| Base original | JasperReports Server CE 8.2.0 / motor JasperReports 6.20.3 |
| Frontend legado | Compilação validada no CI com Node.js 14 e Yarn Classic |
| Backend legado | WAR gerado no CI com Java 8, Maven e Ant |
| Inicialização | PostgreSQL 14 e Tomcat 9 validados no CI |
| Testes funcionais | Autenticação REST e exportações PDF/XLSX validadas na base original |
| Motor JasperReports 7.1.0 | **PR #2 separado e experimental**: CI valida PDF/XLSX, viewer, tabelas interativas e visualizações Community; compatibilidade ampliada e integridade das dependências seguem em revisão |

O [PR #1](https://github.com/rftd/jasperreports-server-ce/pull/1) mantém a base original testada. O [PR #2](https://github.com/rftd/jasperreports-server-ce/pull/2) trata da migração experimental para o motor 7.1.0.

## Manutenção assistida por IA

**Inteligência artificial é utilizada como ferramenta de apoio** para análise de código, migração, documentação e elaboração de alterações. O uso de IA não garante correção ou segurança: propostas precisam de supervisão humana, revisão e testes automatizados.

A manutenção é comunitária e realizada em regime de melhor esforço, sem garantias de suporte, correções de segurança ou prazos. Consulte [MAINTENANCE.md](MAINTENANCE.md).

## Compatibilidade e compilação

Os nomes internos originais foram mantidos intencionalmente durante esta fase: pastas `jasperserver/`, pacotes Java, coordenadas Maven, nome do WAR e contexto web `/jasperserver`. A identidade pública aparece na documentação, no login, no cabeçalho e na janela Sobre. As APIs e os identificadores técnicos permanecem compatíveis.

- [Guia do backend](jasperserver/README.md)
- [Guia do frontend](jasperserver-ui/ce/README.md)
- [Plano e limitações da migração](MIGRATION.md)

A presença de ferramentas legadas sem suporte e a aprovação no CI **não equivalem a uma auditoria de segurança**.

## Licenças e créditos

O código é derivado de um projeto existente. **Os avisos de copyright, autoria e licença originais devem ser preservados.** Os cabeçalhos de arquivos originais identificam a licença GNU AGPL, versão 3 ou posterior, salvo licenciamento comercial separado. Componentes de terceiros podem ter outras licenças e exigências de redistribuição.

O nome OpenJRS Server **não modifica nem elimina** essas obrigações. Veja [BRANDING.md](BRANDING.md) e a [ordem de integração dos PRs](MERGING.md).

## Contribuições

Use as [Issues](https://github.com/rftd/jasperreports-server-ce/issues) e os pull requests do repositório para informar problemas e propor melhorias. Inclua logs, passos para reproduzir e a versão ou branch afetada.
