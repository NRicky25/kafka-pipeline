# Kafka Streaming Pipeline (MVP)

## 📌 Overview

This project is a **streaming data pipeline** built with **Java, Apache Kafka, and PostgreSQL**.  
It demonstrates how real-time events can be produced, consumed, and persisted to a database for further analysis.

The system simulates **transaction events** (purchases by customers) and processes them through a Kafka topic.  
A consumer application stores both the raw transactions and aggregated per-customer totals in PostgreSQL.

This MVP showcases core data engineering concepts:

- **Streaming ingestion** (Kafka Producer)
- **Real-time processing** (Kafka Consumer)
- **Persistent storage** (PostgreSQL with aggregation)

---

## ⚙️ Architecture

Producer (Java) → Kafka Topic → Consumer (Java) → PostgreSQL

- **ProducerApp** generates synthetic transaction events and publishes them to the `transactions` topic.
- **ConsumerApp** listens to the topic, writes events to a `transactions_raw` table, and maintains running totals in a `customer_agg` table.
- **Postgres** stores both raw and aggregated data.

---

## 🚀 How It Works

**Producer** creates random transactions with:

- `id` (UUID)
- `ts` (timestamp)
- `customer_id` (customer reference)
- `amount_cents` (purchase amount)
- `category` (e.g., grocery, fitness, electronics)

**Consumer**:

- Inserts each transaction into `transactions_raw`
- Updates per-customer totals in `customer_agg`

**Postgres**:

- Holds the persisted data
- Allows downstream queries & analytics

---

## 🛠️ Setup Instructions

### 1. Clone the repository

```bash
git clone https://github.com/NRicky25/kafka-pipeline.git
cd kafka-pipeline
```

### 2. Environment setup

Copy and configure env files:

```bash
cp .env.example .env
cp app/src/main/resources/app.properties.example app/src/main/resources/app.properties
```

### 3. Start infrastructure

docker compose up -d

### 4. Create tables (first run only)

```bash
docker exec -e PGPASSWORD=app -it kp-postgres psql -U app -d streamdb -c "
CREATE TABLE IF NOT EXISTS public.transactions_raw (
  id UUID PRIMARY KEY,
  ts TIMESTAMPTZ NOT NULL,
  customer_id TEXT NOT NULL,
  amount_cents INT NOT NULL,
  category TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS public.customer_agg (
  customer_id TEXT PRIMARY KEY,
  total_cents BIGINT NOT NULL DEFAULT 0,
  txn_count BIGINT NOT NULL DEFAULT 0
);
"
```

### 5. Run the consumer

```bash
./gradlew -p app runConsumer
```

### 6. Run the producer (in another terminal)

```bash
./gradlew -p app runProducer
```

## 📊 Verifying Data Flow

### Check row counts:

```bash
docker exec -e PGPASSWORD=app -it kp-postgres psql -U app -d streamdb -c \
"SELECT COUNT(*) FROM transactions_raw;"
```

### Check aggregates:

```bash
docker exec -e PGPASSWORD=app -it kp-postgres psql -U app -d streamdb -c \
"SELECT * FROM customer_agg ORDER BY customer_id;"
```

### Example output:

```bash
 customer_id | total_cents | txn_count
-------------+-------------+-----------
 c001        |      758489 |       254
 c002        |      757283 |       254
 c003        |      704332 |       236
 c004        |      646465 |       225
 c005        |      703739 |       231

```

## 🎯 MVP Achievements

✅ Built a real-time ingestion pipeline with Kafka & Java

✅ Stored both raw and aggregated data in PostgreSQL

✅ Verified end-to-end flow with synthetic transactions

✅ Configured clean secrets handling with .env and app.properties
