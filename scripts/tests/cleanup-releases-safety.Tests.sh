#!/usr/bin/env bash
# Executes the workflow's actual shell with a function mock; never invokes the gh binary.
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
workflow_shell=$(ruby -ryaml -e '
  workflow = YAML.load_file(ARGV.fetch(0))
  abort "Unexpected permissions" unless workflow.fetch("permissions") == {"contents" => "write"}
  triggers = workflow["on"] || workflow[true]
  abort "Cleanup must stay manual" unless triggers.keys == ["workflow_dispatch"]
  steps = workflow.fetch("jobs").fetch("cleanup").fetch("steps")
  abort "Unexpected steps" unless steps.length == 1 && steps[0]["shell"] == "bash"
  puts steps[0].fetch("run")
' "$repo_root/.github/workflows/cleanup-releases.yml")
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
export MOCK_LOG="$fixture/gh.log"
unset GH_TOKEN GH_REPO GITHUB_TOKEN
export BASH_ENV=/dev/null

gh() {
    local args=("$@") found_repo=0 index
    printf '%s|' "$@" >> "$MOCK_LOG"
    printf '\n' >> "$MOCK_LOG"
    for ((index=0; index<${#args[@]}; index++)); do
        if [[ "${args[$index]}" == --repo &&
                "${args[$((index+1))]:-}" == audit/mock-repository ]]; then
            found_repo=1
        fi
        [[ "${args[$index]}" != --cleanup-tag ]] || return 91
    done
    [[ "$found_repo" == 1 ]] || return 92
    if [[ "$1 $2" == 'release list' ]]; then
        printf '%s' "$MOCK_RELEASES"
        return "$MOCK_LIST_EXIT"
    fi
    [[ "$1 $2" == 'release delete' && "$5" == audit/mock-repository &&
            "$6" == --yes && "$#" == 6 ]] || return 93
    return "$MOCK_DELETE_EXIT"
}

passed=0
run_case() {
    local name=$1 keep=$2 releases=$3 list_exit=$4 delete_exit=$5 expected_exit=$6
    local expected_deletes=$7 result=0 actual_deletes=0
    : > "$MOCK_LOG"
    (cd "$fixture" && GITHUB_REPOSITORY=audit/mock-repository KEEP_LATEST="$keep" \
        MOCK_RELEASES="$releases" MOCK_LIST_EXIT="$list_exit" MOCK_DELETE_EXIT="$delete_exit" \
        bash -c "$(declare -f gh)"$'\n'"$workflow_shell") \
        > "$fixture/stdout" 2> "$fixture/stderr" || result=$?
    [[ "$result" == "$expected_exit" ]] || {
        printf 'FAIL %s: expected exit %s, got %s\n' "$name" "$expected_exit" "$result"
        cat "$fixture/stderr"
        exit 1
    }
    while IFS= read -r call; do
        if [[ "$call" == 'release|delete|'* ]]; then
            actual_deletes=$((actual_deletes+1))
        fi
    done < "$MOCK_LOG"
    [[ "$actual_deletes" == "$expected_deletes" ]] || {
        printf 'FAIL %s: expected %s deletes, got %s\n' "$name" "$expected_deletes" "$actual_deletes"
        exit 1
    }
    passed=$((passed+1))
}

run_case list-failure 3 '' 17 0 17 0
run_case partial-list-failure 0 $'v4\nv3\n' 18 0 18 0
run_case empty-list 0 '' 0 0 0 0
run_case below-retention 3 $'v2\nv1' 0 0 0 0
run_case exact-retention 3 $'v3\nv2\nv1' 0 0 0 0
run_case older-releases 3 $'v5\nv4\nv3\nv2\nv1\n' 0 0 0 2
[[ "$(tail -n 2 "$MOCK_LOG")" == \
    $'release|delete|v2|--repo|audit/mock-repository|--yes|\nrelease|delete|v1|--repo|audit/mock-repository|--yes|' ]]
run_case zero-retention 0 $'v2\nv1' 0 0 0 2
run_case leading-zero-retention 03 $'v4\nv3\nv2\nv1' 0 0 0 1
run_case delete-failure 0 $'v3\nv2\nv1' 0 19 19 1
run_case quoted-tag 0 'v1; must-not-run' 0 0 0 1
[[ "$(tail -n 1 "$MOCK_LOG")" == \
    'release|delete|v1; must-not-run|--repo|audit/mock-repository|--yes|' ]]
for invalid in '' -1 abc '1; exit 0'; do
    run_case invalid-retention "$invalid" v1 0 0 1 0
    [[ ! -s "$MOCK_LOG" ]]
done
printf 'PASS: %s cleanup safety scenarios (mocked gh; no network/deletion)\n' "$passed"
