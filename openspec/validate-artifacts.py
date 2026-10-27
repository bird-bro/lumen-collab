#!/usr/bin/env python3
"""LoopForge artifact validator - validates OpenSpec change artifacts structure.

Validates: proposal.md, specs/*/spec.md, design.md, tasks.md.
Reports ERROR/WARNING/INFO with file:path. Exit 1 on ERROR.
"""
import sys, os, re

MIN_WHY_LENGTH = 20
MAX_DELTAS = 15

def extract_section(content, header_re, level=2):
    """Extract text under a header matching header_re.
    level: the header level (2=##, 3=###, 4=####). Stops at same-or-higher headers."""
    m = re.search(header_re, content, re.MULTILINE)
    if not m:
        return None
    start = m.end()
    rest = content[start:]
    # Build pattern for same-or-higher level headers
    prefixes = '#' * (level)  # e.g. level=3 -> '###'
    # Match headers with level <= current (fewer or equal # signs)
    stop_re = r'^#{2,' + str(level) + r'}\s+'
    nm = re.search(stop_re, rest, re.MULTILINE)
    if nm:
        return rest[:nm.start()].strip()
    return rest.strip()

def validate_proposal(path):
    issues = []
    if not os.path.exists(path):
        return issues
    content = open(path, encoding='utf-8').read()
    why = extract_section(content, r'^##\s+Why\s*$', level=2)
    if why is None:
        issues.append(('ERROR', 'proposal.md:why', 'Missing ## Why section'))
    elif len(why.strip()) < MIN_WHY_LENGTH:
        issues.append(('ERROR', 'proposal.md:why', '## Why too brief (min %d chars)' % MIN_WHY_LENGTH))
    what = extract_section(content, r'^##\s+What Changes\s*$', level=2)
    if what is None:
        issues.append(('ERROR', 'proposal.md:whatChanges', 'Missing ## What Changes section'))
    elif not what.strip():
        issues.append(('ERROR', 'proposal.md:whatChanges', '## What Changes is empty'))
    return issues

def validate_spec(path):
    issues = []
    if not os.path.exists(path):
        return issues
    content = open(path, encoding='utf-8').read()
    delta_ops = ['ADDED', 'MODIFIED', 'REMOVED', 'RENAMED']
    found_ops = [op for op in delta_ops if re.search(r'^##\s+' + op + r'\s+Requirements\s*$', content, re.MULTILINE)]
    if not found_ops:
        issues.append(('ERROR', 'spec.md:deltas', 'No ADDED/MODIFIED/REMOVED/RENAMED section found'))
        return issues
    total = 0
    for op in found_ops:
        section = extract_section(content, r'^##\s+' + op + r'\s+Requirements\s*$', level=2)
        if not section:
            continue
        reqs = re.findall(r'^###\s+Requirement:\s*(.+?)\s*$', section, re.MULTILINE)
        seen = set()
        for name in reqs:
            total += 1
            key = name.strip().lower()
            if key in seen:
                issues.append(('ERROR', 'spec.md:%s.%s' % (op.lower(), key), 'Duplicate: %s' % name))
            seen.add(key)
            block = extract_section(section, r'^###\s+Requirement:\s*' + re.escape(name) + r'\s*$', level=3)
            if block:
                if not re.search(r'\b(SHALL|MUST)\b', block, re.IGNORECASE):
                    issues.append(('ERROR', 'spec.md:%s.%s' % (op.lower(), key), '%s "%s" missing SHALL/MUST' % (op, name)))
                if not re.search(r'(WHEN|Scenario|scenario)', block):
                    issues.append(('ERROR', 'spec.md:%s.%s' % (op.lower(), key), '%s "%s" has no scenario (WHEN/THEN)' % (op, name)))
    if total == 0:
        issues.append(('ERROR', 'spec.md:deltas', 'Delta sections exist but no requirements (### Requirement:) found'))
    elif total > MAX_DELTAS:
        issues.append(('WARNING', 'spec.md:deltas', 'Many deltas (%d > %d) - consider splitting' % (total, MAX_DELTAS)))
    return issues

def validate_design(path):
    issues = []
    if not os.path.exists(path):
        return issues
    content = open(path, encoding='utf-8').read()
    if not extract_section(content, r'^##\s+Decisions\s*$', level=2):
        issues.append(('WARNING', 'design.md:decisions', 'Missing ## Decisions section (recommended)'))
    return issues

def validate_tasks(path):
    issues = []
    if not os.path.exists(path):
        return issues
    content = open(path, encoding='utf-8').read()
    numbered = re.findall(r'^\d+[\.\)]\s+', content, re.MULTILINE)
    checkboxes = re.findall(r'^-\s+\[[ xX]\]\s+', content, re.MULTILINE)
    if not numbered and not checkboxes:
        issues.append(('ERROR', 'tasks.md', 'No numbered or checkbox tasks found'))
    return issues

def main():
    if len(sys.argv) < 2:
        print('Usage: python3 validate-artifacts.py <change-dir>')
        sys.exit(2)
    change_dir = sys.argv[1]
    if not os.path.isdir(change_dir):
        print('Error: %s is not a directory' % change_dir)
        sys.exit(2)
    all_issues = []
    all_issues.extend(validate_proposal(os.path.join(change_dir, 'proposal.md')))
    specs_dir = os.path.join(change_dir, 'specs')
    if os.path.isdir(specs_dir):
        for entry in sorted(os.listdir(specs_dir)):
            sf = os.path.join(specs_dir, entry, 'spec.md')
            if os.path.isfile(sf):
                for level, loc, msg in validate_spec(sf):
                    all_issues.append((level, 'specs/%s/%s' % (entry, loc), msg))
    else:
        sf = os.path.join(change_dir, 'spec.md')
        if os.path.isfile(sf):
            all_issues.extend(validate_spec(sf))
    all_issues.extend(validate_design(os.path.join(change_dir, 'design.md')))
    all_issues.extend(validate_tasks(os.path.join(change_dir, 'tasks.md')))
    icons = {'ERROR': 'X', 'WARNING': '!', 'INFO': 'i'}
    has_err = False
    for level, loc, msg in all_issues:
        print('  [%s] %s: %s' % (level, loc, msg))
        if level == 'ERROR':
            has_err = True
    if not all_issues:
        print('  All artifacts valid')
    elif has_err:
        errs = sum(1 for l, _, _ in all_issues if l == 'ERROR')
        print('\n  Validation FAILED (%d errors)' % errs)
    else:
        warns = sum(1 for l, _, _ in all_issues if l == 'WARNING')
        print('\n  Validation passed (%d warnings)' % warns)
    sys.exit(1 if has_err else 0)

if __name__ == '__main__':
    main()
