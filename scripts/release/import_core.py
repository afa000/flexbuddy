"""Library-only release guards. Standard library only; importing this module never publishes."""

import argparse
import base64
import hashlib
from http.client import HTTPException
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
import xml.etree.ElementTree as ET
import zipfile


REGISTRY = "https://maven.pkg.github.com/afa000/flexbuddy"
GROUP = "com.angel"
ARTIFACT = "flexbuddy-import-core"
SERVER_ID = "github-import-core"
POM_PATH = "flexbuddy/import-core/pom.xml"
MAX_BYTES = 5 * 1024 * 1024
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class ReleaseError(Exception):
    pass


def version(value):
    if not isinstance(value, str) or len(value) > 60 or not re.fullmatch(
            r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", value):
        raise ReleaseError("Use a stable numeric version, such as 0.1.0; snapshots are not releases.")
    return value


def source_sha(value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ReleaseError("A resolved source commit is required.")
    return value


def dispatch_guard(env):
    if (env.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or env.get("GITHUB_REF") != "refs/heads/main"
            or env.get("GITHUB_REPOSITORY") != "afa000/flexbuddy"):
        raise ReleaseError("Publishing is available only by manual dispatch from afa000/flexbuddy main.")


def xml_root(data):
    if len(data) > MAX_BYTES or b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise ReleaseError("Unexpected XML declaration or size.")
    try:
        return ET.fromstring(data)
    except ET.ParseError:
        raise ReleaseError("Invalid release XML.") from None


def check_pom(data, expected):
    root = xml_root(data)
    if root.tag != "{" + NS["m"] + "}project":
        raise ReleaseError("Expected a Maven project POM.")
    for key, wanted in (("groupId", GROUP), ("artifactId", ARTIFACT), ("version", version(expected))):
        values = root.findall("m:" + key, NS)
        if len(values) != 1 or values[0].text != wanted:
            raise ReleaseError("Library coordinates or POM version do not match the requested release.")
    if root.findtext("m:packaging", "jar", NS) != "jar":
        raise ReleaseError("Only the thin library JAR may be published.")
    for name in ("parent", "modules", "profiles"):
        if root.find("m:" + name, NS) is not None:
            raise ReleaseError("The library POM must be standalone.")
    for dependency in root.findall("m:dependencies/m:dependency", NS):
        if dependency.findtext("m:scope", "compile", NS) != "test":
            raise ReleaseError("The library must have no runtime dependencies.")
    repository = root.find("m:distributionManagement/m:repository", NS)
    if (repository is None or repository.findtext("m:id", namespaces=NS) != SERVER_ID
            or repository.findtext("m:url", namespaces=NS) != REGISTRY):
        raise ReleaseError("The library must publish only to its approved repository.")


def git(repo, *args):
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if result.returncode:
        raise ReleaseError("Git source validation failed; confirm the tag exists on main.")
    return result.stdout.decode("utf-8").strip()


def resolve_source(repo, release_version):
    release_version = version(release_version)
    tag = "import-core-v" + release_version
    # No caller-supplied arbitrary ref reaches git; tags are derived from a validated numeric version.
    sha = source_sha(git(repo, "rev-parse", "--verify", "refs/tags/" + tag + "^{commit}"))
    git(repo, "merge-base", "--is-ancestor", sha, "refs/remotes/origin/main")
    check_pom(source_pom(repo, sha), release_version)
    return {"version": release_version, "tag": tag, "source-sha": sha}


def source_pom(repo, sha):
    pom = subprocess.run(["git", "-C", str(repo), "show", sha + ":" + POM_PATH], capture_output=True)
    if pom.returncode:
        raise ReleaseError("The tag has no library POM.")
    return pom.stdout


def names(release_version):
    stem = ARTIFACT + "-" + version(release_version)
    return [stem + ".jar", stem + ".pom"]


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def check_jar(path):
    try:
        with zipfile.ZipFile(path) as jar:
            classes = 0
            for entry in jar.namelist():
                if entry.endswith("/"):
                    continue
                if entry.startswith("com/angel/flexbuddy/importcore/") and entry.endswith(".class"):
                    classes += 1
                elif entry not in ("META-INF/MANIFEST.MF",
                                    "META-INF/maven/com.angel/flexbuddy-import-core/pom.xml",
                                    "META-INF/maven/com.angel/flexbuddy-import-core/pom.properties"):
                    raise ReleaseError("The thin JAR contains an unexpected file.")
            if not classes:
                raise ReleaseError("The library JAR contains no classes.")
    except zipfile.BadZipFile:
        raise ReleaseError("Invalid library JAR.") from None


def stage(source, payload, release_version, sha):
    release_version, sha = version(release_version), source_sha(sha)
    check_pom((source / POM_PATH).read_bytes(), release_version)
    if git(source, "rev-parse", "HEAD") != sha:
        raise ReleaseError("The build checkout does not match the validated source commit.")
    if (source / POM_PATH).read_bytes() != source_pom(source, sha):
        raise ReleaseError("The library POM changed during the build.")
    target = source / "flexbuddy/import-core/target"
    dependency_lines = (target / "release-dependencies.txt").read_text(encoding="utf-8").splitlines()
    if [line.strip() for line in dependency_lines if line.strip()] != [f"{GROUP}:{ARTIFACT}:jar:{release_version}"]:
        raise ReleaseError("The runtime dependency tree is not empty.")
    jar_name, pom_name = names(release_version)
    check_jar(target / jar_name)
    payload.mkdir(parents=True, exist_ok=True)
    if any(payload.iterdir()):
        raise ReleaseError("Release staging must start with an empty directory.")
    shutil.copyfile(target / jar_name, payload / jar_name)
    shutil.copyfile(source / POM_PATH, payload / pom_name)
    manifest = {"version": release_version, "tag": "import-core-v" + release_version, "source-sha": sha,
                "sha256": {name: sha256((payload / name).read_bytes()) for name in names(release_version)}}
    (payload / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def check_payload(payload, release_version, sha):
    release_version, sha = version(release_version), source_sha(sha)
    try:
        manifest = json.loads((payload / "manifest.json").read_text(encoding="utf-8"))
    except (ValueError, OSError):
        raise ReleaseError("Missing or invalid release manifest.") from None
    expected_files = set(names(release_version))
    if (not isinstance(manifest, dict) or set(manifest) != {"version", "tag", "source-sha", "sha256"}
            or manifest["version"] != release_version or manifest["source-sha"] != sha
            or manifest["tag"] != "import-core-v" + release_version
            or not isinstance(manifest["sha256"], dict) or set(manifest["sha256"]) != expected_files):
        raise ReleaseError("The artifact manifest does not match the validated release.")
    if {path.name for path in payload.iterdir()} != expected_files | {"manifest.json"}:
        raise ReleaseError("Only the JAR, POM and manifest belong in the release payload.")
    for name in expected_files:
        path = payload / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_BYTES:
            raise ReleaseError("Unexpected artifact path or size.")
        if manifest["sha256"][name] != sha256(path.read_bytes()):
            raise ReleaseError("A release artifact checksum changed.")
    check_pom((payload / names(release_version)[1]).read_bytes(), release_version)
    check_jar(payload / names(release_version)[0])
    return manifest


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def fetch(url, authorization, opener=None):
    """Bounded HTTPS redirects; credentials go only to the approved Maven registry origin."""
    opener = opener or build_opener(NoRedirect())
    for _ in range(6):
        parsed = urlsplit(url)
        if parsed.scheme != "https" or parsed.username or parsed.password:
            raise ReleaseError("Registry redirected to an unsafe URL.")
        headers = {"User-Agent": "FlexBuddy-import-core-release"}
        if parsed.netloc == urlsplit(REGISTRY).netloc:
            headers["Authorization"] = authorization
        try:
            response = opener.open(Request(url, headers=headers), timeout=30)
        except HTTPError as error:
            if error.code in (301, 302, 303, 307, 308):
                location = error.headers.get("Location")
                error.close()
                if not location:
                    raise ReleaseError("Registry returned an invalid redirect.") from None
                url = urljoin(url, location)
                continue
            status = error.code
            error.close()
            return status, b""
        except (URLError, OSError, TimeoutError, HTTPException, ValueError):
            raise ReleaseError("Registry request failed; release state is unknown.") from None
        with response:
            data = response.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise ReleaseError("Registry response exceeds the artifact size limit.")
            return response.status, data
    raise ReleaseError("Too many registry redirects.")


def artifact_url(release_version, name):
    return f"{REGISTRY}/com/angel/{ARTIFACT}/{version(release_version)}/{name}"


def preflight(release_version, get):
    release_version = version(release_version)
    status, data = get(f"{REGISTRY}/com/angel/{ARTIFACT}/maven-metadata.xml")
    if status == 200:
        root = xml_root(data)
        if (root.tag != "metadata" or root.findtext("groupId") != GROUP
                or root.findtext("artifactId") != ARTIFACT
                or root.find("versioning/versions") is None):
            raise ReleaseError("Unexpected registry metadata.")
        if release_version in [element.text for element in root.findall("versioning/versions/version")]:
            raise ReleaseError("This version already exists; publish a new version instead.")
    elif status != 404:
        raise ReleaseError(f"Registry metadata check returned HTTP {status}; refusing to publish.")
    # Check both files too: partial publications must never be silently replaced.
    for name in names(release_version):
        status, _ = get(artifact_url(release_version, name))
        if status == 200:
            raise ReleaseError("This version has existing artifacts; inspect the partial release.")
        if status != 404:
            raise ReleaseError(f"Registry artifact check returned HTTP {status}; refusing to publish.")


def verify_remote(payload, manifest, get, sleep=time.sleep):
    for name, expected in manifest["sha256"].items():
        for attempt in range(5):
            status, data = get(artifact_url(manifest["version"], name))
            if status == 404 and attempt < 4:
                sleep(2)
                continue
            if status != 200 or sha256(data) != expected:
                raise ReleaseError("Remote verification failed; release is incomplete. Do not overwrite or retry blindly.")
            break


def write_maven_control(directory):
    settings = directory / "settings.xml"
    settings.write_text('''<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers><server><id>github-import-core</id>
    <username>${env.GITHUB_ACTOR}</username><password>${env.GH_PACKAGES_TOKEN}</password>
  </server></servers>
</settings>
''', encoding="utf-8")
    global_settings = directory / "global-settings.xml"
    global_settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n', encoding="utf-8")
    control = directory / "control.xml"
    control.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.angel.release</groupId><artifactId>upload</artifactId><version>1</version></project>
''', encoding="utf-8")
    return settings, global_settings, control


def deploy_command(repo, payload, release_version, settings, global_settings, control, destination=REGISTRY):
    # destination is injectable only for local file-backed validation, never a workflow input.
    if destination != REGISTRY and not destination.startswith("file:///"):
        raise ReleaseError("Unapproved release destination.")
    jar, pom = names(release_version)
    # repo is the dispatch commit's trusted checkout, so its wrapper pins Maven exactly as the project builds do.
    # retryFailedDeploymentCount=1 is the plugin's single attempt in total: a failed upload is never retried.
    return ["sh", str(repo / "flexbuddy/mvnw"), "-B", "-ntp", "--settings", str(settings),
            "--global-settings", str(global_settings),
            "-f", str(control), "org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file",
            "-DrepositoryId=" + SERVER_ID, "-Durl=" + destination, "-DgeneratePom=false",
            "-DretryFailedDeploymentCount=1", "-Dfile=" + str(payload / jar), "-DpomFile=" + str(payload / pom)]


def publish(repo, payload, release_version, sha, env):
    dispatch_guard(env)
    manifest = check_payload(payload, release_version, sha)
    if resolve_source(repo, release_version)["source-sha"] != sha:
        raise ReleaseError("The release tag no longer matches the built source.")
    actor, token = env.get("GITHUB_ACTOR", ""), env.get("GH_PACKAGES_TOKEN", "")
    if not actor or not token:
        raise ReleaseError("The publishing job's actor and ephemeral package token are required.")
    authorization = "Basic " + base64.b64encode((actor + ":" + token).encode()).decode()
    get = lambda url: fetch(url, authorization)
    preflight(release_version, get)
    # No tagged POM lifecycle, wrapper or scripts execute in this privileged job; the wrapper is the dispatch commit's.
    with tempfile.TemporaryDirectory(prefix="import-core-upload-", dir=env["RUNNER_TEMP"]) as temporary:
        settings, global_settings, control = write_maven_control(Path(temporary))
        result = subprocess.run(deploy_command(repo, payload, release_version, settings, global_settings, control))
        if result.returncode:
            raise ReleaseError("Upload failed; release state may be partial. Inspect it before any retry.")
    verify_remote(payload, manifest, get)
    summary(manifest, env, "Published and verified")


def summary(metadata, env, heading):
    text = (f"## {heading}\n\nCoordinate: `{GROUP}:{ARTIFACT}:{metadata['version']}`\n\n"
            f"Tag: `{metadata['tag']}`\n\nCommit: `{metadata['source-sha']}`\n")
    for name, digest in metadata.get("sha256", {}).items():
        text += f"\nSHA-256 `{name}`: `{digest}`\n"
    if env.get("GITHUB_STEP_SUMMARY"):
        with open(env["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["resolve", "stage", "publish"])
    operation = parser.parse_args().operation
    env = os.environ
    dispatch_guard(env)
    release_version = version(env.get("RELEASE_VERSION"))
    repo = Path.cwd()
    if operation == "resolve":
        metadata = resolve_source(repo, release_version)
        with open(env["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"version={metadata['version']}\nsource-sha={metadata['source-sha']}\n")
        summary(metadata, env, "Validated release source")
    else:
        sha = source_sha(env.get("RELEASE_SHA"))
        payload = Path(env["RUNNER_TEMP"]) / "import-core-release"
        if operation == "stage":
            metadata = stage(repo / "release-source", payload, release_version, sha)
            summary(metadata, env, "Built release artifacts")
        else:
            publish(repo, payload, release_version, sha, env)


if __name__ == "__main__":
    try:
        main()
    except (ReleaseError, OSError, KeyError) as error:
        # Network errors are translated above; never print raw requests, settings or credentials.
        print("Release refused:", error if isinstance(error, ReleaseError) else "Missing release file or environment.")
        raise SystemExit(1)
