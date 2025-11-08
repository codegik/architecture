# Deployment Architecture - Microservices on EKS

## Overview

This document describes the complete deployment architecture for the fintech payment platform, showing how microservices are deployed across AWS EKS clusters with high availability, scalability, and fault tolerance.

## Multi-Region Deployment

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         AWS Global Infrastructure                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌─────────────────────────────────┐  ┌────────────────────────────────┐│
│  │  Primary Region (us-east-1)     │  │ Secondary Region (sa-east-1)   ││
│  │  ┌───────────────────────────┐  │  │ ┌──────────────────────────┐  ││
│  │  │   EKS Cluster (3 AZs)     │  │  │ │  EKS Cluster (3 AZs)     │  ││
│  │  │   - Payment Service       │  │  │ │  - All Services (DR)     │  ││
│  │  │   - Fraud Detection       │  │  │ │  - Standby Mode          │  ││
│  │  │   - Account Service       │  │  │ │                          │  ││
│  │  │   - Transaction Service   │◄─┼──┼─┤  Cross-Region            │  ││
│  │  │   - Notification Service  │  │  │ │  Replication             │  ││
│  │  │   - Audit Service         │  │  │ │                          │  ││
│  │  │   - Tenant Registration   │  │  │ │                          │  ││
│  │  └───────────────────────────┘  │  │ └──────────────────────────┘  ││
│  │                                  │  │                               ││
│  │  ┌───────────────────────────┐  │  │ ┌──────────────────────────┐  ││
│  │  │   Aurora PostgreSQL       │  │  │ │  Aurora PostgreSQL       │  ││
│  │  │   (100 tenant DBs)        │◄─┼──┼─┤  (Read Replicas)         │  ││
│  │  │   Multi-AZ Primary        │  │  │ │                          │  ││
│  │  └───────────────────────────┘  │  │ └──────────────────────────┘  ││
│  │                                  │  │                               ││
│  │  ┌───────────────────────────┐  │  │ ┌──────────────────────────┐  ││
│  │  │   ElastiCache Redis       │  │  │ │  ElastiCache Redis       │  ││
│  │  │   (3 shards, 6 nodes)     │  │  │ │  (Standby)               │  ││
│  │  └───────────────────────────┘  │  │ └──────────────────────────┘  ││
│  └─────────────────────────────────┘  └────────────────────────────────┘│
│                                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Route 53 (DNS + Health Checks)                  │ │
│  │         Routes traffic to healthy region based on health checks    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## EKS Cluster Architecture (Single Region View)

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        VPC: fintech-payment-prod                           │
│                              10.0.0.0/16                                   │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │                      Public Subnets (3 AZs)                          │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │ │
│  │  │   AZ-1a     │  │   AZ-1b     │  │   AZ-1c     │                 │ │
│  │  │ 10.0.1.0/24 │  │ 10.0.2.0/24 │  │ 10.0.3.0/24 │                 │ │
│  │  │             │  │             │  │             │                 │ │
│  │  │  ┌───────┐  │  │  ┌───────┐  │  │  ┌───────┐  │                 │ │
│  │  │  │  ALB  │  │  │  │  ALB  │  │  │  │  ALB  │  │                 │ │
│  │  │  └───┬───┘  │  │  └───┬───┘  │  │  └───┬───┘  │                 │ │
│  │  │      │      │  │      │      │  │      │      │                 │ │
│  │  │  ┌───▼───┐  │  │  ┌───▼───┐  │  │  ┌───▼───┐  │                 │ │
│  │  │  │  NAT  │  │  │  │  NAT  │  │  │  │  NAT  │  │                 │ │
│  │  │  │Gateway│  │  │  │Gateway│  │  │  │Gateway│  │                 │ │
│  │  │  └───┬───┘  │  │  └───┬───┘  │  │  └───┬───┘  │                 │ │
│  │  └──────┼──────┘  └──────┼──────┘  └──────┼──────┘                 │ │
│  └─────────┼─────────────────┼─────────────────┼────────────────────────┘ │
│            │                 │                 │                          │
│  ┌─────────▼─────────────────▼─────────────────▼────────────────────────┐ │
│  │                  Private Subnets (EKS Worker Nodes)                  │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │ │
│  │  │   AZ-1a         │  │   AZ-1b         │  │   AZ-1c         │     │ │
│  │  │ 10.0.10.0/24    │  │ 10.0.11.0/24    │  │ 10.0.12.0/24    │     │ │
│  │  │                 │  │                 │  │                 │     │ │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │     │ │
│  │  │ │ Node Group  │ │  │ │ Node Group  │ │  │ │ Node Group  │ │     │ │
│  │  │ │ General     │ │  │ │ General     │ │  │ │ General     │ │     │ │
│  │  │ │ t3.large    │ │  │ │ t3.large    │ │  │ │ t3.large    │ │     │ │
│  │  │ │ (2 nodes)   │ │  │ │ (2 nodes)   │ │  │ │ (2 nodes)   │ │     │ │
│  │  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │     │ │
│  │  │                 │  │                 │  │                 │     │ │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │     │ │
│  │  │ │ Node Group  │ │  │ │ Node Group  │ │  │ │ Node Group  │ │     │ │
│  │  │ │ Compute     │ │  │ │ Compute     │ │  │ │ Compute     │ │     │ │
│  │  │ │ c6i.xlarge  │ │  │ │ c6i.xlarge  │ │  │ │ c6i.xlarge  │ │     │ │
│  │  │ │ (1 node)    │ │  │ │ (1 node)    │ │  │ │ (1 node)    │ │     │ │
│  │  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │     │ │
│  │  │                 │  │                 │  │                 │     │ │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │     │ │
│  │  │ │ Node Group  │ │  │ │ Node Group  │ │  │ │ Node Group  │ │     │ │
│  │  │ │ Memory      │ │  │ │ Memory      │ │  │ │ Memory      │ │     │ │
│  │  │ │ r6i.large   │ │  │ │ r6i.large   │ │  │ │ r6i.large   │ │     │ │
│  │  │ │ (1 node)    │ │  │ │ (1 node)    │ │  │ │ (1 node)    │ │     │ │
│  │  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │     │ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐ │
│  │              Isolated Subnets (Databases - No Internet)              │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │ │
│  │  │   AZ-1a         │  │   AZ-1b         │  │   AZ-1c         │     │ │
│  │  │ 10.0.20.0/24    │  │ 10.0.21.0/24    │  │ 10.0.22.0/24    │     │ │
│  │  │                 │  │                 │  │                 │     │ │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │     │ │
│  │  │ │  Aurora     │ │  │ │  Aurora     │ │  │ │  Aurora     │ │     │ │
│  │  │ │  Primary    │◄┼──┼─┤  Replica    │◄┼──┼─┤  Replica    │ │     │ │
│  │  │ │  (Writer)   │ │  │ │  (Reader)   │ │  │ │  (Reader)   │ │     │ │
│  │  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │     │ │
│  │  │                 │  │                 │  │                 │     │ │
│  │  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │     │ │
│  │  │ │  Redis      │ │  │ │  Redis      │ │  │ │  Redis      │ │     │ │
│  │  │ │  Shard 1    │ │  │ │  Shard 2    │ │  │ │  Shard 3    │ │     │ │
│  │  │ │  + Replica  │ │  │ │  + Replica  │ │  │ │  + Replica  │ │     │ │
│  │  │ └─────────────┘ │  │ └────────────���┘ │  │ └─────────────┘ │     │ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │ │
│  └──────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────┘
```

## Microservices Pod Distribution

```
┌─────────────────────────��────────────────────────────────────────────────┐
│                        EKS Cluster: fintech-payment-prod                 │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Availability Zone 1a    │  Availability Zone 1b    │  Availability Zone 1c │
│  ─────────────────────   │  ─────────────────────   │  ─────────────────────│
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Payment Service │     │  │ Payment Service │     │  │ Payment Service │  │
│  │ Pod 1           │     │  │ Pod 2           │     │  │ Pod 3           │  │
│  │ v1.2.3          │     │  │ v1.2.3          │     │  │ v1.2.3          │  │
│  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Payment Service │     │  │ Payment Service │     │  │ Payment Service │  │
│  │ Pod 4           │     │  │ Pod 5           │     │  │ Pod 6           │  │
│  │ v1.2.3          │     │  │ v1.2.3          │     │  │ v1.2.3          │  │
│  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Fraud Detection │     │  │ Fraud Detection │     │  │ Fraud Detection │  │
│  │ Service Pod 1   │     │  │ Service Pod 2   │     │  │ Service Pod 3   │  │
│  │ v2.1.0          │     │  │ v2.1.0          │     │  │ v2.1.0          │  │
│  │ 1000m CPU, 2Gi  │     │  │ 1000m CPU, 2Gi  │     │  │ 1000m CPU, 2Gi  │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Fraud Detection │     │  │ Fraud Detection │     │  │ Fraud Detection │  │
│  │ Service Pod 4   │     │  │ Service Pod 5   │     │  │ Service Pod 6   │  │
│  │ v2.1.0          │     │  │ v2.1.0          │     │  │ v2.1.0          │  │
│  │ 1000m CPU, 2Gi  │     │  │ 1000m CPU, 2Gi  │     │  │ 1000m CPU, 2Gi  │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Account Service │     │  │ Account Service │     │  │ Account Service │  │
│  │ Pod 1           │     │  │ Pod 2           │     │  │ Pod 3           │  │
│  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Transaction     │     │  │ Transaction     │     │  │ Transaction     │  │
│  │ Service Pod 1   │     │  │ Service Pod 2   │     │  │ Service Pod 3   │  │
│  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Notification    │     │  │ Notification    │     │  │ Notification    │  │
│  │ Service Pod 1   │     │  │ Service Pod 2   │     │  │ Service Pod 3   │  │
│  │ 250m CPU, 512Mi │     │  │ 250m CPU, 512Mi │     │  │ 250m CPU, 512Mi │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Audit Service   │     │  │ Audit Service   │     │  │ Audit Service   │  │
│  │ Pod 1           │     │  │ Pod 2           │     │  │ Pod 3           │  │
│  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │     │  │ 500m CPU, 1Gi   │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
│  ┌─────────────────┐     │  ┌─────────────────┐     │  ┌─────────────────┐  │
│  │ Tenant Reg.     │     │  │ Tenant Reg.     │     │  │ Tenant Reg.     │  │
│  │ Service Pod 1   │     │  │ Service Pod 2   │     │  │ Service Pod 3   │  │
│  │ 250m CPU, 512Mi │     │  │ 250m CPU, 512Mi │     │  │ 250m CPU, 512Mi │  │
│  └─────────────────┘     │  └─────────────────┘     │  └─────────────────┘  │
│                          │                          │                       │
└──────────────────────────────────────────────────────────────────────────┘

