#!/usr/bin/env python3
"""Sign an APK and stage it only after checking compatibility with the live channel."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

EXPECTED_CERT = 'b72faffa58949b4c411c7cecf9439cc2f18660451c1644004251b253d1dd8772'
LEGACY_CERT = '04b320989176ff0f34a4503999d6cc33c07472a283f722701b98411c8f2f72de'
LEGACY_MANIFEST = {
    'version': 'v0.2.6',
    'size': 95116,
    'digest': 'sha256:ed87fe47bab0036b8b0279bf5d83b4ca0cf26fd668d9fefd9919c5a0ab6a7a62',
}
ASSET = 'WeChatTodo-debug.apk'


def certificate(apksigner, apk, expected=EXPECTED_CERT):
    output = subprocess.check_output(
        [str(apksigner), 'verify', '--print-certs', str(apk)], text=True)
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$',
                         output, re.MULTILINE)
    if digests != [expected]:
        raise ValueError('APK signing certificate does not match the expected update certificate')


def package_info(aapt, apk):
    output = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
                      output, re.MULTILINE)
    if not match:
        raise ValueError('Cannot read APK package/version metadata')
    name, code, version = match.groups()
    if name != 'com.wechattodo.assistant' or not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Unexpected app identity or version')
    return int(code), version


def prepare(apk, channel, mode):
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / os.getenv('ANDROID_BUILD_TOOLS_VERSION', '35.0.0')
    apksigner, aapt = tools / 'apksigner', tools / 'aapt'
    old_apk = channel / ASSET
    old_code, old_version = package_info(aapt, old_apk)
    old_manifest = json.loads((channel / 'updates.json').read_text())
    old_data = old_apk.read_bytes()
    expected_old = {'version': 'v' + old_version, 'size': len(old_data),
                    'digest': 'sha256:' + hashlib.sha256(old_data).hexdigest()}
    if old_manifest != expected_old:
        raise ValueError('Existing update manifest does not match its APK')
    # Only this exact archived release may be the starting point for the signing transition.
    certificate(apksigner, old_apk, LEGACY_CERT if old_manifest == LEGACY_MANIFEST else EXPECTED_CERT)
    if mode == '--already-signed':
        stage_update(apk, channel, apksigner, aapt, old_code, old_version)
        return

    for name in ('ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
                 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD'):
        if not os.environ.get(name):
            raise ValueError(f'Missing GitHub Actions secret: {name}')
    with tempfile.TemporaryDirectory(prefix='android-sign-', dir=os.environ.get('RUNNER_TEMP')) as tmp:
        key, signed = Path(tmp) / 'signing.keystore', Path(tmp) / 'signed.apk'
        with key.open('xb') as stream:
            os.chmod(key, 0o600)
            stream.write(base64.b64decode(os.environ['ANDROID_KEYSTORE_BASE64'].strip(), validate=True))
        subprocess.run([str(apksigner), 'sign', '--ks', str(key),
                        '--ks-key-alias', os.environ['ANDROID_KEY_ALIAS'],
                        '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD',
                        '--key-pass', 'env:ANDROID_KEY_PASSWORD',
                        '--out', str(signed), str(apk)], check=True)
        if mode == '--verify-key':
            certificate(apksigner, signed)
            print('Update signing key verified; update channel unchanged')
            return
        stage_update(signed, channel, apksigner, aapt, old_code, old_version)


def stage_update(signed, channel, apksigner, aapt, old_code, old_version):
    certificate(apksigner, signed)
    new_code, version = package_info(aapt, signed)
    if new_code <= old_code or tuple(map(int, version.split('.'))) <= tuple(map(int, old_version.split('.'))):
        raise ValueError('Both versionCode and versionName must increase before publishing')
    tag = 'v' + version
    target = channel / tag
    if target.exists():
        raise ValueError(f'Refusing to replace immutable version: {tag}')
    data = signed.read_bytes()
    if not 0 < len(data) <= 64 * 1024 * 1024:
        raise ValueError('APK exceeds updater size limit')
    manifest = {'version': tag, 'size': len(data),
                'digest': 'sha256:' + hashlib.sha256(data).hexdigest()}
    target.mkdir()
    shutil.copyfile(signed, target / ASSET)
    shutil.copyfile(signed, channel / ASSET)
    (channel / 'updates.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Prepared {tag}; update signing certificate verified')


if __name__ == '__main__':
    try:
        if len(sys.argv) not in (3, 4) or (len(sys.argv) == 4 and sys.argv[3] not in ('--verify-key', '--already-signed')):
            sys.exit('Usage: prepare-update.py APK CHANNEL [--verify-key | --already-signed]')
        prepare(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3] if len(sys.argv) == 4 else None)
    except (ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
