#!/usr/bin/env python3
"""Preview or merge three quest-name hints without changing quest/player data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import zipfile
from build_plugins import ROOT
from fetch_world import safe_path

KEYS={"questListTitle","questNotFound","COMMAND_TAKE_HELP"}
LOCALE="data/plugins/Quests/lang/de-DE/strings.yml"


def configure(root=ROOT,apply=False,expected_sha256=None):
    root=Path(root).resolve();target=safe_path(root,LOCALE)
    patch=json.loads(safe_path(root,"config/quests/messages-de-DE.json").read_text())
    if set(patch)!=KEYS or any(not isinstance(v,str) or "\n" in v or "\r" in v for v in patch.values()):
        raise ValueError("Expected exactly three single-line quest message hints")
    exists=target.exists();original=target.read_bytes() if exists else None
    before=hashlib.sha256(original).hexdigest() if exists else "absent"
    if exists:
        text=original.decode("utf-8")
    else:
        items=json.loads(safe_path(root,"dependencies-runtime.json").read_text())
        item=next(item for item in items if item.get("project")=="quests")
        jar=safe_path(root,"data/plugins/"+item["name"])
        algorithm="sha512" if "sha512" in item else "sha256"
        if hashlib.new(algorithm,jar.read_bytes()).hexdigest()!=item[algorithm]:
            raise ValueError("Quests JAR does not match the pinned dependency")
        with zipfile.ZipFile(jar) as archive:text=archive.read("lang/de-DE/strings.yml").decode("utf-8")
    for key,value in patch.items():
        pattern=re.compile(r"^"+re.escape(key)+r":[^\r\n]*",re.MULTILINE);matches=list(pattern.finditer(text))
        if len(matches)>1 or matches and matches[0].group().split(":",1)[1].strip().startswith(("|",">")):
            raise ValueError("Duplicate or multiline message key: "+key)
        if matches:
            for line in text[matches[0].end():].splitlines()[1:]:
                if not line.strip() or line.lstrip().startswith("#"):continue
                if line[0].isspace():raise ValueError("Multiline message key: "+key)
                break
        replacement=key+": "+json.dumps(value,ensure_ascii=False)
        if matches:text=pattern.sub(lambda match:replacement,text,count=1)
        else:text+=("" if text.endswith("\n") else "\n")+replacement+"\n"
    content=text.encode("utf-8");changed=original!=content
    result={"target":str(target),"changed":changed,"before_sha256":before,"after_sha256":hashlib.sha256(content).hexdigest(),"applied":False}
    if not apply or not changed:return result
    if expected_sha256!=before:raise ValueError("Locale changed or no expected SHA256 supplied; preview again")
    target.parent.mkdir(parents=True,exist_ok=True)
    descriptor,name=tempfile.mkstemp(prefix=".strings-",dir=target.parent);temporary=Path(name)
    try:
        with os.fdopen(descriptor,"wb") as stream:stream.write(content);stream.flush();os.fsync(stream.fileno())
        if exists:
            metadata=target.stat();os.chmod(temporary,stat.S_IMODE(metadata.st_mode));os.chown(temporary,metadata.st_uid,metadata.st_gid)
        else:os.chmod(temporary,0o644)
        # Reject a concurrent operator/plugin edit before atomic replacement.
        current=hashlib.sha256(target.read_bytes()).hexdigest() if target.exists() else "absent"
        if current!=before:raise ValueError("Locale changed during merge; preview again")
        temporary.replace(target);result["applied"]=True
    finally:temporary.unlink(missing_ok=True)
    return result


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root",type=Path,default=ROOT,help="Installation directory containing config/ and data/")
    parser.add_argument("--apply",action="store_true",help="Write only this language file; never reload/restart Quests")
    parser.add_argument("--expected-sha256",help="before_sha256 from preview; use absent for a new locale")
    options=parser.parse_args()
    print(json.dumps(configure(options.root,options.apply,options.expected_sha256),ensure_ascii=False,indent=2))
