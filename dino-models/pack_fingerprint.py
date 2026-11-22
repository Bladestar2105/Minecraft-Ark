"""Separate a ZIP's cache-relevant contents from compression and timestamp changes."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def fingerprint(path):
    data = Path(path).read_bytes()
    entries, metadata, timestamps = {}, {}, {}
    with zipfile.ZipFile(path) as archive:
        order = archive.namelist()
        if len(order) != len(set(order)):
            raise ValueError('Duplicate ZIP entries cannot identify one canonical pack')
        for info in archive.infolist():
            if not info.is_dir():
                entries[info.filename] = digest(archive.read(info))
            offset = info.header_offset
            if data[offset:offset+4] != b'PK\x03\x04':
                raise ValueError('Invalid ZIP local header')
            name_size, extra_size = struct.unpack_from('<HH', data, offset+26)
            start = offset + 30 + name_size + extra_size
            metadata[info.filename] = dict(compression=info.compress_type, flags=info.flag_bits,
                external_attr=info.external_attr, internal_attr=info.internal_attr,
                create_system=info.create_system, create_version=info.create_version,
                extract_version=info.extract_version, extra=info.extra.hex(), comment=info.comment.hex(),
                local_extra=data[offset+30+name_size:start].hex(),
                compressed_payload_sha256=digest(data[start:start+info.compress_size]))
            timestamps[info.filename] = list(info.date_time)
        header = json.loads(archive.read('manifest.json')).get('header') if 'manifest.json' in order else None
        comment = archive.comment.hex()
    canonical = ''.join(name + '\0' + entries[name] + '\n' for name in sorted(entries)).encode('utf-8')
    return dict(zip_sha256=digest(data), canonical_content_sha256=digest(canonical),
                canonical_algorithm='SHA256 of sorted UTF-8 filename + NUL + uncompressed SHA256 hex + LF; directories excluded',
                header=header, entries=entries, zip_order=order, zip_metadata=metadata,
                zip_timestamps=timestamps, zip_comment=comment)


def compare(before, after):
    changed = sorted(name for name in before['entries'].keys() | after['entries'].keys()
                     if before['entries'].get(name) != after['entries'].get(name))
    same_content = before['canonical_content_sha256'] == after['canonical_content_sha256']
    same_other_metadata = all(before[key] == after[key] for key in ['zip_order', 'zip_metadata', 'zip_comment'])
    same_zip = before['zip_sha256'] == after['zip_sha256']
    old_header, new_header = before.get('header') or {}, after.get('header') or {}
    return dict(same_zip=same_zip, same_canonical_content=same_content, changed_content_entries=changed,
                only_zip_timestamps_changed=not same_zip and same_content and same_other_metadata
                    and before['zip_timestamps'] != after['zip_timestamps'],
                same_header_uuid=old_header.get('uuid') == new_header.get('uuid') if old_header and new_header else None,
                same_header_version=old_header.get('version') == new_header.get('version') if old_header and new_header else None)


if __name__ == '__main__':
    result = fingerprint(sys.argv[1])
    if len(sys.argv) > 2:
        result = compare(result, fingerprint(sys.argv[2]))
    print(json.dumps(result, sort_keys=True))
