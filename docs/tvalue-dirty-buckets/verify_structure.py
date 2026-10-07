from pathlib import Path
import re,json,hashlib,subprocess
root=Path(__file__).parent;temp=Path('../build/tvalue-dirty-buckets-source')
assert not subprocess.check_output(['git','diff','c90c3619ef3b9cf2267af0b618bb60c452516913','--','kanger/src','kanger-qualification/src','.github'])
results={}
for name in ['Mind.java','Linker.java','factory/TValueFactory.java']:
    orig=Path('kanger/src/org/kanger/'+name).read_text();body=(temp/name).read_text()
    if name=='Mind.java':
        body=re.sub(r'    private boolean commit\(IMind m, boolean settleRejectedChild\) throws Exception \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
        body=re.sub(r'    public void release\(IMind m\) throws Exception \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
        body=re.sub(r'    public void setUnitDeleted\(IUnit unit, boolean on\) \{\n.*?\n    \}\n\n','',body,count=1,flags=re.S)
        body=body.replace('private void journalSetUnitDeletedBody(','public void setUnitDeleted(')
        body=body.replace('private boolean journalCommitBody(','private boolean commit(').replace('private void journalReleaseBody(','public void release(')
        body=body.replace('        TValueDirtyJournal.constructed(this);\n','')
    elif name=='Linker.java':body=re.sub(r'        (?:    )?TValueDirtyJournal\.(?:linkStart|observe)\(.*?;\n','',body)
    else:
        body=body.replace('        long journalMark=cache.mark();\n        org.kanger.TValueDirtyJournal.mark(mind);\n        return journalMark;','        return cache.mark();')
        body=re.sub(r'(?m)^ +org.kanger.TValueDirtyJournal\.(?:reset|complete|touch|promoted)\(mind.*?;\n','',body)
    assert body==orig,name
    results[name]={'all_original_code_recovered_byte_for_byte':True,'sha256':hashlib.sha256(orig.encode()).hexdigest()}
(root/'structural-check.json').write_text(json.dumps(results,indent=2)+'\n')
print('TVALUE_DIRTY_JOURNAL_STRUCTURE_OK')
