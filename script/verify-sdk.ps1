param(
    [string]$Abi = 'arm64-v8a',
    [ValidateSet('debug', 'release')][string]$Variant = 'debug'
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-ZipText($archive, [string]$name) {
    $entry = $archive.GetEntry($name)
    if (!$entry) { throw "产物缺少文件：$name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Test-Archive([string]$path, [string]$nativePath) {
    if (!(Test-Path -LiteralPath $path)) { throw "产物不存在：$path" }
    $archive = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $native = $archive.GetEntry($nativePath)
        if (!$native -or $native.Length -lt 100000) { throw "原生引擎缺失：$path" }
        $stream = $native.Open()
        try {
            $magic = 1..4 | ForEach-Object { $stream.ReadByte() }
            if (($magic -join ',') -ne '127,69,76,70') { throw "原生库不是 ELF：$path" }
        } finally { $stream.Dispose() }

        $checksums = Read-ZipText $archive 'assets/checksums.json' | ConvertFrom-Json
        foreach ($property in $checksums.files.PSObject.Properties) {
            if (!$property.Value) { continue }
            $entry = $archive.GetEntry("assets/$($property.Name)")
            if (!$entry) { throw "校验清单中的资源缺失：$($property.Name)" }
            $stream = $entry.Open()
            $sha = [Security.Cryptography.SHA256]::Create()
            try {
                $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
                if ($hash -ne $property.Value) { throw "资源校验失败：$($property.Name)" }
            } finally {
                $stream.Dispose()
                $sha.Dispose()
            }
        }
        $schema = Read-ZipText $archive 'assets/shared/luna_pinyin.schema.yaml'
        if ($schema -notmatch '(?m)^schema:' -or $schema.Trim().StartsWith('../')) {
            throw '拼音方案仍是链接占位文本'
        }
        foreach ($name in @('essay.txt', 'luna_pinyin.dict.yaml', 'stroke.dict.yaml', 'wubi86.dict.yaml')) {
            if ($archive.GetEntry("assets/shared/$name").Length -lt 1000) {
                throw "词库内容不完整：$name"
            }
        }
        foreach ($name in @('pinyin_t9', 'double_pinyin', 'double_pinyin_flypy', 'double_pinyin_mspy', 'double_pinyin_abc', 'double_pinyin_sogou', 'double_pinyin_ziguang', 'wubi86', 'stroke')) {
            if (!$archive.GetEntry("assets/shared/$name.schema.yaml")) { throw "缺少中文方案：$name" }
        }
        foreach ($name in @('builtin.default.yaml', 'zhishurufa.trime.yaml')) {
            if (!$archive.GetEntry("assets/shared/$name")) { throw "缺少默认配置：$name" }
        }
        $theme = Read-ZipText $archive 'assets/shared/zhishurufa.trime.yaml'
        if ($theme -notmatch '(?ms)^  phone:' -or $theme -notmatch '(?ms)^  phone:.*?commit:.*\+') {
            throw '主题缺少电话键盘或加号键'
        }
        if ($path.EndsWith('.aar')) {
            $manifest = [xml](Read-ZipText $archive 'AndroidManifest.xml')
            $application = $manifest.manifest.application
            $android = 'http://schemas.android.com/apk/res/android'
            $manifestText = $manifest.OuterXml
            if ($manifestText -match '@xml/method' -or $manifestText -notmatch '@xml/trime_method') {
                throw 'SDK 清单未使用前缀化输入法方法资源'
            }
            if ($manifestText -match 'Theme\.TrimeAppTheme|Theme\.DialogTheme') {
                throw 'SDK 清单仍引用未隔离主题'
            }
            if ($application.HasAttribute('name', $android) -or $application.HasAttribute('theme', $android)) {
                throw 'SDK 清单覆盖宿主 Application 或主题'
            }
            $namespaces = [Xml.XmlNamespaceManager]::new($manifest.NameTable)
            $namespaces.AddNamespace('android', $android)
            if ($application.SelectNodes('.//category[@android:name="android.intent.category.LAUNCHER"]', $namespaces).Count -gt 0) {
                throw 'SDK 清单带入额外启动入口'
            }
        }
        Write-Output "通过：$([IO.Path]::GetFileName($path))，原生引擎、词库及资源哈希完整"
    } finally { $archive.Dispose() }
}

Test-Archive (Join-Path $root "ime-sdk/build/outputs/aar/ime-sdk-$Variant.aar") "jni/$Abi/librime_jni.so"
foreach ($module in @('app', 'sample-host')) {
    $directory = Join-Path $root "$module/build/outputs/apk/$Variant"
    $metadata = Get-Content -Raw (Join-Path $directory 'output-metadata.json') | ConvertFrom-Json
    foreach ($element in $metadata.elements) {
        if ($element.filters.Count -eq 0 -or $element.filters.value -contains $Abi) {
            Test-Archive (Join-Path $directory $element.outputFile) "lib/$Abi/librime_jni.so"
        }
    }
}
