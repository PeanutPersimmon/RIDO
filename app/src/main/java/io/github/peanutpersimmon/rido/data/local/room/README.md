# Room boundary

`RidoDatabase` is the production database boundary. Version 1 is exported to `app/schemas/`. Every later version must add an explicit `Migration` and migration tests; destructive fallback is forbidden. UI and ViewModels must not access DAOs directly.
