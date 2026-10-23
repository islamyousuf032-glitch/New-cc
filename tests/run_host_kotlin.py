#!/usr/bin/env python3
"""Compile/run production pure Kotlin timeline projections, not Android code.
Usage: python3 tests/run_host_kotlin.py JAVA_HOME KOTLIN_HOME
Requires a compiler distribution matching the app Kotlin pin (currently 2.3.21).
Generated jars live only in a temporary directory. No Android stubs are used.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
java_home, kotlin_home = [Path(arg).resolve() for arg in sys.argv[1:]]
env = dict(os.environ, JAVA_HOME=str(java_home), PATH=str(java_home/'bin')+os.pathsep+os.environ['PATH'])
with tempfile.TemporaryDirectory(prefix='owncut-kotlin-') as directory:
    jar = Path(directory)/'projection-tests.jar'
    sources = [root/'app/src/main/java/com/owncut/app'/name for name in ('TimelineSnapshot.kt','TimelineClipIndex.kt')]
    subprocess.run(['bash',str(kotlin_home/'bin/kotlinc'),*[str(p) for p in sources],
                    str(root/'tests/kotlin/TimelineProjectionTest.kt'),'-Werror','-include-runtime','-d',str(jar)],env=env,check=True)
    subprocess.run([str(java_home/'bin/java'),'-jar',str(jar)],env=env,check=True)
