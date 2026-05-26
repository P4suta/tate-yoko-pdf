# tate-yoko-pdf

**縦書き日本語スキャンPDFを、見開きレイアウトに変換するツール（CLI + ブラウザGUI）**

2つの縦長（ポートレート）ページを1つの横長（ランドスケープ）見開きページに結合し、右綴じ（RTL）の読み順を正しく再現します。どのPDFリーダーでも正しい見開き表示が得られます。

---

## 解決する課題

日本語の縦書き書籍をスキャンしてPDF化すると、各ページは個別の縦長画像として格納されます。一般的なPDFリーダーの見開きモードは横書き（LTR）前提のため、ページの左右が逆転してしまいます。

```
一般的なPDFリーダーの見開き表示（LTR）    tate-yoko-pdf による見開き変換（RTL）

┌─────────┬─────────┐              ┌─────────┬─────────┐
│         │         │              │         │         │
│  Page 1 │  Page 2 │   ──変換──>  │  Page 2 │  Page 1 │
│  (左)   │  (右)   │              │  (左)   │  (右)   │
│         │         │              │         │         │
└─────────┴─────────┘              └─────────┴─────────┘
   ← 読み順が逆！                     → 正しい読み順！
```

---

## 2つの使い方

### A. ブラウザGUI（おすすめ）

ダブルクリックで起動 → 既定ブラウザが自動で開く → PDFをドラッグして変換 → ダウンロード。
インストール不要・サーバー設定不要・ネットワーク不要（完全ローカル）。

```
$ ./tate-yoko-pdf       # 引数なしで起動
→ http://127.0.0.1:NNNN/ がブラウザで開く
→ フォームでPDFをアップロード、方向（RTL/LTR）、表紙単独を指定
→ 進捗バーが完了 → ダウンロード
→ ブラウザのタブを閉じれば、サーバーも自動シャットダウン
```

特徴:
- **WebSocketで進捗ストリーミング**（ポーリングなし）
- **タブを閉じると自動シャットダウン**（WebSocket keepaliveで生存判定、60秒の猶予あり）
- **多重起動防止**: 起動済みインスタンスがあればそのURLを開くだけ（ロックファイル + PID + /health 確認）
- **一時ファイルの自動GC**: ダウンロード完了で即削除、未ダウンロードでも1時間でGC

### B. CLI（パイプライン・自動化向け）

```bash
./tate-yoko-pdf novel.pdf                       # ゼロ設定でRTL見開き
./tate-yoko-pdf novel.pdf -o out/spread.pdf     # 出力先指定
./tate-yoko-pdf novel.pdf --cover-single        # 表紙を単独見開きに
./tate-yoko-pdf textbook.pdf -d LTR             # 横書きPDF用
./tate-yoko-pdf novel.pdf -v                    # DEBUGログ
./tate-yoko-pdf --help                          # ヘルプ
```

| オプション | 説明 | デフォルト |
|---|---|---|
| `<input>` | 入力PDFファイルパス（必須） | — |
| `-o`, `--output` | 出力PDFファイルパス | `<input>_spread.pdf` |
| `-d`, `--direction` | 読み順: `RTL` または `LTR` | `RTL` |
| `--cover-single` | 表紙を単独見開きにする | `false` |
| `-v`, `--verbose` | DEBUGレベルのログ出力 | `false` |

---

## インストール

各OSのネイティブ単一バイナリ（JREのインストール不要）を CI で 3 OS 並列にビルドしています。

| OS | 配布物 | サイズ目安 |
|---|---|---|
| Linux x86_64 | `tate-yoko-pdf` （実行可能バイナリ） | ~48 MB |
| Windows x86_64 | `tate-yoko-pdf.exe` | ~50 MB |
| macOS | `tate-yoko-pdf` | ~55 MB |

