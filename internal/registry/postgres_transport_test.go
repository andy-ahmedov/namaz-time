package registry

import (
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
)

func TestRegistryPostgresTransportRequiresAuthenticatedTLSRemotely(t *testing.T) {
	tests := []struct {
		name    string
		dsn     string
		wantErr bool
	}{
		{name: "remote plaintext", dsn: "postgres://user:secret@198.51.100.10/database?sslmode=disable", wantErr: true},
		{name: "remote encryption without verification", dsn: "postgres://user:secret@198.51.100.10/database?sslmode=require", wantErr: true},
		{name: "remote verified", dsn: "postgres://user:secret@database.example.test/database?sslmode=verify-full"},
		{name: "loopback development", dsn: "postgres://user:secret@127.0.0.1/database?sslmode=disable"},
		{name: "unix socket development", dsn: "postgres://user:secret@/database?host=/var/run/postgresql&sslmode=disable"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			config, err := pgxpool.ParseConfig(test.dsn)
			if err != nil {
				t.Fatalf("ParseConfig() error = %v", err)
			}
			err = validateRegistryPostgresTransport(config)
			if (err != nil) != test.wantErr {
				t.Fatalf("validateRegistryPostgresTransport() error = %v, wantErr=%v", err, test.wantErr)
			}
		})
	}
}
