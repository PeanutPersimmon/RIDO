# Room schema history

Room exports the canonical database schema JSON into this directory. The generated versioned files are source artifacts and must be committed. Every database version after v1 requires both an explicit `Migration` and migration tests; destructive migration is forbidden.