最新ビルドは [Actions の最新 run](https://github.com/P4suta/tate-yoko-pdf/actions/workflows/ci.yml) → 任意の成功 run → "Artifacts" から `tate-yoko-pdf-<os>` をダウンロードしてください。

---

## 開発

開発環境は完全にDocker内で完結します。ホスト側に必要なものは git + Docker + （任意で）mise / lefthook / just のみ。

### 初回セットアップ

```bash
mise install          # lefthook と just を入れる（任意・推奨）
lefthook install      # pre-commit / pre-push hooks を有効化（任意）
docker compose build dev
```

### 日常コマンド

```bash
just                  # 利用可能なレシピ一覧
just check            # test + spotless + errorprone + nullaway + jacoco
just test             # テストのみ
just format           # spotlessApply
just web              # JVMモードでWeb起動（http://127.0.0.1:8080/）
just web-native       # native-imageでWeb起動
just web-stop         # 停止
just shadow           # shadowJar 生成
just native           # native-image ビルド
just sample-pdf       # build/test-data/sample.pdf を生成
just typos            # 誤字スキャン
just typos-fix        # 誤字自動修正
just shell            # devコンテナでシェル
just docker-status    # Docker のディスク占有と本プロジェクトの状態を表示
just docker-tui       # lazydocker でマシン全体の Docker 状態を TUI で確認
just docker-clean     # 本プロジェクトの Docker artifacts (container/network/named volume/image) を一掃
```

`just` を入れていない場合は `docker compose run --rm dev ./gradlew <task>` 形式でも同等。

### 開発支援ツール

| ツール | 役割 |
|---|---|
| Spotless + google-java-format | 全Javaソースのフォーマット強制 |
| Error Prone | コンパイラ静的解析（数百のチェック） |
| NullAway (JSpecify mode) | null安全性検査（`@Nullable` で表現） |
| ben-manes versions plugin | 依存ライブラリの更新確認 |
| JaCoCo | テストカバレッジ |
| typos | コメント・識別子の誤字検出（自動修正） |
| lefthook | git pre-commit / pre-push hook（spotless / typos / check） |
| just | タスクランナー |

---

## アーキテクチャ

ヘキサゴナルアーキテクチャ（Ports & Adapters）を採用し、ドメインロジックをPDFライブラリから完全に隔離しています。Web層はCLI層と並列のprimary adapterとして追加。

```
                ┌──────────────────────────────────────────┐
                │   Primary Adapters                       │
                │   ┌──────────────┐  ┌──────────────────┐ │
                │   │  CLI         │  │  Web (Javalin)   │ │
                │   │  picocli     │  │  JTE + WebSocket │ │
                │   └──────┬───────┘  └────────┬─────────┘ │
                └──────────┼───────────────────┼───────────┘
                           │                   │
                ┌──────────▼───────────────────▼───────────┐
                │  Application Layer                        │
                │  SpreadService (オーケストレーション)        │
                └──────┬─────────────────────┬───────────────┘
                       │                     │
                ┌──────▼──────┐    ┌─────────▼───────────────┐
                │ Domain      │    │  Port Layer              │
                │ (純粋Java)   │    │  SourceDocument /        │
                │             │    │  SpreadDocument /        │
                │ Calculator  │    │  DocumentFactory         │
                │ Pagination  │    └─────────┬───────────────┘
                └─────────────┘              │ implements
                                  ┌──────────▼───────────────┐
                                  │  Infrastructure          │
                                  │  PDFBox 実装               │
                                  └──────────────────────────┘
```

### Web層の追加コンポーネント

```
web/
├── WebLauncher            # 起動シーケンス (lock check → server start → browser open)
├── BrowserLauncher        # OS別ブラウザ起動 (xdg-open / open / start), AWT非依存
├── routes/
│   ├── PageController     # index.jte
│   └── JobController      # submit / progress / result / download / WebSocket
├── job/
│   ├── Job + JobStatus    # sealed: Pending / Running / Completed / Failed
│   ├── JobRegistry        # ジョブとプログレスリスナーの管理
│   ├── ProgressEvent      # sealed: Started / Progress / Completed / Failed
│   └── WebProgressListener # ProgressListener 実装、pub/sub fan-out
└── lifecycle/
    ├── IdleShutdown       # /ws/keepalive の生存数で自動シャットダウン判定
    ├── SingleInstanceLock # PID + port を ~/.tate-yoko-pdf/app.lock で管理
    ├── TempFileGc         # 1時間TTLで一時ディレクトリGC
    └── WorkDirs           # 再帰削除ヘルパー
```

---

## 技術スタック

| カテゴリ | 技術 | バージョン |
|---|---|---|
| 言語 | Java | 21 (toolchain 25でビルド) |
| PDF操作 | Apache PDFBox | 3.0.7 |
| CLI | picocli | 4.7.7 |
| Web サーバ | Javalin | 7.2.2 |
| HTML テンプレ | JTE（precompiled） | 3.2.4 |
| ロギング | SLF4J + Logback | 1.5.32 |
| ビルド | Gradle (Kotlin DSL) | 9.5.1 |
| Fat JAR | Shadow Plugin | 9.4.1 |
| ネイティブ | GraalVM native-image / Liberica NIK Full (AWT 対応) | 1.1.0 / NIK 25 |
| テスト | JUnit Jupiter / AssertJ | 6.1.0 / 3.27.7 |
| 静的解析 | Error Prone / NullAway / JSpecify | 2.49.0 / 0.13.4 / 1.0.0 |
| フォーマット | Spotless + google-java-format | 8.5.1 / 1.35.0 |
| カバレッジ | JaCoCo | (Gradle 同梱) |

---

## テスト

```bash
just check    # 全テスト + 静的解析 + カバレッジ (JaCoCo 層別 threshold)
just test     # テストのみ
```

テストは多層構成:
- **Unit** (`domain.*`, `application`, `port.exception`, `observability`) — 純粋ロジック、外部依存なし
- **Property-based** (`jqwik`) — Pagination / SpreadLayoutCalculator の不変条件を 1000 ケースで検証
- **Integration** (`infrastructure.pdfbox`) — 実 PDFBox 経由で破損 / 暗号化 / 回転 PDF を扱う
- **Component** (`web.routes`, `web.job`, `web.lifecycle`) — Javalin in-process (`JavalinTest`) + `WebTestHarness`
- **CLI** (`cli`) — picocli `CommandLine#execute` を直接呼び stdout/stderr/exit code を assert

JaCoCo は層別 threshold で `check` の必須ゲート: `domain.*` 95% / `application` 85% / `infrastructure.*` 75% / `web.routes` 50% / `web.job` 85% / `web.lifecycle` 70% / `observability` 80% / 全体 78%。

---

## トラブルシュート

### エラーが出たら

エラー画面 (Web) や `Error[KIND]: ...` (CLI) に出る **ErrorKind** で原因が判別できます。

| ErrorKind | 意味 | 対処 |
|---|---|---|
| `UPLOAD_EMPTY` | PDF が添付されていない | 「PDFファイル」フィールドに `.pdf` を選択してアップロード |
| `UPLOAD_INVALID` | 拡張子が `.pdf` でない / Content-Type が違う / 先頭 4 バイトが `%PDF` でない (= 偽 PDF) / ファイル名が長すぎる | 本物の PDF を選び直す |
| `PDF_CORRUPTED` | PDF が破損している | ブラウザ等で開けるか確認、別ツールで再エクスポート |
| `PDF_PASSWORD_PROTECTED` | パスワード保護されている | 保護を解除した PDF を渡す |
| `PDF_TOO_LARGE` | アップロード上限 (500 MB) 超 | ページ削減 or 分割 |
| `PDF_NOT_FOUND` | CLI で指定したファイルが存在しない | パスを確認 |
| `PDF_INVALID_PAGE` | ページ数 ≤ 0 など不正なページ指定 | 入力 PDF を確認 |
| `PDF_WRITE_FAILED` | 出力先に書き込めない | 書き込み権限 / 空き容量を確認 |
| `JOB_NOT_FOUND` | ジョブ ID が見つからない | URL を確認、または最初からやり直す |
| `JOB_EXPIRED` / `JOB_OUTPUT_GONE` | 保存期間 (1 時間) 切れ / 出力ファイルが削除済み | 最初からやり直す |
| `INVALID_PARAMETER` | `direction` が `RTL`/`LTR` 以外、など | パラメータを確認 |
| `OUT_OF_MEMORY` | JVM ヒープ不足 | `JAVA_TOOL_OPTIONS=-Xmx1g` でヒープを増やす |
| `INTERNAL` | 上記以外の予期しないエラー | `traceId` をサポートに連絡 |

### CLI exit code (sysexits.h 風)

| code | 定数 | 意味 |
|---:|---|---|
| 0 | OK | 成功 |
| 2 | USAGE | コマンドライン使い方エラー |
| 65 | INPUT_DATA | PDF が破損 / 不正 |
| 66 | INPUT_NOTFOUND | 入力ファイルが存在しない |
| 70 | INTERNAL | 予期しないエラー |
| 73 | OUTPUT_WRITE | 出力に書き込めない |
| 74 | IO_ERROR | I/O エラー |
| 77 | PASSWORD | パスワード保護 PDF |
| 78 | CONFIG | 設定/パラメータエラー |
| 137 | OOM | OutOfMemory (= 128 + SIGKILL) |

シェルから連結する例: `tate-yoko-pdf in.pdf -o out.pdf || echo "failed: exit=$?"`。

### 詳細ログ

- **CLI**: `-v` / `--verbose` で DEBUG ログ + stack trace + technicalDetail
- **Web (console)**: stdout に `[traceId=...] [jobId=...] [LEVEL] ...` 形式
- **Web (JSON)**: 環境変数 `TATE_YOKO_LOG_FORMAT=json` で起動すると JSON 1 行/イベントに切り替え (Docker/Kubernetes でログ集約する場合に便利)
- **traceId**: HTTP レスポンスの `X-Trace-Id` ヘッダ、エラー画面の下部、WS フレーム (`"traceId":"..."`) すべて同じ 32 文字 hex ID。サポートに連絡する際はこの ID を伝える

### ヘルスチェック

| エンドポイント | 用途 | 200 / 503 の意味 |
|---|---|---|
| `GET /health/live` | プロセス生存確認 (liveness) | 200=alive、503=shutdown 中 |
| `GET /health/ready` | 依存検査 (readiness) | 200=workDir 書込可 / disk > 100MB / executor 健全、503=どれか DOWN または shutdown 中 |
| `GET /health` | 後方互換 (= `/health/ready`) | 同上 |

disk threshold は env `TATE_YOKO_HEALTH_MIN_FREE_MB` で上書き可能 (デフォルト 100MB)。

### ネイティブバイナリ (AWT / フォント)

PDFBox の内部実装は `java.awt.image.Raster` を経由してフォントとカラーマネジメントを扱うため、native-image には AWT/Swing 対応が必要です。本プロジェクトは [Liberica NIK Full](https://hub.docker.com/r/bellsoft/liberica-native-image-kit-container) (BellSoft の GraalVM 派生で AWT サポート強化版) を Docker 経由でビルドに使用し、runtime image には `fontconfig` / `libfreetype6` をバンドルしています (`Dockerfile`)。`just native` / `just web-native` でビルド・起動できます。

- **Linux**: Liberica NIK で実 PDF 変換まで動作 (CI で smoke 済)
- **macOS / Windows**: CI も Liberica NIK でビルド (`graalvm/setup-graalvm@v1` の `distribution: 'liberica'`)。AWT が同梱されるため理論上は実 PDF 変換可だが、3 OS 揃った smoke は今後の段階で追加予定。

---

## ライセンス

[MIT License](LICENSE)

---

## 著者

**P4suta** — [GitHub](https://github.com/P4suta)
