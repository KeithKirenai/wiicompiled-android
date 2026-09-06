using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Threading;

namespace WiiDiscExtractor;

/// <summary>
/// A single progress record for reporting extraction progress.
/// </summary>
public record ProgressRecord(string Message, int Percent);

/// <summary>
/// Interface for reading from raw ISO or block-mapped WBFS.
/// </summary>
public interface IDiscSource : IDisposable
{
    byte[] ReadAt(long position, int length);
    byte[] ReadAt(long position, int length, byte[] dst, int dstOffset);
}

public sealed class FileDiscSource : IDiscSource
{
    private readonly FileStream _stream;

    public FileDiscSource(string path)
    {
        _stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read, 0x10000, FileOptions.SequentialScan);
    }

    public byte[] ReadAt(long position, int length)
    {
        var buf = new byte[length];
        ReadAt(position, length, buf, 0);
        return buf;
    }

    public byte[] ReadAt(long position, int length, byte[] dst, int dstOffset)
    {
        _stream.Seek(position, SeekOrigin.Begin);
        int total = 0;
        while (total < length)
        {
            int n = _stream.Read(dst, dstOffset + total, length - total);
            if (n == 0) throw new EndOfStreamException($"Unexpected EOF at 0x{position + total:X} while reading {length} bytes.");
            total += n;
        }
        return dst;
    }

    public void Dispose() => _stream.Dispose();
}

public sealed class WbfsDiscSource : IDiscSource
{
    private readonly IDiscSource _raw;
    private readonly long _wbfsSectorSize;
    private readonly int[] _blockTable;

    public WbfsDiscSource(IDiscSource raw, long wbfsSectorSize, int[] blockTable)
    {
        _raw = raw;
        _wbfsSectorSize = wbfsSectorSize;
        _blockTable = blockTable;
    }

    public byte[] ReadAt(long position, int length)
    {
        var buf = new byte[length];
        ReadAt(position, length, buf, 0);
        return buf;
    }

    public byte[] ReadAt(long position, int length, byte[] dst, int dstOffset)
    {
        long remaining = length;
        long curPos = position;
        int curDst = dstOffset;

        while (remaining > 0)
        {
            int blockIndex = (int)(curPos / _wbfsSectorSize);
            long offsetInBlock = curPos % _wbfsSectorSize;
            int chunk = (int)Math.Min(remaining, _wbfsSectorSize - offsetInBlock);

            if (blockIndex >= _blockTable.Length || _blockTable[blockIndex] == 0)
            {
                Array.Clear(dst, curDst, chunk);
            }
            else
            {
                int physBlock = _blockTable[blockIndex];
                long physPos = physBlock * _wbfsSectorSize + offsetInBlock;
                _raw.ReadAt(physPos, chunk, dst, curDst);
            }

            curPos += chunk;
            curDst += chunk;
            remaining -= chunk;
        }

        return dst;
    }

    public void Dispose() => _raw.Dispose();
}

/// <summary>
/// Reads a Wii disc image (ISO or WBFS) and extracts the Mario Kart Wii PAL
/// (RMCP01) game files — mainly main.dol and StaticR.rel — with SHA256
/// verification against the pinned reference hashes.
/// </summary>
public sealed class WiiDiscImage : IDisposable
{
    private readonly IDiscSource _source;

    public WiiDiscImage(string path)
    {
        if (string.IsNullOrWhiteSpace(path))
            throw new ArgumentException("Disc image path must be provided.", nameof(path));
        if (!File.Exists(path))
            throw new FileNotFoundException("Disc image not found.", path);

        ImagePath = Path.GetFullPath(path);
        var rawSource = new FileDiscSource(ImagePath);
        _source = WrapIfWbfs(rawSource);
    }

    public string ImagePath { get; }
    public string Extension => Path.GetExtension(ImagePath);

    // --- Wii constants for Mario Kart Wii PAL (RMCP01) ---
    public const string ExpectedGameId = "RMCP01";
    public const string ExpectedDolSha256 = "80d18895b39c63bd80f457398bfcbb91b7d16ac116a41a88967e954080155b05";
    public const string ExpectedRelSha256 = "16d9d146112541fefea701ecb5bc1a496f9d50e4a752fbb5b6778e7c6399f67d";
    public const uint WiiMagic = 0x5D1C9EA3;
    public const int ClusterSize = 0x8000;
    public const int ClusterDataSize = 0x7C00;
    public const int ClusterHeaderSize = 0x400;

    private static readonly byte[] RetailCommonKey = new byte[] {
        0xEB, 0xE4, 0x2A, 0x22, 0x5E, 0x85, 0x93, 0xE4,
        0x48, 0xD9, 0xC5, 0x45, 0x73, 0x81, 0xAA, 0xF7
    };

