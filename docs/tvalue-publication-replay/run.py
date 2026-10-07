from pathlib import Path
import subprocess,tempfile,gzip,json,sys
root=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
matrix=[]
for build in ('order','shadow'):
    for mode,repeat in [('on',''),('off',''),('verify',''),('on','repeat-')]:
        label=build+'-'+mode+'-'+repeat+'capture'
        matrix.append((build,mode,label,'ThreadedPublicationCaptureRunner',[str(root/(label+'.cases')),str(root/(label+'.events'))],True))
        matrix.append(('clean',mode,label+'-clean-replay','CleanPublicationReplayRunner',[str(root/(label+'.cases'))],False))
for mode in ('on','off','verify'):
    matrix.append(('shadow',mode,'shadow-'+mode+'-TValueDirtyJournalSafetyRunner','TValueDirtyJournalSafetyRunner',[],False))
    matrix.append(('order',mode,'order-'+mode+'-KangerMindCommitExceptionAtomicitySafetyRunner','KangerMindCommitExceptionAtomicitySafetyRunner',[],False))
for build in ('order','shadow'):
    for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner'):
        label=build+'-on-'+runner
        args=['exact','all'] if runner=='ExactCandidateReplayRunner' else [str(root/(label+'.state')),str(root/(label+'.work'))]
        matrix.append((build,'on',label,runner,args,True))
markers={'ThreadedPublicationCaptureRunner':'THREADED_PUBLICATION_CAPTURE_OK cases=18 workers=54 entries=54 exits=54 registrations=0','CleanPublicationReplayRunner':'CLEAN_PUBLICATION_REPLAY_OK cases=18 full_inputs=true full_results=true reservations=0','TValueDirtyJournalSafetyRunner':'TVALUE_DIRTY_JOURNAL_SAFETY_OK checks=83','KangerMindCommitExceptionAtomicitySafetyRunner':'MIND_COMMIT_EXCEPTION_ATOMICITY_OK','ExactCandidateReplayRunner':'EXACT_CANDIDATE_REPLAY_OK','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}
start=int(sys.argv[1]) if len(sys.argv)>1 else 0;stop=int(sys.argv[2]) if len(sys.argv)>2 else len(matrix)
for i,(build,mode,label,runner,args,wrapper) in enumerate(matrix):
    if not start<=i<stop:continue
    p=root/label;options=['-Dkanger.experiment.'+f+'='+('false' if mode=='off' else 'true') for f in flags]
    if mode=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    wrapped=build=='shadow' and wrapper
    target=['org.kanger.DirtyJournalRunner',runner,*args] if wrapped else ['org.kanger.'+runner,*args]
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='publication-replay-'),'-Djournal.path='+str(p)+'.trace',*options,'-cp','../build/tvalue-publication-replay-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',*target]
    print('START',i,label,flush=True)
    run=subprocess.run(cmd,capture_output=True,timeout=300);Path(str(p)+'.err').write_bytes(run.stderr);Path(str(p)+'.log.gz').write_bytes(gzip.compress(run.stdout,mtime=0))
    assert run.returncode==0 and not run.stderr and markers[runner] in run.stdout.decode(),(label,run.returncode,run.stderr.decode()[-1500:])
    if wrapped or runner=='TValueDirtyJournalSafetyRunner':
        trace=Path(str(p)+'.trace');data=trace.read_bytes();assert data.decode().splitlines()[-1].startswith('DIRTY_JOURNAL_OK ')
        Path(str(trace)+'.gz').write_bytes(gzip.compress(data,mtime=0));trace.unlink()
    print('DONE',i,label,flush=True)
# A real native wrong-order replay must fail despite retaining six solutions.
if stop==len(matrix):
    rows=(root/'order-on-capture.cases').read_text().splitlines();fields=rows[0].split('\t');assert fields[1]=='123';fields[1]='213';rows[0]='\t'.join(fields)
    tampered=root/'tampered-order.cases';tampered.write_text('\n'.join(rows)+'\n')
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='publication-order-gap-'),*['-Dkanger.experiment.'+f+'=true' for f in flags],'-cp','../build/tvalue-publication-replay-clean:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.CleanPublicationReplayRunner',str(tampered)]
    run=subprocess.run(cmd,capture_output=True,timeout=120);(root/'tampered-order.log.gz').write_bytes(gzip.compress(run.stdout,mtime=0));(root/'tampered-order.err').write_bytes(run.stderr)
    assert run.returncode!=0 and b'complete native replay outcome forced-0 order=213' in run.stderr and b'CLEAN_PUBLICATION_REPLAY_OK' not in run.stdout
    (root/'tampered-order-result.json').write_text(json.dumps({'exit_code':run.returncode,'expected_rejection':True,'changed_native_publication_order':'123 -> 213','native_both_branches_have_six_solutions':True},indent=2)+'\n')
    print('EXPECTED_NATIVE_ORDER_REJECTION_OK',flush=True)