Note: Each service has Pod Anti-Affinity rules to ensure pods are distributed 
across different availability zones for high availability.
```

## Database Connection Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                     Microservice (e.g., Payment Service)               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │                  Tenant Connection Pool Manager                  │ │
│  │                                                                  │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐   │ │
│  │  │  Tenant A      │  │  Tenant B      │  │  Tenant C      │   │ │
│  │  │  Connection    │  │  Connection    │  │  Connection    │   │ │
│  │  │  Pool          │  │  Pool          │  │  Pool          │   │ │
│  │  │  ┌──────────┐  │  │  ┌──────────┐  │  │  ┌──────────┐  │   │ │
│  │  │  │ HikariCP │  │  │  │ HikariCP │  │  │  │ HikariCP │  │   │ │
│  │  │  │ Min: 2   │  │  │  │ Min: 2   │  │  │  │ Min: 2   │  │   │ │
│  │  │  │ Max: 20  │  │  │  │ Max: 20  │  │  │  │ Max: 20  │  │   │ │
│  │  │  └────┬─────┘  │  │  └────┬─────┘  │  │  └────┬─────┘  │   │ │
│  │  └───────┼────────┘  └───────┼────────┘  └───────┼────────┘   │ │
│  │          │                    │                   │            │ │
│  └──────────┼────────────────────┼───────────────────┼────────────┘ │
│             │                    │                   │              │
│             ▼                    ▼                   ▼              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │            AWS Secrets Manager / Kubernetes Secrets          │  │
│  │  tenant_a_db_url, tenant_a_db_user, tenant_a_db_password    │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       Isolated Database Subnets                        │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │   Tenant A DB    │  │   Tenant B DB    │  │   Tenant C DB    │   │
│  │   Aurora Cluster │  │   Aurora Cluster │  │   Aurora Cluster │   │
│  │   ┌───────────┐  │  │   ┌───────────┐  │  │   ┌───────────┐  │   │
│  │   │  Primary  │  │  │   │  Primary  │  │  │   │  Primary  │  │   │
│  │   │  Writer   │  │  │   │  Writer   │  │  │   │  Writer   │  │   │
│  │   │  (AZ-1a)  │  │  │   │  (AZ-1a)  │  │  │   │  (AZ-1a)  │  │   │
│  │   └───────────┘  │  │   └───────────┘  │  │   └───────────┘  │   │
│  │   ┌───────────┐  │  │   ┌───────────┐  │  │   ┌───────────┐  │   │
│  │   │  Replica  │  │  │   │  Replica  │  │  │   │  Replica  │  │   │
│  │   │  Reader   │  │  │   │  Reader   │  │  │   │  Reader   │  │   │
│  │   │  (AZ-1b)  │  │  │   │  (AZ-1b)  │  │  │   │  (AZ-1b)  │  │   │
│  │   └───────────┘  │  │   └───────────┘  │  │   └───────────┘  │   │
│  │   ┌───────────┐  │  │   ┌───────────┐  │  │   ┌───────────┐  │   │
│  │   │  Replica  │  │  │   │  Replica  │  │  │   │  Replica  │  │   │
│  │   │  Reader   │  │  │   │  Reader   │  │  │   │  Reader   │  │   │
│  │   │  (AZ-1c)  │  │  │   │  (AZ-1c)  │  │  │   │  (AZ-1c)  │  │   │
│  │   └───────────┘  │  │   └───────────┘  │  │   └───────────┘  │   │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘   │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

## Tenant Onboarding Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                      New Tenant Registration Flow                      │
└────────────────────────────────────────────────────────────────────────┘

1. Admin creates new tenant via API
         │
         ▼
┌─────────────────────────┐
│ Tenant Registration     │
│ Service                 │
│ - Validate tenant data  │
│ - Generate tenant_id    │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Provision Aurora DB     │
│ - Create cluster        │
│ - Run schema migrations │
│ - Create DB user        │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Store credentials in    │
│ AWS Secrets Manager     │
│ tenant_{id}_db_url      │
│ tenant_{id}_db_password │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Publish Kafka event     │
│ topic: tenant.created   │
│ payload: {tenant_id,    │
│          db_config}     │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ All Microservices consume event and:                        │
│ 1. Fetch DB credentials from AWS Secrets Manager            │
│ 2. Create new HikariCP connection pool for this tenant      │
│ 3. Initialize connection pool (min 2 connections)           │
│ 4. Mark tenant as "active" in local cache                   │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────┐
│ Tenant ready to accept  │
│ API requests            │
└─────────────────────────┘

Timeline: ~5-10 minutes for complete onboarding
```