    private static IDiscSource WrapIfWbfs(IDiscSource raw)
    {
        var header = raw.ReadAt(0, 512);
        string magic = Encoding.ASCII.GetString(header, 0, 4);
        if (magic == "WBFS")
        {
            int hdSecSzS = header[8] & 0xFF;
            int wbfsSecSzS = header[9] & 0xFF;
            long hdSecSz = 1L << hdSecSzS;
            long wbfsSecSz = 1L << wbfsSecSzS;

            long discInfoOffset = hdSecSz;
            long wlbaOffset = discInfoOffset + 0x100;
            const int maxWbfsBlocks = 2241;
            var wlbaBytes = raw.ReadAt(wlbaOffset, maxWbfsBlocks * 2);

            var blockTable = new int[maxWbfsBlocks];
            for (int i = 0; i < maxWbfsBlocks; i++)
            {
                int b0 = wlbaBytes[i * 2] & 0xFF;
                int b1 = wlbaBytes[i * 2 + 1] & 0xFF;
                blockTable[i] = (b0 << 8) | b1;
            }

            return new WbfsDiscSource(raw, wbfsSecSz, blockTable);
        }

        return raw;
    }

    public byte[] ReadAt(long position, int length) => _source.ReadAt(position, length);
    public byte[] ReadAt(long position, int length, byte[] dst, int dstOffset) => _source.ReadAt(position, length, dst, dstOffset);

