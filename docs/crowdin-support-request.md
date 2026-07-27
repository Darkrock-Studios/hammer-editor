# Crowdin support request (draft — not sent)

Send to **support@crowdin.com**. This is the only route to a server-side fix: there is
no self-serve setting. Crowdin staff offered exactly this remedy for an identical
Compose Multiplatform case on their forum:
<https://community.crowdin.com/t/compose-multiplatform-xml-should-not-escape-android-special-characters/14364>

Until it is applied, `scripts/Fix-CrowdinEscapes.ps1` cleans up after each download.

---

**To:** support@crowdin.com
**Subject:** Disable Android XML quote escaping for Compose Multiplatform files (project 834522)

Hi,

Project: **Hammer Editor**, id **834522**, <https://crowdin.com/project/hammer-editor>

Could you connect the post-processor that disables quote escaping in Android XML? I
understand from your community forum that your team can attach this on request. I need
it on some files in the project but explicitly not on others.

**Enable it on** these 17 files (branch `develop`, directory id 75,
`/develop/common/src/commonMain/composeResources/values/`):

> 79, 99, 101, 103, 105, 107, 109, 111, 113, 115, 117, 119, 121, 123, 125, 135, 136

**Leave file 127 as it is** (`/develop/android/src/main/res/values/strings.xml`). That
one is a genuine Android resource compiled by AAPT, which requires the escaping. Its
current behaviour is correct.

**Why the other 17 are different**

They use Android XML syntax but are consumed by JetBrains' Compose Multiplatform
resource pipeline, not by AAPT. Compose Resources unescapes `\n`, `\t` and `\uXXXX`
only, and passes every other backslash through to the UI. So an exported French string
renders to the end user with the backslash visible:

    <string name="ideas_header">Idées d\'histoire</string>   ->   Idées d\'histoire

The translations stored in Crowdin are correct; the escaping is added at export. Via
your API:

    GET /projects/834522/translations?stringId=5358&languageId=fr
      -> "Idées d'histoire"                                        (clean)

    POST /projects/834522/translations/builds/files/136 {"targetLanguageId":"fr"}
      -> <string name="ideas_header">Idées d\'histoire</string>    (escaped)

Because it happens at export it returns on every `crowdin download`, so re-uploading
corrected translations does not help. This currently affects 275 strings across 33 files
in four locales (French, Brazilian Portuguese, German, Ukrainian). French is worst hit
because elision makes apostrophes unavoidable (`d'histoire`, `l'idée`).

For completeness, I confirmed there is no setting I can change myself:
`PATCH /projects/834522/files/136` with
`importOptions: {escapeQuotes: 0, escapeSpecialCharacters: 0}` returns 200 but silently
drops both fields, and Android XML is not listed among the configurable formats at
<https://support.crowdin.com/parsers-configuration/>.

Thanks,
Adam
