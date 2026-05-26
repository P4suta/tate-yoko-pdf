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
| ネイティブ | GraalVM native-image | 1.1.0 / GraalVM 25 |
| テスト | JUnit Jupiter / AssertJ | 6.1.0 / 3.27.7 |
| 静的解析 | Error Prone / NullAway / JSpecify | 2.49.0 / 0.13.4 / 1.0.0 |
| フォーマット | Spotless + google-java-format | 8.5.1 / 1.35.0 |
| カバレッジ | JaCoCo | (Gradle 同梱) |

---

## テスト

```bash
just check    # 全テスト + 静的解析 + カバレッジ
just test     # テストのみ
```

ドメイン層は PDF非依存で純粋ユニットテスト、`SpreadServiceIntegrationTest` がプログラマティックPDFでE2E検証。JUnit 5 並列実行を有効化済み。

---

## ライセンス

[MIT License](LICENSE)

---

## 著者

**P4suta** — [GitHub](https://github.com/P4suta)
