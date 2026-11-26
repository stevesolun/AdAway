#!/usr/bin/env bash
# Run the workflow shell with a gh function mock; never contact GitHub.
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/../.." && pwd)
workflow_shell=$(ruby -ryaml -e '
  w = YAML.load_file(ARGV.fetch(0))
  abort unless w.fetch("permissions") == {"contents" => "write"}
  triggers = w["on"] || w[true]
  abort unless triggers.keys == ["workflow_dispatch"]
  apply = triggers["workflow_dispatch"]["inputs"]["apply"]
  abort unless apply["type"] == "boolean" && apply.fetch("default") == false
  job = w.fetch("jobs").fetch("cleanup")
  abort unless job.fetch("if").include?("github.event.repository.default_branch")
  steps = job.fetch("steps")
  abort unless steps.length == 1 && steps[0]["shell"] == "bash"
  puts steps[0].fetch("run")
' "$repo_root/.github/workflows/cleanup-releases.yml")
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
unset GH_TOKEN GH_REPO GITHUB_TOKEN
export BASH_ENV=/dev/null
export MOCK_LOG="$fixture/gh.log"

gh() {
    printf '%s\n' "$*" >> "$MOCK_LOG"
    case "$*" in
        'api --paginate --slurp repos/audit/mock-repository/releases?per_page=100')
            printf '%s' "$PAGES"; return "$LIST_RC";;
        'api repos/audit/mock-repository/releases/2')
            printf '%s' "$CURRENT"; return "$VIEW_RC";;
        'api --method DELETE repos/audit/mock-repository/releases/2') return "$DELETE_RC";;
        *) echo "Unexpected request: $*" >&2; return 97;;
    esac
}

release() {
    jq -cn --argjson id "$1" --arg day "$2" '{
      id:$id,tag_name:("v1.0."+($id|tostring)+"b"),draft:false,prerelease:true,
      immutable:false,assets:[],created_at:("2026-01-"+$day+"T01:00:00Z"),
      published_at:("2026-01-"+$day+"T02:00:00Z"),updated_at:("2026-01-"+$day+"T03:00:00Z")}'
}
legacy=$(release 2 02)
head_beta=$(release 3 03)
protected=$(jq -cn --argjson old "$legacy" --argjson head "$head_beta" '[
  [$old, ($old+{id:10,prerelease:false}), ($old+{id:11,draft:true}),
   ($old+{id:12,assets:[{name:"AdAway_1.0.12b.apk"}]}),
   ($old+{id:13,assets:[{name:"manifest.json"}]}), ($old+{id:14,immutable:true})],
  [$head, ($head+{id:15}), ($old+{id:16,assets:null}), ($old+{id:17,tag_name:"not-a-version"}),
   ($old+{id:18,immutable:null}), ($old+{id:19,assets:[{name:"other"}]})]]')
passed=0
run_case() {
    local name=$1 keep=$2 apply=$3 pages=$4 current=$5 list_rc=$6 view_rc=$7 delete_rc=$8 expected_rc=$9
    local deletes=${10} result=0 actual
    : > "$MOCK_LOG"
    (cd "$fixture" && GITHUB_REPOSITORY=audit/mock-repository KEEP_LATEST="$keep" APPLY="$apply" \
        PAGES="$pages" CURRENT="$current" LIST_RC="$list_rc" VIEW_RC="$view_rc" DELETE_RC="$delete_rc" \
        bash -c "$(declare -f gh)"$'\n'"$workflow_shell") > "$fixture/out" 2>&1 || result=$?
    actual=$(awk '/^api --method DELETE /{n++} END{print n+0}' "$MOCK_LOG")
    if [[ "$result" != "$expected_rc" || "$actual" != "$deletes" ]]; then
        printf 'FAIL %s: exit %s/%s deletes %s/%s\n' "$name" "$result" "$expected_rc" "$actual" "$deletes"
        cat "$fixture/out" "$MOCK_LOG"; exit 1
    fi
    passed=$((passed+1))
}
run_case preview 0 false "$protected" '' 0 0 0 0 0
[[ $(wc -l < "$MOCK_LOG") -eq 1 && $(cat "$fixture/out") == 'Eligible: v1.0.2b (release 2)' ]]
run_case protected-and-paginated 0 true "$protected" "$legacy" 0 0 0 0 1
run_case retention-floor 020 true "$protected" '' 0 0 0 0 0
run_case empty 0 true '[[]]' '' 0 0 0 0 0
run_case sole-beta 0 true "[[$legacy]]" '' 0 0 0 0 0
run_case no-response 0 true '' '' 0 0 0 1 0
run_case failed-enumeration 0 true "$protected" '' 17 0 0 17 0
run_case failed-recheck 0 true "$protected" '' 0 18 0 18 0
run_case empty-recheck 0 true "$protected" '' 0 0 0 1 0
run_case ambiguous-recheck 0 true "$protected" "$legacy $legacy" 0 0 0 1 0
run_case failed-delete 0 true "$protected" "$legacy" 0 0 19 19 1
for change in '{"assets":[{"name":"manifest.json"}]}' '{"id":20}' '{"tag_name":"changed"}' \
    '{"prerelease":false}' '{"draft":true}' '{"immutable":true}' '{"updated_at":"changed"}'; do
    current=$(jq -c --argjson patch "$change" '. + $patch' <<< "$legacy")
    run_case changed-since-plan 0 true "$protected" "$current" 0 0 0 1 0
done
for invalid in '' -1 abc '1; exit 0' 1000000000; do
    run_case invalid-retention "$invalid" true "$protected" '' 0 0 0 1 0
    [[ ! -s "$MOCK_LOG" ]]
done
run_case invalid-apply 0 yes "$protected" '' 0 0 0 1 0
[[ ! -s "$MOCK_LOG" ]]
printf 'PASS: %s release cleanup scenarios (mocked; no network/deletion)\n' "$passed"
