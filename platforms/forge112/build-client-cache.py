"""使用既有实例的精确客户端和 SpecialSource 构建薄宿主，不启动 Minecraft。"""
from __future__ import annotations

import argparse
import csv
import io
import json
import lzma
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def run(command: list[str], cwd: Path) -> None:
    # Java8 Windows launcher无法处理本机中文绝对路径；只在进程边界转相对参数。
    def relative(value: str) -> str:
        if not Path(value).is_absolute():
            return value
        try:
            return os.path.relpath(value, cwd)
        except ValueError:  # 不同盘符仍使用原绝对路径。
            return value

    arguments = [os.pathsep.join(relative(part) for part in value.split(os.pathsep))
                 if os.pathsep in value and not value.startswith("-") else relative(value)
                 for value in command[1:]]
    subprocess.run([command[0], *arguments], cwd=cwd, check=True)


def mapping_files(target: str, libraries: Path, snapshot: Path, output: Path) -> None:
    with zipfile.ZipFile(snapshot) as source:
        fields = {row["searge"]: row["name"] for row in csv.DictReader(io.StringIO(source.read("fields.csv").decode()))}
        methods = {row["searge"]: row["name"] for row in csv.DictReader(io.StringIO(source.read("methods.csv").decode()))}
    development, production = [], []
    if target == "112":
        with zipfile.ZipFile(libraries / "net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-universal.jar") as forge:
            lines = lzma.decompress(forge.read("deobfuscation_data-1.12.2.lzma")).decode().splitlines()
        for line in lines:
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "CL:":
                development.append(line)
            elif parts[0] == "FD:":
                owner, name = parts[2].rsplit("/", 1)
                renamed = owner + "/" + fields.get(name, name)
                development.append(f"FD: {parts[1]} {renamed}")
                production.append(f"FD: {renamed} {parts[2]}")
            elif parts[0] == "MD:":
                owner, name = parts[3].rsplit("/", 1)
                renamed = owner + "/" + methods.get(name, name)
                development.append(f"MD: {parts[1]} {parts[2]} {renamed} {parts[4]}")
                production.append(f"MD: {renamed} {parts[4]} {parts[3]} {parts[4]}")
    else:
        with zipfile.ZipFile(libraries / "de/oceanlabs/mcp/mcp_config/1.16.5-20210115.111550/mcp_config-1.16.5-20210115.111550.zip") as mcp:
            lines = mcp.read("config/joined.tsrg").decode().splitlines()
        classes = {line.split()[0]: line.split()[1] for line in lines if not line.startswith("\t")}
        owner = ""
        for line in lines:
            parts = line.split()
            if not parts:
                continue
            if not line.startswith("\t"):
                owner = parts[1]
            elif len(parts) == 2:
                original, renamed = owner + "/" + parts[1], owner + "/" + fields.get(parts[1], parts[1])
                development.append(f"FD: {original} {renamed}")
                production.append(f"FD: {renamed} {original}")
            else:
                original, renamed = owner + "/" + parts[2], owner + "/" + methods.get(parts[2], parts[2])
                descriptor = re.sub(r"L([^;]+);", lambda match: "L" + classes.get(match[1], match[1]) + ";", parts[1])
                development.append(f"MD: {original} {descriptor} {renamed} {descriptor}")
                production.append(f"MD: {renamed} {descriptor} {original} {descriptor}")
    (output / "to-development.srg").write_text("\n".join(development) + "\n", encoding="utf-8")
    (output / "to-production.srg").write_text("\n".join(production) + "\n", encoding="utf-8")


def merge_archives(destination: Path, sources: list[Path], manifest: str | None = None) -> None:
    seen = set()
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as result:
        if manifest is not None:
            result.writestr("META-INF/MANIFEST.MF", manifest)
            seen.add("META-INF/MANIFEST.MF")
        for path in sources:
            with zipfile.ZipFile(path) as source:
                for entry in source.infolist():
                    if entry.is_dir() or entry.filename in seen or entry.filename.endswith((".SF", ".RSA", ".DSA")):
                        continue
                    result.writestr(entry, source.read(entry))
                    seen.add(entry.filename)


def dependencies(launcher: Path, minecraft: str, forge: str, target: str) -> list[Path]:
    libraries = launcher / "libraries"
    result = []
    for component, version in [("net.minecraft", minecraft), ("net.minecraftforge", forge),
                               ("org.lwjgl" if target == "112" else "org.lwjgl3", "2.9.4-nightly-20150209" if target == "112" else "3.2.2")]:
        metadata = json.loads((launcher / "meta" / component / (version + ".json")).read_text(encoding="utf-8"))
        for item in metadata.get("libraries", []):
            parts = item["name"].split("@")[0].split(":")
            if len(parts) < 3 or parts[:2] == ["net.minecraftforge", "forge"]:
                continue
            group, artifact, release = parts[:3]
            name = artifact + "-" + release + ("-" + parts[3] if len(parts) > 3 else "") + ".jar"
            path = libraries / group.replace(".", "/") / artifact / release / name
            if path.is_file():
                result.append(path)
    return list(dict.fromkeys(result))


