//go:build linux

package setupbundle

import (
	"golang.org/x/sys/unix"
	"os"
)

func renameExclusive(oldPath, newPath string) error {
	return unix.Renameat2(unix.AT_FDCWD, oldPath, unix.AT_FDCWD, newPath, unix.RENAME_NOREPLACE)
}

// Nonblocking/no-follow opens prevent a swapped FIFO or symlink from turning
// a bounded input read into an uninterruptible open. fstat still requires a file.
func openForRead(path string) (*os.File, error) {
	return os.OpenFile(path, os.O_RDONLY|unix.O_NOFOLLOW|unix.O_NONBLOCK, 0)
}
func openRootForRead(root *os.Root, path string) (*os.File, error) {
	return root.OpenFile(path, os.O_RDONLY|unix.O_NOFOLLOW|unix.O_NONBLOCK, 0)
}
