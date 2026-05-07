# Event-Driven Replication Migration Phases

## Phase 1: Setup Event Infrastructure

```
┌─────────────────────────────────────────────────────────────┐
│                     Event Infrastructure                     │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            Amazon EventBridge / SNS/SQS              │  │
│  │                                                       │  │
│  │  Event Schemas:                                      │  │
│  │  - UserEvents (Created, Updated, Deleted)            │  │
│  │  - ProductEvents (Listed, Updated, Delisted)         │  │
│  │  - OrderEvents (Created, StatusChanged, Completed)   │  │
│  │  - ReviewEvents (Created, Updated, Deleted)          │  │
│  │  - CommentEvents (Created, Updated, Deleted)         │  │
│  │                                                       │  │
│  │  Infrastructure:                                     │  │
│  │  - Dead Letter Queues (DLQ)                          │  │
│  │  - CloudWatch Monitoring                             │  │
│  │  - Event Replay Capability                           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Phase 2: Initial Data Sync

```
┌──────────────────┐                         ┌──────────────────┐
│                  │    AWS DMS / ETL Job    │                  │
│  Monolith DB     │────────────────────────>│   Service DB     │
│  (Source)        │    One-time bulk copy   │   (Target)       │
│                  │                         │                  │
└──────────────────┘                         └──────────────────┘
      │                                              │
      │  Data Tables:                                │  Seeded Tables:
      │  - users                                     │  - users
      │  - products                                  │  - (service-specific)
      │  - orders                                    │
      │  - reviews                                   │
      │  - comments                                  │
      └──────────────────────────────────────────────┘
```

## Phase 3: Read-Only Service with Event Sync

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ┌──────────────┐                                                   │
│  │              │  Write Operations                                 │
│  │   Monolith   │────────┐                                          │
│  │              │        │                                          │
│  └──────────────┘        ▼                                          │
│         │         ┌──────────────┐                                  │
│         │         │  Monolith DB │                                  │
│         │         └──────────────┘                                  │
│         │                                                            │
│         │  Publish Events                                           │
│         │  (UserCreated, ProductListed, etc.)                       │
│         │                                                            │
│         ▼                                                            │
│  ┌──────────────────────────────────────┐                          │
│  │      Amazon EventBridge/SNS          │                          │
│  │                                       │                          │
│  │  Events:                              │                          │
│  │  {                                    │                          │
│  │    "eventType": "UserCreated",        │                          │
│  │    "aggregateId": "user-123",         │                          │
│  │    "data": {...}                      │                          │
│  │  }                                    │                          │
│  └──────────────────────────────────────┘                          │
│         │                                                            │
│         │  Consume Events                                           │
│         ▼                                                            │
│  ┌──────────────┐                                                   │
│  │              │  Read Operations Only                             │
│  │   Service    │◄────────────────────────── API Gateway           │
│  │              │                                                   │
│  └──────────────┘                                                   │
│         │                                                            │
│         │  Update local DB                                          │
│         ▼                                                            │
│  ┌──────────────┐                                                   │
│  │  Service DB  │                                                   │
│  │  (Synced)    │                                                   │
│  └──────────────┘                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

Traffic Flow:
- 100% writes → Monolith
- Reads split: 0-30% → Service (increasing gradually)
```

## Phase 4: Gradual Write Migration (Bidirectional)

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ┌──────────────┐                                                   │
│  │              │  Some Write Operations (90% → 50% → 10%)          │
│  │   Monolith   │────────┐                                          │
│  │              │        │                                          │
│  └──────────────┘        ▼                                          │
│         │         ┌──────────────┐                                  │
│         │         │  Monolith DB │                                  │
│         │         └──────────────┘                                  │
│         │                                                            │
│         │  Publish Events                                           │
│         ▼                                                            │
│  ┌──────────────────────────────────────┐                          │
│  │      Amazon EventBridge/SNS          │                          │
│  │                                       │                          │
│  │         ┌──────────────────┐         │                          │
│  │         │  Event Routing   │         │                          │
│  │         │  & Deduplication │         │                          │
│  │         └──────────────────┘         │                          │
│  │                                       │                          │
│  │  DLQ for failed events                │                          │
│  └──────────────────────────────────────┘                          │
│         │                      ▲                                    │
│         │                      │  Publish Events                    │
│         │                      │                                    │
│         │  Consume Events      │                                    │
│         ▼                      │                                    │
│  ┌──────────────┐              │                                    │
│  │              │  Some Write Operations (10% → 50% → 90%)          │
│  │   Service    │◄─────────────────────────── API Gateway           │
│  │              │                                                   │
│  └──────────────┘                                                   │
│         │                                                            │
│         │  Write to own DB                                          │
│         ▼                                                            │
│  ┌──────────────┐                                                   │
│  │  Service DB  │                                                   │
│  │  (Primary)   │                                                   │
│  └──────────────┘                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

