# Use cases / domain services

Feature-facing use cases live here. Session overlap enforcement, active-session replacement, task completion, parent completion, categorization and statistics each have one domain implementation. Pure shared policies currently live under `domain/service`; Room transactions are coordinated by `RoomRidoRepository`. UI and Composables must not reproduce these decisions.
