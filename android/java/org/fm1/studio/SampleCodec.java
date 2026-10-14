/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
/** WAV decoding and firmware-compatible IMA ADPCM slot construction. */
final class SampleCodec {
 static final int RATE=22050, MAX=81920-512;
 static final int[] IMA_STEP={7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,22385,24623,27086,29794,32767};
 static final int[] IMA_IDX={-1,-1,-1,-1,2,4,6,8};
 static short[] wav(byte[] input){
  ByteBuffer b=ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
  if(input.length<44||b.getInt(0)!=0x46464952||b.getInt(8)!=0x45564157)throw new IllegalArgumentException("Choose a RIFF WAV file");
  int format=0,channels=0,rate=0,bits=0,offset=0,size=0,align=0;
  for(int p=12;p+8<=input.length;){int id=b.getInt(p),n=b.getInt(p+4);if(n<0||n>input.length-p-8)throw new IllegalArgumentException("Damaged WAV chunk");if(id==0x20746d66&&n>=16){format=b.getShort(p+8)&65535;channels=b.getShort(p+10)&65535;rate=b.getInt(p+12);align=b.getShort(p+20)&65535;bits=b.getShort(p+22)&65535;}if(id==0x61746164){offset=p+8;size=n;}p+=8+n+(n&1);}
  if((format!=1&&format!=3)||channels<1||channels>8||rate<8000||rate>192000||offset==0||align!=channels*((bits+7)/8)||!(bits==8||bits==16||bits==24||bits==32)||format==3&&bits!=32)throw new IllegalArgumentException("Use PCM 8/16/24/32-bit or float WAV");
  int frames=size/align;if(frames<2)throw new IllegalArgumentException("WAV is empty");if((long)frames>rate*180L)throw new IllegalArgumentException("Choose a recording up to three minutes");int count=(int)((long)frames*RATE/rate);short[] out=new short[count];
  for(int i=0;i<count;i++){double pos=i*(double)rate/RATE;int a=Math.min(frames-1,(int)pos),c=Math.min(frames-1,a+1);double mix=0;for(int ch=0;ch<channels;ch++){double x=pcm(b,offset+a*align+ch*(bits/8),bits,format),y=pcm(b,offset+c*align+ch*(bits/8),bits,format);mix+=x+(y-x)*(pos-a);}out[i]=(short)Math.max(-32768,Math.min(32767,Math.round(mix/channels)));}return out;
 }
 static double pcm(ByteBuffer b,int p,int bits,int format){if(format==3){float f=b.getFloat(p);return Float.isFinite(f)?f*32767:0;}if(bits==8)return ((b.get(p)&255)-128)*256;if(bits==16)return b.getShort(p);if(bits==24){int v=(b.get(p)&255)|((b.get(p+1)&255)<<8)|(b.get(p+2)<<16);return v/256.0;}return b.getInt(p)/65536.0;}
 static byte[] encode(short[] samples){int pred=0,idx=0;byte[] out=new byte[(samples.length+1)/2];for(int i=0;i<samples.length;i++){int step=IMA_STEP[idx],diff=samples[i]-pred,code=0;if(diff<0){code=8;diff=-diff;}int vd=step>>3;if(diff>=step){code|=4;diff-=step;vd+=step;}if(diff>=(step>>1)){code|=2;diff-=step>>1;vd+=step>>1;}if(diff>=(step>>2)){code|=1;vd+=step>>2;}pred=Math.max(-32768,Math.min(32767,pred+((code&8)!=0?-vd:vd)));idx=Math.max(0,Math.min(88,idx+IMA_IDX[code&7]));out[i/2]|=(byte)(code<<((i&1)*4));}return out;}
 static final class Zone {short[] pcm;int root=60,lo=0,hi=127;String name;boolean keep=true;Zone(short[] pcm,String name){this.pcm=pcm;this.name=name;}}
 static byte[] slot(List<Zone> zones,String name,boolean drums){List<Zone> keep=new ArrayList<>();for(Zone z:zones)if(z.keep&&z.pcm.length>0)keep.add(z);if(keep.isEmpty()||keep.size()>16)throw new IllegalArgumentException("Keep 1–16 sounds");if(drums){HashSet<Integer> notes=new HashSet<>();for(Zone z:keep)if(!notes.add(z.root))throw new IllegalArgumentException("Assign each drum sound to a different lane");}if(!drums)keep.sort(Comparator.comparingInt(z->z.root));
  ByteArrayOutputStream data=new ByteArrayOutputStream();ByteBuffer h=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);h.putInt(0,0x504d5346);h.putShort(4,(short)1);h.put(6,(byte)keep.size());for(int i=0;i<Math.min(8,name.length());i++)h.put(8+i,(byte)name.charAt(i));
  for(int i=0;i<keep.size();i++){Zone z=keep.get(i);if(z.root<0||z.root>127)throw new IllegalArgumentException("Root note must be 0–127");int p=32+i*28;byte[] adp=encode(z.pcm);h.putInt(p,data.size());h.putInt(p+4,z.pcm.length);h.putInt(p+8,0);h.putInt(p+12,z.pcm.length-1);h.putInt(p+16,32768);h.putShort(p+20,(short)(z.root*16));h.put(p+25,(byte)(drums?z.lo:i==0?0:(keep.get(i-1).root+z.root)/2+1));h.put(p+26,(byte)(drums?z.hi:i+1==keep.size()?127:(z.root+keep.get(i+1).root)/2));data.write(adp,0,adp.length);}
  if(data.size()>MAX)throw new IllegalArgumentException("Slot is too long. Trim sounds or use Fit to slot.");byte[] d=data.toByteArray();CRC32 crc=new CRC32();crc.update(d);h.putInt(16,d.length);h.putInt(20,(int)crc.getValue());ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(h.array(),0,512);out.write(d,0,d.length);return out.toByteArray();
 }
 static void fit(List<Zone> zones){int n=0;for(Zone z:zones)if(z.keep)n+=z.pcm.length;int allowed=MAX*2-32;if(n<=allowed)return;for(Zone z:zones)if(z.keep)z.pcm=Arrays.copyOf(z.pcm,Math.max(2,(int)((long)z.pcm.length*allowed/n)));}
}
