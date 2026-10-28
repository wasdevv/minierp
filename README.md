# Mini ERP — Gestão de Vendas e Estoque

> Projeto demonstrativo para praticar e apresentar conceitos de desenvolvimento backend com Java e Spring Boot, templates server-side, persistência relacional e geração de relatórios PDF.

![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue)
![License](https://img.shields.io/badge/License-MIT-green)

O **Mini ERP** é uma aplicação de exemplo para pequenas operações comerciais. A proposta é centralizar o cadastro de produtos, clientes e vendas, acompanhar movimentações básicas de estoque e gerar relatórios de vendas em PDF.

Este repositório é um projeto de estudo e demonstração. Ajuste as funcionalidades e tecnologias descritas aqui ao que estiver realmente implementado no código.

## Objetivos

- Praticar a construção de uma aplicação web com Java e Spring Boot.
- Organizar o código em camadas, separando apresentação, regras de negócio e persistência.
- Persistir dados em PostgreSQL.
- Construir páginas server-side com Thymeleaf.
- Gerar relatórios em PDF com JasperReports.
- Demonstrar validação de dados, tratamento de erros e testes automatizados.

## Funcionalidades planejadas

### Cadastros
- [ ] Cadastro, consulta, edição e exclusão de produtos.
- [ ] Cadastro, consulta, edição e exclusão de clientes.
- [ ] Listagem com paginação e busca.
- [ ] Validação de campos obrigatórios e dados inválidos.

### Vendas e estoque
- [ ] Registrar uma venda com um ou mais itens.
- [ ] Calcular subtotal e total no servidor.
- [ ] Atualizar o estoque ao confirmar uma venda.
- [ ] Impedir vendas com quantidade maior que o estoque disponível.
- [ ] Registrar data, itens, quantidades e preços praticados na venda.
- [ ] Garantir consistência transacional entre venda e estoque.

### Relatórios
- [ ] Gerar relatório de vendas por período.
- [ ] Exibir total vendido e quantidade de vendas.
- [ ] Exportar relatório em PDF usando JasperReports.
- [ ] Tratar períodos inválidos e resultados vazios.

### Qualidade e manutenção
- [ ] Testes unitários para regras de negócio.
- [ ] Testes de integração para persistência e endpoints.
- [ ] Mensagens de erro claras para o usuário.
- [ ] Instruções de execução reproduzíveis.

> Marque os itens conforme forem realmente implementados. Não apresente funcionalidades planejadas como já concluídas.

## Stack tecnológica

| Tecnologia | Uso proposto |
|---|---|
| Java 17 ou superior | Linguagem principal |
| Spring Boot 3.x | Configuração da aplicação e integração dos componentes |
| Spring MVC | Rotas web e controllers |
| Spring Data JPA / Hibernate | Persistência e mapeamento objeto-relacional |
| PostgreSQL | Banco de dados relacional |
| Thymeleaf | Renderização de páginas HTML no servidor |
| JasperReports | Geração de relatórios em PDF |
| Maven | Gerenciamento de dependências e build |
| JUnit 5 / Mockito | Testes unitários |
| Spring Boot Test | Testes de integração |

As versões devem ser definidas no `pom.xml` e compatíveis entre si. Caso a implementação use outras versões ou ferramentas, atualize esta seção.

## Arquitetura sugerida

A aplicação pode ser organizada em camadas:

```text
src/
├── main/
│   ├── java/com/example/minierp/
│   │   ├── MiniErpApplication.java
│   │   ├── controller/       # Rotas web e tratamento de requisições
│   │   ├── service/          # Regras de negócio
│   │   ├── repository/       # Acesso a dados
│   │   ├── model/            # Entidades JPA
│   │   ├── dto/              # Objetos de entrada/saída, se necessários
│   │   ├── config/           # Configurações
│   │   └── exception/        # Exceções e tratamento global
│   └── resources/
│       ├── templates/        # Páginas Thymeleaf
│       ├── static/           # CSS, JavaScript e imagens
│       ├── reports/          # Templates .jrxml do JasperReports
│       └── application.yml
└── test/
    └── java/com/example/minierp/
```

Essa estrutura é uma sugestão inicial, não uma afirmação sobre a estrutura já existente. Mantenha o pacote e a organização que fizerem sentido para o código real.

## Modelo de dados sugerido

### `products`
- `id`: identificador.
- `name`: nome do produto.
- `sku`: código único do produto.
- `description`: descrição opcional.
- `price`: preço atual de venda.
- `stock_quantity`: quantidade disponível.
- `active`: indica se o produto está ativo.
- `created_at` e `updated_at`: datas de auditoria.

### `customers`
- `id`: identificador.
- `name`: nome do cliente.
- `email`: e-mail opcional ou obrigatório, conforme a regra definida.
- `document`: documento opcional, protegido conforme necessário.
- `created_at` e `updated_at`: datas de auditoria.

### `sales`
- `id`: identificador.
- `customer_id`: cliente associado, se aplicável.
- `sale_date`: data e hora da venda.
- `total_amount`: total calculado pelo backend.
- `status`: estado da venda, se houver fluxo de status.

### `sale_items`
- `id`: identificador.
- `sale_id`: venda associada.
- `product_id`: produto vendido.
- `quantity`: quantidade vendida.
- `unit_price`: preço unitário registrado no momento da venda.
- `subtotal`: subtotal do item.

O modelo final deve ser refletido nas entidades, migrations e restrições reais. Use `BigDecimal` para valores monetários em Java, evitando `float` ou `double` para cálculos financeiros.

## Regras de negócio importantes

1. O backend deve calcular os totais; não confie em valores enviados pelo navegador.
2. A quantidade de cada item deve ser maior que zero.
3. O preço não pode ser negativo.
4. Uma venda não deve ser confirmada se algum item não tiver estoque suficiente.
5. A gravação da venda e a baixa de estoque devem ocorrer na mesma transação.
6. Registre o preço aplicado em cada item da venda, para preservar o histórico mesmo que o preço do produto mude.
7. Defina como cancelamentos afetam estoque e relatórios, caso o cancelamento seja implementado.
8. Valide entradas tanto na camada web quanto na camada de serviço quando necessário.

## Relatórios com JasperReports

Uma implementação típica pode seguir este fluxo:

1. O usuário seleciona o período do relatório.
2. O controller valida os parâmetros e chama a camada de serviço.
3. O serviço consulta as vendas no período.
4. Os dados são enviados ao template `.jrxml`.
5. O JasperReports preenche o relatório e exporta o resultado em PDF.
6. A aplicação devolve o arquivo com o tipo de conteúdo `application/pdf`.

Sugestões para o primeiro relatório:
- Título e período selecionado.
- Data da venda e identificador.
- Nome do cliente, se houver.
- Quantidade de itens e total da venda.
- Total geral do período.

Inclua apenas informações necessárias no PDF e evite expor documentos pessoais ou outros dados sensíveis sem necessidade.

## Configuração local

### Pré-requisitos

- JDK compatível com a versão escolhida do Spring Boot.
- Maven, ou Maven Wrapper incluído no repositório.
- PostgreSQL.
- Git.

### 1. Clonar o projeto

```bash
git clone <URL_DO_SEU_REPOSITORIO>
cd mini-erp
```

### 2. Criar o banco

No PostgreSQL, crie um banco de desenvolvimento:

```sql
CREATE DATABASE mini_erp;
```

Crie um usuário específico para desenvolvimento e conceda apenas as permissões necessárias. Não use credenciais de produção em ambiente local.

### 3. Configurar variáveis de ambiente

Configure a conexão por variáveis de ambiente. Exemplo de `application.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mini_erp}
    username: ${DB_USERNAME:mini_erp}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  thymeleaf:
    cache: false

server:
  port: ${SERVER_PORT:8080}
```

Ajuste `ddl-auto` ao mecanismo de schema adotado. Em projetos com migrations, prefira versionar as alterações do banco e evitar `create` ou `update` como estratégia de produção. Se não houver migrations implementadas, documente claramente como o schema é criado.

No PowerShell, por exemplo:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/mini_erp"
$env:DB_USERNAME="mini_erp"
$env:DB_PASSWORD="sua_senha_local"
```

Não versione senhas, tokens, arquivos `.env` reais ou credenciais de qualquer ambiente.

### 4. Executar

Se o projeto usa Maven Wrapper:

```bash
./mvnw spring-boot:run
```

No Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Ou, com Maven instalado:

```bash
mvn spring-boot:run
```

Acesse `http://localhost:8080`, caso a aplicação esteja configurada para essa porta e possua uma página inicial.

## Testes

Execute a suíte com:

```bash
./mvnw test
```

No Windows:

```powershell
.\mvnw.cmd test
```

Casos importantes para cobrir:

- Criar, editar, consultar e excluir produtos.
- Rejeitar preço negativo e quantidade inválida.
- Rejeitar uma venda sem estoque suficiente.
- Confirmar que a venda e a baixa de estoque são atômicas.
- Confirmar o cálculo do total no backend.
- Filtrar relatórios por período.
- Gerar PDF quando existem vendas.
- Tratar relatório sem resultados e datas inválidas.
- Verificar que erros de validação retornam mensagens adequadas.

Se os testes de integração dependerem de PostgreSQL, documente como iniciar uma instância de teste. Testes unitários não devem depender de serviços externos sem necessidade.

## Segurança e boas práticas

- Nunca commitar segredos ou dados reais de clientes.
- Validar e normalizar os dados de entrada.
- Usar consultas parametrizadas e os mecanismos do Spring Data.
- Aplicar autenticação e autorização antes de disponibilizar dados comerciais em ambientes compartilhados.
- Não confiar em totais calculados pelo frontend.
- Configurar logs sem senhas, tokens ou dados pessoais desnecessários.
- Tratar erros sem retornar stack traces ao usuário.
- Usar transações nas operações que alteram vendas e estoque.
- Manter dependências atualizadas e revisar vulnerabilidades.

## Roadmap

- [ ] Implementar CRUD de produtos.
- [ ] Implementar CRUD de clientes.
- [ ] Implementar fluxo de vendas.
- [ ] Implementar baixa transacional de estoque.
- [ ] Criar relatório de vendas em PDF.
- [ ] Adicionar testes unitários e de integração.
- [ ] Adicionar Docker Compose para desenvolvimento, se útil.
- [ ] Criar screenshots reais da interface.
- [ ] Documentar endpoints ou fluxos principais.
- [ ] Configurar CI para executar os testes.

## Screenshots

Adicione capturas reais da aplicação nesta seção quando a interface estiver pronta.

```text
docs/
└── screenshots/
    ├── dashboard.png
    ├── products.png
    └── sales-report.png
```

Exemplo de inclusão no Markdown, depois de adicionar a imagem ao repositório:

```markdown
![Tela de produtos](docs/screenshots/products.png)
```

## Como contribuir

1. Crie uma branch para sua alteração.
2. Implemente a mudança e adicione testes.
3. Execute a suíte de testes.
4. Revise o diff para remover segredos, arquivos temporários e logs.
5. Abra um Pull Request com contexto, alterações e evidências de teste.

## Licença

Este projeto pode ser distribuído sob a licença MIT se você optar por essa licença. Antes de publicar, adicione um arquivo `LICENSE` com o texto oficial e confirme que todas as dependências e assets utilizados permitem essa distribuição.

## Sobre o projeto

O Mini ERP é um projeto demonstrativo para estudar e demonstrar desenvolvimento de aplicações de gestão com Java, Spring Boot, PostgreSQL, Thymeleaf e JasperReports. Atualize este README para refletir com precisão o que foi implementado, testado e validado no repositório.
