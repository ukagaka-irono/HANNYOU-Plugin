# HANNYOU-Plugin（汎用プラグイン）

AI エージェントが作業の前に読む、このプラグインに固有の情報。分からない欄は空けたままにして、作者に聞く。

## 概要

観戦・進行役向けの **スペクモード**（`/hannyou spec`）のために作ったプラグイン。ほかの機能はおまけ。
おまけとして、プレイヤーに「役職」を付与して装備・インベントリ・永続ポーション効果を配る **Knights Editor** が入っている（KnightsEditor を移植し、GUI の見た目はそのままに内部を作り直したもの）。
試合中の活躍でランクが上がる **ウデマエ** もある（RandomTeam などのチーム分けで、各チームの強さをそろえるため）。
使うのはサーバーの OP（管理者・進行役）。ウデマエの確認だけは一般のプレイヤーも使う。

## 対象

| | |
|---|---|
| サーバー | Paper 26.1.2（Spigot と CraftBukkit では動かないことがある） |
| Java | 25 |
| 形式 | plugin.yml（Bukkit）。`build.gradle.kts` の `bukkitPluginYaml { }`（resource-factory）から生成し、`src/main/resources/` には置かない |
| 依存プラグイン | なし |
| ビルド | paperweight-userdev（開発用バンドル）、shadow、run-paper。サーバーの内部（`net.minecraft`、CraftBukkit）は使っていない |

## コマンドと権限

`/hannyou udemae`（自分のウデマエを見る）を除いて **OP だけ**（`/hannyou` の根元には条件を付けず、子のノードごとに `.requires` で `isOp()` を要求。OP でないと補完にも出ない）。コンソールからも使える。GUI も OP だけで、開いたあとも操作のたびに OP かを確かめる。権限ノードは使わない。

| コマンド | 権限 | 既定 | 内容 |
|---|---|---|---|
| `/hannyou` | なし | 全員 | ヘルプ（OP でない人には udemae の行だけ） |
| `/hannyou udemae` | なし | 全員 | 自分のウデマエを見る（プレイヤーだけ） |
| `/hannyou udemae <プレイヤー>` | OP | OP | ほかの人のウデマエを見る（オフラインでも、記録があれば名前で引ける） |

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
| `udemae.ranks` | `[C-, C, C+, B-, B, B+, A-, A, A+, S, S+]` | ランクの名前（下から順） |
| `udemae.points-per-rank` | `100` | 1つのランクの中のポイント |
| `udemae.export-objective` | `udemae` | 合計ポイントを書き出すスコア（空なら書き出さない） |
| `udemae.match.objective` / `holder` / `value` | `hannyou_match` / `""` / `1` | 試合中の判定。holder が空ならプレイヤーごとのスコア、`#game` などなら、その持ち主のスコアが value のとき試合中。**`hannyou_match` は仮の名前**（作者がデータパックのスコア名を教えてくれたら直す） |
| `udemae.win-objective` / `lose-objective` | `_game_win` / `_game_lose` | 勝ち・負けのスコア。0 から 0 以外に変わったときに一度だけ数える |
| `udemae.assist-seconds` | `10` | 倒される前の何秒以内の攻撃をアシストにするか |
| `udemae.capture.objective` / `min` / `stay-seconds` | `_game_capture` / `1` / `5` | 占領の判定（占領はデータパックが管理する）。プレイヤーごとのスコアが min 以上なら占領中。占領地に stay-seconds 秒いるごとに `capture-stay`、占領中にプレイヤーを倒すと `capture-kill` / `capture-assist` を上乗せ。**`_game_capture` は仮の名前** |
| `udemae.points.<行動>` | `UdemaeAction` の既定値 | 行動ごとのポイント（仮の数字。マイナスで減る）。キーは `UdemaeAction` を見る |

既存のサーバーの `config.yml` に `udemae` が無くても、jar の中の既定値が使われる。

## データ

