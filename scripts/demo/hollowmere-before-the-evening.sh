#!/usr/bin/env bash
# Builds the "Before the evening" timeline (GREGORIAN, 1893 to 1923) in the dev profile's
# "The Hollowmere Affair": eleven plain events plus scene 4 and entry 28, which are also on main.
# Dev data only; not shipped. Refuses to run if the timeline already exists.
#
# Runs the Hammer CLI from this checkout. Set HAMMER_CP to the desktop runtime classpath to skip
# resolving it through Gradle (the script prints the export line for reuse).
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROJECT="The Hollowmere Affair"
NAME="Before the evening"

if [[ -z "${HAMMER_CP:-}" ]]; then
	init="$(mktemp --suffix=.init.gradle.kts)"
	trap 'rm -f "$init"' EXIT
	cat >"$init" <<'EOF'
gradle.projectsEvaluated {
	rootProject.findProject(":desktop")?.run {
		tasks.register("printHammerClasspath") {
			val jar = tasks.named("jvmJar")
			dependsOn(jar)
			val cp = files(jar, configurations.getByName("jvmRuntimeClasspath"))
			doLast { println("HAMMER_CP=" + cp.asPath) }
		}
	}
}
EOF
	HAMMER_CP="$("$REPO/gradlew" -p "$REPO" -q --no-configuration-cache -I "$init" :desktop:printHammerClasspath |
		sed -n 's/^HAMMER_CP=//p')"
	[[ -n "$HAMMER_CP" ]] || { echo "Could not resolve the desktop classpath" >&2; exit 1; }
	echo "export HAMMER_CP='$HAMMER_CP'" >&2
fi

# The main class is called directly: gradle run re-splits arguments and mangles --json.
hammer() {
	java -cp "$HAMMER_CP" com.darkrockstudios.apps.hammer.desktop.MainKt --dev "$@"
}

existing="$(hammer timeline settings read --project "$PROJECT" </dev/null |
	jq -r --arg n "$NAME" '.timelines[] | select((.name | ascii_downcase) == ($n | ascii_downcase)) | .id')"
if [[ -n "$existing" ]]; then
	echo "\"$NAME\" already exists in $PROJECT (id $existing); not creating it again" >&2
	exit 1
fi

# The plan's people, by entry id; checked so a renumbered project fails here, not halfway through.
HOLLOWMERE=15 HAYES=16 WARD=17
entries="$(hammer entry list --project "$PROJECT" </dev/null)"
for pair in "$HOLLOWMERE:Lord Hollowmere" "$HAYES:Col. Hayes" "$WARD:Mrs. Ward" "28:The Hollowmere murder"; do
	id="${pair%%:*}" want="${pair#*:}"
	got="$(jq -r --argjson id "$id" '.entries[] | select(.id == $id) | .name' <<<"$entries")"
	[[ "$got" == "$want" ]] || { echo "Entry $id is \"$got\", expected \"$want\"" >&2; exit 1; }
done
scene4="$(hammer scene tree --project "$PROJECT" </dev/null | jq -r '.. | objects | select(.id? == 4) | .name' | head -n1)"
[[ "$scene4" == "Dinner" ]] || { echo "Scene 4 is \"$scene4\", expected \"Dinner\"" >&2; exit 1; }

T="$(hammer timeline def create --json "$(jq -nc --arg p "$PROJECT" --arg n "$NAME" '{
	project: $p, name: $n, mode: "dated", calendar: {preset: "gregorian"},
	laneOrder: ["Col. Hayes", "Lord Hollowmere", "Mrs. Ward", "Pike"]
}')" </dev/null | jq -r '.id')"
echo "Created \"$NAME\": $T" >&2

# event <title> <lane> <start> <end|""> <milestone> <references> <body>
event() {
	local input
	input="$(jq -nc --arg p "$PROJECT" --arg t "$T" --arg title "$1" --arg lane "$2" \
		--argjson start "$3" --arg end "$4" --argjson milestone "$5" --argjson refs "$6" '
		{project: $p, timeline: $t, title: $title, lane: $lane, start: $start, milestone: $milestone, references: $refs}
		+ (if $end == "" then {} else {end: ($end | fromjson)} end)')"
	printf '%s' "$7" | hammer timeline create --json "$input" | jq -r '"  event \(.id): \(.displayTitle)"' >&2
}

event "Hayes and Hollowmere meet at Sandhurst" "Col. Hayes" '{"year":1893}' "" false "[$HOLLOWMERE,$HAYES]" \
	"Cadets in the same intake. The friendship of thirty years starts here."
event "Hollowmere & Ward founded" "Lord Hollowmere" '{"year":1902,"month":3}' "" false "[$HOLLOWMERE]" \
	"Hollowmere puts up the capital; Edward Ward runs the business."
