import hashlib
import json
import os
import zlib
from typing import Any, Union

B91_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,./:;<=>?@[]^_`{|}~"'
B91_DECODE = {c: i for i, c in enumerate(B91_ALPHABET)}

TYPE_CP1251 = 0
TYPE_UTF8 = 1
TYPE_BYTES = 2
TYPE_JSON = 3
FLAG_COMPRESSED = 4
FLAG_SALTED = 8

def b91_encode(data: Union[bytes, bytearray, memoryview]) -> str:
    b = 0
    n = 0
    out = []
    alphabet = B91_ALPHABET
    for byte in memoryview(data):
        b |= byte << n
        n += 8
        if n > 13:
            v = b & 8191
            if v > 88:
                b >>= 13
                n -= 13
            else:
                v = b & 16383
                b >>= 14
                n -= 14
            out.append(alphabet[v % 91])
            out.append(alphabet[v // 91])
    if n:
        out.append(alphabet[b % 91])
        if n > 7 or b > 90:
            out.append(alphabet[b // 91])
    return "".join(out)

def b91_decode(encoded: str) -> bytes:
    v = -1
    b = 0
    n = 0
    out = bytearray()
    decode_map = B91_DECODE
    for c in encoded:
        c_val = decode_map.get(c)
        if c_val is None:
            continue
        if v < 0:
            v = c_val
        else:
            v += c_val * 91
            b |= v << n
            n += 13 if (v & 8191) > 88 else 14
            while n > 7:
                out.append(b & 255)
                b >>= 8
                n -= 8
            v = -1
    if v + 1:
        out.append((b | (v << n)) & 255)
    return bytes(out)

def xor_crypt(
    data: Union[bytes, bytearray],
    key: Union[str, bytes, bytearray],
    salt: bytes = b"",
) -> bytes:
    if not data or not key:
        return bytes(data)
    key_bytes = key.encode("utf-8") if isinstance(key, str) else bytes(key)
    length = len(data)
    stream = hashlib.shake_256(key_bytes + b"\x00" + salt).digest(length)
    return (
        int.from_bytes(data, "little") ^ int.from_bytes(stream, "little")
    ).to_bytes(length, "little")

def _serialize_payload(data: Any) -> tuple[bytes, int]:
    if isinstance(data, str):
        try:
            return data.encode("cp1251"), TYPE_CP1251
        except UnicodeEncodeError:
            return data.encode("utf-8"), TYPE_UTF8
    if isinstance(data, (bytes, bytearray, memoryview)):
        return bytes(data), TYPE_BYTES
    raw_json = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return raw_json.encode("utf-8"), TYPE_JSON

def _deserialize_payload(raw: bytes, dtype: int) -> Any:
    if dtype == TYPE_CP1251:
        return raw.decode("cp1251")
    if dtype == TYPE_UTF8:
        return raw.decode("utf-8")
    if dtype == TYPE_BYTES:
        return raw
    if dtype == TYPE_JSON:
        return json.loads(raw.decode("utf-8"))
    raise ValueError("Unknown payload type")

def pack_and_hide(
    data: Any,
    key: Union[str, bytes, None] = None,
    salted: bool = False,
    custom_salt: bytes = None
) -> str:
    raw, dtype = _serialize_payload(data)
    c_obj = zlib.compressobj(level=9, method=zlib.DEFLATED, wbits=-15)
    compressed = c_obj.compress(raw) + c_obj.flush()

    if len(compressed) < len(raw):
        payload = compressed
        flags = dtype | FLAG_COMPRESSED
    else:
        payload = raw
        flags = dtype

    key_bytes = (
        key.encode("utf-8")
        if isinstance(key, str)
        else (bytes(key) if key else b"")
    )
    if salted and key_bytes:
        flags |= FLAG_SALTED

    crc_tag = (zlib.crc32(raw) & 0xF) << 4
    flags |= crc_tag

    if key_bytes:
        if salted:
            salt = custom_salt if custom_salt is not None else os.urandom(4)
            first_mask = hashlib.shake_256(key_bytes + b"\x00").digest(1)[0]
            head = bytes([flags ^ first_mask])
            obfuscated = head + salt + xor_crypt(payload, key_bytes, salt)
        else:
            obfuscated = xor_crypt(bytes([flags]) + payload, key_bytes)
    else:
        obfuscated = bytes([flags]) + payload

    return b91_encode(obfuscated)

def unpack_and_read(
    token: str,
    key: Union[str, bytes, None] = None,
) -> Any:
    obfuscated = b91_decode(token)
    if not obfuscated:
        raise ValueError("Empty or invalid token")

    key_bytes = (
        key.encode("utf-8")
        if isinstance(key, str)
        else (bytes(key) if key else b"")
    )

    if key_bytes:
        first_mask = hashlib.shake_256(key_bytes + b"\x00").digest(1)[0]
        flags = obfuscated[0] ^ first_mask
        if flags & FLAG_SALTED:
            if len(obfuscated) < 5:
                raise ValueError("Corrupted salted token")
            salt = obfuscated[1:5]
            payload = xor_crypt(obfuscated[5:], key_bytes, salt)
        else:
            payload = xor_crypt(obfuscated, key_bytes)[1:]
    else:
        flags = obfuscated[0]
        payload = obfuscated[1:]

    try:
        raw = (
            zlib.decompress(payload, -15)
            if (flags & FLAG_COMPRESSED)
            else payload
        )
        if (zlib.crc32(raw) & 0xF) != (flags >> 4):
            raise ValueError("Checksum mismatch")
        return _deserialize_payload(raw, flags & 3)
    except (zlib.error, UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        raise ValueError("Invalid key or corrupted data") from exc

if __name__ == "__main__":
    t1 = pack_and_hide("Привет, мир!", key="pass123", salted=False)
    print("Token non-salted:", t1)
    res1 = unpack_and_read(t1, key="pass123")
    print("Unpacked non-salted:", res1)
    assert res1 == "Привет, мир!"

    salt = b"1234"
    t2 = pack_and_hide("Привет, мир!", key="pass123", salted=True, custom_salt=salt)
    print("Token salted:", t2)
    res2 = unpack_and_read(t2, key="pass123")
    print("Unpacked salted:", res2)
    assert res2 == "Привет, мир!"

    t3 = pack_and_hide({"foo": "bar", "num": 42}, key="pass123", salted=False)
    print("Token json:", t3)
    res3 = unpack_and_read(t3, key="pass123")
    print("Unpacked json:", res3)
