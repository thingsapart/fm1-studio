/* SPDX-License-Identifier: GPL-3.0-only */
package org.fm1.studio;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class SampleCodecTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  short[] values={0,1000,-1000,30000,-30000,1234,0};byte[] encoded=SampleCodec.encode(values);
  Files.write(Paths.get(args[0]),encoded);
  ArrayList<SampleCodec.Zone> zones=new ArrayList<>();zones.add(new SampleCodec.Zone(values,"TEST"));byte[] slot=SampleCodec.slot(zones,"TEST",false);ByteBuffer h=ByteBuffer.wrap(slot).order(ByteOrder.LITTLE_ENDIAN);
  check(h.getInt(0)==0x504d5346&&h.get(6)==1&&h.getInt(36)==7&&h.get(57)==0&&h.get(58)==127);
  CRC32 crc=new CRC32();crc.update(slot,512,slot.length-512);check(h.getInt(20)==(int)crc.getValue());
  zones.get(0).pcm=new short[SampleCodec.RATE*10];try{SampleCodec.slot(zones,"LONG",false);throw new AssertionError();}catch(IllegalArgumentException expected){}
  SampleCodec.fit(zones);check(SampleCodec.slot(zones,"FIT",false).length<=81920);
  ByteBuffer wav=ByteBuffer.allocate(44+16).order(ByteOrder.LITTLE_ENDIAN);wav.putInt(0,0x46464952);wav.putInt(4,52);wav.putInt(8,0x45564157);wav.putInt(12,0x20746d66);wav.putInt(16,16);wav.putShort(20,(short)1);wav.putShort(22,(short)2);wav.putInt(24,44100);wav.putInt(28,176400);wav.putShort(32,(short)4);wav.putShort(34,(short)16);wav.putInt(36,0x61746164);wav.putInt(40,16);for(int i=44;i<60;i+=2)wav.putShort(i,(short)12000);
  short[] mono=SampleCodec.wav(wav.array());check(mono.length==2&&mono[0]==12000);
  try{SampleCodec.wav(new byte[44]);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS WAV decoding, ADPCM, slot CRC, size rejection and fit");
 }
}
