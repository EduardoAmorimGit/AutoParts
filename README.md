# 🔧 AutoParts

API REST de uma loja de peças e itens de manutenção automotiva: o cliente informa a **placa**, o sistema identifica o modelo exato do carro e mostra os itens **confirmados** para ele (filtros, óleos e afins), com entrega rápida no Rio de Janeiro.

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat&logo=spring-boot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_17-4169E1?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)

## ✨ Funcionalidades

- **Catálogo de produtos**: CRUD com validação, paginação e ordenação; SKU normalizado e único; controle de edição concorrente (`ETag` / `If-Match`).
- **Consulta por placa**: integração com a Simples API para identificar o veículo; o resultado é salvo para não consultar o provedor de novo, e as consultas pagas têm limite por IP.
- **Fichas de veículo e compatibilidade**: a compatibilidade é cadastrada por **ficha** (marca, modelo, motor, combustível, faixa de anos), não por placa. A placa é resolvida para a ficha automaticamente quando os dados batem sem ambiguidade; caso contrário, o veículo entra numa fila para o administrador.
- **Importação de catálogos**: aplicações de fabricantes/fornecedores entram por planilha CSV, sempre com a **fonte** registrada, e só aparecem para o cliente depois de **validadas** por um administrador. Nenhuma compatibilidade é inferida ou inventada.
- **Contas e login**: cadastro de clientes, login com **JWT** (token de acesso curto + refresh token rotativo), troca de senha e papel `ADMIN` para o painel.
- **Erros padronizados**: todas as respostas de erro seguem o `ProblemDetail` (RFC 9457 – `application/problem+json`).
- **LGPD**: guarda só o necessário do veículo (sem dono, CPF, endereço ou cor), não expõe placas em endpoints públicos e não registra placa, senha ou chave de API em log.

## 🧱 Stack

Java 17 · Spring Boot 4 (Web MVC, Data JPA, Security + OAuth2 Resource Server, Validation, Actuator) · PostgreSQL 17 · Flyway · Lombok · Docker Compose · JUnit 5 + MockMvc + Testcontainers · GitHub Actions

## 🏗️ Arquitetura

Monólito modular, organizado por domínio (package-by-feature):

```
src/main/java/.../
├── product/        # catálogo de produtos
├── vehicle/        # consulta por placa, normalização e persistência do veículo
├── compatibility/  # fichas de veículo, aplicações (fitments), importação CSV e resolução placa → ficha
├── identity/       # usuários, login JWT, refresh tokens, primeiro admin
├── integration/
│   └── vehicle/    # cliente da Simples API isolado atrás da interface VehicleLookupProvider
├── config/         # segurança, JWT, rate limit, handlers de 401/403
└── common/         # paginação, tratamento global de erros, rate limiter, leitor CSV
```

O provedor externo fica isolado atrás de uma interface (`VehicleLookupProvider`), então dá para trocar de fornecedor sem mexer no domínio. Nos testes, um *stub* substitui o provedor real.

## 🚀 Como rodar

Pré-requisitos: Java 17 e Docker.

```bash
# 1. Subir o PostgreSQL (porta 5433)
docker compose up -d

# 2. Variáveis de ambiente (opcional — há valores padrão para desenvolvimento)
cp .env.example .env

# 3. Rodar a aplicação (Windows: .\mvnw.cmd spring-boot:run)
./mvnw spring-boot:run
```

- As migrations do Flyway criam o banco automaticamente.
- Na primeira execução é criado o administrador `ADMIN_EMAIL` / `ADMIN_PASSWORD` (em desenvolvimento: `admin@autoparts.local` / `admin`).
- Sem `SIMPLES_API_KEY`, a consulta por placa responde `503`; o restante da API funciona normalmente.
- Sem `JWT_SECRET`, uma chave aleatória é usada em desenvolvimento (os tokens deixam de valer ao reiniciar).
- Com o perfil `prod`, a aplicação **não sobe** sem `JWT_SECRET` ou com a senha de admin padrão.

## 📡 Endpoints

