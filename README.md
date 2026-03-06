# tate-yoko-pdf

**縦書き日本語スキャンPDFを、見開きレイアウトに変換するCLIツール**

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

本ツールはソースPDFの連続する2ページを物理的に1ページへ結合し、RTL順で配置した新しいPDFを生成します。

---

## 特徴

- **ゼロ設定で実用可能** — 入力ファイルパスだけで正しい見開きPDFを生成
- **OCRテキストレイヤー保持** — FormXObject方式によりテキスト・画像・ベクターを丸ごと保持
- **不均一ページサイズ対応** — ペアごとの最大サイズに合わせて自動センタリング
- **奇数ページ・表紙対応** — 最終ページや表紙を単独見開きとして処理
- **RTL / LTR 両対応** — 縦書き（右綴じ）・横書き（左綴じ）を切り替え可能
- **Fat JAR & ネイティブバイナリ** — Shadow JAR、またはGraalVM native-imageで単一バイナリ配布

---

## クイックスタート

### 前提条件

- Java 21 以上

### ビルド

```bash
./gradlew build        # コンパイル＋全テスト実行
./gradlew shadowJar    # Fat JAR を生成
```

### 実行

```bash
# 最小構成（ゼロ設定）: RTL見開きを自動生成
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar novel.pdf
# → novel_spread.pdf が生成される

# 出力先を指定
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar novel.pdf -o output/novel_spreads.pdf

# 表紙を単独見開きに
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar novel.pdf --cover-single

# 横書きPDF用（LTR方向）
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar textbook.pdf -d LTR

# 詳細ログを有効化
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar novel.pdf -v

# ヘルプ表示
java -jar build/libs/tate-yoko-pdf-1.0.0-all.jar --help
```

### CLI オプション

| オプション | 説明 | デフォルト |
|---|---|---|
| `<input>` | 入力PDFファイルパス（必須） | — |
| `-o`, `--output` | 出力PDFファイルパス | `<input>_spread.pdf` |
| `-d`, `--direction` | 読み順: `RTL` または `LTR` | `RTL` |
| `--cover-single` | 表紙（1ページ目）を単独見開きにする | `false` |
| `-v`, `--verbose` | DEBUGレベルのログ出力を有効化 | `false` |

---

## アーキテクチャ

**ヘキサゴナルアーキテクチャ（Ports & Adapters）** を採用し、ドメインロジックをPDFライブラリから完全に隔離しています。

```
┌──────────────────────────────────────────────────────────────┐
│  CLI Layer                                                   │
│  SpreadCommand (picocli) ── 手動DI ──> Infrastructure        │
└──────────────┬───────────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────────┐
│  Application Layer                                           │
│  SpreadService (オーケストレーション)                          │
└──────┬───────────────────┬───────────────────────────────────┘
       │                   │
┌──────▼──────┐   ┌────────▼──────────────────────────────────┐
│ Domain Layer│   │  Port Layer                                │
│ (純粋Java)  │   │  SourceDocument / SpreadDocument           │
│             │   │  DocumentFactory                           │
│ Calculator  │   │                  ▲                         │
│ Pagination  │   └──────────────────┼─────────────────────────┘
└─────────────┘                      │ implements
                          ┌──────────┴─────────────────────────┐
                          │  Infrastructure Layer               │
                          │  PdfBox Implementations             │
                          └────────────────────────────────────┘
```

### 設計上のポイント

| 設計判断 | 理由 |
|---|---|
| **ドメイン層にPDF依存なし** | 純粋なユニットテストが可能。PDFBoxの差し替えも容易 |
| **Sealed Interface** | `PagePairSpec`、`PaginationStrategy` にパターンマッチングの網羅性保証 |
| **FormXObject方式** | ページ全体をカプセル化し、OCR・注釈・ベクターを透過的に保持 |
| **手動DI** | 小規模CLIツールにDIフレームワークのオーバーヘッドを持ち込まない |
| **Graphics State管理** | `saveGraphicsState`/`restoreGraphicsState` で座標変換の独立性を保証 |

---

## プロジェクト構造