## Rolling Update / Deployment Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Zero-Downtime Rolling Update                         │
└────────────────────────────────────────────────────────────────────────┘

Initial State: 6 pods running v1.2.3
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │  All healthy
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
   ▲       ▲       ▲       ▲       ▲       ▲
   └───────┴───────┴───────┴───────┴───────┘
              Traffic (100%)

Step 1: Deploy v1.2.4 (maxSurge: 1, maxUnavailable: 0)
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v2  │  New pod starting
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
   ▲       ▲       ▲       ▲       ▲       ▲       ⏳
   └───────┴───────┴───────┴───────┴───────┘
              Traffic (100% to v1)
              
Wait for v2 pod to be ready (readinessProbe passes)

Step 2: Route traffic to v2, terminate one v1 pod
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v2  │
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
   ▲       ▲       ▲       ▲       ▲       ▲
   └───────┴───────┴───────┴───────┴───────┘
        Traffic (83% v1, 17% v2)

Step 3: Repeat - deploy another v2 pod
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v1  │ │ v2  │ │ v2  │
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
   ▲       ▲       ▲       ▲       ▲       ▲       ⏳
   └───────┴───────┴───────┴───────┴───────┘
        Traffic (83% v1, 17% v2)

... Continue this pattern ...

Final State: All 6 pods running v1.2.4
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ v2  │ │ v2  │ │ v2  │ │ v2  │ │ v2  │ │ v2  │  All healthy
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
   ▲       ▲       ▲       ▲       ▲       ▲
   └───────┴───────┴───────┴───────┴───────┘
              Traffic (100% to v2)

