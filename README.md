# BadrLink - Notification Service

**The in-app notification and Web Push service powering BadrLink, built with Java, Spring Boot, PostgreSQL, Kafka, and Flyway.**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square\&logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square\&logo=openjdk\&logoColor=white)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square\&logo=postgresql\&logoColor=white)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-4.1.1-231F20?style=flat-square\&logo=apachekafka\&logoColor=white)](https://kafka.apache.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square\&logo=flyway\&logoColor=white)](https://documentation.red-gate.com/flyway)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square\&logo=docker\&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square\&logo=apachemaven\&logoColor=white)](https://maven.apache.org/)

A dedicated microservice responsible for the in-app notification feed, notification preferences, and Web Push delivery within BadrLink.

---

## Responsibilities

This service owns:

* The in-app notification feed
* Notification preferences (global mute per type, push toggle)
* Web Push subscriptions and delivery
* A read-model mirror of Chat room membership and per-room mute state
* A read-model map of message senders (to route reaction notifications)
* A Web Push outbox for delivery retries

It **does not call other services synchronously** and does not read their databases. Every input arrives as a Kafka event; the only outbound network calls are Web Push delivery to browser push services.

The service was a bare scaffold before Sprint 7.

---

## Architecture

The Notification Service consumes the platform's Kafka topics and owns its own PostgreSQL database.

```text
                    ┌─────────────────────┐
                    │    Chat Service     │
                    │       :8083         │
                    └──────────┬──────────┘
                               │
                    chat.message / room /
                    invitation events
                               │
                    ┌──────────┴──────────┐
                    │   User Service      │
                    │       :8082         │
                    └──────────┬──────────┘
                               │
                    user.profile events
                               │
                               ▼
                    ┌─────────────────────┐
                    │      Kafka          │
                    │       :9092         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │    Notification     │
                    │       :8085         │
                    ├─────────────────────┤
                    │ In-app feed         │
                    │ Preferences         │
                    │ Push subscriptions  │
                    │ Read-model caches   │
                    │ Push outbox         │
                    └───────┬───────┬─────┘
                            │       │
                            ▼       ▼
                    ┌──────────┐ ┌────────────┐
                    │PostgreSQL│ │  Web Push  │
                    │notif_db  │ │  (browser) │
                    │  :5434   │ │            │
                    └──────────┘ └────────────┘
```

The service owns its own database and does not share entities with other services.

Centralized references: [`/docs/API_ENDPOINTS.md`](../docs/API_ENDPOINTS.md), [`/docs/EVENTS.md`](../docs/EVENTS.md), [`/docs/adr/0009-notification-service.md`](../docs/adr/0009-notification-service.md), [`/docs/INCOHERENCES_AND_RESOLUTIONS.md`](../docs/INCOHERENCES_AND_RESOLUTIONS.md).

---

## Notification Flow

A notification is created from an event and delivered in two independent stages.

```text
Kafka event
      │
      ▼
Consumer (group: notification-service)
      │
      ▼
Local transaction
      ├── insert notification row (unique user + source)
      └── insert pending push delivery (if push enabled)
      │
      ▼
  committed ──► in-app feed served by REST API
      │
      ▼
Scheduled push relay
      │
      ▼
Web Push to the user's subscriptions
      │
      ├── 201/200 ──► stamped as delivered
      └── 410 Gone ─► dead subscription deleted
```

Network push calls never run on the Kafka listener thread, so a slow push endpoint cannot block offset commits.

---

## API

All endpoints are owner-scoped: the caller is taken from the JWT `sub`, never from the request.

### Notifications

| Method  | Endpoint                                  | Description                              |
| ------- | ----------------------------------------- | ---------------------------------------- |
| `GET`   | `/notifications`                          | Cursor-paginated feed (`cursor`, `limit`, `unreadOnly`) |
| `GET`   | `/notifications/unread-count`             | Unread badge count                       |
| `PATCH` | `/notifications/{id}/read`                | Mark one notification as read            |
| `POST`  | `/notifications/read-all`                 | Mark all notifications as read           |

### Subscriptions

| Method   | Endpoint                              | Description                                |
| -------- | ------------------------------------- | ------------------------------------------ |
| `POST`   | `/notifications/subscriptions`        | Register a Web Push subscription           |
| `DELETE` | `/notifications/subscriptions/{id}`   | Remove a Web Push subscription             |

### Preferences

| Method  | Endpoint                        | Description                              |
| ------- | ------------------------------- | ---------------------------------------- |
| `GET`   | `/notifications/preferences`    | Read `mutedTypes` and `pushEnabled`      |
| `PATCH` | `/notifications/preferences`    | Update `mutedTypes` and/or `pushEnabled` |

The list endpoints return a cursor page: `{ items, nextCursor, hasMore }`.

---

## Data Model

The service manages six tables:

```text
Notification
 ├── user, type, content
 ├── source (type + id)
 ├── created / delivered timestamps
 └── read flag

PushSubscription
 ├── endpoint
 └── p256dh / auth keys

NotificationPreference
 ├── muted types
 └── push enabled

RoomMembership (read model)
 ├── room ↔ user
 └── mute state

MessageSender (read model)
 └── message → sender

PendingPushDelivery (outbox)
 ├── notification
 ├── attempts
 └── delivered timestamp
```

A unique index on `(user_id, source_type, source_id)` makes notification creation idempotent: redelivered Kafka events are dropped cleanly instead of producing duplicates.

---

## Getting Started

### Requirements

* Java 21
* Docker
* Maven (or the included Maven Wrapper)
* A running Kafka broker (started by one of the Sprint 6 services' compose files)

### Environment Configuration

Copy the example environment file and configure the required variables:

```bash
cp .env.example .env
```

Then update `.env` with your local configuration if needed.

> **Note:** `.env` contains environment-specific values and should not be committed. Use `.env.example` as the template for required variables.

### Start the database

```bash
docker compose up -d
```

This starts PostgreSQL on `5434` with the `notification_db` database.

### Run the service

```bash
./mvnw spring-boot:run
```

The service will be available at:

```text
http://localhost:8085
```

### Run tests

```bash
./mvnw test
```

The test suite is database-free: services, consumers, and the push relay are exercised with mocks, and the Web Push crypto has a round-trip test. No Docker is required.

---


## Web Push

* VAPID credentials are supplied through environment variables.
* Payloads use RFC 8291 `aes128gcm` encryption and an RFC 8292 ES256 VAPID token, implemented against the JDK's JCA primitives.
* Push is skipped when a user has `pushEnabled=false` or no subscriptions; the in-app row is still created.
* A push service returning HTTP `410 Gone` deletes the dead subscription.
* Delivery is retried up to `PUSH_MAX_ATTEMPTS` through the `pending_push_deliveries` outbox.

---

## Testing

The service currently contains **21 tests** covering:

* `MESSAGE_SENT` fan-out rules (sender and muted members skipped)
* Self-reaction suppression
* Idempotent duplicate insert handling
* Block privacy (`USER_BLOCKED` produces zero notifications)
* HTTP `410` deleting dead push subscriptions
* Push enqueue rules (muted type, `pushEnabled=false`, subscriptions)
* Cursor pagination and malformed cursors
* Web Push payload encryption round-trip and VAPID signature verification

---

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/ziyadsamhaoui/messagingnotificationservice/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── exception/
│   │       ├── kafka/
│   │       ├── model/
│   │       ├── push/
│   │       ├── repository/
│   │       ├── security/
│   │       └── service/
│   └── resources/
│       ├── db/migration/
│       └── application.yaml
└── test/
    └── java/
```

---

## Current Scope & Limitations

The service currently provides:

* In-app notifications with database-enforced idempotency
* Per-type global mute and a push toggle
* Web Push delivery through a local outbox
* Membership and mute read models fed entirely by events

It currently does **not** provide:

* Email or mobile push channels (Web Push only)
* A WebSocket delivery channel (notifications are pulled through REST)
* Notifications for blocks, unblocks, or declined invitations (deliberate)
* Strong consistency with Chat/User: a message may fan out before its room's membership events are consumed

---

## Related Services

BadrLink is split into several independent services:

| Service          | Port   | Responsibility                                        |
| ---------------- | ------ | ----------------------------------------------------- |
| API Gateway      | `8080` | External routing, authentication edge, rate limiting  |
| Auth Service     | `8081` | Authentication, credentials, JWT                      |
| User Service     | `8082` | Profiles, blocks, connections                         |
| Chat Service     | `8083` | Rooms, participants, messages                         |
| Realtime Gateway | `8084` | STOMP, WebSocket, realtime delivery                   |
| Notification     | `8085` | In-app notifications, Web Push                        |
| Kafka            | `9092` | Shared event backbone                                 |
