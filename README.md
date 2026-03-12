# 🐇 Spring Boot Microservices with RabbitMQ

A production-grade Spring Boot 2.x microservices project demonstrating async event-driven
communication via RabbitMQ.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client / API                             │
└────────────────────────────┬────────────────────────────────────┘
                             │ POST /api/v1/orders
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     ORDER SERVICE :8081                         │
│  - PostgreSQL (orders, order_items tables)                      │
│  - Publishes: OrderCreatedEvent                                 │
│  - Consumes: InventoryResponseEvent                             │
│  - Resilience4j CircuitBreaker                                  │
└──────────────┬────────────────────────┬────────────────────────┘
               │                        ▲
               │ order.created          │ inventory.response
               ▼                        │
┌─────────────────────────────────────────────────────────────────┐
│                        RABBITMQ :5672                           │
│                   Management UI :15672                          │
│                                                                 │
│  Exchanges:                                                     │
│    order.exchange  (Topic)                                      │
│    order.dlx       (Direct, Dead Letter)                        │
│                                                                 │
│  Queues:                                                        │
│    order.created.queue      ──dlq──▶  order.created.queue.dlq  │
│    inventory.response.queue                                     │
└──────────────┬────────────────────────┬────────────────────────┘
               │                        │
       order.created            inventory.response
               │                        │
     ┌─────────▼──────────┐   ┌────────▼────────────────┐
     │  INVENTORY :8082   │   │  NOTIFICATION :8083      │
     │  - MongoDB         │   │  - Redis (idempotency)   │
     │  - Check stock     │   │  - Email on events       │
     │  - Reserve items   │   └─────────────────────────-┘
     │  - Reply to Order  │
     └────────────────────┘
```

---

## 🔄 Event Flow

```
1. Client POST /api/v1/orders
2. Order Service saves order (PENDING) to PostgreSQL
3. Order Service publishes OrderCreatedEvent → order.exchange
4. Inventory Service receives OrderCreatedEvent
   a. Checks all product stock in MongoDB
   b. If all available: reserves stock, publishes InventoryResponseEvent (available=true)
   c. If not: publishes InventoryResponseEvent (available=false)
5. Order Service receives InventoryResponseEvent
   a. available=true  → status = INVENTORY_RESERVED
   b. available=false → status = FAILED
6. Notification Service receives BOTH events (fan-out via separate queue bindings)
   a. Sends order confirmation email on OrderCreatedEvent
   b. Sends status update email on InventoryResponseEvent
   c. Uses Redis for idempotency (deduplication)
```

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Maven 3.8+

### Run with Docker Compose

```bash
# Clone / navigate to project root
cd rabbitmq-microservices

# Start all services
docker-compose up -d

# Check logs
docker-compose logs -f order-service
docker-compose logs -f inventory-service
docker-compose logs -f notification-service
```

### Run locally (dev mode)

```bash
# Start infrastructure only
docker-compose up -d rabbitmq postgres mongodb redis

# Terminal 1 - Order Service
cd order-service
mvn spring-boot:run

# Terminal 2 - Inventory Service
cd inventory-service
mvn spring-boot:run

# Terminal 3 - Notification Service
cd notification-service
mvn spring-boot:run
```

---

## 🧪 Test the Flow

### 1. Create an Order

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "customerEmail": "customer@example.com",
    "items": [
      {
        "productId": "PROD-001",
        "productName": "MacBook Pro 14",
        "quantity": 1,
        "unitPrice": 1999.00
      },
      {
        "productId": "PROD-002",
        "productName": "iPhone 15 Pro",
        "quantity": 2,
        "unitPrice": 999.00
      }
    ]
  }'
```

### 2. Check Order Status

```bash
# Replace ORD-XXXXXXXX with your order number from step 1
curl http://localhost:8081/api/v1/orders/ORD-XXXXXXXX
```

### 3. View All Orders

```bash
curl http://localhost:8081/api/v1/orders
```

### 4. View Inventory

```bash
curl http://localhost:8082/api/v1/inventory
```

### 5. Test Insufficient Stock (order more than available)

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-002",
    "customerEmail": "test@example.com",
    "items": [
      {
        "productId": "PROD-001",
        "productName": "MacBook Pro 14",
        "quantity": 9999,
        "unitPrice": 1999.00
      }
    ]
  }'
```

---

## 📊 RabbitMQ Management UI

URL: http://localhost:15672  
Username: `admin`  
Password: `admin123`

Useful views:
- **Queues** → see message counts, DLQ activity
- **Exchanges** → see `order.exchange` bindings
- **Connections** → verify service connections

---

## 🔧 Service URLs

| Service | URL | Description |
|---------|-----|-------------|
| Order Service | http://localhost:8081 | REST API |
| Inventory Service | http://localhost:8082 | REST API |
| Notification Service | http://localhost:8083 | Event consumer |
| RabbitMQ UI | http://localhost:15672 | Management console |
| PostgreSQL | localhost:5432 | ordersdb |
| MongoDB | localhost:27017 | inventorydb |
| Redis | localhost:6379 | Idempotency store |

---

## 🛡️ Production Patterns Used

| Pattern | Implementation |
|---------|---------------|
| **Dead Letter Queue (DLQ)** | Failed messages → `order.created.queue.dlq` |
| **Manual ACK** | `channel.basicAck/Nack` for reliable processing |
| **Idempotency** | Redis deduplication in Notification Service |
| **Circuit Breaker** | Resilience4j on Order Service |
| **Message TTL** | 5-min TTL on `order.created.queue` |
| **Prefetch** | Per-service prefetch limits |
| **Publisher Confirms** | RabbitTemplate confirm callback |
| **Exponential Backoff** | Spring AMQP retry with multiplier |
| **Health Checks** | Docker Compose healthchecks + Actuator |

---
