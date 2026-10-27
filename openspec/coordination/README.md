# Cross-Stack Coordination (LoopForge convention for OpenSpec 1.6.0)

> OpenSpec 1.6.0 removed `workspace`/`context-store`/`initiative`. This directory is the
> LoopForge-managed replacement for the "parent" that tracks a cross-stack feature across
> per-stack changes. It is plain documentation - the openspec CLI does not parse it.

## How a cross-stack feature works
1. **Create the parent** - copy `_template.md` to `<feature>.md`, fill the shared design/decisions.
2. **Per stack** - in each stack run `openspec new change <name> --goal "<feature>"` (soft-tagged).
3. **Register** - list each per-stack change in `<feature>.md`'s change registry table.
4. **Gate** - the feature is done when ALL registered changes verify PASS (`openspec/verify.config.yaml`); archive each.

## Open all stacks together
```bash
openspec workset open lumen-collab --tool code   # IDE (VS Code/Cursor). Agent open is temporarily disabled in 1.6.0.
```
For a coordinator agent session, launch `codex` / `claude` manually in the project root.

## Separate repos (advanced)
If stacks live in independent git repos (each its own OpenSpec root), register and address them with stores:
`openspec store register --id <project>-<stack> <repo-path>` then `openspec new change <name> --goal "<feature>" --store <project>-<stack>`.
