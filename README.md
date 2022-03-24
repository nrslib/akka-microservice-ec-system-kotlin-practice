# Akka Microservice by Kotlin

---

## Build

### Publish artifact to local

```bash
gradlew publishToMavenLocal
```

in the following path

- ./shared
- ./billing/billing-service-api
- ./order/order-service-api
- ./stock/stock-service-api

### Make Fat Jar for building docker image

```bash
gradlew shadowJar
```

in the following path

- ./billing/billing-service
- ./order/order-service
- ./stock/stock-service

### Prepare Infra

#### Create network

```bash
docker network create develop-network
```

#### Launch confluent platform (kafka)

```bash
docker-compose -f ./infra/confluent-platform/docker-compose.yml up -d
```

### Run services

```bash
docker-compose up -d
```

---

## Usage

### OrderCreateSaga

1. post order (order-service)

    ```bash
    curl --location --request POST 'http://localhost:5000/orders' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "accountId" : "test-account-id"
    }'
    ```

2. get billing id list (billing-service)

    ```bash
    curl --location --request GET 'http://localhost:5001/billings'
    ```

3. approve billing by billing id (billing-service)

    ```bash
    curl --location --request POST 'http://localhost:5001/billings/{billing-id}/approve'
    ```
   