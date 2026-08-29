using System.Diagnostics;

namespace WiiCompiled.Setup.Linux;

/// <summary>
/// Invokes Launcher/local-build.sh and turns its stdout into progress reports. Replaces
/// LocalBuildService.cs's hardcoded Windows PowerShell 5.1 invocation - there is no PowerShell
/// dependency here at all, just bash.
/// </summary>
internal static class BuildRunner
{
    public static async Task RunAsync(
        string workspace, string profile, string outputDir, string? baseOutputDir,
        string? retroDir, string? retroWfcOfflineDir, bool skipRetroWfcPayload,
        bool forceCleanBuild, string? translatorBin, IInstallReporter reporter, CancellationToken cancellationToken)
    {
        var script = Path.Combine(workspace, "Launcher", "local-build.sh");
        if (!File.Exists(script)) throw new FileNotFoundException("local-build.sh is missing", script);

        var startInfo = new ProcessStartInfo("bash")
        {
            WorkingDirectory = workspace,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        startInfo.ArgumentList.Add(script);
        startInfo.ArgumentList.Add("--profile"); startInfo.ArgumentList.Add(profile);
        startInfo.ArgumentList.Add("--output-dir"); startInfo.ArgumentList.Add(outputDir);
        if (!string.IsNullOrEmpty(baseOutputDir))
        {
            startInfo.ArgumentList.Add("--base-output-dir"); startInfo.ArgumentList.Add(baseOutputDir);
        }
        if (!string.IsNullOrEmpty(retroDir))
        {
            // Still forwarded to local-build.sh under its own internal name -
            // --retro-rewind-package-dir - matching LocalBuild.ps1's own -RetroRewindPackageDirectory.
            startInfo.ArgumentList.Add("--retro-rewind-package-dir"); startInfo.ArgumentList.Add(retroDir);
        }
        if (!string.IsNullOrEmpty(retroWfcOfflineDir))
        {
            startInfo.ArgumentList.Add("--retro-wfc-offline-dir"); startInfo.ArgumentList.Add(retroWfcOfflineDir);
        }
        if (skipRetroWfcPayload) startInfo.ArgumentList.Add("--skip-retro-wfc-payload");
        if (forceCleanBuild) startInfo.ArgumentList.Add("--force-clean-build");
        if (!string.IsNullOrEmpty(translatorBin))
        {
            startInfo.ArgumentList.Add("--translator-bin"); startInfo.ArgumentList.Add(translatorBin);
        }

        using var process = new Process { StartInfo = startInfo };
        var window = new BuildProgressWindow(reporter, InstallStages.Build, start: 6, end: 96);

        process.OutputDataReceived += (_, e) => { if (e.Data is not null) window.Observe(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (e.Data is not null) reporter.Diagnostic(e.Data); };

        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        try
        {
            await process.WaitForExitAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            KillProcessTree(process);
            throw;
        }

        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException($"local-build.sh failed (exit {process.ExitCode}). See diagnostics above.");
        }
    }

    private static void KillProcessTree(Process process)
    {
        try { process.Kill(entireProcessTree: true); } catch { /* best-effort */ }
    }
}
