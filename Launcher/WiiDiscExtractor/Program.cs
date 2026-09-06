using System;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using WiiDiscExtractor;

namespace WiiDiscExtractor.Cli;

internal static class Program
{
    // Supported container extensions the library handles natively.
    private static readonly string[] SupportedExtensions =
    { ".iso", ".gcm", ".gcz", ".ciso", ".chd", ".wbfs", ".wia", ".rvz" };

    public static int Main(string[] args)
    {
        var options = ParseOptions(args);
        if (options.ShowHelp)
        {
            PrintHelp();
            return 0;
        }
        if (options.ShowVersion)
        {
            Console.WriteLine("WiiDiscExtractor 1.0.0");
            return 0;
        }

        try
        {
            return Run(options);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"ERROR: {ex.Message}");
            return 1;
        }
    }

    private static int Run(Options options)
    {
        string imagePath = options.ImagePath;
        if (!File.Exists(imagePath))
        {
            Console.Error.WriteLine($"Image not found: {imagePath}");
            return 1;
        }

        string ext = Path.GetExtension(imagePath).ToLowerInvariant();
        bool knownContainer = Array.Exists(SupportedExtensions, e => e.Equals(ext, StringComparison.OrdinalIgnoreCase));
        if (!knownContainer)
        {
            Console.Error.WriteLine($"Unrecognized extension '{ext}'. Supported: {string.Join(", ", SupportedExtensions)}");
            Console.Error.WriteLine("Proceeding anyway — the library will inspect the magic bytes.");
        }

        // Decide extraction target: explicit -out, current directory, or auto next to the image.
        string outputRoot;
        if (options.OutputDir != null)
        {
            outputRoot = Path.GetFullPath(options.OutputDir);
        }
        else if (options.AssetsDir != null)
        {
            outputRoot = Path.GetFullPath(options.AssetsDir);
        }
        else
        {
            // Default: create a folder named <imagename>_extracted alongside the image.
            string stem = Path.GetFileNameWithoutExtension(imagePath);
            outputRoot = Path.Combine(Path.GetDirectoryName(imagePath)!, stem + "_extracted");
        }

        Console.WriteLine($"Image      : {imagePath}");
        Console.WriteLine($"Format     : {(knownContainer ? ext : "auto-detect")}");
        Console.WriteLine($"Output     : {outputRoot}");
        Console.WriteLine();

        using var image = new WiiDiscImage(imagePath);
        var report = image.Extract(
        outputRoot,
        new Progress<ProgressRecord>(p =>
        {
            string bar = new string('#', p.Percent / 2) + new string('-', 50 - p.Percent / 2);
            Console.Write($"\r[{bar}] {p.Percent,3:D2}%  {p.Message}");
        }),
        options.CancellationToken);

        Console.WriteLine();
        Console.WriteLine();

        if (!report.Success)
        {
            Console.Error.WriteLine($"Extraction failed: {report.Error}");
            return 1;
        }

        Console.WriteLine("--- Extraction report ---");
        Console.WriteLine($"Game ID    : {report.GameId}");
        Console.WriteLine($"Files found in FST: {report.TotalFilesInFst}");
        Console.WriteLine($"Files extracted   : {report.FilesExtracted}");
        Console.WriteLine($"Bytes extracted   : {report.BytesExtracted:N0}");
        Console.WriteLine();
        Console.WriteLine("main.dol");
        Console.WriteLine($"  path    : {Path.Combine(outputRoot, "sys", "main.dol")}");
        Console.WriteLine($"  sha256  : {report.DolSha256}");
        Console.WriteLine($"  verified: {(report.DolVerified ? "MATCH (RMCP01 PAL)" : "MISMATCH — wrong disc or corrupted image?")}");
        Console.WriteLine();
        Console.WriteLine("StaticR.rel");
        Console.WriteLine($"  path    : {Path.Combine(outputRoot, "files", "staticr.rel")}");
        Console.WriteLine($"  sha256  : {report.RelSha256}");
        Console.WriteLine($"  verified: {(report.RelVerified ? "MATCH (RMCP01 PAL)" : "MISMATCH — wrong disc or corrupted image?")}");
        Console.WriteLine();

        if (!report.DolVerified || !report.RelVerified)
        {
            Console.WriteLine("WARNING: one or both hashes did not match the pinned RMCP01 values.");
            Console.WriteLine("If this is your own PAL disc, the image may be a different revision.");
        }

        // If the caller wants a staging-ready layout (main.dol + StaticR.rel side by side),
        // copy them next to each other into the output root when -staging was requested.
        if (options.Staging)
        {
            string stagingDir = outputRoot;
            string dolSrc = Path.Combine(outputRoot, "sys", "main.dol");
            string relSrc = Path.Combine(outputRoot, "files", "staticr.rel");

            if (File.Exists(dolSrc))
                File.Copy(dolSrc, Path.Combine(stagingDir, "main.dol"), overwrite: true);
            if (File.Exists(relSrc))
                File.Copy(relSrc, Path.Combine(stagingDir, "StaticR.rel"), overwrite: true);

            Console.WriteLine($"Staging copy written to: {stagingDir}");
        }

        return 0;
    }

    // --- CLI parsing -----------------------------------------------------------

    private static Options ParseOptions(string[] args)
    {
        var o = new Options();
        for (int i = 0; i < args.Length; i++)
        {
            string a = args[i];
            switch (a.ToLowerInvariant())
            {
                case "-h":
                case "--help":
                    o.ShowHelp = true;
                    break;
                case "-v":
                case "--version":
                    o.ShowVersion = true;
                    break;
                case "--image":
                case "-i":
                    i++;
                    if (i >= args.Length) throw new ArgumentException("Missing value for -i / --image");
                    o.ImagePath = args[i];
                    break;
                case "--output":
                case "-o":
                    i++;
                    if (i >= args.Length) throw new ArgumentException("Missing value for -o / --output");
                    o.OutputDir = args[i];
                    break;
                case "--assets":
                case "-a":
                    i++;
                    if (i >= args.Length) throw new ArgumentException("Missing value for -a / --assets");
                    o.AssetsDir = args[i];
                    break;
                case "--staging":
                    o.Staging = true;
                    break;
                case "--cancel-after":
                    i++;
                    if (i >= args.Length) throw new ArgumentException("Missing value for --cancel-after");
                    if (!int.TryParse(args[i], out int cancelSecs) || cancelSecs < 0)
                        throw new ArgumentException("Invalid --cancel-after value");
                    o.CancelAfterSeconds = cancelSecs;
                    break;
                default:
                    // Treat a lone positional argument as the image path.
                    if (a.StartsWith("-", StringComparison.Ordinal))
                        throw new ArgumentException($"Unknown option: {a}");
                    if (o.ImagePath == null)
                        o.ImagePath = a;
                    else
                        throw new ArgumentException($"Unexpected extra argument: {a}");
                    break;
            }
        }

        if (o.ImagePath == null)
            throw new ArgumentException("No image specified. Pass -i <path> or a positional path.");

        return o;
    }

    private static void PrintHelp()
    {
        Console.WriteLine("WiiDiscExtractor — extract main.dol + StaticR.rel from a Wii ISO/WBFS image.");
        Console.WriteLine();
        Console.WriteLine("Usage:");
        Console.WriteLine("  WiiDiscExtractor <image.iso>");
        Console.WriteLine("  WiiDiscExtractor -i <image.iso> -o <output_dir>");
        Console.WriteLine("  WiiDiscExtractor -i <image.iso> -a <assets_dir> --staging");
        Console.WriteLine();
        Console.WriteLine("Options:");
        Console.WriteLine("  -i, --image <path>     Path to the Wii disc image (ISO, WBFS, GCM, GCZ, CISO, CHD, WIA, RVZ).");
        Console.WriteLine("  -o, --output <dir>     Directory to extract the full game tree into (sys/ + files/).");
        Console.WriteLine("  -a, --assets <dir>     Like --output, but also copies main.dol + StaticR.rel side-by-side");
        Console.WriteLine("                          into the directory for the android-bootstrap -DiscSource flow.");
        Console.WriteLine("  --staging              With -a/--assets, copy main.dol + StaticR.rel into the assets dir.");
        Console.WriteLine("  --cancel-after <secs>  Cancel extraction after this many seconds (testing/CI).");
        Console.WriteLine("  -h, --help             Show this help.");
        Console.WriteLine("  -v, --version          Show version.");
        Console.WriteLine();
        Console.WriteLine("If no -o or -a is given, the tree is written next to the image as <name>_extracted/.");
        Console.WriteLine("If no -i is given, the first positional argument is treated as the image path.");
    }

    private sealed class Options
    {
        public string? ImagePath { get; set; }
        public string? OutputDir { get; set; }
        public string? AssetsDir { get; set; }
        public bool Staging { get; set; }
        public bool ShowHelp { get; set; }
        public bool ShowVersion { get; set; }
        public int CancelAfterSeconds { get; set; }

        public CancellationToken CancellationToken { get; private set; } = CancellationToken.None;

        // Hook called by the extractor progress reporter; we wire a timer for --cancel-after here.
        internal void PrepareCancellation()
        {
            if (CancelAfterSeconds > 0)
            {
                var source = new CancellationTokenSource(CancelAfterSeconds * 1000);
                CancellationToken = source.Token;
            }
        }
    }
}
