package publication

import (
	"errors"
	"fmt"
)

type Error struct {
	Op   string
	Code string
	Err  error
}

func (e *Error) Error() string { return fmt.Sprintf("%s: %s: %v", e.Op, e.Code, e.Err) }
func (e *Error) Unwrap() error { return e.Err }

func newError(op, code string, err error) error { return &Error{Op: op, Code: code, Err: err} }

func IsErrorCode(err error, code string) bool {
	var publicationError *Error
	return errors.As(err, &publicationError) && publicationError.Code == code
}
