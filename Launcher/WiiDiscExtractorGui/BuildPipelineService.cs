using System;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using WiiDiscExtractor;

namespace WiiCompiled.Android.Builder;

public record BuildOptions(
    string WorkspaceRoot,
    string? DiscImagePath,
    bool IsRelease,
    bool FastBuild,
    bool ForceReTranslate,
    bool AutoInstall,
    string? SelectedDevice
);

public record BuildProgress(int Step, int TotalSteps, int Percentage, string Message);

public sealed class BuildPipelineService
{
    private readonly Action<string> _log;
    private readonly Action<BuildProgress> _progress;

    public BuildPipelineService(Action<string> log, Action<BuildProgress> progress)
    {
        _log = log;
        _progress = progress;
    }

    public async Task<string> RunAsync(BuildOptions options, ToolchainStatus toolchain, CancellationToken ct)
    {
        string root = options.WorkspaceRoot;
        string assetsDir = Path.Combine(root, "Assets");
        string dolPath = Path.Combine(assetsDir, "main.dol");
        string relPath = Path.Combine(assetsDir, "StaticR.rel");
        string generatedDir = Path.Combine(root, "generated");
        string shardsCmake = Path.Combine(generatedDir, "build_shards", "shards.cmake");
        string shardArchive = Path.Combine(root, "android", "app", "src", "main", "jniLibs", "arm64-v8a", "libmkw_base_shared.a");
        string localProps = Path.Combine(root, "android", "local.properties");
        string manifest = Path.Combine(root, "projects", "mkwii", "recomp.yml");

        // -------------------------------------------------------------
        // Step 1: Disc Extraction & Assets Staging
        // -------------------------------------------------------------
        _progress(new BuildProgress(1, 4, 0, "Checking game assets..."));
        _log("=== Step 1/4: Game Disc Assets ===");

        Directory.CreateDirectory(assetsDir);

        if (!string.IsNullOrWhiteSpace(options.DiscImagePath))
        {
            _log($"Extracting from disc image: {options.DiscImagePath}");
            _progress(new BuildProgress(1, 4, 10, "Extracting disc image (RMCP01)..."));

            string tempExtractDir = Path.Combine(root, ".disc_extract_tmp");
            try
            {
                if (Directory.Exists(tempExtractDir))
                    Directory.Delete(tempExtractDir, recursive: true);
                Directory.CreateDirectory(tempExtractDir);

                using (var disc = new WiiDiscImage(options.DiscImagePath))
                {
                    var report = await Task.Run(() => disc.Extract(
                        tempExtractDir,
                        new Progress<ProgressRecord>(p =>
                        {
                            _progress(new BuildProgress(1, 4, Math.Min(25, 5 + p.Percent / 5), $"Extracting: {p.Message}"));
                        }),
                        ct
                    ), ct);

                    if (!report.Success)
                        throw new InvalidOperationException($"Disc extraction failed: {report.Error}");

                    _log($"Extraction report: Game ID = {report.GameId}, Files = {report.FilesExtracted}");
                    _log($"  main.dol SHA256: {report.DolSha256} (Verified: {report.DolVerified})");
                    _log($"  StaticR.rel SHA256: {report.RelSha256} (Verified: {report.RelVerified})");

                    if (!report.DolVerified || !report.RelVerified)
                        _log("[WARNING] Pinned RMCP01 PAL hash mismatch! Continuing if compatible...");

                    // Copy extracted files to Assets
                    string extractedDol = Path.Combine(tempExtractDir, "sys", "main.dol");
                    string extractedRel = Path.Combine(tempExtractDir, "files", "staticr.rel");

                    if (!File.Exists(extractedDol))
                        throw new FileNotFoundException("Extracted sys/main.dol was not found.");
                    if (!File.Exists(extractedRel))
                        throw new FileNotFoundException("Extracted files/staticr.rel was not found.");

                    File.Copy(extractedDol, dolPath, overwrite: true);
                    File.Copy(extractedRel, relPath, overwrite: true);
                    _log("Staged main.dol and StaticR.rel into Assets/");
                }
            }
            finally
            {
                try
                {
                    if (Directory.Exists(tempExtractDir))
                        Directory.Delete(tempExtractDir, recursive: true);
                }
                catch { }
            }
        }
        else
        {
            if (!File.Exists(dolPath) || !File.Exists(relPath))
            {
                throw new InvalidOperationException(
                    "No disc image was specified and Assets/ is missing main.dol or StaticR.rel. " +
                    "Please select your Mario Kart Wii PAL (.iso / .wbfs) disc dump.");
            }

            _log("Using existing game assets in Assets/ (main.dol + StaticR.rel).");
            VerifyExistingAsset(dolPath, WiiDiscImage.ExpectedDolSha256, "main.dol");
            VerifyExistingAsset(relPath, WiiDiscImage.ExpectedRelSha256, "StaticR.rel");
        }

        _progress(new BuildProgress(1, 4, 25, "Game assets ready."));

        // -------------------------------------------------------------
        // Step 2: Translation (PowerPC -> C++ Shards)
        // -------------------------------------------------------------
        _progress(new BuildProgress(2, 4, 25, "Checking translation..."));
        _log("\n=== Step 2/4: PowerPC Ahead-Of-Time Translation ===");

        bool needTranslation = options.ForceReTranslate || !File.Exists(shardsCmake);
        if (needTranslation)
        {
            _log("Translating PowerPC code into C++ shards (this may take 1-3 minutes)...");
            _progress(new BuildProgress(2, 4, 30, "Translating PowerPC game graph..."));

            string translatorCsproj = Path.Combine(root, "translator", "src", "Translator.Cli", "Translator.Cli.csproj");
            string translatorDll = Path.Combine(root, "translator", "src", "Translator.Cli", "bin", "Release", "net8.0", "Translator.Cli.dll");

            if (!File.Exists(translatorDll))
            {
                _log("Building Translator.Cli...");
                await RunProcessAsync("dotnet", $"build \"{translatorCsproj}\" -c Release --nologo -v q", root, ct);
            }

            _log("Executing: translate-recursive 0x800060A4...");
            string metadataOut = Path.Combine(generatedDir, "base_translation_output.json");
            await RunProcessAsync("dotnet", $"\"{translatorDll}\" translate-recursive 0x800060A4 --project \"{manifest}\" --output-metadata \"{metadataOut}\"", root, ct);

            _progress(new BuildProgress(2, 4, 40, "Generating data initializers..."));
            _log("Executing: generate-data-init...");
            await RunProcessAsync("dotnet", $"\"{translatorDll}\" generate-data-init --project \"{manifest}\"", root, ct);

            _progress(new BuildProgress(2, 4, 48, "Emitting CMake shard graph..."));
            _log("Executing: emit-build-shards...");
            await RunProcessAsync("dotnet", $"\"{translatorDll}\" emit-build-shards --project \"{manifest}\"", root, ct);

            _log("Translation completed successfully.");
        }
        else
        {
            _log("Shard CMake graph already exists. Skipping translation (fast path).");
        }

        _progress(new BuildProgress(2, 4, 50, "Shards generated."));

        // -------------------------------------------------------------
        // Step 3: Compiling C++ Shards -> libmkw_base_shared.a
        // -------------------------------------------------------------
        _progress(new BuildProgress(3, 4, 50, "Compiling native ARM64 shards..."));
        _log("\n=== Step 3/4: Compiling C++ Shards (libmkw_base_shared.a) ===");

        bool needShardCompile = !options.FastBuild || !File.Exists(shardArchive);
        if (needShardCompile)
        {
            _log("Building native ARM64 static archive via NDK toolchain & Ninja...");
            string shardsCmakeDir = Path.Combine(root, "cmake", "shards");
            string buildArm64Dir = Path.Combine(shardsCmakeDir, "build_arm64");
            string outDir = Path.Combine(root, "android", "app", "src", "main", "jniLibs", "arm64-v8a");
            Directory.CreateDirectory(outDir);

            string ndk = toolchain.NdkPath ?? throw new InvalidOperationException("Android NDK is required.");
            string cmake = toolchain.CMakePath ?? "cmake.exe";
            string ninja = toolchain.NinjaPath ?? "ninja.exe";

            string toolchainFile = Path.Combine(ndk, "build", "cmake", "android.toolchain.cmake");

            string cmakeArgs =
                $"-H\"{shardsCmakeDir}\" " +
                $"-B\"{buildArm64Dir}\" " +
                "-GNinja " +
                $"-DCMAKE_MAKE_PROGRAM=\"{ninja}\" " +
                "-DCMAKE_SYSTEM_NAME=Android " +
                "-DCMAKE_SYSTEM_VERSION=28 " +
                "-DANDROID_PLATFORM=android-28 " +
                "-DANDROID_ABI=arm64-v8a " +
                "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a " +
                $"-DANDROID_NDK=\"{ndk}\" " +
                $"-DCMAKE_ANDROID_NDK=\"{ndk}\" " +
                $"-DCMAKE_TOOLCHAIN_FILE=\"{toolchainFile}\" " +
                "-DCMAKE_BUILD_TYPE=Release " +
                "-DANDROID_STL=c++_shared";

            _log("Configuring Shards CMake...");
            await RunProcessAsync(cmake, cmakeArgs, root, ct);

            _progress(new BuildProgress(3, 4, 60, "Compiling shards with Ninja..."));
            _log("Compiling mkw_base_shared with Ninja...");
            await RunProcessAsync(ninja, $"-C \"{buildArm64Dir}\" mkw_base_shared", root, ct);

            string builtArchive = Path.Combine(buildArm64Dir, "libmkw_base_shared.a");
            if (!File.Exists(builtArchive))
                throw new FileNotFoundException("Ninja build succeeded but libmkw_base_shared.a was not generated.");

            File.Copy(builtArchive, shardArchive, overwrite: true);
            _log($"Archive written: {shardArchive}");
        }
        else
        {
            _log($"Using existing prebuilt shard archive: {shardArchive}");
        }

        _progress(new BuildProgress(3, 4, 75, "Native shards compiled."));

        // -------------------------------------------------------------
        // Step 4: Build APK (Gradle)
        // -------------------------------------------------------------
        _progress(new BuildProgress(4, 4, 75, "Packaging Android APK..."));
        string variantName = options.IsRelease ? "Release" : "Debug";
        string gradleTask = options.IsRelease ? "assembleRelease" : "assembleDebug";
        string apkPath = Path.Combine(root, "android", "app", "build", "outputs", "apk",
            options.IsRelease ? "release" : "debug",
            options.IsRelease ? "app-release.apk" : "app-debug.apk");

        _log($"\n=== Step 4/4: Packaging Android {variantName} APK ===");

        // Write local.properties for Gradle
        if (!string.IsNullOrEmpty(toolchain.SdkPath))
        {
            var sb = new StringBuilder();
            sb.AppendLine($"sdk.dir={toolchain.SdkPath.Replace("\\", "/")}");
            if (!string.IsNullOrEmpty(toolchain.NdkPath))
                sb.AppendLine($"ndk.dir={toolchain.NdkPath.Replace("\\", "/")}");
            File.WriteAllText(localProps, sb.ToString(), Encoding.ASCII);
        }

        string androidDir = Path.Combine(root, "android");
        string gradlew = Path.Combine(androidDir, "gradlew.bat");

        _log($"Running Gradle: gradlew.bat {gradleTask}...");
        await RunProcessAsync("cmd.exe", $"/c \"\"{gradlew}\" {gradleTask}\"", androidDir, ct);

        if (!File.Exists(apkPath))
            throw new FileNotFoundException($"Gradle finished but {apkPath} was not found.");

        var fi = new FileInfo(apkPath);
        _log($"\nSUCCESS: {variantName} APK built!");
        _log($"Location: {apkPath}");
        _log($"Size: {fi.Length:N0} bytes ({fi.Length / (1024.0 * 1024.0):F2} MB)");

        // -------------------------------------------------------------
        // Step 5: Optional ADB Install
        // -------------------------------------------------------------
        if (options.AutoInstall && toolchain.AdbPath != null)
        {
            _progress(new BuildProgress(4, 4, 95, "Installing APK to connected phone..."));
            _log($"\nInstalling to Android device via ADB...");
            string devArg = !string.IsNullOrEmpty(options.SelectedDevice) ? $"-s {options.SelectedDevice} " : "";
            await RunProcessAsync(toolchain.AdbPath, $"{devArg}install -r \"{apkPath}\"", root, ct);
            _log("APK installed successfully on device!");
        }

        _progress(new BuildProgress(4, 4, 100, "Build Complete!"));
        return apkPath;
    }