def package_mod(destination: Path, classes: Path, package: str, resources: Path, manifest: str, repository: Path,
                gallery: Path | None = None, verification: Path | None = None) -> None:
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as result:
        result.writestr("META-INF/MANIFEST.MF", manifest)
        packages = [package]
        if package == "club/heiqi/uilib/host/forge112":
            packages.append("club/heiqi/uilib/host/lwjgl2")
        for current in packages:
            for path in (classes / current).rglob("*.class"):
                result.write(path, path.relative_to(classes).as_posix())
        for path in resources.rglob("*"):
            if path.is_file():
                result.write(path, path.relative_to(resources).as_posix())
        result.write(repository / "LICENSE", "LICENSE")
        if gallery:
            with zipfile.ZipFile(gallery) as source:
                for entry in source.infolist():
                    if not entry.is_dir() and entry.filename != "META-INF/MANIFEST.MF":
                        result.writestr(entry, source.read(entry))
        if verification:
            with zipfile.ZipFile(verification) as source:
                for entry in source.infolist():
                    # 人工包需要共享核心验证的服务实现、用例和资源。
                    if not entry.is_dir() and entry.filename not in ("META-INF/MANIFEST.MF", "LICENSE"):
                        result.writestr(entry, source.read(entry))


def verify(install: Path, target: str) -> None:
    for path in install.glob("*.jar"):
        with zipfile.ZipFile(path) as source:
            names = source.namelist()
            for name in names:
                if name.endswith(".class") and not name.startswith("META-INF/versions/"):
                    if struct.unpack(">H", source.read(name)[6:8])[0] > 52:
                        raise RuntimeError("产物不兼容 Java8: " + name)
            if "forge" in path.name and "club/heiqi/uilib/ui/reactive/Signal.class" in names:
                raise RuntimeError("宿主重复携带核心: " + path.name)
            if "manual" in path.name:
                for required in ["club/heiqi/uilib/testing/gallery/TestGallery.class", "uilib-media/segment.ts",
                                 "META-INF/services/club.heiqi.uilib.testing.gallery.GalleryChecks",
                                 "club/heiqi/uilib/testing/gallery/verification/CoreGalleryChecks.class"]:
                    if required not in names:
                        raise RuntimeError("人工附加包缺少 " + required)
            elif any(name.startswith("club/heiqi/uilib/testing/gallery/") for name in names):
                raise RuntimeError("正式产物包含测试广场: " + path.name)
            if target == "116" and "forge" not in path.name:
                if "FMLModType: LIBRARY" not in source.read("META-INF/MANIFEST.MF").decode():
                    raise RuntimeError("Forge116 独立库缺少装载标记")
    print("产物的 Java8 字节码、核心隔离与所属资源验证通过", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=["112", "116"], required=True)
    parser.add_argument("--launcher", type=Path, required=True)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True, help="本Minecraft仓根目录")
    parser.add_argument("--project", type=Path, required=True, help="当前独立Gradle工程")
    parser.add_argument("--shared", type=Path, required=True, help="当前工程从Maven解析并同步的正式依赖")
    parser.add_argument("--version", required=True)
    parser.add_argument("--install-bundle", action="store_true", help="显式装配人工附加包与媒体运行依赖")
    args = parser.parse_args()
    repository = args.repository.resolve()
    project = args.project.resolve()
    cache = project / ".gradle/runtime-cache"
    cache.mkdir(parents=True, exist_ok=True)
    classes = cache / "classes"
    classes.mkdir(exist_ok=True)
    # 只清理本构建器拥有的字节码，避免已删除的宿主类残留进新成品。
    for obsolete in classes.rglob("*.class"):
        obsolete.unlink()
    staging = project / "build/client-cache"
    staging.mkdir(parents=True, exist_ok=True)
    output = project / "build" / ("install" if args.install_bundle else "production")
    output.mkdir(parents=True, exist_ok=True)
    shared = args.shared.resolve()
    libraries = args.launcher.resolve() / "libraries"
    minecraft, forge, binding = (("1.12.2", "14.23.5.2864", "lwjgl2") if args.target == "112"
                                  else ("1.16.5", "36.2.42", "lwjgl3"))
    java, javac = str(args.jdk.resolve() / "bin/java.exe"), str(args.jdk.resolve() / "bin/javac.exe")
    mapping_files(args.target, libraries, args.snapshot.resolve(), cache)
    forge_root = libraries / f"net/minecraftforge/forge/{minecraft}-{forge}"
    if args.target == "112":
        clients = [forge_root / f"forge-{minecraft}-{forge}-universal.jar",
                   libraries / f"com/mojang/minecraft/{minecraft}/minecraft-{minecraft}-client.jar"]
    else:
        clients = [forge_root / f"forge-{minecraft}-{forge}-client.jar",
                   libraries / "net/minecraft/client/1.16.5-20210115.111550/client-1.16.5-20210115.111550-srg.jar",
                   forge_root / f"forge-{minecraft}-{forge}-universal.jar", forge_root / f"forge-{minecraft}-{forge}-launcher.jar"]
    merge_archives(cache / "production-client.jar", clients)
    tools = [libraries / "net/md-5/SpecialSource/1.11.0/SpecialSource-1.11.0.jar",
             libraries / "com/google/guava/guava/21.0/guava-21.0.jar",
             libraries / "net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar"]
    tools += [libraries / f"org/ow2/asm/{name}/9.3/{name}-9.3.jar" for name in ["asm", "asm-tree", "asm-commons"]]
    shared_names = ["core", "font-adapter", "gl-renderer", binding]
    if args.install_bundle:
        shared_names += ["media-adapter", "gallery", "verification"]
    for dependency in tools + clients + [shared / (name + ".jar") for name in shared_names]:
        if not dependency.is_file():
            raise FileNotFoundError(dependency)
    remapper = [java, "-Xmx2G", "-cp", ";".join(map(str, tools)), "net.md_5.specialsource.SpecialSource"]
    run(remapper + ["--in-jar", str(cache / "production-client.jar"), "--out-jar", str(cache / "development-client.jar"),
                    "--srg-in", str(cache / "to-development.srg"), "--quiet"], repository)
    dependencies_list = dependencies(args.launcher.resolve(), minecraft, forge, args.target)
    (cache / "build-inputs.json").write_text(json.dumps({
        "target": args.target, "minecraft": minecraft, "forge": forge,
        "launcher": str(args.launcher.resolve()), "snapshot": str(args.snapshot.resolve()),
        "java": java, "clients": list(map(str, clients)), "includeManual": args.install_bundle,
        "shared": [str(shared / (name + ".jar")) for name in shared_names]
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    classpath = [cache / "development-client.jar"] + dependencies_list
    classpath += [shared / (name + ".jar") for name in shared_names]
    sources = list((repository / f"src/minecraft{args.target}/java").rglob("*.java"))
    if args.target == "112":
        sources += list((repository / "src/minecraftShared/java").rglob("*.java"))
    if args.install_bundle:
        sources += list((repository / f"src/minecraft{args.target}Manual/java").rglob("*.java"))
    run([javac, "-J-Dfile.encoding=UTF-8", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-proc:none", "-cp", ";".join(map(str, classpath)),
         "-d", str(classes)] + list(map(str, sources)), repository)
    base_manifest = "Manifest-Version: 1.0\r\nImplementation-Version: " + args.version + "\r\n"
    host_manifest = base_manifest
    if args.target == "112":
        host_manifest += "FMLCorePlugin: club.heiqi.uilib.host.forge112.loading.UILibLoadingPlugin\r\nFMLCorePluginContainsFMLMod: true\r\n"
    for kind in (["host", "manual"] if args.install_bundle else ["host"]):
        development = staging / (kind + "-dev.jar")
        package_mod(development, classes, f"club/heiqi/uilib/{'host' if kind == 'host' else 'manual'}/forge{args.target}",
                    repository / f"src/minecraft{args.target}{'Manual' if kind == 'manual' else ''}/resources",
                    (host_manifest if kind == "host" else base_manifest) + "\r\n", repository,
                    shared / "gallery.jar" if kind == "manual" else None,
                    shared / "verification.jar" if kind == "manual" else None)
        production = output / (f"qz_uilib_{'manual_' if kind == 'manual' else ''}forge{args.target}-{args.version}.jar")
        live_classpath = tools + classpath + [development]
        run([java, "-Xmx1G", "-cp", ";".join(map(str, live_classpath)), "net.md_5.specialsource.SpecialSource", "--live",
             "--in-jar", str(development), "--out-jar", str(production), "--srg-in", str(cache / "to-production.srg"), "--quiet"], repository)
    library_names = ["core", "font-adapter", "gl-adapter"]
    if args.install_bundle:
        library_names.append("media-adapter")
    for name in library_names:
        inputs = [shared / (name + ".jar")] if name != "gl-adapter" else [shared / "gl-renderer.jar", shared / (binding + ".jar")]
        manifest = "Manifest-Version: 1.0\r\n" + ("FMLModType: LIBRARY\r\n" if args.target == "116" else "") + "\r\n"
        merge_archives(output / (f"qz_uilib-{name}-java8.jar"), inputs, manifest)
    verify(output, args.target)
    print("已生成 Forge" + args.target + (" 安装组合: " if args.install_bundle else " 生产组合: ") + str(output), flush=True)


if __name__ == "__main__":
    main()
