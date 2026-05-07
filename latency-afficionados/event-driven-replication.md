# Event-Driven Replication Migration Phases

## Phase 1: Setup Event Infrastructure

```
┌────────────────────────────────────────────────────────────┐
│                     Event Infrastructure                   │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            Amazon EventBridge / SNS/SQS              │  │
│  │                                                      │  │
│  │  Event Schemas:                                      │  │
│  │  - UserEvents (Created, Updated, Deleted)            │  │
│  │  - ProductEvents (Listed, Updated, Delisted)         │  │
│  │  - OrderEvents (Created, StatusChanged, Completed)   │  │
│  │  - ReviewEvents (Created, Updated, Deleted)          │  │
│  │  - CommentEvents (Created, Updated, Deleted)         │  │
│  │                                                      │  │
│  │  Infrastructure:                                     │  │
│  │  - Dead Letter Queues (DLQ)                          │  │
│  │  - CloudWatch Monitoring                             │  │
│  │  - Event Replay Capability                           │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

## Phase 2: Initial Data Sync

```
┌──────────────────┐                         ┌──────────────────┐
│                  │                         │                  │
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
│                                                                     │
│  ┌──────────────┐                                                   │
│  │              │  Write Operations                                 │
│  │   Monolith   │────────┐                                          │
│  │              │        │                                          │
│  └──────────────┘        ▼                                          │
│         │         ┌──────────────┐                                  │
│         │         │  Monolith DB │                                  │
│         │         └──────────────┘                                  │
│         │                                                           │
│         │  Publish Events                                           │
│         │  (UserCreated, ProductListed, etc.)                       │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────────────────────────────-─┐                          │
│  │      Amazon EventBridge/SNS           │                          │
│  │                                       │                          │
│  │  Events:                              │                          │
│  │  {                                    │                          │
│  │    "eventType": "UserCreated",        │                          │
│  │    "aggregateId": "user-123",         │                          │
│  │    "data": {...}                      │                          │
│  │  }                                    │                          │
│  └──────────────────────────────────────┘                           │
│         │                                                           │
│         │  Consume Events                                           │
│         ▼                                                           │
│  ┌──────────────┐                                                   │
│  │              │  Read Operations Only                             │
│  │   Service    │◄────────────────────────── API Gateway            │
│  │              │                                                   │
│  └──────────────┘                                                   │
│         │                                                           │
│         │  Update local DB                                          │
│         ▼                                                           │
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
│                                                                     │
│  ┌──────────────┐                                                   │
│  │              │  Some Write Operations (90% → 50% → 10%)          │
│  │   Monolith   │────────┐                                          │
│  │              │        │                                          │
│  └──────────────┘        ▼                                          │
│         │         ┌──────────────┐                                  │
│         │         │  Monolith DB │                                  │
│         │         └──────────────┘                                  │
│         │                                                           │
│         │  Publish Events                                           │
│         ▼                                                           │
│  ┌──────────────────────────────────────┐                           │
│  │      Amazon EventBridge/SNS          │                           │
│  │                                      │                           │
│  │         ┌──────────────────┐         │                           │
│  │         │  Event Routing   │         │                           │
│  │         │  & Deduplication │         │                           │
│  │         └──────────────────┘         │                           │
│  │                                      │                           │
│  │  DLQ for failed events               │                           │
│  └──────────────────────────────────────┘                           │
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
│         │                                                           │
│         │  Write to own DB                                          │
│         ▼                                                           │
│  ┌──────────────┐                                                   │
│  │  Service DB  │                                                   │
│  │  (Primary)   │                                                   │
│  └──────────────┘                                                   │
│                                                                     │
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
│                                                                     │
│  ┌──────────────┐                                                   │
│  │              │                                                   │
│  │   Monolith   │  (Retired - no longer processes requests)         │
│  │              │                                                   │
│  └──────────────┘                                                   │
│                                                                     │
│         ┌──────────────┐                                            │
│         │  Monolith DB │  (Archived - read-only backup)             │
│         └──────────────┘                                            │
│                                                                     │
│                                                                     │
│  ┌──────────────────────────────────────┐                           │
│  │      Amazon EventBridge/SNS          │                           │
│  │                                      │                           │
│  │  Service publishes events for:       │                           │
│  │  - Other microservices to consume    │                           │
│  │  - Audit trail                       │                           │
│  │  - Analytics                         │                           │
│  └──────────────────────────────────────┘                           │
│         ▲                      │                                    │
│         │                      │                                    │
│         │  Publish Events      │  Consumed by other services        │
│         │                      ▼                                    │
│  ┌──────────────┐      ┌──────────────┐                             │
│  │              │      │              │                             │
│  │   Service    │◄─────│  API Gateway │◄────── 100% Traffic         │
│  │              │      │              │                             │
│  └──────────────┘      └──────────────┘                             │
│         │                                                           │
│         │                                                           │
│         ▼                                                           │
│  ┌──────────────┐                                                   │
│  │  Service DB  │                                                   │
│  │  (Source of  │                                                   │
│  │   Truth)     │                                                   │
│  └──────────────┘                                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

Traffic Flow:
- 100% writes → Service
- 100% reads → Service
- Service is the source of truth
- Continues publishing events for other services
```
