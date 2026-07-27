<#
.SYNOPSIS
Strips Android-style \' and \" escapes from the Compose Multiplatform locale XML.

.DESCRIPTION
Crowdin parses common/src/commonMain/composeResources as file type "android" and its
exporter escapes apostrophes and double quotes on every download. Compose Resources
does no unescaping (the build-time converter handles \n and \uXXXX only, then the
value is base64'd verbatim into the .cvr and decoded straight to the UI), so those
backslashes render on screen.

Run this after every `crowdin download`.

android/src/main/res is deliberately untouched: AAPT requires the escapes there.

.PARAMETER Check
Report what would change and exit 1 if anything would, without writing. For CI.
#>
[CmdletBinding()]
param(
	[string]$Root,
	[switch]$Check
)

$ErrorActionPreference = 'Stop'

if (-not $Root) {
	$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
}

$localeGlob = Join-Path $Root 'common\src\commonMain\composeResources'
if (-not (Test-Path $localeGlob)) {
	throw "composeResources not found under $Root"
}

# \' and \" only, and not when the backslash is itself escaped (\\' is a real backslash).
$pattern = "(?<!\\)\\(['""])"

$files = Get-ChildItem $localeGlob -Directory |
	Where-Object { $_.Name -like 'values-*' } |
	ForEach-Object { Get-ChildItem $_.FullName -Filter '*.xml' -File }

$totalFixes = 0
$touched = @()

foreach ($file in $files) {
	$original = [IO.File]::ReadAllText($file.FullName)
	$count = [regex]::Matches($original, $pattern).Count
	if ($count -eq 0) { continue }

	$totalFixes += $count
	$touched += [pscustomobject]@{
		Path  = $file.FullName.Substring($Root.ToString().Length).TrimStart('\')
		Count = $count
	}

	if (-not $Check) {
		$fixed = [regex]::Replace($original, $pattern, '$1')
		# Preserve the file's existing encoding: these are UTF-8 without BOM.
		[IO.File]::WriteAllText($file.FullName, $fixed, (New-Object Text.UTF8Encoding $false))
	}
}

foreach ($t in $touched) {
	Write-Output ("{0,4}  {1}" -f $t.Count, $t.Path)
}

if ($totalFixes -eq 0) {
	Write-Output 'No Android-style quote escapes found.'
	exit 0
}

if ($Check) {
	Write-Output ""
	Write-Output "$totalFixes escape(s) across $($touched.Count) file(s) need stripping. Run without -Check to fix."
	exit 1
}

Write-Output ""
Write-Output "Stripped $totalFixes escape(s) across $($touched.Count) file(s)."
