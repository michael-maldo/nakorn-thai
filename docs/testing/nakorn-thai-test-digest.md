


CI could orchestrate

        CI
        │
        ├── Backend
        │     └── mvn verify
        │
        ├── Frontend
        │     └── npm test
        │
        ├── Build/start application
        │     ├── PostgreSQL
        │     ├── Spring Boot
        │     └── React
        │
        └── E2E
        └── npx playwright test
        │
        ├── customer ordering
        ├── reservations
        ├── staff login
        ├── menu management
        └── kitchen workflow