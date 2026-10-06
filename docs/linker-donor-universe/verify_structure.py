from pathlib import Path
import subprocess,re,json,hashlib
root=Path(__file__).parent
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
old=subprocess.check_output(['git','show',base+':kanger/src/org/kanger/Linker.java']).decode()
new=Path('kanger/src/org/kanger/Linker.java').read_text()
def strip_comments(text):
    return re.sub(r'\s+','',re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S))
def region(text,start,end):return text[text.index(start):text.index(end)]
before=region(old,'public class Linker','    public void link(')
assert before==region(new,'public class Linker','    public void link(')
tail='    private boolean rotateVariables'
assert old[old.index(tail):]==new[new.index(tail):]
link=region(new,'    public void link(','    private boolean rotator(')
link=link.replace('rotator(leftList, leftList, causes, logging);','rotator(leftList, causes, logging);').replace('rotator(ruleList, ruleList, causes, logging);','rotator(ruleList, causes, logging);')
assert strip_comments(link)==strip_comments(region(old,'    public void link(','    private boolean rotator('))
rot=region(new,'    private boolean rotator(',tail)
rot=re.sub(r'private boolean rotator\(.*?throws Exception \{','private boolean rotator(final Collection<IRule> ruleList, final Map<IRule, Set<Cause>> causes, final boolean logging) throws Exception {',rot,count=1,flags=re.S)
rot=rot.replace('buildDomainIndex(donorRules)','buildDomainIndex(ruleList)').replace('for (IRule r : executionRules)','for (IRule r : ruleList)')
assert strip_comments(rot)==strip_comments(region(old,'    private boolean rotator(',tail))
assert 'LinkerScopeTrace' not in new
result={'base':base,'pre_link_body_identical':True,'semantic_kernel_tail_identical':True,'link_normalized_identical':True,'rotator_normalized_identical':True,'production_trace_absent':True,'kernel_sha256':hashlib.sha256(new[new.index(tail):].encode()).hexdigest()}
(root/'structural-check.json').write_text(json.dumps(result,indent=2)+'\n')
print('DONOR_STRUCTURE_OK')
