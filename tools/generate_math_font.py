#!/usr/bin/env python3
"""Extract the pinned STIX face using Python 3 standard library only.

Run: python tools/generate_math_font.py [--check] [--resource-dir DIR]
--check compares canonical UTF-8/LF JSON byte for byte without writing.
No download, timestamps, absolute paths, float rounding or platform font APIs.
Schema 1 uses decimal codepoint/glyph-id object keys and font design units;
percent constants retain percent units. Missing attachment is absent (not zero).
Ink bounds are deliberately measured by the existing runtime glyph probe, not
invented from hmtx. This data feeds FontCatalog's atomic candidate and existing
GlyphGenerator/pages/PaintCommand; it is not a renderer or an atlas.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

FONT = "STIXTwoMath-Regular.otf"
SHA = "95bc2729e41faf93b0bcae9e96c4dc4da45855067fd0581e621e30734fe8d90b"
VALUE_CONSTANTS = """MathLeading AxisHeight AccentBaseHeight FlattenedAccentBaseHeight
SubscriptShiftDown SubscriptTopMax SubscriptBaselineDropMin SuperscriptShiftUp
SuperscriptShiftUpCramped SuperscriptBottomMin SuperscriptBaselineDropMax
SubSuperscriptGapMin SuperscriptBottomMaxWithSubscript SpaceAfterScript
UpperLimitGapMin UpperLimitBaselineRiseMin LowerLimitGapMin LowerLimitBaselineDropMin
StackTopShiftUp StackTopDisplayStyleShiftUp StackBottomShiftDown
StackBottomDisplayStyleShiftDown StackGapMin StackDisplayStyleGapMin
StretchStackTopShiftUp StretchStackBottomShiftDown StretchStackGapAboveMin
StretchStackGapBelowMin FractionNumeratorShiftUp FractionNumeratorDisplayStyleShiftUp
FractionDenominatorShiftDown FractionDenominatorDisplayStyleShiftDown
FractionNumeratorGapMin FractionNumDisplayStyleGapMin FractionRuleThickness
FractionDenominatorGapMin FractionDenomDisplayStyleGapMin SkewedFractionHorizontalGap
SkewedFractionVerticalGap OverbarVerticalGap OverbarRuleThickness OverbarExtraAscender
UnderbarVerticalGap UnderbarRuleThickness UnderbarExtraDescender RadicalVerticalGap
RadicalDisplayStyleVerticalGap RadicalRuleThickness RadicalExtraAscender
RadicalKernBeforeDegree RadicalKernAfterDegree""".split()


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Face:
    def __init__(self, data):
        self.data = data
        self.device_adjustments = {}
        require(hashlib.sha256(data).hexdigest() == SHA, "Unexpected font SHA-256")
        require(data[:4] == b"OTTO", "Expected single OpenType CFF face")
        self.tables = {}
        for i in range(self.u16(4)):
            p = 12 + i * 16
            tag = data[p:p + 4].decode("ascii")
            start, size = self.unpack(">II", p + 8)
            require(start + size <= len(data), "Table out of bounds")
            self.tables[tag] = start
        self.glyph_count = self.u16(self.tables["maxp"] + 4)

    def unpack(self, fmt, p):
        require(p >= 0 and p + struct.calcsize(fmt) <= len(self.data), "Offset out of bounds")
        return struct.unpack_from(fmt, self.data, p)

    def u16(self, p):
        return self.unpack(">H", p)[0]

    def u32(self, p):
        return self.unpack(">I", p)[0]

    def value(self, p, base=None, key=None):
        offset = self.u16(p + 2)
        if offset:
            require(base is not None and key is not None, "Device adjustment needs semantic key")
            d = base + offset
            start, end, fmt = self.unpack(">HHH", d)
            require(fmt in (1, 2, 3) and start <= end, "Unsupported device/variation format")
            bits = 1 << fmt
            per_word = 16 // bits
            deltas = []
            for i in range(end - start + 1):
                word = self.u16(d + 6 + (i // per_word) * 2)
                value = (word >> (16 - bits * (i % per_word + 1))) & ((1 << bits) - 1)
                deltas.append(value - (1 << bits) if value & (1 << (bits - 1)) else value)
            self.device_adjustments[key] = {"startPpem": start, "endPpem": end, "deltaPixels": deltas}
        return self.unpack(">h", p)[0]

    def coverage(self, p):
        fmt, count = self.unpack(">HH", p)
        if fmt == 1:
            result = [self.u16(p + 4 + i * 2) for i in range(count)]
        else:
            require(fmt == 2, "Unsupported coverage format")
            result = []
            for i in range(count):
                start, end, index = self.unpack(">HHH", p + 4 + i * 6)
                require(start <= end and index == len(result), "Invalid coverage index")
                result.extend(range(start, end + 1))
        require(result == sorted(set(result)), "Coverage must be ordered and unique")
        require(all(0 <= g < self.glyph_count for g in result), "Coverage glyph out of bounds")
        return result

    def cmap(self):
        base = self.tables["cmap"]
        candidates = []
        for i in range(self.u16(base + 2)):
            platform, encoding, offset = self.unpack(">HHI", base + 4 + i * 8)
            p = base + offset
            if platform == 0 or (platform == 3 and encoding in (1, 10)):
                if self.u16(p) == 12:
                    candidates.append(p)
        require(candidates, "Unicode cmap format 12 required for pinned face")
        maps = []
        for p in sorted(set(candidates)):
            mapping = {}
            for i in range(self.u32(p + 12)):
                start, end, glyph = self.unpack(">III", p + 16 + i * 12)
                require(start <= end <= 0x10ffff, "Invalid Unicode range")
                for cp in range(start, end + 1):
                    g = glyph + cp - start
                    require(not 0xd800 <= cp <= 0xdfff and 0 <= g < self.glyph_count,
                            "Invalid cmap entry")
                    if g:
                        mapping[str(cp)] = g
            maps.append(mapping)
        require(all(m == maps[0] for m in maps), "Conflicting Unicode cmap tables")
        return maps[0]

    def glyph_values(self, p, field):
        if not p:
            return {}
        cov = self.coverage(p + self.u16(p))
        require(len(cov) == self.u16(p + 2), "MATH value count mismatch")
        return {str(g): self.value(p + 4 + i * 4, p, field + "." + str(g)) for i, g in enumerate(cov)}

    def construction(self, p):
        assembly_offset, count = self.unpack(">HH", p)
        variants = []
        for i in range(count):
            glyph, advance = self.unpack(">HH", p + 4 + i * 4)
            require(0 < glyph < self.glyph_count, "Variant glyph out of bounds")
            variants.append({"glyphId": glyph, "advance": advance})
        assembly = None
        if assembly_offset:
            a = p + assembly_offset
            parts = []
            for i in range(self.u16(a + 4)):
                glyph, start, end, full, flags = self.unpack(">HHHHH", a + 6 + i * 10)
                require(0 < glyph < self.glyph_count and full > 0 and flags in (0, 1),
                        "Invalid assembly part: " + repr((glyph, start, end, full, flags)))
                parts.append({"glyphId": glyph, "startConnector": start,
                              "endConnector": end, "fullAdvance": full, "extender": bool(flags)})
            # Preserve upstream numbers, including connector lengths above fullAdvance.
            # The flag records this comparison only; it does not define runtime overlap policy.
            assembly = {"italicCorrection": self.value(a), "parts": parts,
                        "validConnectorLengths": all(part["startConnector"] <= part["fullAdvance"]
                                                     and part["endConnector"] <= part["fullAdvance"]
                                                     for part in parts)}
        return {"variants": variants, "assembly": assembly}

    def extract(self):
        m = self.tables["MATH"]
        require(self.u32(m) == 0x10000, "Unsupported MATH version")
        c = m + self.u16(m + 4)
        constants = dict(zip(("ScriptPercentScaleDown", "ScriptScriptPercentScaleDown",
                             "DelimitedSubFormulaMinHeight", "DisplayOperatorMinHeight"),
                            self.unpack(">hhHH", c)))
        for i, name in enumerate(VALUE_CONSTANTS):
            constants[name] = self.value(c + 8 + i * 4, c, "constants." + name)
        constants["RadicalDegreeBottomRaisePercent"] = self.u16(c + 8 + len(VALUE_CONSTANTS) * 4)
        info = m + self.u16(m + 6)
        italic, accent, extended, kern = self.unpack(">HHHH", info)
        v = m + self.u16(m + 8)
        overlap, vertical_cov, horizontal_cov, nv, nh = self.unpack(">HHHHH", v)
        constructions = {}
        for axis, offset, count, skip in (("vertical", vertical_cov, nv, 0),
                                          ("horizontal", horizontal_cov, nh, nv)):
            cov = self.coverage(v + offset) if offset else []
            require(len(cov) == count, "Variant coverage count mismatch")
            constructions[axis] = {str(g): self.construction(v + self.u16(v + 10 + (i + skip) * 2))
                                   for i, g in enumerate(cov)}
        hmtx = self.tables["hmtx"]
        metrics_count = self.u16(self.tables["hhea"] + 34)
        advances = [self.u16(hmtx + min(g, metrics_count - 1) * 4) for g in range(self.glyph_count)]
        return {"schemaVersion": 1, "fontSha256": SHA, "faceIndex": 0,
                "profile": "stix-two-math-v1", "unitsPerEm": self.u16(self.tables["head"] + 18),
                "glyphCount": self.glyph_count, "cmap": self.cmap(), "advanceWidths": advances,
                "constants": constants, "italicCorrections": self.glyph_values(info + italic if italic else 0, "italicCorrections"),
                "topAccentAttachments": self.glyph_values(info + accent if accent else 0, "topAccentAttachments"),
                "extendedShapeGlyphs": self.coverage(info + extended) if extended else [],
                "minConnectorOverlap": overlap, "constructions": constructions,
                "deviceAdjustments": self.device_adjustments,
                "omittedTables": ["MathKernInfo"] if kern else []}


def canonical(value):
    return (json.dumps(value, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("utf-8")


def outputs(directory):
    font = (directory / FONT).read_bytes()
    license_data = (directory / "OFL.txt").read_bytes()
    require(hashlib.sha256(license_data).hexdigest() ==
            "bec17cee6412788db45fd2644b301afbc99cc5d372ddd3345dc4a7bfdefb9f04",
            "Unexpected OFL SHA-256")
    data = canonical(Face(font).extract())
    manifest = {"schemaVersion": 1, "family": "STIX Two Math", "version": "2.12 b168",
                "sourceUrl": "https://mirrors.tuna.tsinghua.edu.cn/CTAN/fonts/stix2-otf.zip",
                "license": "OFL-1.1", "modified": False, "faceIndex": 0,
                "generator": "tools/generate_math_font.py", "files": {}}
    for name, content in ((FONT, font), ("OFL.txt", license_data), ("math-data.json", data)):
        manifest["files"][name] = {"bytes": len(content), "sha256": hashlib.sha256(content).hexdigest()}
    return {"math-data.json": data, "source-manifest.json": canonical(manifest)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resource-dir", type=Path, default=Path(__file__).resolve().parents[1]
                        / "src/main/resources/assets/qz_uilib/fonts/math/stix-two")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for name, content in outputs(args.resource_dir).items():
        path = args.resource_dir / name
        if args.check:
            require(path.read_bytes() == content, "Non-deterministic or stale resource: " + name)
        else:
            path.write_bytes(content)
        print(("verified " if args.check else "generated ") + name + " " + hashlib.sha256(content).hexdigest())


if __name__ == "__main__":
    main()