| 保存先 | 内容 |
|---|---|
| `plugins/HANNYOU-Plugin/knights-role.yml` | 役職データ（YAML）。各役職に `uid`（UUID）、`display_name`、`icon`、`equipment`、`inventory`、`hotbar`、`effects`。アイテムは Paper のバイナリ形式（`serializeAsBytes`）を Base64 にした文字列。削除した役職は `deleted` に uid ごとの記録（id と effects）を残す。保存は別スレッドで、書き終えてから置き換える。読めないときは `knights-role.yml.broken-<日時>` に退避する |
| プレイヤーの PDC `role_uid`、`role_id` | 付与した役職の uid と role id。参加時に、オフライン中の名前変更・削除・作り直しを見分けて、タグと効果を直す |
| プレイヤーの PDC `spec_gamemode`、`spec_team`、`spec_items` | スペク前のゲームモード・チーム・持ち物。ログアウトや再起動をまたいでスペクを続けるため |
| プレイヤーの PDC `trial_items`、`trial_effects` | 試着前の持ち物と効果 |
| スコアボードのタグ（role id） | 役職の判定に使う。役職を付与するとプレイヤーに role id のタグが付く |
| スコアボードのチーム `hannyou_spec` | スペク中の人を見えなくするためのチーム |
| `plugins/HANNYOU-Plugin/udemae.yml` | ウデマエ。プレイヤーの UUID ごとに `name`（最後に見た名前）と `total`（合計ポイント）。ランクは total から計算するので、ランクの段階を変えても壊れない。変更は数秒ごとにまとめて別スレッドで保存する |
| スコアボードのスコア `udemae` | 合計ポイントの書き出し先（RandomTeam などが読む）。参加時と変化のたびに書く |
| エンティティの PDC `soldier_owner` | スポーンエッグで出したモブ（モブ兵士）の持ち主の UUID |

## 構成

| パッケージ / クラス | 役割 |
|---|---|
| `com.ukagaka.irono.hANNYOUPlugin.HANNYOUPlugin` | プラグイン本体。コマンドの登録（`LifecycleEvents.COMMANDS`） |
| `Permissions` / `PluginConfig` | OP の判定 / `config.yml` の読み込み |
| `command` | Brigadier のコマンド（`HannyouCommand` が根元、`KnightsCommand`、`SpecCommand`、共通の `CommandSupport`） |
| `gui` | インベントリの GUI（`BaseGUI`、`RoleListGUI`、`RoleEditorGUI`、`ConfirmDeleteGUI`、金床の入力 `TextPrompt`、`GuiListener`） |
| `role` | 役職（`Role`、`RoleManager`、付与と参加時の照合 `RoleApplier`、試着 `TrialManager`） |
| `spec` | スペクモード（`SpecManager`、操作の禁止 `SpecRestrictions`） |
| `storage` | `knights-role.yml` の読み書き（`RoleStorage`、`RoleYaml`、`ItemCodec`）と、別スレッドでの書き込み（`AsyncFileWriter`、ウデマエでも使う） |
| `udemae` | ウデマエ（`UdemaeManager`、`udemae.yml` の `UdemaeStorage`、設定 `UdemaeSettings`、行動の一覧 `UdemaeAction`、試合中・占領中・勝ち負けの判定 `MatchWatcher`、キル・アシストとモブ兵士の持ち主 `CombatTracker`） |
| `audit` | 監査ログ（`AuditLogger`） |
| `util` | 文字とボタン（`Text`） |

## 決まり

- ウデマエはコマンドで書き換えられないようにする（試合の結果だけで変わる。作者の決定で set・add・reset を消した）。
- コマンドと GUI は OP だけが使える。新しいコマンドも OP だけにする（例外にするときは作者に確かめる）。今の例外は `/hannyou udemae`（自分の分を見る）だけ。
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
| 起動後に `scoreboard objectives list` | `udemae` のスコアがある |
| `hannyou udemae`（コンソールから） | `このコマンドはプレイヤーだけが使えます` |
| `/hannyou udemae`（ゲーム内、OP でない人） | `あなたのウデマエ: C- 0/100`。`/hannyou` の補完には `udemae` だけが出る |
| 試合中のスコアを入れて、別チームの人を倒す（ゲーム内） | 倒した人に近接 +10 / 遠距離 +12、10 秒以内に攻撃していた人にアシスト。`scoreboard players get <名前> udemae` も同じ値になる |

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

- **ウデマエの残り**: 試合中を示すスコアと占領のスコアの本当の名前（作者に聞く）。ポイントが減る行動（今は `death` と `lose` が 0）。
- 占領はデータパックが管理するので、プラグインに占領機能は作らない。
