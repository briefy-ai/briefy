# create-flyway-migration

Creates Flyway migration files following project conventions.

## Usage

```bash
./scripts/genDDLFile.sh "description"
```

Examples:
- `./scripts/genDDLFile.sh "create user table"`
- `./scripts/genDDLFile.sh "add email column to user"`
- `./scripts/genDDLFile.sh "dml add initial admin users"`

### File naming
- Format: `V{YYYYMMDDHHmmss}__{description}.sql`
- DML files: prefix description with `dml_`

### Indexes
- Format: `INDEX idx_{column_name} (column_name)`
- Define inline in CREATE TABLE
