"""Восстановление exact trees; существующие каталоги не перезаписываются."""
from pathlib import Path
import subprocess,tarfile,io,hashlib,json
pins={'K3-observer-native':'2422f7f6d9e1af7608203b1566df64b5f29fe344','K3-current-native':'60fdc34736c51fa4e5659982630347fb1d1b59fc'}
for name,pin in pins.items():
 subprocess.run(['git','cat-file','-e',pin+'^{commit}'],check=True)
 dest=Path('..')/name;assert not dest.exists(),str(dest)+' already exists'
 dest.mkdir()
 with tarfile.open(fileobj=io.BytesIO(subprocess.check_output(['git','archive',pin]))) as t:t.extractall(dest,filter='data')
compiler=Path('../tooling/ecj.jar');assert compiler.exists(),'download ECJ 3.33.0 as ../tooling/ecj.jar first'
assert hashlib.sha256(compiler.read_bytes()).hexdigest()==json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())['compiler_sha256']
print('EXACT_TREES_AND_COMPILER_OK')
