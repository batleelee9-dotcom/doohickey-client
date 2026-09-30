# Measures a launcher's time-to-window, idle RAM and idle CPU across its whole
# process tree (a Tauri/Electron app is several processes, not one).
#   .\scripts\bench.ps1 -Exe path\to\quartz.exe
#   .\scripts\bench.ps1 -Exe "$env:LOCALAPPDATA\Programs\lunarclient\Lunar Client.exe" -WindowClass Chrome_WidgetWin_1
# "private WS" matches Task Manager's Memory column; "total WS" also counts shared DLL pages.
param([Parameter(Mandatory)][string]$Exe, [string]$WindowClass = "Tauri Window", [int]$Runs = 3, [int]$IdleSeconds = 10)

Add-Type @"
using System; using System.Text; using System.Runtime.InteropServices;
public static class W {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc f, IntPtr l);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
  // Only the real app window counts; tao's message-only helper windows report visible too.
  public static bool AppWindowVisible(uint pid, string cls) {
    bool found = false;
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p);
      if (p == pid && IsWindowVisible(h)) { var sb = new StringBuilder(64); GetClassName(h, sb, 64);
        if (sb.ToString() == cls) found = true; }
      return !found; }, IntPtr.Zero);
    return found;
  }
}
"@

function Get-Tree([int]$RootId) {
  $all = Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId, Name
  $ids = [System.Collections.Generic.List[int]]::new(); $ids.Add($RootId)
  for ($i = 0; $i -lt $ids.Count; $i++) {
    $all | Where-Object { $_.ParentProcessId -eq $ids[$i] } | ForEach-Object { $ids.Add([int]$_.ProcessId) }
  }
  $ids
}

function Get-PrivateWS([int[]]$Ids) {
  # Task Manager's "Memory" column is the private working set; it's only exposed as a perf counter.
  $samples = (Get-Counter '\Process(*)\ID Process', '\Process(*)\Working Set - Private' -ErrorAction SilentlyContinue).CounterSamples
  $pidByInstance = @{}
  $samples | Where-Object { $_.Path -like '*\id process' } | ForEach-Object { $pidByInstance[$_.InstanceName + '|' + ($_.Path -replace '\\id process$', '')] = [int]$_.CookedValue }
  $total = 0
  $samples | Where-Object { $_.Path -like '*\working set - private' } | ForEach-Object {
    $key = $_.InstanceName + '|' + ($_.Path -replace '\\working set - private$', '')
    if ($Ids -contains $pidByInstance[$key]) { $total += $_.CookedValue }
  }
  $total
}

for ($run = 1; $run -le $Runs; $run++) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $p = Start-Process -FilePath $Exe -PassThru
  do { Start-Sleep -Milliseconds 5 } until ([W]::AppWindowVisible([uint32]$p.Id, $WindowClass) -or $sw.ElapsedMilliseconds -gt 15000)
  $visibleMs = $sw.ElapsedMilliseconds

  Start-Sleep -Seconds 3  # let startup work finish before measuring idle
  $ids = Get-Tree $p.Id
  $cpu0 = 0; Get-Process -Id $ids -ErrorAction SilentlyContinue | ForEach-Object { $cpu0 += $_.TotalProcessorTime.TotalMilliseconds }
  Start-Sleep -Seconds $IdleSeconds
  $procs = Get-Process -Id $ids -ErrorAction SilentlyContinue
  $cpu1 = 0; $procs | ForEach-Object { $cpu1 += $_.TotalProcessorTime.TotalMilliseconds }
  $cpuPct = ($cpu1 - $cpu0) / ($IdleSeconds * 1000 * [Environment]::ProcessorCount) * 100
  $ws = ($procs | Measure-Object WorkingSet64 -Sum).Sum
  $pws = Get-PrivateWS $ids

  "run {0}: window visible {1} ms | {2} processes | private WS {3:N1} MB | total WS {4:N1} MB | idle CPU {5:N2}% of machine" -f `
    $run, $visibleMs, $ids.Count, ($pws / 1MB), ($ws / 1MB), $cpuPct
  Stop-Process -Id $p.Id
  Start-Sleep -Seconds 2
}

