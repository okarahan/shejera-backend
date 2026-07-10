# shejera

Homelab app — Sourcecode-Repo. Deployment via [homelab](https://github.com/okarahan/homelab) + Flux.

## Lokal

```bash
python src/main.py
curl http://localhost:8080/
```

## Image

GitHub Actions pusht nach `ghcr.io/okarahan/shejera` bei Push auf `main` oder Tags `v*`.

Nach erstem Push: Package auf **public** stellen unter GitHub → Packages.

## Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Image-Tag in `homelab/kubernetes/apps/shejera/deployment.yaml` setzen.
