package stricthtml_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/stricthtml"
)

func TestValidRawStartTagAttributes(t *testing.T) {
	for _, raw := range []string{
		"<td>", "<br/>", "<br />", "<input checked disabled>", "<div id=text_block>",
		`<a href="/path?a=1&amp;b=2" title='a > b; class="one" class="two"'>`,
		`<div title="single ' quotes; key=value key=value" class='one'>`,
		"<DIV\tID = 'one'\nCLASS = \"two\" >", "<img src=image.jpg/>",
		`<o:p xmlns:o="urn:schemas-microsoft-com:office:office">`,
	} {
		t.Run(raw, func(t *testing.T) {
			if err := stricthtml.ValidateStartTagAttributes(raw); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func TestDuplicateRawAttributesAreNotDeduplicated(t *testing.T) {
	for _, raw := range []string{
		`<div id="one" id="two">`, `<div id="one" ID="two">`,
		"<div id=one id=two>", "<div id='one' id='two'>", "<input hidden HIDDEN>",
		`<a href='x>y' HREF="other">`, `<br class="x" CLASS="x"/>`,
		`<o:p xml:lang="en" XML:LANG="ru">`,
	} {
		t.Run(raw, func(t *testing.T) {
			if err := stricthtml.ValidateStartTagAttributes(raw); !errors.Is(err, stricthtml.ErrDuplicateAttribute) {
				t.Fatalf("duplicate attribute not diagnosed: %v", err)
			}
		})
	}
}

func TestMalformedRawStartTagAttributesFailClosed(t *testing.T) {
	for _, raw := range []string{
		"", "text", "<", "<>", "</div>", "<!--comment-->", "<!DOCTYPE html>",
		"<div", "<div id=>", "<div id=", "<div id='unterminated>", "<div id = >",
		"<div id`bad=x>", "<div id=a=b>", "<div / >", "<div id=<bad>>",
		"<div id='x'\x00>", "<div id='\xff'>", "<div " + strings.Repeat("x", 4*1024*1024) + ">",
	} {
		if err := stricthtml.ValidateStartTagAttributes(raw); err == nil {
			t.Fatalf("invalid raw start tag accepted: %.100q", raw)
		}
	}
}

func FuzzValidateStartTagAttributes(f *testing.F) {
	for _, raw := range []string{`<div class="x" class="y">`, `<a title='x>y a=one a=two'>`, "<div id=x>", "<"} {
		f.Add(raw)
	}
	f.Fuzz(func(t *testing.T, raw string) {
		first := stricthtml.ValidateStartTagAttributes(raw)
		second := stricthtml.ValidateStartTagAttributes(raw)
		if (first == nil) != (second == nil) {
			t.Fatal("nondeterministic raw-markup validation")
		}
	})
}
