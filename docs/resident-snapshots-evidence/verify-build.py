from pathlib import Path
import json,hashlib,subprocess,struct
out=Path(__file__).parent
ref=Path('../build/defaults-classes');exp=Path('../build/resident-snapshots-classes')
assert (ref/'org/kanger/SonProfileRunner.class').read_bytes()==(exp/'org/kanger/SonProfileRunner.class').read_bytes()
assert (ref/'org/kanger/units/Predicate.class').read_bytes()==(exp/'org/kanger/units/Predicate.class').read_bytes()
assert b'compactFindSnapshots' not in (ref/'org/kanger/storage/Escalera.class').read_bytes()
assert b'directPredicateName' not in (exp/'org/kanger/units/Predicate.class').read_bytes()
def normalize_lines(data):
    # Locate the UTF8 constant for LineNumberTable, then normalize only its
    # validated line entries. All other bytes must remain identical.
    pattern=b'\x01\x00\x0fLineNumberTable';pos=data.index(pattern)
    count=struct.unpack_from('>H',data,8)[0];p=10;i=1;index=None
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,18:4,19:2,20:2}
    while i<count:
        if p==pos:index=i
        tag=data[p];p+=1
        if tag==1:
            n=struct.unpack_from('>H',data,p)[0];p+=2+n
        else:p+=sizes[tag]
        i+=2 if tag in (5,6) else 1
    assert index is not None
    result=bytearray(data);needle=struct.pack('>H',index);start=p;tables=0
    while True:
        start=data.find(needle,start)
        if start<0:break
        if start+8<=len(data):
            length=struct.unpack_from('>I',data,start+2)[0]
            n=struct.unpack_from('>H',data,start+6)[0]
            if length==2+4*n and start+6+length<=len(data):
                for j in range(n):result[start+10+4*j:start+12+4*j]=b'\x00\x00'
                tables+=1
        start+=2
    assert tables==4,tables
    return bytes(result)
diffs=[str(p.relative_to(exp)) for p in exp.rglob('*.class') if (ref/p.relative_to(exp)).exists() and p.read_bytes()!=(ref/p.relative_to(exp)).read_bytes()]
inner='org/kanger/storage/Escalera$WalkIterator.class'
assert set(diffs)=={'org/kanger/factory/RuleFactory.class','org/kanger/factory/TValueFactory.class','org/kanger/storage/Escalera.class',inner}
assert normalize_lines((ref/inner).read_bytes())==normalize_lines((exp/inner).read_bytes())
manifest={'base':'3a2a6f0bccac11ac0d646178c4384ba73d7a5260','code':subprocess.check_output(['git','rev-parse','HEAD']).decode().strip(),'java':'17.0.20','reference_classpath':str(ref),'experiment_classpath':str(exp),'natives_sha256':hashlib.sha256(Path('natives.k').read_bytes()).hexdigest(),'runner_bytecode_equal':True,'predicate_bytecode_equal':True,'changed_class_files':diffs,'walk_iterator_difference':'line numbers only','reference_has_prototype_flags':False,'name_prototype_absent':True}
(out/'build-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('ISOLATED_BUILD_VERIFIED')