**Cliente**

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/register` | público | Cria conta de cliente |
| POST | `/api/auth/login` | público | Login (retorna access + refresh token) |
| POST | `/api/auth/refresh` | público | Renova os tokens (rotação) |
| POST | `/api/auth/logout` | público | Revoga o refresh token |
| GET | `/api/me` | autenticado | Dados da própria conta |
| PUT | `/api/me/password` | autenticado | Troca a senha (encerra as outras sessões) |
| GET | `/api/products?page=0&size=20&sort=name,asc` | público | Lista produtos |
| GET | `/api/products/{id}` | público | Busca produto |
| POST | `/api/vehicles/lookup` | público | Consulta veículo pela placa |
| GET | `/api/vehicles/{id}/products` | público | Itens confirmados para o veículo (`RESOLVED` ou `PENDING`) |
| GET / POST / PUT / DELETE | `/api/me/addresses` | autenticado | Endereços de entrega (só cidade do Rio de Janeiro) |
| GET / PUT / DELETE | `/api/cart`, `/api/cart/items/{productId}` | autenticado | Carrinho (`GET /api/cart?addressId=` inclui a taxa de entrega do bairro) |
| POST | `/api/checkout` | autenticado | Cria o pedido (header `Idempotency-Key` obrigatório) |
| GET | `/api/orders`, `/api/orders/{id}` | autenticado | Meus pedidos |
| POST | `/api/orders/{id}/cancel` | autenticado | Cancela pedido aguardando pagamento |
| POST | `/api/orders/{id}/payments/pix` | autenticado | Gera o Pix (QR Code e copia e cola) |
| POST | `/api/orders/{id}/payments/card` | autenticado | Paga com cartão de crédito (token do Card Payment Brick) |
| GET | `/api/orders/{id}/payments` | autenticado | Pagamentos do pedido |
| GET | `/api/payments/config` | público | Chave pública do Mercado Pago |
| POST | `/api/payments/webhooks/mercadopago` | assinatura HMAC | Notificações do Mercado Pago |
| GET | `/actuator/health` | público | Health check |

**Administração** (papel `ADMIN`)

| Método | Rota | Descrição |
|---|---|---|
| POST / PUT / DELETE | `/api/products`, `/api/products/{id}` | Gerencia produtos |
| GET | `/api/vehicles/{id}` | Busca veículo (expõe a placa) |
| GET / POST / PUT / DELETE | `/api/admin/vehicle-specs` | Gerencia fichas de veículo |
| POST | `/api/admin/fitments/import` | Importa aplicações de um CSV (`dryRun=true` só valida) |
| GET | `/api/admin/fitments?status=IMPORTED` | Lista aplicações por status |
| POST | `/api/admin/fitments/review` | Valida ou rejeita aplicações em lote |
| POST / DELETE | `/api/admin/fitments` | Cadastra (já validada) ou remove aplicação |
| GET | `/api/admin/vehicles/pending` | Veículos consultados que ainda não têm ficha |
| PUT | `/api/admin/vehicles/{id}/spec` | Associa o veículo a uma ficha (vale para veículos iguais) |
| POST | `/api/admin/vehicles/rematch` | Refaz a identificação automática |
| GET | `/api/admin/users` | Lista usuários |
| GET / POST / PUT / DELETE | `/api/admin/delivery-regions` | Regiões de entrega: taxa e bairros de cada região (bairro sem região ativa com taxa definida não recebe entrega; as 5 regiões do Rio já vêm cadastradas sem taxa) |
| GET | `/api/admin/delivery-regions/uncovered-districts` | Bairros com endereços de clientes sem região de entrega |
| GET | `/api/admin/orders?status=`, `/api/admin/orders/{id}` | Pedidos |
| GET | `/api/admin/payments/attention` | Pagamentos que precisam de atenção (estornos automáticos, divergências, chargebacks) |
| GET / POST / PUT / DELETE | `/api/categories`, `/api/admin/categories` | Categorias (listagem pública) |
| POST | `/api/admin/products/import` | Importa produtos de um CSV (`dryRun=true` só valida) |

**Exemplo: login e criação de produto**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@autoparts.local","password":"admin"}' | jq -r .accessToken)

curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sku":"flt-oleo-001","name":"Filtro de óleo","brand":"Tecfil","price":39.90,"stockQuantity":25}'
```

**Exemplo: itens de um veículo**

```json
{
  "status": "RESOLVED",
  "spec": { "brand": "VOLKSWAGEN", "model": "GOL", "engine": "1.0", "fuel": "FLEX",
            "yearStart": 2017, "yearEnd": 2022, "oilCapacityLiters": 4.0 },
  "products": { "content": [ { "sku": "WO 340", "name": "Filtro de óleo Wega WO 340", "price": 29.90 } ],
                "page": 0, "size": 20, "totalElements": 1 }
}
```

**Planilha de importação de aplicações** (CSV com `;` ou `,`, UTF-8):

```
sku;marca;modelo;versao;motor;combustivel;ano_inicio;ano_fim;capacidade_oleo_litros;fonte;observacao
WO 340;VW;Gol;;1.0 8V;Flex;2017;2022;4,0;Catálogo Wega p.86;
```

## 🧪 Testes

Testes unitários e de integração (services, controllers, segurança, fluxo JWT completo, importação CSV, identificação de fichas e integração com o provedor externo). O banco de teste é um PostgreSQL descartável criado pelo **Testcontainers**, então só é preciso ter o Docker rodando.

```bash
./mvnw test
```

O GitHub Actions executa `./mvnw verify` a cada push e pull request.

## 🗺️ Próximos passos

- [x] Importação de produtos por planilha, categorias, especificações e kit troca de óleo
- [x] Taxa de entrega por região (bairros agrupados em regiões editáveis no painel)
- [x] Carrinho e checkout (entrega no Rio de Janeiro, reserva de estoque, expiração de pedidos não pagos)
- [x] Pagamento com Mercado Pago (Pix e crédito) — falta validar no sandbox
- [ ] Débito online
- [ ] Painel de operação de pedidos e rastreio da entrega
- [ ] Frontend da loja e do painel (React + TypeScript)
- [ ] Documentação interativa com Swagger/OpenAPI
- [ ] Deploy

---

Desenvolvido por **Eduardo Amorim** · [LinkedIn](https://www.linkedin.com/in/eduardosiamorim)
