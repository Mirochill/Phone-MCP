"""Phone MCP launch film. Original motion design, rendered with Pillow + FFmpeg.

Requires Python 3.11+, Pillow, numpy, imageio-ffmpeg.
Usage: python render_launch.py --root /path/to/Phone-MCP --audio track.m4a --preview
       python render_launch.py --root /path/to/Phone-MCP --audio track.m4a
Fonts are selected locally and are not redistributed. Set PHONE_MCP_FONT_DIR
to a directory with arial.ttf, arialbd.ttf, consola.ttf or provide DejaVu fonts.
"""
from pathlib import Path
import argparse, math, os, subprocess, json, functools, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import imageio_ffmpeg

W,H,FPS,DURATION=1920,1080,30,32
INK=(9,17,24); PAPER=(241,244,230); MINT=(130,228,208)
MUTED=(145,160,165); ORANGE=(255,119,76); CARD=(21,34,45)
parser=argparse.ArgumentParser();parser.add_argument('--root',required=True);parser.add_argument('--audio',required=True);parser.add_argument('--preview',action='store_true')
args=parser.parse_args();root=Path(args.root);media=root/'docs/media';media.mkdir(parents=True,exist_ok=True)
images=root/'docs/images';ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
font_dirs=[Path(os.environ.get('PHONE_MCP_FONT_DIR','C:/Windows/Fonts')),Path('/usr/share/fonts/truetype/dejavu')]
@functools.lru_cache(None)
def font(size,bold=True,mono=False):
    choices=['consola.ttf','DejaVuSansMono.ttf'] if mono else (['arialbd.ttf','DejaVuSans-Bold.ttf'] if bold else ['arial.ttf','DejaVuSans.ttf'])
    for folder in font_dirs:
        for name in choices:
            if (folder/name).exists():return ImageFont.truetype(str(folder/name),size)
    raise RuntimeError('A local Arial or DejaVu font is required')
def clamp(x):return max(0,min(1,x))
def ease(x):x=clamp(x);return 1-(1-x)**3
def reveal(t,delay=0,dur=.5):return ease((t-delay)/dur)
def txt(im,text,x,y,size=54,color=INK,bold=True,mono=False,anchor='la'):
    ImageDraw.Draw(im).text((int(x),int(y)),text,font=font(size,bold,mono),fill=color,anchor=anchor,stroke_width=0)
def rect(im,box,fill,r=22,outline=None,width=2):ImageDraw.Draw(im).rounded_rectangle(tuple(map(int,box)),radius=r,fill=fill,outline=outline,width=width)
def line(im,points,fill,width=4):ImageDraw.Draw(im).line(points,fill=fill,width=width,joint='curve')
def mark(im,x,y,s,color):
    d=ImageDraw.Draw(im);d.rounded_rectangle((x,y,x+s*.64,y+s),radius=s*.13,outline=color,width=max(3,int(s*.07)))
    d.line((x+s*.23,y+s*.84,x+s*.4,y+s*.84),fill=color,width=max(3,int(s*.06)))
    d.line((x+s*.78,y+s*.28,x+s*1.02,y+s*.5,x+s*.78,y+s*.72),fill=color,width=max(3,int(s*.07)),joint='curve')
def brand(im,dark=False):
    c=PAPER if dark else INK;mark(im,88,58,42,c);txt(im,'Phone MCP',153,62,30,c)
    txt(im,'ANDROID × AI',W-86,70,22,MUTED if dark else (73,89,87),mono=True,anchor='ra')
def footer(im,text,dark=False):
    txt(im,text,88,H-69,22,MUTED if dark else (73,89,87),bold=False)
    txt(im,'MIROCHILL / 0.8.2',W-88,H-69,20,MUTED if dark else (73,89,87),mono=True,anchor='ra')
@functools.lru_cache(None)
def phone_asset(name,width):
    shot=Image.open(images/name).convert('RGB');height=round(shot.height*width/shot.width);pad=13
    out=Image.new('RGBA',(width+pad*2+28,height+pad*2+28))
    rect(out,(14,18,width+pad*2+14,height+pad*2+18),(0,0,0,35),r=42)
    rect(out,(0,0,width+pad*2,height+pad*2),(9,15,21,255),r=37,outline=(65,85,90,255))
    resized=shot.resize((width,height),Image.Resampling.LANCZOS);mask=Image.new('L',resized.size);ImageDraw.Draw(mask).rounded_rectangle((0,0,width,height),radius=26,fill=255)
    out.paste(resized,(pad,pad),mask);return out