Total deployment time: ~10-15 minutes (6 pods × 2-3 min each)
Zero downtime: Always 6 healthy pods serving traffic
```

## Canary Deployment Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                      Canary Deployment with Istio                      │
└────────────────────────────────────────────────────────────────────────┘

Step 1: Deploy canary version (10% traffic)
┌──────────────────────────────────────┐  ┌────────────────────────────┐
│     Stable (v1.2.3) - 6 pods         │  │  Canary (v1.2.4) - 1 pod   │
│  ┌─────┐ ┌─────┐ ┌─────┐            │  │      ┌─────┐               │
│  │ Pod │ │ Pod │ │ Pod │            │  │      │ Pod │               │
│  └─────┘ └─────┘ └─────┘            │  │      └─────┘               │
│  ┌─────┐ ┌─────┐ ┌─────┐            │  │                            │
│  │ Pod │ │ Pod │ │ Pod │            │  │                            │
│  └─────┘ └─────┘ └─────┘            │  │                            │
└──────────────────────────────────────┘  └────────────────────────────┘
            ▲                                       ▲
            │ 90%                                   │ 10%
            └───────────────┬───────────────────────┘
                            │
                   ┌────────▼────────┐
                   │  Istio Virtual  │
                   │  Service        │
                   │  (Weight-based  │
                   │   routing)      │
                   └─────────────────┘

Monitor for 1 hour:
- Error rate < 1%
- P99 latency < 500ms
- Business metrics (payment success rate)

Step 2: Increase to 25% traffic (if metrics good)
        Stable: 75%  │  Canary: 25% (2 pods)

Step 3: Increase to 50% traffic (if metrics good)
        Stable: 50%  │  Canary: 50% (3 pods)

Step 4: Increase to 100% traffic (if metrics good)
        Stable: 0%   │  Canary: 100% (6 pods)

Automatic Rollback Triggers:
- Error rate > 1% → immediate rollback to stable
- P99 latency > 500ms → immediate rollback to stable
- Payment success rate drops > 2% → immediate rollback to stable

Total canary rollout time: 4-6 hours
```

