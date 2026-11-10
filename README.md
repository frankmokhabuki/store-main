# Store Application
The Store application keeps track of customers and orders in a database.

# Assumptions
This README assumes you're using a posix environment. It's possible to run this on Windows as well:
* Instead of `./gradlew` use `gradlew.bat`
* The syntax for creating the Docker container is different. You could also install PostgreSQL on bare metal if you prefer


# Prerequisites
This service assumes the presence of a postgresql 16.2 database server running on localhost:5433 (note the non-standard port)
It assumes a username and password `admin:admin` can be used.
It assumes there's already a database called `store`

You can start the PostgreSQL instance like this:
```shell
docker run -d \
  --name postgres \
  --restart always \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin \
  -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data \
  -p 5433:5432 \
  postgres:16.2 \
  postgres -c wal_level=logical
```

# Running the application
You should be able to run the service using
```shell
./gradlew bootRun
```

The application uses Liquibase to migrate the schema. Some sample data is provided. You can create more data by reading the documentation in utils/README.md

# Data model
An order has an ID, a description, and is associated with the customer which made the order.
A customer has an ID, a name, and 0 or more orders.

# API
Two endpoints are provided:
   * /order
   * /customer

Each of them supports a POST and a GET. The data model is circular - a customer owns a number of orders, and that order necessarily refers back to the customer which owns it.
To avoid loops in the serializer, when writing out a Customer or an Order, they're mapped to CustomerDTO and OrderDTO which contain truncated versions of the dependent object - CustomerOrderDTO and OrderCustomerDTO respectively.

The API is documented in the OpenAPI file OpenAPI.yaml. Note that this spec includes part of one of the tasks below (the new /products endpoint)

# Tasks

1. Extend the order endpoint to find a specific order, by ID
2. Extend the customer endpoint to find customers based on a query string to match a substring of one of the words in their name
3. Users have complained that in production the GET endpoints can get very slow. The database is unfortunately not co-located with the application server, and there's high latency between the two. Identify if there are any optimisations that can improve performance
4. Add a new endpoint /products to model products which appear in an order:
      * A single order contains 1 or more products. 
      * A product has an ID and a description. 
      * Add a POST endpoint to create a product
      * Add a GET endpoint to return all products, and a specific product by ID
        * In both cases, also return a list of the order IDs which contain those products
      * Change the orders endpoint to return a list of products contained in the order

# Bonus points
1. Implement a CI pipeline on the platform of your choice to build the project and deliver it as a Dockerized image

# Notes on the tasks
Assume that the project represents a production application.
Think carefully about the impact on performance when implementing your changes
The specifications of the tasks have been left deliberately vague. You will be required to exercise judgement about what to deliver - in a real world environment, you would clarify these points in refinement, but since this is a project to be completed without interaction, feel free to make assumptions - but be prepared to defend them when asked.
There's no CI pipeline associated with this project, but in reality there would be. Consider the things that you would expect that pipeline to verify before allowing your code to be promoted
Feel free to refactor the codebase if necessary. Bad choices were deliberately made when creating this project.

# Implementation notes

This section documents the decisions and assumptions made while implementing the tasks above.

## Task 1 - Get order by ID
`GET /order/{id}` returns the order (including its customer and products) or a `404` if the
ID does not exist. The lookup uses the same fetch-join query as the list endpoint, so it costs
exactly one database round trip.

## Task 2 - Customer search
`GET /customer?query=<text>` performs a **case-insensitive substring** match on the customer
name. Assumptions made:
* A substring of any *word* in the name is also a substring of the full name, so matching
  against the full name satisfies the requirement (the only extra matches are query strings
  that themselves span a space, which is considered acceptable - and usually desirable).
* The filter is applied in the database, not in memory - loading every customer over a
  high-latency link just to discard most of them would defeat the purpose.
* `%`, `_` and `\` in the query are escaped so they are matched literally rather than acting
  as `LIKE` wildcards.
* A blank/absent `query` parameter returns all customers, keeping the endpoint backwards
  compatible with the original `GET /customer`.
* A `pg_trgm` GIN index on `lower(name)` (migration 3) makes the `%term%` predicate
  index-assisted in PostgreSQL, so the search scales with table size.

## Task 3 - Performance
The GET endpoints were slow because of the classic **N+1 select problem**: `findAll()` loaded
the entities, and the DTO mapping then triggered one extra lazy query per order (for its
customer) and per customer (for their orders). With 10,000 orders that is ~10,001 sequential
round trips - fatal when the database sits behind a high-latency link. Fixes:
* All list/detail repository queries now use JPQL `join fetch` so each endpoint executes
  **exactly one SQL query**, verified empirically with `spring.jpa.show-sql=true`.
* `default_batch_fetch_size: 10` only batches the N+1 queries (still ~N/10 round trips);
  fetch joins remove them entirely.
* Replaced Lombok `@Data` on the JPA entities with `@Getter`/`@Setter`. `@Data` generates
  `equals`/`hashCode`/`toString` over all fields, including lazy associations - dangerous for
  entities, and actively wrong once entities live in a `Set` (the new `products` collection).
* Added missing database indexes: `order.customer_id` (FK columns are not auto-indexed in
  PostgreSQL), `order_product.product_id`, and the trigram index for the name search.
* Also fixed a latent data bug: the sample-data changesets insert explicit IDs without
  advancing the `BIGSERIAL` sequences, so the first `POST` after a fresh install failed with a
  primary-key collision. Migration 4 re-syncs all three sequences.
* Not done deliberately: pagination of the list endpoints. It is the obvious next step for a
  production API, but it changes the response contract and was out of scope for these tasks.

## Task 4 - Products
* `Product` is a new entity (`id`, `description`) in a **many-to-many** relationship with
  `Order` via the `order_product` join table - an order contains 1..n products and the same
  product can appear in many orders (the `/products` response lists every order containing
  it, which only makes sense in a many-to-many world).
* `POST /products` creates a product; `GET /products` and `GET /products/{id}` return the
  product(s) with the list of order IDs that contain them (`orders: [1, 2, ...]`), matching
  the OpenAPI spec.
* `GET /order` and `GET /order/{id}` now include the products in each order
  (`products: [{id, description}]`).
* `POST /order` accepts `{"description": ..., "customer": {"id": ...}, "products": [{"id": ...}]}`.
  The customer and product IDs are resolved against the database; unknown IDs are rejected
  with `400` instead of failing later with an FK violation (or silently persisting garbage,
  as the original code did). Client-supplied entity IDs on POST are now ignored - IDs are
  database-generated, so a POST can never silently overwrite an existing row.
* Schema and sample data ship as **new** Liquibase changesets (3 and 4) so existing databases
  migrate cleanly; changesets 1 and 2 are untouched (editing them would break checksum
  validation on deployed instances). Products use a `Set` on the entity - an order cannot
  contain the same product twice, and it avoids bag-fetch problems.

## Bonus - CI pipeline
`.github/workflows/ci.yml` (GitHub Actions) with two jobs:
1. **build-and-test** - checks out the code, runs `spotlessCheck` (formatting gate), the unit
   tests (with the JaCoCo coverage report uploaded as a build artifact), and assembles the jar.
   These are the gates a PR must pass before promotion.
2. **docker** - builds the multi-stage `Dockerfile` (Gradle build stage + minimal JRE runtime,
   running as a non-root user) and pushes the image to GitHub Container Registry, tagged with
   the commit SHA (and `latest` on main). Pull requests build the image but do not push it.

The pipeline intentionally verifies formatting, tests and coverage *before* any image is
published - the things a real pipeline should gate on.
