from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[3]

for workflow_name in ("verify", "diagnose"):
    path = ROOT / ".github" / "workflows" / f"{workflow_name}.yml"
    workflow = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert workflow.get("cache-mode") == "read", (
        f"{workflow_name}.yml must keep provider caches read-only"
    )
    for job_name, job in workflow["jobs"].items():
        assert "cache-mode" not in job, (
            f"{workflow_name}.yml job {job_name!r} must not override the "
            "workflow-level read-only cache boundary"
        )

print("Provider cache contract passed: verify and diagnose are read-only.")