## Autoscaling Behavior

```
┌────────────────────────────────────────────────────────────────────────┐
│                    Horizontal Pod Autoscaling (HPA)                    │
└────────────────────────────────────────────────────────────────────────┘

Normal Load (CPU: 40%, Memory: 50%)
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │  6 replicas (min)
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘

         ↓ Load increases (CPU: 75%, Memory: 85%)

High Load - Scale Up (1 minute later)
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │  8 replicas
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘

         ↓ Load continues to increase (CPU: 85%)

Peak Load - Scale Up More (2 minutes later)
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
12 replicas (scaling continues up to max 30 replicas)

         ↓ Load decreases (CPU: 35%, Memory: 40%)
         ↓ Wait 10 minutes (scale-down stabilization)

Normal Load - Scale Down
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │ │ Pod │  Back to 6 replicas
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘

Cluster Autoscaler (Nodes):
- If pods are unschedulable → provision new nodes (3-5 minutes)
- If node utilization < 50% for 10 minutes → drain and terminate node
```

## Cost Breakdown (Monthly - Production)

```
┌────────────────────────────────────────────────────────────────────┐
│                      AWS Cost Estimation                           │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  EKS Cluster Control Plane                                         │
│  - Cost: $0.10/hour × 730 hours = $73 per region × 2 = $146       │
│                                                                    │
│  Worker Nodes (Average 30 nodes)                                   │
│  - General Purpose (t3.large): 18 nodes × $0.0832/hr × 730h       │
│    = $1,093/month                                                  │
│  - Compute Optimized (c6i.xlarge): 9 nodes × $0.17/hr × 730h      │
│    = $1,117/month                                                  │
│  - Memory Optimized (r6i.large): 3 nodes × $0.126/hr × 730h       │
│    = $276/month                                                    │
│  - Spot Instances Discount: 60% savings = -$1,390/month            │
│  - Reserved Instances (40% of capacity): 40% savings = -$600/month │
│  Total Worker Nodes: ~$3,600/month                                 │
│                                                                    │
│  Aurora PostgreSQL (100 tenant databases)                          │
│  - Primary instances: 100 × db.r6i.large × $0.205/hr × 730h       │
│    = $14,965/month                                                 │
│  - Read replicas: 200 × $0.205/hr × 730h × 0.5 (partial usage)    │
│    = $14,965/month                                                 │
│  - Storage: 50TB × $0.10/GB-month = $5,000/month                   │
│  - Backup storage: 20TB × $0.021/GB-month = $420/month             │
│  Total Aurora: ~$35,350/month (with optimization: ~$15,000)        │
│                                                                    │
│  ElastiCache Redis                                                 │
│  - 6 nodes × cache.r6g.large × $0.182/hr × 730h = $798/month      │
│  Total Redis: ~$600/month (with reserved instances)                │
│                                                                    │
│  Load Balancers & Networking                                       │
│  - ALB: 3 × $16.20/month + $0.008/LCU-hour = $200/month            │
│  - NAT Gateway: 3 × $32.40/month + data processing = $200/month    │
│  Total Networking: ~$400/month                                     │
│                                                                    │
│  Monitoring & Observability                                        │
│  - CloudWatch Logs: 500GB × $0.50/GB = $250/month                  │
│  - CloudWatch Metrics: Custom metrics = $50/month                  │
│  - X-Ray traces: 1M traces × $5/million = $5/month                 │
│  Total Monitoring: ~$300/month                                     │
│                                                                    │
│  Data Transfer                                                     │
│  - Inter-AZ data transfer: 2TB × $0.01/GB = $20/month              │
│  - Cross-region replication: 500GB × $0.02/GB = $10/month          │
│  - Internet egress: 5TB × $0.09/GB = $450/month                    │
│  Total Data Transfer: ~$500/month                                  │
│                                                                    │
│  Secrets Manager & KMS                                             │
│  - Secrets: 500 secrets × $0.40/month = $200/month                 │
│  - KMS requests: 10M requests × $0.03/10k = $30/month              │
│  Total Secrets & KMS: ~$230/month                                  │
│                                                                    │
├────────────────────────────────────────────────────────────────────┤
│  TOTAL ESTIMATED COST: ~$20,546/month                              │
│                                                                    │
│  With optimizations (Spot, RIs, right-sizing):                     │
│  OPTIMIZED COST: ~$15,000 - $18,000/month                          │
└────────────────────────────────────────────────────────────────────┘
```

