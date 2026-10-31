# HANNYOU-Plugin（汎用プラグイン）

AI エージェントが作業の前に読む、このプラグインに固有の情報。分からない欄は空けたままにして、作者に聞く。

## 概要

観戦・進行役向けの **スペクモード**（`/hannyou spec`）のために作ったプラグイン。ほかの機能はおまけ。
おまけとして、プレイヤーに「役職」を付与して装備・インベントリ・永続ポーション効果を配る **Knights Editor** が入っている（KnightsEditor を移植し、GUI の見た目はそのままに内部を作り直したもの）。
使うのはサーバーの OP（管理者・進行役）。

## 対象

| | |
|---|---|
| サーバー | Paper 26.1.2（Spigot と CraftBukkit では動かないことがある） |
| Folia | 対応しない |
| Java | 25 |
| 形式 | plugin.yml（Bukkit）。`build.gradle.kts` の `bukkitPluginYaml { }`（resource-factory）から生成し、`src/main/resources/` には置かない |
| 依存プラグイン | なし |
| ビルド | paperweight-userdev（開発用バンドル）、shadow、run-paper。サーバーの内部（`net.minecraft`、CraftBukkit）は使っていない |

## コマンドと権限

すべて **OP だけ**（`/hannyou` の根元で `isOp()` を要求。OP でないと補完にも出ない）。コンソールからも使える。GUI も OP だけで、開いたあとも操作のたびに OP かを確かめる。権限ノードは使わない。

| コマンド | 権限 | 既定 | 内容 |
|---|---|---|---|
| `/hannyou` | OP | OP | ヘルプ |
| `/hannyou spec [対象]` | OP | OP | スペクモードにする（省略時は自分） |
| `/hannyou unspec [対象]` | OP | OP | スペクモードを解除する（省略時は自分） |
| `/hannyou reload` | OP | OP | `config.yml` を読み直す |
| `/hannyou knights list [role id]` | OP | OP | 役職一覧の GUI（コンソールではチャットに一覧）。role id を付けるとチャットに詳細と【編集】【試着】【削除】ボタン |
| `/hannyou knights editor [role id]` | OP | OP | 役職エディタ（省略で新規作成、指定で編集） |
| `/hannyou knights give <対象> <role id>` | OP | OP | 役職を付与する（セレクター可） |
| `/hannyou knights remove <role id>` | OP | OP | 削除の確認画面を開く |

## 設定ファイル

`config.yml` のキーと意味。変えたら `/hannyou reload` で反映。

| キー | 既定 | 意味 |
|---|---|---|
| `log-role-changes` | `true` | 役職の作成・編集・削除などを、サーバーのログと OP のチャットに記録する（ファイルには書かない） |
| `settings.max-roles` | `-1` | 登録できる役職の最大数（-1 = 無制限） |
| `settings.reapply-items-on-join` | `true` | 参加時に役職の装備・インベントリを配り直す（false ならポーション効果だけ付け直す） |

## データ

| 保存先 | 内容 |
|---|---|
| `plugins/HANNYOU-Plugin/knights-role.yml` | 役職データ（YAML）。各役職に `uid`（UUID）、`display_name`、`icon`、`equipment`、`inventory`、`hotbar`、`effects`。アイテムは Paper のバイナリ形式（`serializeAsBytes`）を Base64 にした文字列。削除した役職は `deleted` に uid ごとの記録（id と effects）を残す。保存は別スレッドで、書き終えてから置き換える。読めないときは `knights-role.yml.broken-<日時>` に退避する |
| プレイヤーの PDC `role_uid`、`role_id` | 付与した役職の uid と role id。参加時に、オフライン中の名前変更・削除・作り直しを見分けて、タグと効果を直す |
| プレイヤーの PDC `spec_gamemode`、`spec_team`、`spec_items` | スペク前のゲームモード・チーム・持ち物。ログアウトや再起動をまたいでスペクを続けるため |
| プレイヤーの PDC `trial_items`、`trial_effects` | 試着前の持ち物と効果 |
| スコアボードのタグ（role id） | 役職の判定に使う。役職を付与するとプレイヤーに role id のタグが付く |
| スコアボードのチーム `hannyou_spec` | スペク中の人を見えなくするためのチーム |

