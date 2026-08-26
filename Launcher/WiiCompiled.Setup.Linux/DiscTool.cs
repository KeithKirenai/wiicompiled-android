using System.Security.Cryptography;
using System.Text.Json;

namespace WiiCompiled.Setup.Linux;

/// <summary>
/// Validates and extracts the user's own Mario Kart Wii disc via the system-installed
/// `dolphin-tool` (required prerequisite, per the project's scoping decision to depend on system
/// tools rather than bundling one) - the Linux analogue of the Windows installer shelling out to a
/// bundled DolphinTool.exe.
/// </summary>
internal static class DiscTool
{
    public static async Task ValidateAndExtractAsync(
        string isoPath, ProjectManifest manifest, string assetsDirectory,
        IInstallReporter reporter, CancellationToken cancellationToken)
    {
        reporter.Progress(InstallStages.ExtractDisc, "Reading the disc header", 2);
        var headerJson = await RunAsync(["header", "-i", isoPath, "-j"], cancellationToken);
        using var header = JsonDocument.Parse(headerJson);
        var gameId = header.RootElement.GetProperty("game_id").GetString();
        if (!string.Equals(gameId, manifest.GameId, StringComparison.Ordinal))
        {
            throw new InvalidOperationException(
                $"This disc is '{gameId}', not the expected '{manifest.GameId}' (Mario Kart Wii, region {manifest.Region}). " +
                "Only your own legally-owned copy of that exact game/region can be used.");
        }

        reporter.Progress(InstallStages.ExtractDisc, "Extracting the disc image", 4);
        var scratch = Path.Combine(Path.GetTempPath(), "wiicompiled-disc-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(scratch);
        try
        {
            await RunAsync(["extract", "-i", isoPath, "-g", "-o", scratch, "-q"], cancellationToken);

            var dolPath = Path.Combine(scratch, "DATA", "sys", "main.dol");
            var relPath = Path.Combine(scratch, "DATA", "files", "rel", "StaticR.rel");
            if (!File.Exists(dolPath)) throw new FileNotFoundException("dolphin-tool did not produce main.dol", dolPath);
            if (!File.Exists(relPath)) throw new FileNotFoundException("dolphin-tool did not produce StaticR.rel", relPath);

            var dolSha = Sha256Of(dolPath);
            var relSha = Sha256Of(relPath);
            if (!string.Equals(dolSha, manifest.DolSha256, StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    $"main.dol sha256 mismatch: expected {manifest.DolSha256}, got {dolSha}. " +
                    "This disc revision does not match what this project's manifest is pinned to.");
            }
            if (!string.Equals(relSha, manifest.RelSha256, StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    $"StaticR.rel sha256 mismatch: expected {manifest.RelSha256}, got {relSha}. " +
                    "This disc revision does not match what this project's manifest is pinned to.");
            }

            Directory.CreateDirectory(assetsDirectory);
            File.Copy(dolPath, Path.Combine(assetsDirectory, "main.dol"), overwrite: true);
            File.Copy(relPath, Path.Combine(assetsDirectory, "StaticR.rel"), overwrite: true);
            reporter.Progress(InstallStages.ExtractDisc, "Disc validated and extracted", 6);
        }
        finally
        {
            try { Directory.Delete(scratch, recursive: true); } catch { /* best-effort cleanup */ }
        }
    }

    private static string Sha256Of(string path)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
    }

    private static async Task<string> RunAsync(string[] arguments, CancellationToken cancellationToken)
    {
        var startInfo = new System.Diagnostics.ProcessStartInfo("dolphin-tool")
        {
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        foreach (var argument in arguments) startInfo.ArgumentList.Add(argument);

        using var process = System.Diagnostics.Process.Start(startInfo)
            ?? throw new InvalidOperationException("Failed to start dolphin-tool. Is it installed and on PATH?");
        var stdout = await process.StandardOutput.ReadToEndAsync(cancellationToken);
        var stderr = await process.StandardError.ReadToEndAsync(cancellationToken);
        await process.WaitForExitAsync(cancellationToken);
        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException(
                $"dolphin-tool {string.Join(' ', arguments)} failed (exit {process.ExitCode}): {stderr}");
        }
        return stdout;
    }
}
