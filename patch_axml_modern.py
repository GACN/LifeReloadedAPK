#!/usr/bin/env python3
import struct, sys, zipfile
from pathlib import Path

ANDROID_NS='http://schemas.android.com/apk/res/android'
ATTR={
 'label':0x01010001,'icon':0x01010002,'name':0x01010003,'exported':0x01010010,'roundIcon':0x0101052c,
 'versionCode':0x0101021b,'versionName':0x0101021c,
 'minSdkVersion':0x0101020c,'targetSdkVersion':0x01010270,
 'allowBackup':0x01010280,'usesCleartextTraffic':0x010104ec,
}
BAD={0x01010433:ATTR['label'],0x0101056c:ATTR['name']}
TYPE_INT_DEC=0x10
TYPE_STRING=0x03
TYPE_BOOL=0x12

class AXML:
 def __init__(self,data):
  self.data=bytearray(data)
  self.xml_size=struct.unpack_from('<I',self.data,4)[0]
  # string pool is normally first chunk after XML header
  off=8
  typ,head,size=struct.unpack_from('<HHI',self.data,off)
  if typ!=0x0001:
   raise RuntimeError(f'expected string pool at 8, got {typ:x}')
  self.sp_off=off; self.sp_size=size
  self.str_count,self.style_count,self.flags,self.strings_start,self.styles_start=struct.unpack_from('<IIIII',self.data,off+8)
  self.offsets_base=off+head
  self.strings_base=off+self.strings_start
  self.utf8=bool(self.flags & 0x100)
  self.strings=[self.get_string(i) for i in range(self.str_count)]
 def get_string(self,i):
  off=self.strings_base+struct.unpack_from('<I',self.data,self.offsets_base+i*4)[0]
  if self.utf8:
   p=off
   a=self.data[p]; p+=1
   if a&0x80: p+=1
   b=self.data[p]; p+=1
   if b&0x80:
    byte_len=((b&0x7f)<<8)|self.data[p]; p+=1
   else: byte_len=b
   return bytes(self.data[p:p+byte_len]).decode('utf-8','replace')
  else:
   n=struct.unpack_from('<H',self.data,off)[0]; p=off+2
   return bytes(self.data[p:p+n*2]).decode('utf-16le','replace')
 def sidx(self,s):
  try: return self.strings.index(s)
  except ValueError: return None
 def chunks(self):
  p=8+self.sp_size
  while p < len(self.data)-8:
   typ,head,size=struct.unpack_from('<HHI',self.data,p)
   yield p,typ,head,size
   if size<=0: break
   p+=size
 def resource_map(self):
  for p,typ,head,size in self.chunks():
   if typ==0x0180:
    return p,size
   if typ not in (0x0180,0x0100,0x0102,0x0103,0x0104):
    continue
  return None,None
 def patch_resource_map(self):
  p,size=self.resource_map()
  if p is None: return 0
  # Android badging/install parsers need the ResourceMap entry for each android attr
  # string index, not just name/label. Old Termux aapt emits only two entries.
  needed=[]
  for name in ATTR:
   idx=self.sidx(name)
   if idx is not None:
    needed.append((idx,ATTR[name]))
  if not needed: return 0
  old_count=(size-8)//4
  new_count=max(old_count, max(i for i,_ in needed)+1)
  vals=[0]*new_count
  for i in range(min(old_count,new_count)):
   vals[i]=struct.unpack_from('<I',self.data,p+8+i*4)[0]
  n=0
  for idx,aid in needed:
   if vals[idx] != aid:
    vals[idx]=aid; n+=1
  new_size=8+4*new_count
  new_chunk=struct.pack('<HHI',0x0180,8,new_size)+b''.join(struct.pack('<I',v) for v in vals)
  delta=new_size-size
  self.data[p:p+size]=new_chunk
  if delta:
   self.xml_size += delta
   struct.pack_into('<I', self.data, 4, self.xml_size)
  return n
 def elements(self):
  for p,typ,head,size in self.chunks():
   if typ==0x0102:
    line,comment,ns,name,attr_start,attr_size,attr_count,id_idx,class_idx,style_idx=struct.unpack_from('<IIIIHHHHHH',self.data,p+8)
    ename=self.strings[name]
    yield p,ename,attr_start,attr_size,attr_count
 def patch_attrs(self):
  android_ns=self.sidx(ANDROID_NS)
  n=0
  for elem_off,ename,attr_start,attr_size,attr_count in self.elements():
   for i in range(attr_count):
    off=elem_off+16+attr_start+i*attr_size
    ns,name,raw,tsize,res0,dtype,data=struct.unpack_from('<IIIHBBI',self.data,off)
    name_s=self.strings[name] if name < len(self.strings) else ''
    # Fix bad android name/label IDs by name string and bad data IDs.
    if name_s in ATTR:
     if android_ns is not None and ns != android_ns:
      struct.pack_into('<I',self.data,off,android_ns); n+=1
    # Direct replace wrong resource IDs anywhere in attr typed fields/resource map
    if data in BAD:
     struct.pack_into('<I',self.data,off+16,BAD[data]); n+=1
  # brute-force old bad ids in whole file
  for i in range(len(self.data)-3):
   val=struct.unpack_from('<I',self.data,i)[0]
   if val in BAD:
    struct.pack_into('<I',self.data,i,BAD[val]); n+=1
  return n

def main():
 src,out=sys.argv[1],sys.argv[2]
 with zipfile.ZipFile(src) as z: data=z.read('AndroidManifest.xml')
 ax=AXML(data)
 rn=ax.patch_resource_map(); an=ax.patch_attrs()
 Path(out).parent.mkdir(parents=True,exist_ok=True); Path(out).write_bytes(ax.data)
 print('strings',len(ax.strings),'resource_map_patches',rn,'attr_patches',an)
if __name__=='__main__': main()