Traffic Flow:
- Writes: Gradually shift from Monolith to Service (10% → 50% → 100%)
- Feature Flags control routing
- Both systems consume each other's events
- Conflict resolution: Service Wins (during migration)
```

## Phase 5: Full Cutover

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ┌──────────────┐                                                   │
│  │              │                                                   │
│  │   Monolith   │  (Retired - no longer processes requests)         │
│  │              │                                                   │
│  └──────────────┘                                                   │
│                                                                      │
│         ┌──────────────┐                                            │
│         │  Monolith DB │  (Archived - read-only backup)             │
│         └──────────────┘                                            │
│                                                                      │
│                                                                      │
│  ┌──────────────────────────────────────┐                          │
│  │      Amazon EventBridge/SNS          │                          │
│  │                                       │                          │
│  │  Service publishes events for:       │                          │
│  │  - Other microservices to consume    │                          │
│  │  - Audit trail                        │                          │
│  │  - Analytics                          │                          │
│  └──────────────────────────────────────┘                          │
│         ▲                      │                                    │
│         │                      │                                    │
│         │  Publish Events      │  Consumed by other services        │
│         │                      ▼                                    │
│  ┌──────────────┐      ┌──────────────┐                            │
│  │              │      │              │                            │
│  │   Service    │◄─────│  API Gateway │◄────── 100% Traffic        │
│  │              │      │              │                            │
│  └──────────────┘      └──────────────┘                            │
│         │                                                            │
│         │                                                            │
│         ▼                                                            │
│  ┌──────────────┐                                                   │
│  │  Service DB  │                                                   │
│  │  (Source of  │                                                   │
│  │   Truth)     │                                                   │
│  └──────────────┘                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

Traffic Flow:
- 100% writes → Service
- 100% reads → Service
- Service is the source of truth
- Continues publishing events for other services
```

## Event Flow Details

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Event Anatomy                                 │
│                                                                      │
│  {                                                                   │
│    "eventId": "550e8400-e29b-41d4-a716-446655440000",               │
│    "eventType": "UserCreated",                                      │
│    "eventVersion": "1.0",                                           │
│    "timestamp": "2026-05-07T10:30:00Z",                             │
│    "source": "monolith",                                            │
│    "aggregateId": "user-12345",                                     │
│    "data": {                                                         │
│      "userId": "user-12345",                                        │
│      "email": "user@example.com",                                   │
│      "name": "John Doe",                                            │
│      "createdAt": "2026-05-07T10:30:00Z"                            │
│    },                                                                │
│    "metadata": {                                                     │
│      "correlationId": "req-abc-123",                                │
│      "causationId": "event-xyz-789"                                 │
│    }                                                                 │
│  }                                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Event Processing Pipeline                         │
│                                                                      │
│  Publisher                Event Bus              Consumer            │
│  ┌────────┐              ┌────────┐              ┌────────┐         │
│  │        │              │        │              │        │         │
│  │ Write  │──publish───> │ Queue  │──consume──>  │ Read & │         │
│  │  to    │   event      │ Event  │   event      │ Update │         │
│  │  DB    │              │        │              │   DB   │         │
│  │        │              │        │              │        │         │
│  └────────┘              └────────┘              └────────┘         │
│                               │                                      │
│                               │  On Failure                          │
│                               ▼                                      │
│                          ┌────────┐                                  │
│                          │  DLQ   │──────> Manual Investigation      │
│                          │        │        & Replay                  │
│                          └────────┘                                  │
│                                                                      │
│  Retry Policy:                                                       │
│  - Attempt 1: Immediate                                              │
│  - Attempt 2: 5 seconds later                                        │
│  - Attempt 3: 30 seconds later                                       │
│  - Failed: Send to DLQ                                               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## Multiple Services Migration

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Microservices Architecture                          │
│                                                                      │
│                    ┌──────────────────────┐                         │
│                    │  Amazon EventBridge  │                         │
│                    │                      │                         │
│                    │   Event Router &     │                         │
│                    │   Message Broker     │                         │
│                    └──────────────────────┘                         │
│                             │                                        │
│          ┌──────────────────┼──────────────────┐                   │
│          │                  │                  │                   │
│          ▼                  ▼                  ▼                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐          │
│  │    User      │   │   Product    │   │    Order     │          │
│  │   Service    │   │   Service    │   │   Service    │          │
│  └──────────────┘   └──────────────┘   └──────────────┘          │
│         │                   │                   │                  │
│         ▼                   ▼                   ▼                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐          │
│  │   User DB    │   │  Product DB  │   │   Order DB   │          │
│  │  (Aurora)    │   │  (Aurora)    │   │  (Aurora)    │          │
│  └──────────────┘   └──────────────┘   └──────────────┘          │
│                                                                      │
│          ▼                  ▼                  ▼                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐          │
│  │    Review    │   │   Comment    │   │ Recommend    │          │
│  │   Service    │   │   Service    │   │   Service    │          │
│  └──────────────┘   └──────────────┘   └──────────────┘          │
│         │                   │                   │                  │
│         ▼                   ▼                   ▼                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐          │
│  │  Review DB   │   │  Comment DB  │   │ Recommend DB │          │
│  │  (Aurora)    │   │  (Aurora)    │   │  (Aurora)    │          │
│  └──────────────┘   └──────────────┘   └──────────────┘          │
│                                                                      │
│  Each service:                                                       │
│  - Has its own isolated Aurora PostgreSQL database                  │
│  - Publishes events when data changes                               │
│  - Consumes events from other services as needed                    │
│  - Maintains eventual consistency via events                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```
