# Generates the three Break-the-Pane cue WAVs into app/src/main/res/raw.
# Deterministic 44.1 kHz 16-bit mono; the committed binaries come from this script.
$ErrorActionPreference = "Stop"
$rate = 44100
$outDir = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Wav([string]$name, [double[]]$samples) {
    $path = Join-Path $outDir $name
    $bytes = [byte[]]::new(44 + $samples.Length * 2)
    $enc = [Text.Encoding]::ASCII
    $enc.GetBytes("RIFF").CopyTo($bytes, 0)
    [BitConverter]::GetBytes([uint32]($bytes.Length - 8)).CopyTo($bytes, 4)
    $enc.GetBytes("WAVE").CopyTo($bytes, 8)
    $enc.GetBytes("fmt ").CopyTo($bytes, 12)
    [BitConverter]::GetBytes([uint32]16).CopyTo($bytes, 16)
    [BitConverter]::GetBytes([uint16]1).CopyTo($bytes, 20)   # PCM
    [BitConverter]::GetBytes([uint16]1).CopyTo($bytes, 22)   # mono
    [BitConverter]::GetBytes([uint32]$rate).CopyTo($bytes, 24)
    [BitConverter]::GetBytes([uint32]($rate * 2)).CopyTo($bytes, 28)
    [BitConverter]::GetBytes([uint16]2).CopyTo($bytes, 32)
    [BitConverter]::GetBytes([uint16]16).CopyTo($bytes, 34)
    $enc.GetBytes("data").CopyTo($bytes, 36)
    [BitConverter]::GetBytes([uint32]($samples.Length * 2)).CopyTo($bytes, 40)
    for ($i = 0; $i -lt $samples.Length; $i++) {
        $v = [int][Math]::Max(-32768, [Math]::Min(32767, [int]($samples[$i] * 32767)))
        [BitConverter]::GetBytes([int16]$v).CopyTo($bytes, 44 + $i * 2)
    }
    [IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "wrote $path"
}

# Ding (success): 880 + 1760 Hz partials, 0.55 s exponential decay.
$n = [int](0.55 * $rate); $ding = [double[]]::new($n)
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate; $env = [Math]::Exp(-6.0 * $t)
    $ding[$i] = 0.6 * $env * ([Math]::Sin(2 * [Math]::PI * 880 * $t) + 0.5 * [Math]::Sin(2 * [Math]::PI * 1760 * $t))
}
Write-Wav "pane_success_ding.wav" $ding

# Fail: 150 Hz thud with pitch drop + noise tick, 0.4 s.
$n = [int](0.4 * $rate); $fail = [double[]]::new($n); $rnd = [System.Random]::new(42)
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate; $env = [Math]::Exp(-9.0 * $t)
    $f = 150.0 - 60.0 * $t
    $fail[$i] = 0.8 * $env * [Math]::Sin(2 * [Math]::PI * $f * $t) + 0.15 * $env * ($rnd.NextDouble() * 2 - 1)
}
Write-Wav "pane_fail.wav" $fail

# Glass break: burst of decaying high partials + noise crackle, 0.5 s.
$n = [int](0.5 * $rate); $glass = [double[]]::new($n); $rnd2 = [System.Random]::new(7)
$partials = New-Object 'System.Collections.Generic.List[double]'
for ($p = 0; $p -lt 18; $p++) {
    $partials.Add(1800.0 + 4200.0 * $rnd2.NextDouble())
    $partials.Add(0.35 * $rnd2.NextDouble())
    $partials.Add(18.0 + 40.0 * $rnd2.NextDouble())
}
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / $rate
    $v = 0.35 * [Math]::Exp(-7.0 * $t) * ($rnd2.NextDouble() * 2 - 1)
    for ($p = 0; $p -lt $partials.Count; $p += 3) {
        $v += $partials[$p + 1] * [Math]::Exp(-$partials[$p + 2] * $t) * [Math]::Sin(2 * [Math]::PI * $partials[$p] * $t)
    }
    $glass[$i] = $v
}
Write-Wav "pane_glass_break.wav" $glass