def phone(im,name,x,y,width=390,angle=0):
    p=phone_asset(name,width)
    if angle:p=p.rotate(angle,resample=Image.Resampling.BICUBIC,expand=True)
    im.paste(p,(int(x),int(y)),p)
def chip(im,text,x,y,t=1,dark=False,size=24):
    f=font(size,False,True);tw=ImageDraw.Draw(im).textlength(text,font=f)
    rect(im,(x,y,x+tw+40,y+49),CARD if dark else INK,r=11)
    txt(im,text,x+20,y+11,size,MINT if dark else PAPER,bold=False,mono=True)
def arrow(im,x1,y,x2,color=INK):
    line(im,[(x1,y),(x2,y)],color,4);line(im,[(x2-14,y-12),(x2,y),(x2-14,y+12)],color,4)

pcm=subprocess.run([ffmpeg,'-v','error','-i',args.audio,'-t',str(DURATION),'-f','f32le','-ac','1','-ar','12000','pipe:1'],capture_output=True,check=True).stdout
signal=np.frombuffer(pcm,dtype=np.float32);hop=12000//FPS
energy=np.array([np.sqrt(np.mean(signal[i:i+hop]**2)+1e-10) for i in range(0,len(signal),hop)])
energy=np.clip(energy/max(float(np.percentile(energy,95)),1e-5),0,1)
def pulse(t):return float(energy[min(len(energy)-1,max(0,int(t*FPS)))])
scenes=[0,2.3,4.5,9,13.4,17.2,20.5,24,28,32]
def render(t):
    n=max(i for i,s in enumerate(scenes[:-1]) if t>=s);u=t-scenes[n];p=pulse(t)
    bg=[PAPER,INK,MINT,PAPER,INK,ORANGE,INK,PAPER,MINT][n]
    im=Image.new('RGB',(W,H),bg);dark=n in [1,4,6];brand(im,dark)
    if n==0:
        # Opposing kinetic lines resolve into one clear product promise.
        a=reveal(u,.05,.6);b=reveal(u,.35,.6)
        txt(im,'YOUR AI.',88-110*(1-a),255,170)
        txt(im,'YOUR ANDROID.',88+140*(1-b),445,164)
        txt(im,'Now they can work together.',94,680+30*(1-reveal(u,.8)),44,bold=False)
        d=ImageDraw.Draw(im)
        for k in range(24):
            h=18+90*abs(math.sin(k*.76+t*3.5))*p
            rect(im,(96+k*26,853-h,112+k*26,853),INK,5)
        mark(im,1470,264+12*math.sin(t*2),230,INK)
        footer(im,'INTRODUCING A REAL PHONE CONNECTION')
    elif n==1:
        txt(im,'Phone',88-100*(1-reveal(u)),224,166,PAPER)
        txt(im,'MCP',88+130*(1-reveal(u,.18)),405,182,MINT)
        txt(im,'One conversation. A real phone.',95,664,42,PAPER,bold=False)
        nodes=[(1140,336,'AI'),(1450,336,'MCP'),(1450,626,'ANDROID')]
        for i,(x,y,label) in enumerate(nodes):
            sc=reveal(u,.15+i*.16);rect(im,(x,y+40*(1-sc),x+230,y+150+40*(1-sc)),CARD,24,outline=(55,80,83))
            txt(im,label,x+115,y+55+40*(1-sc),30,MINT,anchor='ma')
        arrow(im,1380,411,1432,MINT);line(im,[(1565,503),(1565,608)],MINT,4)
        for k in range(3):
            xx=1382+((t*100+k*20)%52);ImageDraw.Draw(im).ellipse((xx,405,xx+10,415),fill=PAPER)
        footer(im,'MCP OVER HTTPS · AUTHENTICATED WITH OAUTH',True)
    elif n==2:
        for j,word in enumerate(['READ.','TAP.','TYPE.']):txt(im,word,88+90*(1-reveal(u,j*.15)),210+j*155,150)
        for j,label in enumerate(['phone_snapshot','phone_click','phone_set_text']):chip(im,label,98+j*22,730+j*61,size=24)
        phone(im,'live-calculator.jpg',1310+150*(1-reveal(u,.12)),178+8*math.sin(t*1.5),390,angle=-2)
        txt(im,'137 × 24 → 3 288',1308,928,31)
        footer(im,'ACTUAL S24 ULTRA CAPTURE · CONTROLLED THROUGH PHONE MCP')
    elif n==3:
        txt(im,'YOUR SCREEN.',88,202,100)
        txt(im,'THE AI’S SCREEN.',88,319,100)
        txt(im,'Use another app while your agent works.',95,462,34,bold=False)
        # Left panel is deliberately labelled as a diagram; right is a real device capture.
        x=115;y=590;rect(im,(x,y,x+735,y+260),INK,28)
        txt(im,'YOUR PHYSICAL PHONE',x+30,y+26,23,MINT,mono=True)
        txt(im,'Your own app',x+30,y+84,46,PAPER)
        txt(im,'Independent from the AI task',x+30,y+164,26,MUTED,bold=False)
        arrow(im,880,719,1120,INK)
        phone(im,'live-calculator.jpg',1240+90*(1-reveal(u)),164,390,angle=2)
        chip(im,'Separate display / Shizuku',1230,927,size=22)
        footer(im,'DISPLAY DIAGRAM + REAL DEVICE CAPTURE · APP / DEVICE COMPATIBILITY VARIES')
    elif n==4:
        txt(im,'FILES,',88,208,146,PAPER)
        txt(im,'DELIVERED.',88,369,146,MINT)
        txt(im,'APKs, installed.',96,579,63,PAPER)
        txt(im,'Direct HTTPS. Progress. Checksum.',98,677,33,MUTED,bold=False)
        x=1160;y=272;rect(im,(x,y,1814,y+510),CARD,28,outline=(52,75,79))
        chip(im,'phone_download_file',x+35,y+35,dark=True,size=25)
        txt(im,'your-app.apk',x+38,y+145,43,PAPER)
        progress=clamp((u-.35)/1.5);rect(im,(x+38,y+243,1774,y+260),(45,65,73),8)
        if progress>0:rect(im,(x+38,y+243,x+38+(1774-x-38)*progress,y+260),MINT,8)
        txt(im,f'{round(progress*100)}%',x+38,y+282,38,MINT,mono=True)
        label='SHA-256 verified' if u>2 else 'Downloading…'
        txt(im,label,x+38,y+352,30,PAPER,bold=False)
        if u>2.3:chip(im,'phone_install_apk',x+38,y+420,dark=True,size=24)
        footer(im,'WORKFLOW ILLUSTRATION · SHIZUKU REQUIRED · UP TO 512 MiB',True)
    elif n==5:
        txt(im,'REPLY.',88,210,154)
        txt(im,'WITHOUT',88,385,140)
        txt(im,'OPENING.',88,545,140)
        txt(im,'Use a compatible notification action.',96,742,34,bold=False)
        x=1130;y=285;rect(im,(x,y,1816,y+210),PAPER,27)
        txt(im,'EXAMPLE NOTIFICATION',x+32,y+28,22,(73,89,87),mono=True)
        txt(im,'Can you send me the brief?',x+32,y+95,34)
        rect(im,(x+30,y+247,1816,y+422),INK,25)
        msg='Ready when you are.';count=int(reveal(u,.45,1)*len(msg))
        txt(im,msg[:count],x+62,y+292,34,PAPER)
        chip(im,'phone_reply_notification',1128,800,size=24)
        footer(im,'ILLUSTRATIVE COPY · REPLY ONLY WITH YOUR AUTHORIZATION')
    elif n==6:
        for j,label in enumerate(['CHAT.','WORK.','CODEX.']):
            txt(im,label,88+130*(1-reveal(u,j*.14)),200+j*190,164,MINT if j==2 else PAPER)
        x=1190;y=302;rect(im,(x,y,1816,y+340),CARD,28,outline=(57,79,83))
        txt(im,'YOUR MCP AGENT',x+40,y+40,34,MINT,mono=True)
        txt(im,'Same phone.',x+40,y+123,52,PAPER)
        txt(im,'Your choice of AI.',x+40,y+194,46,PAPER)
        for j in range(4):rect(im,(x+44+j*120,y+284,x+124+j*120,y+296),MINT if (int(t*3)+j)%4==0 else (48,72,77),5)
        footer(im,'REMOTE MCP + OAUTH SUPPORT REQUIRED · CLIENT AVAILABILITY VARIES',True)
    elif n==7:
        txt(im,'YOUR SESSION.',88,206,112)
        txt(im,'YOUR RULES.',88,340,112)
        for j,label in enumerate(['Start on your phone.','Choose apps & capabilities.','Stop whenever you want.']):
            y=550+j*106;v=reveal(u,.3+j*.17);rect(im,(100,y,148,y+48),INK,24)
            line(im,[(113,y+24),(122,y+33),(137,y+15)],MINT,4)
            txt(im,label,181+35*(1-v),y+2,42,bold=False)
        phone(im,'android-session.png',1336+85*(1-reveal(u)),180,375,angle=-2)
        footer(im,'REVIEW APP PERMISSIONS BEFORE STARTING')
    else:
        a=reveal(u,.05,.6);mark(im,835,180+50*(1-a),170,INK)
        txt(im,'Phone MCP',W/2,440+28*(1-a),152,INK,anchor='ma')
        txt(im,'Full source. Android APK. MIT.',W/2,639,46,INK,bold=False,anchor='ma')
        rect(im,(363,764,1557,876),INK,20)
        txt(im,'github.com/Mirochill/Phone-MCP',W/2,793,40,MINT,mono=True,anchor='ma')
        txt(im,'Self-host on Cloudflare. Let your AI get to work.',W/2,926,29,INK,bold=False,anchor='ma')
    # A restrained progress rail and audio-driven square tie the film together.
    d=ImageDraw.Draw(im);d.rectangle((0,H-8,int(W*t/DURATION),H),fill=MINT if dark else INK)
    rect(im,(W-112,145,W-88,169),MINT if dark else INK,5)
    # Short edge wipe between chapters; no full-frame flashing.
    if n>0 and u<.22:
        x=int(W*ease(u/.22));d.rectangle((x,0,min(W,x+90),H),fill=MINT if dark else INK)
    return im

