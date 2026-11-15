import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError
from email.message import Message
import zipfile

import import_core as release


ROOT = Path(__file__).resolve().parents[2]
VERSION = "0.1.0"
SHA = "a" * 40
POM = (ROOT / release.POM_PATH).read_bytes()
ENV = {"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
       "GITHUB_REPOSITORY": "afa000/flexbuddy"}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="flexbuddy-release-tests-")
        self.root = Path(self.temporary.name).resolve()
        self.addCleanup(self.cleanup)

    def cleanup(self):
        # Recursive cleanup is confined to the one test directory allocated by TemporaryDirectory.
        assert self.root.parent == Path(tempfile.gettempdir()).resolve()
        assert self.root.name.startswith("flexbuddy-release-tests-")
        self.temporary.cleanup()

    def payload(self):
        payload = self.root / "payload"
        payload.mkdir()
        jar, pom = release.names(VERSION)
        with zipfile.ZipFile(payload / jar, "w") as output:
            output.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            output.writestr("com/angel/flexbuddy/importcore/Parser.class", b"synthetic class fixture")
        (payload / pom).write_bytes(POM)
        manifest = {"version": VERSION, "tag": "import-core-v" + VERSION, "source-sha": SHA,
                    "sha256": {name: release.sha256((payload / name).read_bytes()) for name in (jar, pom)}}
        (payload / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return payload, manifest

    def test_dispatch_is_exactly_manual_main_in_the_expected_repository(self):
        release.dispatch_guard(ENV)
        for key, bad in (("GITHUB_EVENT_NAME", "push"), ("GITHUB_EVENT_NAME", "pull_request"),
                         ("GITHUB_REF", "refs/heads/feature"), ("GITHUB_REF", "refs/tags/v0.1.0"),
                         ("GITHUB_REPOSITORY", "someone/flexbuddy")):
            with self.subTest(key=key, value=bad), self.assertRaises(release.ReleaseError):
                release.dispatch_guard(dict(ENV, **{key: bad}))

    def test_versions_reject_dynamic_refs_snapshots_paths_and_output_injection(self):
        self.assertEqual(VERSION, release.version(VERSION))
        for value in (None, "", "0.1.0-SNAPSHOT", "latest", "v0.1.0", "01.1.0", "1.0", "../main",
                      "0.1.0\nsource-sha=evil", "0.1.0; echo unsafe", "1.0.0-rc1", "1.2.3/other"):
            with self.subTest(value=value), self.assertRaises(release.ReleaseError):
                release.version(value)

    def test_pom_checks_real_coordinates_and_standalone_runtime_dependency_contract(self):
        release.check_pom(POM, VERSION)
        invalid = [POM.replace(b"<version>0.1.0</version>", b"<version>0.1.1</version>", 1),
                   POM.replace(b"<scope>test</scope>", b"<scope>runtime</scope>"),
                   POM.replace(b"<scope>test</scope>", b""),
                   POM.replace(b"</project>", b"<parent/></project>"),
                   POM.replace(b"</project>", b"<profiles/></project>"),
                   POM.replace(b"</project>", b"<version>0.1.0</version></project>"),
                   POM.replace(b"https://maven.pkg.github.com/afa000/flexbuddy", b"https://example.test/other"),
                   b'<!DOCTYPE project [<!ENTITY example "no">]><project/>']
        for index, data in enumerate(invalid):
            with self.subTest(case=index), self.assertRaises(release.ReleaseError):
                release.check_pom(data, VERSION)

    def test_source_resolution_uses_a_derived_tag_and_requires_main_ancestry(self):
        repo = self.root / "source"
        repo.mkdir()
        run = lambda *args: subprocess.run(["git", "-C", str(repo), *args], check=True,
                                          capture_output=True)
        run("init", "-b", "main")
        run("config", "user.name", "Release Test")
        run("config", "user.email", "release@example.test")
        (repo / "empty-hooks").mkdir()
        run("config", "core.hooksPath", str(repo / "empty-hooks"))
        run("config", "commit.gpgsign", "false")
        run("config", "tag.gpgsign", "false")
        pom = repo / release.POM_PATH
        pom.parent.mkdir(parents=True)
        pom.write_bytes(POM)
        run("add", ".")
        run("commit", "-m", "synthetic fixture")
        sha = run("rev-parse", "HEAD").stdout.decode().strip()
        run("update-ref", "refs/remotes/origin/main", sha)
        with self.assertRaises(release.ReleaseError):
            release.resolve_source(repo, VERSION)  # Missing tag.
        run("tag", "-a", "import-core-v0.1.0", "-m", "synthetic test tag")
        self.assertEqual(sha, release.resolve_source(repo, VERSION)["source-sha"])
        run("checkout", "-b", "unmerged")
        (repo / "unmerged.txt").write_text("synthetic", encoding="utf-8")
        run("add", ".")
        run("commit", "-m", "unmerged fixture")
        run("tag", "-f", "import-core-v0.1.0")
        with self.assertRaises(release.ReleaseError):
            release.resolve_source(repo, VERSION)

    def test_payload_binds_files_checksums_version_and_source(self):
        payload, manifest = self.payload()
        self.assertEqual(manifest, release.check_payload(payload, VERSION, SHA))
        with self.assertRaises(release.ReleaseError):
            release.check_payload(payload, VERSION, "b" * 40)
        (payload / release.names(VERSION)[0]).write_bytes(b"changed")
        with self.assertRaises(release.ReleaseError):
            release.check_payload(payload, VERSION, SHA)

    def test_staging_uses_only_the_checked_source_and_empty_runtime_tree(self):
        original, _ = self.payload()
        source = self.root / "source"
        target = source / "flexbuddy/import-core/target"
        target.mkdir(parents=True)
        (source / release.POM_PATH).write_bytes(POM)
        jar = release.names(VERSION)[0]
        (target / jar).write_bytes((original / jar).read_bytes())
        tree = target / "release-dependencies.txt"
        tree.write_text(f"{release.GROUP}:{release.ARTIFACT}:jar:{VERSION}\n", encoding="utf-8")
        with patch.object(release, "git", return_value=SHA), patch.object(release, "source_pom", return_value=POM):
            staged = self.root / "staged"
            manifest = release.stage(source, staged, VERSION, SHA)
            self.assertEqual(manifest, release.check_payload(staged, VERSION, SHA))
            with self.assertRaises(release.ReleaseError):
                release.stage(source, staged, VERSION, SHA)  # Never overwrite an existing staging payload.
            tree.write_text("core\n+- unexpected:runtime:jar:1\n", encoding="utf-8")
            with self.assertRaisesRegex(release.ReleaseError, "runtime dependency"):
                release.stage(source, self.root / "bad-tree", VERSION, SHA)

    def test_staging_refuses_a_mutated_pom_even_when_coordinates_still_match(self):
        source = self.root / "source"
        pom = source / release.POM_PATH
        pom.parent.mkdir(parents=True)
        pom.write_bytes(POM.replace(b"</project>", b"<!-- mutated during build --></project>"))
        with patch.object(release, "git", return_value=SHA), patch.object(release, "source_pom", return_value=POM):
            with self.assertRaisesRegex(release.ReleaseError, "POM changed"):
                release.stage(source, self.root / "staged", VERSION, SHA)

    def test_manifest_rejects_extra_files_and_coordinates_before_upload(self):
        payload, manifest = self.payload()
        (payload / "screenshot.png").write_bytes(b"synthetic")
        with self.assertRaises(release.ReleaseError):
            release.check_payload(payload, VERSION, SHA)
        (payload / "screenshot.png").unlink()
        manifest["version"] = "0.2.0"
        (payload / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaises(release.ReleaseError):
            release.check_payload(payload, VERSION, SHA)

    def test_only_core_classes_and_maven_metadata_belong_in_the_jar(self):
        payload, _ = self.payload()
        jar = payload / release.names(VERSION)[0]
        with zipfile.ZipFile(jar, "a") as output:
            output.writestr("BOOT-INF/classes/Server.class", b"synthetic")
        with self.assertRaises(release.ReleaseError):
            release.check_jar(jar)

    def test_all_three_registry_paths_must_be_absent_before_upload(self):
        get = Mock(return_value=(404, b""))
        release.preflight(VERSION, get)
        self.assertEqual(3, get.call_count)
        self.assertTrue(get.call_args_list[0].args[0].endswith("maven-metadata.xml"))

    def test_existing_metadata_or_either_partial_artifact_refuses_upload(self):
        metadata = (b"<metadata><groupId>com.angel</groupId><artifactId>flexbuddy-import-core</artifactId>"
                    b"<versioning><versions><version>0.1.0</version></versions></versioning></metadata>")
        for responses in ([(200, metadata)], [(404, b""), (200, b"jar")],
                          [(404, b""), (404, b""), (200, b"pom")]):
            with self.subTest(responses=responses), self.assertRaises(release.ReleaseError):
                release.preflight(VERSION, Mock(side_effect=responses))

    def test_a_new_version_can_follow_an_existing_release(self):
        metadata = (b"<metadata><groupId>com.angel</groupId><artifactId>flexbuddy-import-core</artifactId>"
                    b"<versioning><versions><version>0.0.9</version></versions></versioning></metadata>")
        get = Mock(side_effect=[(200, metadata), (404, b""), (404, b"")])
        release.preflight(VERSION, get)
        self.assertEqual(3, get.call_count)

    def test_401_403_rate_limits_redirects_and_server_errors_are_not_missing_versions(self):
        for status in (301, 401, 403, 429, 500, 503):
            for position in range(3):
                responses = [(404, b"")] * position + [(status, b"")]
                with self.subTest(status=status, position=position), self.assertRaises(release.ReleaseError):
                    release.preflight(VERSION, Mock(side_effect=responses))
        with self.assertRaises(release.ReleaseError):
            release.preflight(VERSION, Mock(return_value=(200, b"not metadata")))
        with self.assertRaises(release.ReleaseError):
            release.preflight(VERSION, Mock(return_value=(200, b"<metadata/>")))

    def test_network_failure_is_fail_closed_without_logging_credentials(self):
        opener = Mock()
        opener.open.side_effect = URLError("synthetic network failure")
        with self.assertRaisesRegex(release.ReleaseError, "state is unknown") as caught:
            release.fetch(release.REGISTRY + "/file", "synthetic secret", opener)
        self.assertNotIn("synthetic secret", str(caught.exception))

    def test_redirects_never_forward_credentials_to_another_origin(self):
        headers = Message()
        headers["Location"] = "https://storage.example.test/file"
        redirect = HTTPError(release.REGISTRY + "/file", 302, "redirect", headers, io.BytesIO())
        response = Mock()
        response.status = 200
        response.read.return_value = b"synthetic"
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        opener = Mock()
        opener.open.side_effect = [redirect, response]
        self.assertEqual((200, b"synthetic"), release.fetch(release.REGISTRY + "/file", "secret fixture", opener))
        self.assertEqual("secret fixture", opener.open.call_args_list[0].args[0].get_header("Authorization"))
        self.assertIsNone(opener.open.call_args_list[1].args[0].get_header("Authorization"))

    def test_redirects_to_http_or_embedded_credentials_are_rejected(self):
        for location in ("http://storage.example.test/file", "https://user:password@storage.example.test/file"):
            headers = Message()
            headers["Location"] = location
            opener = Mock()
            opener.open.side_effect = HTTPError(release.REGISTRY, 302, "redirect", headers, io.BytesIO())
            with self.subTest(location=location), self.assertRaises(release.ReleaseError):
                release.fetch(release.REGISTRY, "fixture", opener)
            self.assertEqual(1, opener.open.call_count)

    def test_remote_verification_requires_both_exact_artifacts(self):
        payload, manifest = self.payload()
        get = Mock(side_effect=[(200, (payload / name).read_bytes()) for name in manifest["sha256"]])
        release.verify_remote(payload, manifest, get)
        self.assertEqual(2, get.call_count)
        for status, data in ((401, b""), (403, b""), (500, b""), (200, b"wrong bytes")):
            with self.subTest(status=status), self.assertRaisesRegex(release.ReleaseError, "incomplete"):
                release.verify_remote(payload, manifest, Mock(return_value=(status, data)))

    def test_only_brief_not_found_visibility_delay_is_retried_after_upload(self):
        payload, manifest = self.payload()
        responses = [(404, b"")] + [(200, (payload / name).read_bytes()) for name in manifest["sha256"]]
        sleep = Mock()
        release.verify_remote(payload, manifest, Mock(side_effect=responses), sleep)
        sleep.assert_called_once_with(2)
        with self.assertRaises(release.ReleaseError):
            release.verify_remote(payload, manifest, Mock(return_value=(404, b"")), Mock())

    def test_upload_uses_a_neutral_project_and_environment_references_only(self):
        payload, _ = self.payload()
        settings, global_settings, control = release.write_maven_control(self.root)
        command = release.deploy_command(ROOT, payload, VERSION, settings, global_settings, control)
        self.assertEqual(["sh", str(ROOT / "flexbuddy/mvnw")], command[:2])  # Pinned wrapper, not the runner's mvn.
        self.assertTrue((ROOT / "flexbuddy/mvnw").is_file())
        self.assertIn(str(control), command)
        self.assertIn("org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy-file", command)
        self.assertIn("-DpomFile=" + str(payload / release.names(VERSION)[1]), command)
        self.assertIn("-DretryFailedDeploymentCount=1", command)
        self.assertIn("${env.GH_PACKAGES_TOKEN}", settings.read_text(encoding="utf-8"))
        self.assertNotIn("<build>", control.read_text(encoding="utf-8"))
        with self.assertRaises(release.ReleaseError):
            release.deploy_command(ROOT, payload, VERSION, settings, global_settings, control,
                                   "https://example.test/upload")

    def test_failed_preflight_never_invokes_maven(self):
        payload, _ = self.payload()
        env = dict(ENV, GITHUB_ACTOR="test", GH_PACKAGES_TOKEN="fixture", RUNNER_TEMP=str(self.root))
        with patch.object(release, "resolve_source", return_value={"source-sha": SHA}), \
                patch.object(release, "fetch", return_value=(401, b"")), \
                patch.object(release.subprocess, "run") as run:
            with self.assertRaises(release.ReleaseError):
                release.publish(self.root, payload, VERSION, SHA, env)
            run.assert_not_called()

    def test_a_retargeted_tag_is_refused_before_any_registry_request(self):
        payload, _ = self.payload()
        with patch.object(release, "resolve_source", return_value={"source-sha": "b" * 40}), \
                patch.object(release, "fetch") as fetch:
            with self.assertRaises(release.ReleaseError):
                release.publish(self.root, payload, VERSION, SHA, ENV)
            fetch.assert_not_called()

    def test_upload_failure_and_failed_remote_verification_do_not_report_success(self):
        payload, _ = self.payload()
        env = dict(ENV, GITHUB_ACTOR="test", GH_PACKAGES_TOKEN="fixture", RUNNER_TEMP=str(self.root))
        for upload_exit in (0, 1):
            with self.subTest(upload_exit=upload_exit), \
                    patch.object(release, "resolve_source", return_value={"source-sha": SHA}), \
                    patch.object(release, "fetch", return_value=(404, b"")), \
                    patch.object(release.subprocess, "run", return_value=Mock(returncode=upload_exit)) as run, \
                    patch.object(release, "verify_remote", side_effect=release.ReleaseError("incomplete")), \
                    patch.object(release, "summary") as summary:
                with self.assertRaises(release.ReleaseError):
                    release.publish(self.root, payload, VERSION, SHA, env)
                self.assertEqual(1, run.call_count)
                summary.assert_not_called()


def blocks(lines, indent):
    """Split YAML text into {key: lines} at one indentation level; enough for this workflow's plain layout."""
    found, current = {}, None
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        depth = len(line) - len(line.lstrip(" "))
        if depth < indent:
            current = None
        elif depth == indent and stripped.endswith(":") or depth == indent and ": " in stripped:
            current = stripped.split(":")[0]
            found[current] = [stripped]
        elif current:
            found[current].append(stripped)
    return found


class WorkflowRulesTests(unittest.TestCase):
    """The publishing rules live in YAML, so check the text: these lines would otherwise change without a failure."""

    def setUp(self):
        self.text = (ROOT / ".github/workflows/publish-import-core.yml").read_text(encoding="utf-8")
        lines = self.text.splitlines()
        self.top = blocks(lines, 0)
        self.jobs = blocks(lines[lines.index("jobs:") + 1:], 2)

    def test_only_a_manual_dispatch_from_main_can_start_it(self):
        self.assertEqual(["on:", "workflow_dispatch:"], self.top["on"][:2])
        self.assertFalse([line for line in self.top["on"][1:] if line.endswith(":") and line not in
                          ("workflow_dispatch:", "inputs:", "version:")])
        for trigger in ("push", "pull_request", "pull_request_target", "release", "schedule", "workflow_run"):
            self.assertNotRegex(self.text, r"(?m)^\s+" + trigger + r":")
        self.assertEqual({"validate", "publish"}, set(self.jobs))
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertIn("if: github.ref == 'refs/heads/main' && github.repository == 'afa000/flexbuddy'", job)

    def test_write_permission_and_the_token_belong_only_to_the_publish_job(self):
        self.assertEqual(["permissions:", "contents: read"], self.top["permissions"])
        self.assertEqual(1, self.text.count("packages: write"))
        self.assertIn("packages: write", self.jobs["publish"])
        self.assertNotIn("permissions:", self.jobs["validate"])
        self.assertEqual(1, self.text.count("secrets."))
        self.assertIn("GH_PACKAGES_TOKEN: ${{ secrets.GITHUB_TOKEN }}", self.jobs["publish"])

    def test_every_checkout_drops_its_credentials(self):
        checkouts = self.text.count("uses: actions/checkout@")
        self.assertEqual(3, checkouts)
        self.assertEqual(checkouts, self.text.count("persist-credentials: false"))

    def test_staging_runs_a_copy_taken_before_the_tagged_build(self):
        validate = self.jobs["validate"]
        copy = validate.index('cp scripts/release/import_core.py "$RUNNER_TEMP/release-tooling/import_core.py"')
        tagged_checkout = validate.index("path: release-source")
        check = validate.index(
            'echo "$TOOLING_SHA256  $RUNNER_TEMP/release-tooling/import_core.py" | sha256sum --check --quiet')
        stage = validate.index('python3 -I "$RUNNER_TEMP/release-tooling/import_core.py" stage')
        self.assertLess(copy, tagged_checkout)
        self.assertLess(check, stage)
        self.assertNotIn("scripts/release/import_core.py stage", "\n".join(validate))
        self.assertIn("run: python3 scripts/release/import_core.py publish", self.jobs["publish"])


if __name__ == "__main__":
    unittest.main()
