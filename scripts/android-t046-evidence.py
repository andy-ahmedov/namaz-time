#!/usr/bin/env python3
"""Capture compact presentation on a controlled emulator; requires Pillow and zxing-cpp."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shlex
import subprocess
import time

from PIL import Image, ImageChops, ImageFilter
import zxingcpp

PAYLOADS = {
    "short": "https://example.org/sadaqah",
    "medium": "https://example.org/mosques/community/donate?campaign=renovation-2026&lang=ru",
    "long": "https://example.org/donate?campaign=mosque-renovation-2026&purpose=community-hall&reference=tv-display&return=https%3A%2F%2Fexample.org%2Fthank-you&language=ru",
}
MESSAGE = "Тем же из вас, которые уверовали и расходовали, уготована великая награда."


def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--profiles", nargs="+", choices=["720p", "1080p", "4k"], default=["720p", "1080p"])
    parser.add_argument("--presentation-display", type=int)
    parser.add_argument("--capture-display")
    parser.add_argument(
        "--extra-backgrounds",
        nargs="*",
        choices=["blue_hour", "night_minaret", "luminous_dusk"],
        default=[],
    )
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    records = []
    for profile, size, density in [("720p", (1280, 720), 160), ("1080p", (1920, 1080), 320), ("4k", (3840, 2160), 480)]:
        if profile not in args.profiles:
            continue
        if profile == "4k":
            assert args.presentation_display is not None and args.capture_display, "4K needs an actual secondary display and SurfaceFlinger capture ID"
        else:
            adb("shell", "wm", "size", f"{size[0]}x{size[1]}")
            adb("shell", "wm", "density", density)
        time.sleep(1)

        background_style = "golden_dusk"

        def capture(
            case,
            scenario="compact",
            language="ru",
            iqamah=False,
            qr=True,
            payload="short",
            shift=0,
            instant=None,
            message=MESSAGE,
            title=None,
            attention=False,
            mosque_name=None,
        ):
            adb("shell", "am", "force-stop", "ru.namaztime.tv.debug")
            command = ["am", "start", "-W", "-n", "ru.namaztime.tv.debug/ru.namaztime.tv.presentation.SchedulePresentationEvidenceActivity",
                       "--es", "scenario", scenario, "--es", "background", background_style, "--es", "language", language, "--ez", "iqamah", str(iqamah).lower(),
                       "--ez", "qr", str(qr).lower(), "--es", "payload", PAYLOADS[payload], "--ei", "shift", str(shift)]
            command += ["--ez", "attention", str(attention).lower()]
            if message:
                command += ["--es", "message", message]
            if title:
                command += ["--es", "title", title]
            if instant:
                command += ["--es", "instant", instant]
            if mosque_name:
                command += ["--es", "mosqueName", mosque_name]
            if profile == "4k":
                command += ["--ei", "presentationDisplay", str(args.presentation_display)]
            adb("shell", shlex.join(command))
            time.sleep(1.5)
            command = ["exec-out", "screencap", "-p"]
            if profile == "4k":
                command += ["-d", args.capture_display]
            raw = adb(*command)
            image = Image.open(io.BytesIO(raw)).convert("RGB")
            assert image.size == size, f"Refusing clamped screenshot: expected {size}, actual {image.size}"
            name = f"{profile}-{case}.png"
            (args.output / name).write_bytes(raw)
            record = dict(name=name, scenario=scenario, size=size, density=density, language=language,
                          iqamah=iqamah, qr=qr, payload=payload, shift=shift, instant=instant, background=background_style,
                          sha256=hashlib.sha256(raw).hexdigest())
            if qr and scenario != "background":
                record["decode_pass"] = PAYLOADS[payload] in [result.text for result in zxingcpp.read_barcodes(image)]
                record["blur_055_pass"] = PAYLOADS[payload] in [result.text for result in zxingcpp.read_barcodes(image.filter(ImageFilter.GaussianBlur(.55)))]
            if scenario == "compact":
                diff = ImageChops.difference(image, background)
                record["foreground_bbox"] = diff.getbbox()
                record["left_half_unchanged"] = diff.crop((0, 0, size[0] // 2, size[1])).getbbox() is None
            records.append(record)
            (args.output / "results.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n")
            print(name, record.get("decode_pass"), record.get("foreground_bbox"), flush=True)
            return image

        background = capture("background", scenario="background", qr=False)
        capture("target")
        capture("iqamah-on", iqamah=True)
        capture("no-qr", qr=False)
        capture("english", language="en", message="Your support helps maintain the mosque and serve the community.")
        capture("english-iqamah", language="en", iqamah=True)
        capture("tomorrow", instant="2026-08-19T19:00:00Z")
        capture("warning", attention=True)
        capture(
            "long-mosque-name",
            mosque_name="Синтетическая местная религиозная организация мусульман города Ульяновска",
        )
        capture("six-line-message", message="Первая строка\nВторая строка\nТретья строка\nЧетвёртая строка\nПятая строка\nШестая строка")
        for payload in ["medium", "long"]:
            capture(f"qr-{payload}", payload=payload)
        for shift in range(1, 6):
            capture(f"shift-{shift}", shift=shift)
        # Match the established T047 fixed-clock STANDARD regression fixture.
        capture("standard", scenario="standard")
        if args.extra_backgrounds:
            capture("long-paragraph", message="Every sincere contribution supports the mosque, helps our community, welcomes every visitor, and becomes lasting good.")
            capture("maximum-copy", title=("Информация о работе и мероприятиях нашей общины. " * 4)[:160],
                    message="Первая строка\nВторая строка\nТретья строка\nЧетвёртая строка\nПятая строка\nШестая строка")
        for background_style in args.extra_backgrounds:
            background = capture(background_style + "-background", scenario="background", qr=False)
            capture(background_style + "-compact")
    assert all(record.get("decode_pass", True) and record.get("blur_055_pass", True) and record.get("left_half_unchanged", True) for record in records)


if __name__ == "__main__":
    try:
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
        main()
    finally:
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
