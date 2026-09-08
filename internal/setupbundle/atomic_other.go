//go:build !linux

package setupbundle

import (
	"fmt"
	"os"
)

func renameExclusive(string, string) error {
	return fmt.Errorf("atomic no-replace local setup export requires Linux renameat2")
}

func openForRead(string) (*os.File, error) {
	return nil, fmt.Errorf("local setup export requires Linux")
}
func openRootForRead(*os.Root, string) (*os.File, error) {
	return nil, fmt.Errorf("local setup export requires Linux")
}
