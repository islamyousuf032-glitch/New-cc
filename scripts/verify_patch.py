#!/usr/bin/env python3
"""Fail closed on unexpected signer; publish only public identity/upgrade metadata."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
apk, signer, expected, version = sys.argv[1:]
assert 2 <= int(version) <= 2100000000
normalize = lambda value: re.sub('[^0-9a-f]', '', value.lower())
expected = normalize(expected)
assert len(expected) == 64, 'A pinned SHA-256 certificate fingerprint is required'
result = subprocess.run([signer,'verify','--verbose','--print-certs',apk],check=True,text=True,capture_output=True).stdout
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',result)
assert len(certs) == 1 and normalize(certs[0]) == expected, 'Signing certificate mismatch'
file = Path(apk)
aapt = str(Path(signer).with_name('aapt2'))
badging = subprocess.run([aapt,'dump','badging',apk],check=True,text=True,capture_output=True).stdout
identity = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
assert identity and identity[1] == 'com.owncut.app' and int(identity[2]) == int(version), 'APK package/version mismatch'
assert 'application-debuggable' not in badging, 'Publish the non-debuggable release APK' 
metadata = dict(packageName='com.owncut.app',versionCode=int(version),signerSha256=expected,
                apkSha256=hashlib.sha256(file.read_bytes()).hexdigest(),format='full-apk-update',
                legacyDebugCompatibility='not-guaranteed',requiresSameSigningCertificate=True)
(file.parent/'update-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
print('Pinned signer verified; update metadata generated')