if args.preview:
    times=[1.6,3.4,6.8,11.4,15.8,19.1,22.5,26.4,30.4]
    sheet=Image.new('RGB',(W//2*3,H//2*3),INK)
    for i,t in enumerate(times):sheet.paste(render(t).resize((W//2,H//2),Image.Resampling.LANCZOS),((i%3)*W//2,(i//3)*H//2))
    sheet.save(media/'storyboard.png');render(30.4).save(images/'launch-cover.png')
    print('STORYBOARD_RENDERED',flush=True)
else:
    output=media/'phone-mcp-launch.mp4';temp=media/'launch-silent.mp4'
    command=[ffmpeg,'-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format','rgb24','-video_size',f'{W}x{H}','-framerate',str(FPS),'-i','pipe:0','-an','-c:v','libx264','-preset','medium','-crf','19','-pix_fmt','yuv420p','-movflags','+faststart',str(temp)]
    p=subprocess.Popen(command,stdin=subprocess.PIPE)
    for i in range(FPS*DURATION):
        p.stdin.write(render(i/FPS).tobytes())
        if i%(FPS*4)==0:print(f'RENDER {i/FPS:.0f}/{DURATION}s',flush=True)
    p.stdin.close();assert p.wait()==0
    subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(temp),'-i',args.audio,'-t',str(DURATION),'-map','0:v:0','-map','1:a:0','-c:v','copy','-af','loudnorm=I=-16:TP=-1.5:LRA=9,afade=t=out:st=30.8:d=1.2','-c:a','aac','-b:a','192k','-ar','48000','-movflags','+faststart',str(output)],check=True)
    temp.unlink()
    render(30.4).save(images/'launch-cover.png')
    print('LAUNCH_FILM_RENDERED',output,flush=True)