event "Mrs. Ward marries Edward Ward" "Mrs. Ward" '{"year":1905,"month":6,"day":2}' "" false "[$WARD]" \
	"She marries Hollowmere's partner, and into the firm's affairs."
event "Pike in service" "Pike" '{"year":1912,"month":4}' '{"year":1923,"month":11,"day":14}' false "[]" \
	"Pike enters service at Hollowmere and is still there on the night."
event "Hayes on active service" "Col. Hayes" '{"year":1914,"month":8}' '{"year":1918,"month":11}' false "[]" \
	"Four years in France. He comes home a colonel with expensive habits."
event "The first loan" "Col. Hayes" '{"year":1919,"month":5}' "" false "[$HOLLOWMERE,$HAYES]" \
	"Hollowmere lends Hayes money for the first time, as a favour between friends."
event "Edward Ward dies; the partnership is left unsettled" "Mrs. Ward" '{"year":1921,"month":3,"day":9}' "" true "[$WARD]" \
	"His share passes to his widow. Nothing about the partnership is settled."
event "Hayes signs the note of hand" "Col. Hayes" '{"year":1922,"month":9,"day":14}' "" true "[$HOLLOWMERE,$HAYES]" \
	"The debts are rolled into one note of hand, payable on demand."
event "Hollowmere calls in the note" "Lord Hollowmere" '{"year":1923,"month":10}' "" false "[$HOLLOWMERE,$HAYES]" \
	"Hollowmere writes that he wants the note paid by the new year."
event "Mrs. Ward arrives to settle the partnership" "Mrs. Ward" '{"year":1923,"month":11,"day":10}' "" false "[$WARD]" \
	"She comes to Hollowmere to settle the partnership at last."
event "Hayes arrives at Hollowmere" "Col. Hayes" '{"year":1923,"month":11,"day":13,"hour":17}' "" false "[]" \
	"The afternoon train. He is shown to his usual room."

# place <kind> <subject> <start> <end|""> <milestone>
place() {
	local input
	input="$(jq -nc --arg p "$PROJECT" --arg t "$T" --arg kind "$1" --argjson subject "$2" \
		--argjson start "$3" --arg end "$4" --argjson milestone "$5" '
		{project: $p, timeline: $t, kind: $kind, subject: $subject, start: $start, milestone: $milestone}
		+ (if $end == "" then {} else {end: ($end | fromjson)} end)')"
	hammer timeline place --json "$input" </dev/null | jq -r '"  \(.kind) \(.id): \(.displayTitle)"' >&2
}

place scene 4 '{"year":1923,"month":11,"day":14,"hour":19,"minute":0}' \
	'{"year":1923,"month":11,"day":14,"hour":20,"minute":30}' false
place entry 28 '{"year":1923,"month":11,"day":14}' "" true

# Title, kind, lane, start, end, milestone, in display order.
expected="$(cat <<'EOF'
Hayes and Hollowmere meet at Sandhurst	event	Col. Hayes	{"year":1893}		false
Hollowmere & Ward founded	event	Lord Hollowmere	{"year":1902,"month":3}		false
Mrs. Ward marries Edward Ward	event	Mrs. Ward	{"year":1905,"month":6,"day":2}		false
Pike in service	event	Pike	{"year":1912,"month":4}	{"year":1923,"month":11,"day":14}	false
Hayes on active service	event	Col. Hayes	{"year":1914,"month":8}	{"year":1918,"month":11}	false
The first loan	event	Col. Hayes	{"year":1919,"month":5}		false
Edward Ward dies; the partnership is left unsettled	event	Mrs. Ward	{"year":1921,"month":3,"day":9}		true
Hayes signs the note of hand	event	Col. Hayes	{"year":1922,"month":9,"day":14}		true
Hollowmere calls in the note	event	Lord Hollowmere	{"year":1923,"month":10}		false
Mrs. Ward arrives to settle the partnership	event	Mrs. Ward	{"year":1923,"month":11,"day":10}		false
Hayes arrives at Hollowmere	event	Col. Hayes	{"year":1923,"month":11,"day":13,"hour":17}		false
The Hollowmere murder	entry		{"year":1923,"month":11,"day":14}		true
Dinner	scene		{"year":1923,"month":11,"day":14,"hour":19,"minute":0}	{"year":1923,"month":11,"day":14,"hour":20,"minute":30}	false
EOF
)"
actual="$(hammer timeline list --project "$PROJECT" --timeline "$T" </dev/null | jq -r '
	.events[] | [.displayTitle, .kind, (.lane // ""), (.start | tojson),
		(if .end == null then "" else (.end | tojson) end), (.milestone | tostring)] | @tsv')"

if diff <(echo "$expected") <(echo "$actual"); then
	echo "\"$NAME\" ($T) matches the expected table" >&2
else
	echo "\"$NAME\" ($T) differs from the expected table (< expected, > actual)" >&2
	exit 1
fi