    private void VerifyExistingAsset(string path, string expectedSha, string label)
    {
        using var sha = SHA256.Create();
        using var fs = File.OpenRead(path);
        byte[] hash = sha.ComputeHash(fs);
        string hex = Convert.ToHexString(hash).ToLowerInvariant();
        bool match = hex.Equals(expectedSha, StringComparison.OrdinalIgnoreCase);
        _log($"  {label} SHA256: {hex} (Verified: {match})");
    }

    private async Task RunProcessAsync(string exe, string arguments, string workingDir, CancellationToken ct)
    {
        var psi = new ProcessStartInfo
        {
            FileName = exe,
            Arguments = arguments,
            WorkingDirectory = workingDir,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        using var proc = new Process { StartInfo = psi };

        proc.OutputDataReceived += (_, e) =>
        {
            if (e.Data != null) _log(e.Data);
        };

        proc.ErrorDataReceived += (_, e) =>
        {
            if (e.Data != null) _log($"[ERR] {e.Data}");
        };

        proc.Start();
        proc.BeginOutputReadLine();
        proc.BeginErrorReadLine();

        try
        {
            await proc.WaitForExitAsync(ct);
        }
        catch (OperationCanceledException)
        {
            try
            {
                proc.Kill(entireProcessTree: true);
            }
            catch { }
            throw;
        }

        if (proc.ExitCode != 0)
        {
            throw new InvalidOperationException($"Command failed with exit code {proc.ExitCode}: {exe} {arguments}");
        }
    }
}