## Key Deployment Features

### 1. High Availability
- Multi-AZ deployment (3 availability zones)
- Pod anti-affinity ensures distribution across AZs
- Aurora Multi-AZ with automatic failover (< 1 minute)
- Redis cluster with cross-AZ replication

### 2. Zero-Downtime Deployments
- Rolling updates with maxSurge=1, maxUnavailable=0
- Readiness probes ensure traffic only to healthy pods
- Graceful shutdown with 30-second termination grace period
- Pre-stop hooks to drain connections

### 3. Auto-Scaling
- Horizontal Pod Autoscaler (CPU/Memory thresholds)
- Cluster Autoscaler (add/remove nodes automatically)
- Aurora Serverless v2 (auto-scale database compute)
- Redis Cluster scaling (add/remove shards)

### 4. Security
- Private subnets for all compute and databases
- Security groups with least-privilege access
- Secrets in AWS Secrets Manager (not in code)
- Encryption in transit (TLS) and at rest (KMS)
- IAM roles for service accounts (IRSA)

### 5. Observability
- Health checks (liveness, readiness, startup probes)
- Prometheus metrics from all pods
- CloudWatch Container Insights for cluster metrics
- Distributed tracing with X-Ray
- Centralized logging with FluentBit → CloudWatch/Splunk

### 6. Disaster Recovery
- Cross-region Aurora replication
- Daily automated backups (7-day retention)
- Weekly manual snapshots (30-day retention)
- GitOps for configuration (all configs in Git)
- RTO: 1 hour | RPO: 5 minutes

---

**Document Version**: 1.0  
**Last Updated**: November 8, 2025  
**Maintained By**: Platform Engineering Team

