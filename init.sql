postgres:
    image: postgres:16-alpine
    container_name: kp-postgres
    environment:
      - POSTGRES_USER=app
      - POSTGRES_PASSWORD=app
      - POSTGRES_DB=streamdb
    ports:
      - "5432:5432"
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d streamdb"]
      interval: 5s
      timeout: 3s
      retries: 20
    restart: unless-stopped