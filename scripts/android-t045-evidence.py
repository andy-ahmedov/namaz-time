#!/usr/bin/env python3
import subprocess, time, pathlib, json, hashlib, io
from PIL import Image, ImageChops, ImageFilter
import zxingcpp
import argparse
parser=argparse.ArgumentParser(description="Capture synthetic T045 screens on a controlled Android TV emulator. Requires Pillow and zxing-cpp.")
parser.add_argument('--output',type=pathlib.Path,required=True)
parser.add_argument('--profiles',nargs='+',choices=['720p','1080p','4k'],default=['720p','1080p'])
parser.add_argument('--presentation-display',type=int)
parser.add_argument('--capture-display')
args=parser.parse_args()
out=args.output;out.mkdir(parents=True,exist_ok=True)
records=[]
payloads={
 'short':'https://example.org/sadaqah',
 'medium':'https://example.org/mosques/community/donate?campaign=renovation-2026&lang=ru',
 'long':'https://example.org/donate?campaign=mosque-renovation-2026&purpose=community-hall&reference=tv-display&return=https%3A%2F%2Fexample.org%2Fthank-you&language=ru',
}
def adb(*args):return subprocess.check_output(['adb',*map(str,args)])
def capture(name,scenario,language='ru',iqamah=True,qr=True,payload='short',shift=0):
 adb('shell','am','force-stop','ru.namaztime.tv.debug')
 # adb shell itself parses metacharacters, so quote HTTPS query payload.
 import shlex
 command=['am','start','-W','-n','ru.namaztime.tv.debug/ru.namaztime.tv.presentation.SchedulePresentationEvidenceActivity',
 '--es','scenario',scenario,'--es','language',language,'--ez','iqamah',str(iqamah).lower(),'--ez','qr',str(qr).lower(),
 '--es','payload',payloads[payload],'--ei','shift',str(shift)]
 if profile == '4k':
  command += ['--ei','presentationDisplay',str(args.presentation_display)]
 adb('shell',shlex.join(command));time.sleep(1.5)
 capture_args=['exec-out','screencap','-p']
 if profile == '4k': capture_args += ['-d',args.capture_display]
 raw=adb(*capture_args);im=Image.open(io.BytesIO(raw)).convert('RGB')
 expected=tuple(map(int,size.split('x')))
 assert im.size == expected, f'{name}: expected {expected}, actual {im.size}; do not label a clamped frame as 4K'
 file=out/(name+'.png');file.write_bytes(raw)
 result={'name':name,'scenario':scenario,'size':list(im.size),'language':language,'iqamah':iqamah,'qr':qr,'payload':payload,'shift':shift,'sha256':hashlib.sha256(raw).hexdigest()}
 if qr and scenario not in ('background','settings-iqamah','settings-appearance'):
  codes=zxingcpp.read_barcodes(im)
  result['decoded']=[c.text for c in codes]
  result['decode_pass']=payloads[payload] in result['decoded']
  if not result['decode_pass']: print('DECODE FAILED',name,flush=True)
  degraded=im.filter(ImageFilter.GaussianBlur(.55))
  result['blur_055_pass']=payloads[payload] in [c.text for c in zxingcpp.read_barcodes(degraded)]
 if scenario=='compact':
  difference=ImageChops.difference(im,background)
  bbox=difference.getbbox();result['foreground_bbox']=bbox
  result['left_half_unchanged']=difference.crop((0,0,im.width//2,im.height)).getbbox() is None
  if not result['left_half_unchanged']: print('LEFT HALF FAILED',name,flush=True)
 records.append(result);(out/'results.json').write_text(json.dumps(records,ensure_ascii=False,indent=2))
 print(name,result.get('decode_pass','-'),result.get('foreground_bbox','-'),flush=True)
 return im
try:
 adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
 for profile,size,density in [('720p','1280x720',160),('1080p','1920x1080',320),('4k','3840x2160',480)]:
  if profile not in args.profiles:continue
  if profile == '4k':
   assert args.presentation_display is not None and args.capture_display, '4K requires a real 3840x2160 secondary display and its SurfaceFlinger capture ID'
  else:
   adb('shell','wm','size',size);adb('shell','wm','density',density)
  time.sleep(1)
  background=capture(profile+'-background','background',qr=False)
  for payload in payloads:
   for scenario in ['standard','compact','donation','settings-qr']:
    capture(f'{profile}-{scenario}-{payload}',scenario,iqamah=False,payload=payload)
  for lang in ['ru','en']:
   capture(f'{profile}-compact-on-{lang}','compact',language=lang)
   capture(f'{profile}-compact-noqr-{lang}','compact',language=lang,iqamah=False,qr=False)
  capture(profile+'-standard-on','standard')
  if profile=='1080p':
   capture(profile+'-iqamah-settings','settings-iqamah')
   capture(profile+'-appearance-settings','settings-appearance')
   for shift in range(1,6):capture(f'{profile}-compact-shift-{shift}','compact',shift=shift)
finally:
 adb('shell','wm','size','reset');adb('shell','wm','density','reset')
assert all(r.get('decode_pass',True) and r.get('left_half_unchanged',True) for r in records)