## 構成

| パッケージ / クラス | 役割 |
|---|---|
| `com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin` | プラグイン本体。コマンドの登録（`LifecycleEvents.COMMANDS`） |
| `Permissions` / `PluginConfig` | OP の判定 / `config.yml` の読み込み |
| `command` | Brigadier のコマンド（`HannyouCommand` が根元、`KnightsCommand`、`SpecCommand`、共通の `CommandSupport`） |
| `gui` | インベントリの GUI（`BaseGUI`、`RoleListGUI`、`RoleEditorGUI`、`ConfirmDeleteGUI`、金床の入力 `TextPrompt`、`GuiListener`） |
| `role` | 役職（`Role`、`RoleManager`、付与と参加時の照合 `RoleApplier`、試着 `TrialManager`） |
| `spec` | スペクモード（`SpecManager`、操作の禁止 `SpecRestrictions`） |
| `storage` | `knights-role.yml` の読み書き（`RoleStorage`、`RoleYaml`、`ItemCodec`） |
| `audit` | 監査ログ（`AuditLogger`） |
| `util` | 文字とボタン（`Text`） |

## 決まり

- コマンドと GUI は OP だけが使える。新しいコマンドも OP だけにする（例外にするときは作者に確かめる）。
- 画面に出す言葉は日本語。役職の ID は「role id」と呼ぶ。
- メッセージは今はコードの中に `§` の色コードで書いている（`config.yml` には置いていない）。
- コミットとプッシュは作者に頼まれたときだけ。`run/` と `RandomTeam-master/` はコミットしない。
- 作者はゲームの中で確かめてからコミットしたい。コミットの前に確認の機会をつくる。
- 作者のふだんのサーバー（ポート 25565）は止めない。こちらの確認は別のポート（例: `./gradlew runServer --args="--port 25566 --nogui"`）で行う。
- README は日本語。機能やコマンドを変えたら README も直す。

## 動作確認

テストサーバーで、何をすると何が起きるべきか（コンソールから打つコマンドと、期待する結果）。

| 操作 | 期待する結果 |
|---|---|
| 起動 | ログに `Enabling HANNYOU-Plugin` と `<n>件の役職を読み込みました` が出る |
| `hannyou knights list`（コンソールから） | `=== 役職一覧 (<n>件) ===` と役職の一覧が出る |
| `hannyou knights list <無い id>`（コンソールから） | `役職が見つかりません` が出る |
| `hannyou spec <プレイヤー>` | 対象がクリエイティブになり、ほかのプレイヤーから見えなくなる。`hannyou unspec` で元のゲームモードと持ち物に戻る |
| `/hannyou knights list`（ゲーム内の OP） | 役職一覧の GUI が開く。下段の「兵士の作成」でエディタが開く |

## ブランチ

| ブランチ | 役割 |
|---|---|
| `main` | 配布した版だけ。リリースのタグはここに付ける。直接コミットしない |
| `develop` | 次の版の変更を集める。直接コミットしない |
| `〇〇〇〇-fix` | 1 つの作業ごと（例: `udemae-fix`）。`develop` から作り、`develop` に戻す |

リリースは `develop` → `main`。GitHub には `fix` というブランチもある（以前の分け方の名残）。

## 配布

| | |
|---|---|
| 配布先 | GitHub Releases（https://github.com/ukagaka-irono/HANNYOU-Plugin/releases）。配る jar は `HANNYOU-Plugin-<版>-all.jar` |
| ライセンス | Apache License 2.0（`LICENSE` と `NOTICE` を jar の `META-INF/` に同梱） |
| 版 | `gradle.properties` の `version`（今は 0.1。タグは `v0.1`） |

## これから作るもの

- **ウデマエ**: 試合中の行動（キル・アシスト、近接と遠距離、兵士のキル、占領、勝ち）でポイントが増え、C- 〜 S+ のランクが上がる。スコアボードの `udemae` に合計値を書き出し、RandomTeam（手を加えない別のプラグイン）がチーム分けで平均を取れるようにする。`/hannyou udemae` で自分の分は全員が見られる（OP だけのルールの例外）。試合中を示すスコアの名前は未定。
- **占領機能**: ウデマエのあとで仕様を決める。