```
dev.sakashita.tateyokopdf
├── domain
│   ├── model
│   │   ├── PageDimension.java        # ページ寸法（validation, max()）
│   │   ├── ReadingDirection.java     # RTL / LTR 列挙型
│   │   ├── SpreadSpec.java           # 見開き寸法
│   │   ├── LayoutPosition.java       # 配置オフセット座標
│   │   ├── SpreadLayout.java         # レイアウト計算結果
│   │   └── PagePairSpec.java         # sealed: Pair / Single
│   ├── service
│   │   └── SpreadLayoutCalculator.java  # 見開き幾何計算
│   └── strategy
│       ├── PaginationStrategy.java      # sealed interface
│       ├── StandardPagination.java      # 順次ペアリング
│       └── CoverSinglePagination.java   # 表紙単独ペアリング
├── port
│   ├── PageContent.java              # 不透明マーカーインターフェース
│   ├── PagePlacement.java            # コンテンツ＋位置のバインド
│   ├── SourceDocument.java           # ソースPDF操作インターフェース
│   ├── SpreadDocument.java           # 出力PDF操作インターフェース
│   ├── DocumentFactory.java          # ファクトリインターフェース
│   └── exception/                    # SpreadException 階層
├── infrastructure/pdfbox
│   ├── PdfBoxPageContent.java        # FormXObject遅延インポート
│   ├── PdfBoxSourceDocument.java     # CropBox/Rotation処理
│   ├── PdfBoxSpreadDocument.java     # 見開きページ組み立て
│   └── PdfBoxDocumentFactory.java    # PDF読み込み＋パスワード検出
├── application
│   ├── SpreadOptions.java            # 実行オプション（パス自動導出）
│   ├── ProgressListener.java         # 進捗コールバック
│   └── SpreadService.java            # メインオーケストレーター
└── cli
    ├── SpreadCommand.java            # picocli エントリポイント
    └── ConsoleProgressListener.java  # コンソール進捗表示
```

---

## 技術スタック

| カテゴリ | 技術 | バージョン |
|---|---|---|
| 言語 | Java | 21+ |
| PDF操作 | Apache PDFBox | 3.0.3 |
| CLI | picocli | 4.7.6 |
| ロギング | SLF4J + Logback | 1.5.12 |
| ビルド | Gradle (Kotlin DSL) | 9.2.0 |
| Fat JAR | Shadow Plugin | 9.0.0-beta12 |
| ネイティブ | GraalVM native-image | 0.10.4 |
| テスト | JUnit 5 + AssertJ | 5.11.3 / 3.26.3 |

---

## テスト

```bash
./gradlew test    # 全テスト実行
```

### テスト構成

- **ドメイン層ユニットテスト** — PDF非依存の純粋な計算ロジック
  - `SpreadLayoutCalculatorTest` — RTL/LTR配置、不均一サイズ、単独ページ
  - `StandardPaginationTest` — 偶数/奇数ページ、エッジケース
  - `CoverSinglePaginationTest` — 表紙単独シナリオ
  - `PageDimensionTest` — バリデーション、max()
- **統合テスト** — テスト用PDFをプログラマティックに生成し、エンドツーエンド検証
  - `SpreadServiceIntegrationTest` — 4/5/1ページ、表紙モード、LTR方向

---

## 処理フロー

```
入力PDF (N pages)
    │
    ▼
┌─ PaginationStrategy ─────────────┐
│  ページをペアリング                │
│  [Pair(0,1), Pair(2,3), Single(4)]│
└───────────────┬───────────────────┘
                │
    ┌───────────▼───────────┐
    │  各ペアについてループ   │
    │                       │
    │  1. pageDimension()   │  ← ソースPDFから寸法取得
    │  2. calculate()       │  ← 見開きレイアウト計算
    │  3. pageContent()     │  ← FormXObjectとしてインポート
    │  4. addSpread()       │  ← 出力PDFにページ追加
    │                       │
    └───────────┬───────────┘
                │
                ▼
          出力PDF (spread)
```

---

## ライセンス

[MIT License](LICENSE)

---

## 著者

**P4suta** — [GitHub](https://github.com/P4suta)
