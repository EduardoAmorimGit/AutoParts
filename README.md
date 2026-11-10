# 🔧 AutoParts

API REST para um SaaS de autopeças: catálogo de produtos, consulta de veículo pela **placa** e **compatibilidade peça × veículo** — o cliente informa a placa e descobre quais peças servem no carro dele.

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat&logo=spring-boot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_17-4169E1?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat&logo=flyway&logoColor=white)

## ✨ Funcionalidades

- **Catálogo de produtos**: CRUD completo com validação, paginação e ordenação; SKU normalizado e único.
- **Consulta por placa**: integração com API externa (Simples API) para identificar o veículo; o resultado é salvo para não consultar o provedor de novo.
- **Compatibilidade**: relação N:N entre produtos e veículos (`/api/products/{id}/vehicles` e `/api/vehicles/{id}/products`).
- **Segurança**: leitura pública; escrita protegida por HTTP Basic com papel `ADMIN` (Spring Security).
- **Erros padronizados**: todas as respostas de erro seguem o `ProblemDetail` (RFC 9457 – `application/problem+json`).
- **LGPD**: guarda só o necessário do veículo (sem dono, CPF, endereço ou cor) e não registra placa nem chave de API em log.

## 🧱 Stack

Java 17 · Spring Boot 4 (Web MVC, Data JPA, Security, Validation, Actuator) · PostgreSQL 17 · Flyway · Lombok · Docker Compose · JUnit 5 + MockMvc

## 🏗️ Arquitetura

Organização por domínio (package-by-feature):

```
src/main/java/.../
├── product/        # catálogo: controller, service, repository, DTOs, exceções
├── vehicle/        # consulta por placa e normalização da placa
├── compatibility/  # relação produto × veículo
├── integration/
│   └── vehicle/    # cliente da API externa isolado atrás da interface VehicleLookupProvider
├── config/         # segurança e handlers de 401/403 em ProblemDetail
└── common/         # envelope de paginação (PageResponse)
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

As migrations do Flyway criam o banco automaticamente. Sem `SIMPLES_API_KEY`, a consulta por placa responde `503`, e o restante da API funciona normalmente.

## 📡 Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| GET | `/api/products?page=0&size=20&sort=name,asc` | público | Lista produtos (paginado) |
| GET | `/api/products/{id}` | público | Busca produto |
| POST | `/api/products` | ADMIN | Cria produto |
| PUT | `/api/products/{id}` | ADMIN | Atualiza produto |
| DELETE | `/api/products/{id}` | ADMIN | Remove produto |
| GET | `/api/products/{id}/vehicles` | público | Veículos compatíveis com o produto |
| POST | `/api/vehicles/lookup` | público | Consulta veículo pela placa |
| GET | `/api/vehicles/{id}` | público | Busca veículo |
| GET | `/api/vehicles/{id}/products` | público | Peças compatíveis com o veículo |
| GET | `/actuator/health` | público | Health check |

**Exemplo — criar produto**

```bash
curl -u admin:admin -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"flt-oleo-001","name":"Filtro de óleo","brand":"Tecfil","price":39.90,"stockQuantity":25}'
```

```json
{
  "id": 1, "sku": "FLT-OLEO-001", "name": "Filtro de óleo", "description": null,
  "brand": "Tecfil", "price": 39.90, "stockQuantity": 25, "active": true,
  "createdAt": "...", "updatedAt": "..."
}
```

**Exemplo — erro padronizado (SKU duplicado)**

```json
{ "type": "about:blank", "title": "Duplicate SKU", "status": 409, "detail": "...", "instance": "/api/products" }
```

## 🧪 Testes

Testes unitários e de integração (service, controller, repository, tratamento de erros e integração com o provedor externo).

```bash
docker compose up -d   # os testes de integração usam o Postgres (schema autoparts_test)
./mvnw test
```

## 🗺️ Próximos passos

- [ ] Documentação interativa com Swagger/OpenAPI
- [ ] Carrinho e pedidos
- [ ] Pipeline de CI no GitHub Actions
- [ ] Deploy público

---

Desenvolvido por **Eduardo Amorim** · [LinkedIn](https://www.linkedin.com/in/eduardosiamorim)
