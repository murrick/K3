"""Read method Code length from a local JVM class file; no javap dependency."""
import struct,sys
from pathlib import Path
for filename in sys.argv[1:]:
 b=Path(filename).read_bytes();pos=8
 def u2():
  global pos
  n=struct.unpack_from('>H',b,pos)[0];pos+=2;return n
 def u4():
  global pos
  n=struct.unpack_from('>I',b,pos)[0];pos+=4;return n
 cp={};count=u2();i=1
 while i<count:
  tag=b[pos];pos+=1
  if tag==1:
   n=u2();cp[i]=b[pos:pos+n].decode('utf-8',errors='replace');pos+=n
  elif tag in (3,4,9,10,11,12,17,18):pos+=4
  elif tag in (5,6):pos+=8;i+=1
  elif tag in (7,8,16,19,20):pos+=2
  elif tag==15:pos+=3
  else:raise ValueError(tag)
  i+=1
 pos+=6;interfaces=u2();pos+=interfaces*2
 def attributes():
  global pos
  result=[]
  for _ in range(u2()):
   name=cp[u2()];n=u4();payload=b[pos:pos+n];pos+=n;result.append((name,payload))
  return result
 for _ in range(u2()):pos+=6;attributes()
 for _ in range(u2()):
  access=u2();name=cp[u2()];descriptor=cp[u2()];attrs=attributes()
  if name=='equalsBase':
   code=next(x[1] for x in attrs if x[0]=='Code');length=struct.unpack_from('>I',code,4)[0]
   print(filename,name,descriptor,'bytecode_length='+str(length))