    // --- Big-endian helpers -----------------------------------------------------
    public static int ReadU32BE(byte[] data, int offset)
    {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    public static ushort ReadU16BE(byte[] data, int offset)
    {
        return (ushort)(((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    // --- Disc inspection --------------------------------------------------------
    /// <summary>
    /// Returns (gameId, magic) from the disc header.
    /// In both ISO and WBFS (translated), offset 0 is the Wii disc header.
    /// </summary>
    public (string GameId, uint Magic) Inspect()
    {
        var header = ReadAt(0, 0x20);
        string gameId = Encoding.ASCII.GetString(header, 0, 6).TrimEnd('\0');
        uint magic = (uint)ReadU32BE(header, 0x18);
        return (gameId, magic);
    }

    // --- Extraction -------------------------------------------------------------
    public ExtractionReport Extract(
        string outputRoot,
        IProgress<ProgressRecord>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var report = new ExtractionReport();
        try
        {
            progress?.Report(new ProgressRecord("Validating disc image...", 1));

            // 1. Inspect
            var (gameId, magic) = Inspect();
            report.GameId = gameId;
            if (magic != WiiMagic)
            {
                report.Error = $"Invalid Wii disc image (magic 0x{magic:X8}, expected 0x{WiiMagic:X8}).";
                return report;
            }
            if (gameId != ExpectedGameId)
            {
                report.Error = $"Unsupported game ID '{gameId}'. This tool extracts Mario Kart Wii PAL ({ExpectedGameId}) only.";
                return report;
            }

            // 2. Partition table at 0x40000
            progress?.Report(new ProgressRecord("Reading partition table...", 3));
            var partHeader = ReadAt(0x40000, 0x20);
            int numPartitions = ReadU32BE(partHeader, 0);
            long partTableOffset = (long)ReadU32BE(partHeader, 4) << 2;

            long dataPartitionOffset = -1;
            {
                var partEntries = ReadAt(partTableOffset, numPartitions * 8);
                for (int i = 0; i < numPartitions; i++)
                {
                    int offWords = ReadU32BE(partEntries, i * 8);
                    int type = ReadU32BE(partEntries, i * 8 + 4);
                    long partOffset = (long)offWords << 2;
                    if (type == 0)
                    {
                        dataPartitionOffset = partOffset;
                        break;
                    }
                }
            }

            if (dataPartitionOffset < 0)
            {
                report.Error = "Data partition (type 0) not found on disc.";
                return report;
            }

            cancellationToken.ThrowIfCancellationRequested();

            // 3. Ticket + title key decryption
            progress?.Report(new ProgressRecord("Decrypting partition keys...", 5));
            var ticketBytes = ReadAt(dataPartitionOffset, 0x2A4);
            var titleKeyEnc = new byte[16];
            Array.Copy(ticketBytes, 0x1BF, titleKeyEnc, 0, 16);

            var ticketId = new byte[8];
            Array.Copy(ticketBytes, 0x1DC, ticketId, 0, 8);
            var titleKeyIv = new byte[16];
            Array.Copy(ticketId, 0, titleKeyIv, 0, 8);

            byte[] titleKeySpec;
            using (var aes = Aes.Create())
            {
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.None;
                aes.Key = RetailCommonKey;
                aes.IV = titleKeyIv;
                using var decryptor = aes.CreateDecryptor();
                titleKeySpec = decryptor.TransformFinalBlock(titleKeyEnc, 0, 16);
            }

            cancellationToken.ThrowIfCancellationRequested();

            // 4. Prepare output directories
            var sysDir = Path.Combine(outputRoot, "sys");
            var filesDir = Path.Combine(outputRoot, "files");
            Directory.CreateDirectory(sysDir);
            Directory.CreateDirectory(filesDir);

            // 5. Data offset for cluster streamer
            long encDataOffset = dataPartitionOffset + 0x20000;
            var reader = new DecryptedReader(this, encDataOffset, titleKeySpec);

            // 6. boot.bin + bi2.bin
            progress?.Report(new ProgressRecord("Extracting boot information...", 7));
            byte[] bootBin = reader.Read(0, 0x440);
            File.WriteAllBytes(Path.Combine(sysDir, "boot.bin"), bootBin);

            byte[] bi2Bin = reader.Read(0x440, 0x2000);
            File.WriteAllBytes(Path.Combine(sysDir, "bi2.bin"), bi2Bin);

            long dolOffset = (long)ReadU32BE(bootBin, 0x420) << 2;
            long fstOffset = (long)ReadU32BE(bootBin, 0x424) << 2;
            long fstSize = (long)ReadU32BE(bootBin, 0x428) << 2;

            // 7. apploader.img
            {
                byte[] apploaderHdr = reader.Read(0x2440, 0x20);
                int apploaderSize = ReadU32BE(apploaderHdr, 0x14);
                int apploaderTrailer = ReadU32BE(apploaderHdr, 0x18);
                int apploaderTotal = 0x20 + apploaderSize + apploaderTrailer;
                byte[] apploaderBytes = reader.Read(0x2440, apploaderTotal);
                File.WriteAllBytes(Path.Combine(sysDir, "apploader.img"), apploaderBytes);
            }

            cancellationToken.ThrowIfCancellationRequested();

            // 8. main.dol
            progress?.Report(new ProgressRecord("Extracting main.dol...", 8));
            byte[] dolHdr = reader.Read(dolOffset, 0x100);
            long maxDolEnd = 0;
            for (int i = 0; i < 7; i++)
            {
                long off = ReadU32BE(dolHdr, i * 4);
                long sz = ReadU32BE(dolHdr, 0x90 + i * 4);
                long end = off + sz;
                if (end > maxDolEnd) maxDolEnd = end;
            }
            for (int i = 0; i < 11; i++)
            {
                long off = ReadU32BE(dolHdr, 0x1C + i * 4);
                long sz = ReadU32BE(dolHdr, 0xAC + i * 4);
                long end = off + sz;
                if (end > maxDolEnd) maxDolEnd = end;
            }
            long dolSize = maxDolEnd;

            var dolPath = Path.Combine(sysDir, "main.dol");
            report.DolSha256 = ComputeSha256(reader, dolOffset, dolSize, dolPath);
            report.DolVerified = report.DolSha256.Equals(ExpectedDolSha256, StringComparison.OrdinalIgnoreCase);

            cancellationToken.ThrowIfCancellationRequested();

            // 9. fst.bin
            progress?.Report(new ProgressRecord("Reading file system table (FST)...", 10));
            byte[] fstBytes = reader.Read(fstOffset, (int)fstSize);
            File.WriteAllBytes(Path.Combine(sysDir, "fst.bin"), fstBytes);

            int entryCount = ReadU32BE(fstBytes, 8);
            report.TotalFilesInFst = entryCount;
            int stringTableOffset = entryCount * 12;

            progress?.Report(new ProgressRecord($"Extracting {entryCount} files from FST...", 12));

            long bytesExtracted = 0;
            int filesExtracted = 0;

            for (int i = 1; i < entryCount; i++)
            {
                cancellationToken.ThrowIfCancellationRequested();

                int entryOffset = i * 12;
                byte isDir = fstBytes[entryOffset];
                int nameOff = ReadU32BE(fstBytes, entryOffset) & 0x00FFFFFF;
                long fileOffset = (long)ReadU32BE(fstBytes, entryOffset + 4) << 2;
                long fileSize = (long)ReadU32BE(fstBytes, entryOffset + 8);

                int nameEnd = nameOff;
                while (nameEnd + stringTableOffset < fstBytes.Length && fstBytes[stringTableOffset + nameEnd] != 0)
                    nameEnd++;
                string entryName = Encoding.ASCII.GetString(fstBytes, stringTableOffset + nameOff, nameEnd - nameOff);

                if (isDir != 0)
                {
                    Directory.CreateDirectory(Path.Combine(filesDir, entryName));
                    continue;
                }

                string outPath = Path.Combine(filesDir, entryName);
                if (entryName.Equals("StaticR.rel", StringComparison.OrdinalIgnoreCase))
                {
                    report.RelSha256 = ComputeSha256(reader, fileOffset, fileSize, outPath);
                    report.RelVerified = report.RelSha256.Equals(ExpectedRelSha256, StringComparison.OrdinalIgnoreCase);
                }
                else
                {
                    using var outFs = new FileStream(outPath, FileMode.Create, FileAccess.Write, FileShare.None);
                    reader.StreamCopyTo(fileOffset, fileSize, outFs);
                }

                filesExtracted++;
                bytesExtracted += fileSize;

                if (i % 200 == 0 || i == entryCount - 1)
                {
                    int pct = 12 + (int)((long)i * 86 / entryCount);
                    progress?.Report(new ProgressRecord($"Extracted {i}/{entryCount} files ({entryName})...", pct));
                }
            }

            report.FilesExtracted = filesExtracted;
            report.BytesExtracted = bytesExtracted;
            report.Success = true;
            progress?.Report(new ProgressRecord("Extraction complete.", 100));
        }
        catch (OperationCanceledException)
        {
            report.Error = "Cancelled by user.";
        }
        catch (Exception ex)
        {
            report.Error = ex.Message;
        }

        return report;
    }

    private static string ComputeSha256(DecryptedReader reader, long offset, long length, string outPath)
    {
        using var sha = SHA256.Create();
        using var outFs = new FileStream(outPath, FileMode.Create, FileAccess.Write, FileShare.None);
        using var cs = new CryptoStream(outFs, sha, CryptoStreamMode.Write);
        reader.StreamCopyTo(offset, length, cs);
        cs.FlushFinalBlock();
        return Convert.ToHexString(sha.Hash!).ToLowerInvariant();
    }

    public void Dispose() => _source.Dispose();

    // --- DecryptedReader --------------------------------------------------------
    private sealed class DecryptedReader
    {
        private readonly WiiDiscImage _image;
        private readonly long _encDataOffset;
        private readonly byte[] _keySpec;
        private readonly Aes _aes;
        private readonly byte[] _clusterRaw = new byte[ClusterSize];
        private readonly byte[] _clusterDecrypted = new byte[ClusterDataSize];
        private readonly byte[] _iv = new byte[16];
        private long _cachedClusterIndex = -1;

        public DecryptedReader(WiiDiscImage image, long encDataOffset, byte[] keySpec)
        {
            _image = image;
            _encDataOffset = encDataOffset;
            _keySpec = keySpec;
            _aes = Aes.Create();
            _aes.Mode = CipherMode.CBC;
            _aes.Padding = PaddingMode.None;
            _aes.Key = _keySpec;
        }

        public byte[] Read(long offset, int size)
        {
            var result = new byte[size];
            StreamCopyTo(offset, size, new MemoryStream(result));
            return result;
        }

        public void StreamCopyTo(long offset, long length, Stream destination)
        {
            long remaining = length;
            long curOffset = offset;

            while (remaining > 0)
            {
                long clusterIndex = curOffset / ClusterDataSize;
                int offsetInCluster = (int)(curOffset % ClusterDataSize);
                int chunk = (int)Math.Min(remaining, ClusterDataSize - offsetInCluster);

                byte[] decrypted = GetCluster(clusterIndex);
                destination.Write(decrypted, offsetInCluster, chunk);

                curOffset += chunk;
                remaining -= chunk;
            }
        }

        private byte[] GetCluster(long clusterIndex)
        {
            if (_cachedClusterIndex == clusterIndex)
                return _clusterDecrypted;

            long physPos = _encDataOffset + clusterIndex * ClusterSize;
            _image.ReadAt(physPos, ClusterSize, _clusterRaw, 0);

            Array.Copy(_clusterRaw, 0x3D0, _iv, 0, 16);
            _aes.IV = _iv;

            using var transform = _aes.CreateDecryptor();
            transform.TransformBlock(_clusterRaw, ClusterHeaderSize, ClusterDataSize, _clusterDecrypted, 0);

            _cachedClusterIndex = clusterIndex;
            return _clusterDecrypted;
        }
    }

    // --- Data types -------------------------------------------------------------
    public class ExtractionReport
    {
        public bool Success { get; set; } = false;
        public string? Error { get; set; } = null;
        public string? GameId { get; set; } = null;
        public string? DolSha256 { get; set; } = null;
        public bool DolVerified { get; set; } = false;
        public string? RelSha256 { get; set; } = null;
        public bool RelVerified { get; set; } = false;
        public int TotalFilesInFst { get; set; } = 0;
        public int FilesExtracted { get; set; } = 0;
        public long BytesExtracted { get; set; } = 0;
    }
}
